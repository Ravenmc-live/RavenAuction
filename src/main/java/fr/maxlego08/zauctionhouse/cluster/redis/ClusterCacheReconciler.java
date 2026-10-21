package fr.maxlego08.zauctionhouse.cluster.redis;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import redis.clients.jedis.params.SetParams;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

final class ClusterCacheReconciler {
    private static final List<StorageType> STORES = List.of(StorageType.LISTED, StorageType.EXPIRED, StorageType.PURCHASED);
    private static final String UNLOCK = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    private final AuctionPlugin plugin;
    private final RedisConnection connection;
    private final BooleanSupplier current;
    private CompletableFuture<Void> ongoing;

    ClusterCacheReconciler(AuctionPlugin plugin, RedisConnection connection, BooleanSupplier current) {
        this.plugin = plugin;
        this.connection = connection;
        this.current = current;
    }

    synchronized CompletableFuture<Void> reconcile() {
        if (!this.current.getAsBoolean()) return CompletableFuture.failedFuture(new CancellationException());
        if (this.ongoing != null && !this.ongoing.isDone()) return this.ongoing;
        CompletableFuture<Map<StorageType, Map<Integer, Item>>> captured = new CompletableFuture<>();
        try {
            this.plugin.getScheduler().runNextTick(task -> {
                if (!this.current.getAsBoolean()) { captured.completeExceptionally(new CancellationException()); return; }
                Map<StorageType, Map<Integer, Item>> snapshot = new EnumMap<>(StorageType.class);
                for (StorageType type : STORES) {
                    Map<Integer, Item> items = new HashMap<>();
                    for (Item item : this.plugin.getAuctionManager().getItems(type)) items.put(item.getId(), item);
                    snapshot.put(type, items);
                }
                captured.complete(snapshot);
            });
        } catch (RuntimeException exception) { captured.completeExceptionally(exception); }
        this.ongoing = captured.orTimeout(10, TimeUnit.SECONDS).thenCompose(snapshot ->
                CompletableFuture.supplyAsync(() -> this.plugin.getStorageManager().with(ItemRepository.class).select(),
                        this.plugin.getExecutorService()).thenCompose(rows -> reconcile(snapshot, rows)))
                .orTimeout(30, TimeUnit.SECONDS);
        return this.ongoing;
    }

    private CompletableFuture<Void> reconcile(Map<StorageType, Map<Integer, Item>> snapshot, List<ItemDTO> rows) {
        if (!this.current.getAsBoolean()) return CompletableFuture.completedFuture(null);
        Map<Integer, StorageType> authoritative = new HashMap<>();
        for (ItemDTO row : rows) authoritative.put(row.id(), row.storage_type());
        Set<Integer> candidates = new LinkedHashSet<>(authoritative.keySet());
        for (var bucket : snapshot.values()) candidates.addAll(bucket.keySet());
        candidates.removeIf(id -> STORES.stream().allMatch(type ->
                (authoritative.get(id) == type) == snapshot.get(type).containsKey(id)));
        List<Integer> ids = new ArrayList<>(candidates);
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        for (int index = 0; index < ids.size(); index += 8) {
            List<Integer> batch = ids.subList(index, Math.min(index + 8, ids.size()));
            result = result.thenCompose(ignored -> CompletableFuture.allOf(batch.stream()
                    .map(id -> refresh(id, snapshot, authoritative.containsKey(id))).toArray(CompletableFuture[]::new)));
        }
        return result;
    }

    private CompletableFuture<Void> refresh(int id, Map<StorageType, Map<Integer, Item>> snapshot, boolean existed) {
        if (!this.current.getAsBoolean()) return CompletableFuture.completedFuture(null);
        String key = this.connection.getConfiguration().lockKey(id);
        String token = "reconcile:" + UUID.randomUUID();
        long ttl = this.connection.getConfiguration().lockTtlMs();
        long started = System.nanoTime();
        return CompletableFuture.supplyAsync(() -> {
            try (var jedis = this.connection.getResource()) {
                return "OK".equals(jedis.set(key, token, SetParams.setParams().nx().px(ttl)));
            }
        }, this.plugin.getExecutorService()).thenCompose(locked -> {
            if (!locked) return CompletableFuture.completedFuture(null);
            CompletableFuture<Void> operation;
            try {
                operation = this.plugin.getStorageManager().selectItem(id).thenCompose(item -> {
                    CompletableFuture<Void> applied = new CompletableFuture<>();
                    this.plugin.getScheduler().runNextTick(task -> {
                        try {
                            if (this.current.getAsBoolean() && System.nanoTime() - started < TimeUnit.MILLISECONDS.toNanos(ttl * 3 / 4)) {
                                apply(id, snapshot, existed, item);
                            }
                            applied.complete(null);
                        } catch (Exception exception) { applied.completeExceptionally(exception); }
                    });
                    return applied;
                }).orTimeout(Math.max(1, ttl * 4 / 5), TimeUnit.MILLISECONDS);
            } catch (RuntimeException exception) { operation = CompletableFuture.failedFuture(exception); }
            return operation.whenComplete((result, error) -> release(key, token));
        });
    }

    private void apply(int id, Map<StorageType, Map<Integer, Item>> snapshot, boolean existed, Item fresh) {
        var manager = this.plugin.getAuctionManager();
        for (StorageType type : STORES) {
            Item cached = manager.getItem(type, id);
            if (cached != snapshot.get(type).get(id) || (cached != null && !stable(cached.getStatus()))) return;
        }
        if (fresh == null && existed) return;
        StorageType destination = fresh == null ? null : switch (fresh.getStatus()) {
            case AVAILABLE -> StorageType.LISTED;
            case REMOVED -> StorageType.EXPIRED;
            case PURCHASED -> StorageType.PURCHASED;
            default -> null;
        };
        if (fresh != null && destination == null && fresh.getStatus() != ItemStatus.DELETED) return;
        if (destination == StorageType.LISTED) this.plugin.getCategoryManager().applyCategories(fresh);
        for (StorageType type : STORES) if (manager.getItem(type, id) != null) manager.removeItem(type, id);
        if (destination != null) manager.addItem(destination, fresh);
        manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH, PlayerCacheKey.ITEMS_SELLING,
                PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED);
        Item listed = snapshot.get(StorageType.LISTED).get(id);
        if (listed != null || destination == StorageType.LISTED) manager.updateListedItems(fresh != null ? fresh : listed,
                destination == StorageType.LISTED, null);
    }

    private static boolean stable(ItemStatus status) {
        return status == ItemStatus.AVAILABLE || status == ItemStatus.REMOVED || status == ItemStatus.PURCHASED || status == ItemStatus.DELETED;
    }

    private void release(String key, String token) {
        try {
            CompletableFuture.runAsync(() -> {
                try (var jedis = this.connection.getResource()) { jedis.eval(UNLOCK, List.of(key), List.of(token)); }
            }, this.plugin.getExecutorService()).exceptionally(error -> null);
        } catch (RejectedExecutionException ignored) {}
    }
}

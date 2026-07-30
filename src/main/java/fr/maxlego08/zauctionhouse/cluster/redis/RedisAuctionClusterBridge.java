package fr.maxlego08.zauctionhouse.cluster.redis;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.cluster.redis.message.ClusterMessage;
import fr.maxlego08.zauctionhouse.cluster.redis.message.ClusterMessageType;
import org.bukkit.entity.Player;
import redis.clients.jedis.params.SetParams;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * {@link AuctionClusterBridge} backed by Redis.
 * <p>
 * Item locks are plain keys written with {@code SET NX PX}: the first server to write the key owns
 * the item, and the expiry guarantees a crashed server cannot hold an item hostage. Releases use a
 * compare-and-delete script so a server can never drop a lock that has since been taken over by
 * another one after an expiry.
 * <p>
 * State changes are broadcast on a single pub/sub channel. Payloads intentionally carry identifiers
 * rather than serialized items: receivers re-read the authoritative row from the shared database,
 * which keeps them correct even if messages arrive out of order or a server was briefly offline.
 * <p>
 * Every operation is executed on the plugin executor so no Redis round-trip ever runs on the main
 * server thread.
 */
public class RedisAuctionClusterBridge implements AuctionClusterBridge {

    /**
     * Releases a lock only when it is still the one we acquired, which makes unlocking safe after a
     * TTL expiry handed the item to another server.
     */
    private static final String UNLOCK_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final AuctionPlugin plugin;
    private final RedisConnection connection;
    private final Executor executor;
    private final String sourceId;
    private final String serverName;

    public RedisAuctionClusterBridge(AuctionPlugin plugin, RedisConnection connection, String sourceId) {
        this.plugin = plugin;
        this.connection = connection;
        this.executor = plugin.getExecutorService();
        this.sourceId = sourceId;
        this.serverName = plugin.getConfiguration().getServerName();
    }

    @Override
    public CompletableFuture<Boolean> checkAvailability(Item item) {
        return CompletableFuture.supplyAsync(() -> {
            try (var jedis = this.connection.getResource()) {
                return !jedis.exists(lockKey(item));
            }
        }, this.executor);
    }

    @Override
    public CompletableFuture<LockToken> lockItem(Item item, UUID buyerId, StorageType storageType) {
        return CompletableFuture.supplyAsync(() -> {

            // The token identifies this acquisition uniquely, so an unlock can prove ownership.
            String token = this.sourceId + ':' + UUID.randomUUID();
            var params = SetParams.setParams().nx().px(this.connection.getConfiguration().lockTtlMs());

            try (var jedis = this.connection.getResource()) {
                String result = jedis.set(lockKey(item), token, params);
                // A null reply means the key already existed: another server owns the item.
                return result == null ? LockToken.noop() : new LockToken(token);
            }
        }, this.executor);
    }

    @Override
    public CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType) {

        if (lockToken == null || LockToken.noop().value().equals(lockToken.value())) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try (var jedis = this.connection.getResource()) {
                jedis.eval(UNLOCK_SCRIPT, List.of(lockKey(item)), List.of(lockToken.value()));
            }
        }, this.executor);
    }

    @Override
    public CompletableFuture<Void> notifyItemBought(Player player, Item item) {
        return publish(ClusterMessage.of(ClusterMessageType.ITEM_BOUGHT, this.sourceId, this.serverName)
                .with(ClusterMessage.KEY_ITEM_ID, item.getId())
                .with(ClusterMessage.KEY_BUYER_UUID, player.getUniqueId())
                .with(ClusterMessage.KEY_BUYER_NAME, player.getName())
                .with(ClusterMessage.KEY_SELLER_UUID, item.getSellerUniqueId())
                .build());
    }

    @Override
    public CompletableFuture<Void> notifyItemListed(Item item) {
        return publish(ClusterMessage.of(ClusterMessageType.ITEM_LISTED, this.sourceId, this.serverName)
                .with(ClusterMessage.KEY_ITEM_ID, item.getId())
                .with(ClusterMessage.KEY_SELLER_UUID, item.getSellerUniqueId())
                .build());
    }

    @Override
    public CompletableFuture<Void> notifyItemStatusChange(Item item, ItemStatus oldStatus, ItemStatus newStatus) {
        return publish(ClusterMessage.of(ClusterMessageType.ITEM_STATUS_CHANGED, this.sourceId, this.serverName)
                .with(ClusterMessage.KEY_ITEM_ID, item.getId())
                .with(ClusterMessage.KEY_OLD_STATUS, oldStatus)
                .with(ClusterMessage.KEY_NEW_STATUS, newStatus)
                .build());
    }

    @Override
    public CompletableFuture<Void> removeItem(Item item, StorageType storageType) {
        return removeItem(item, storageType, null);
    }

    @Override
    public CompletableFuture<Void> removeItem(Item item, StorageType sourceStorageType, StorageType destinationStorageType) {
        return publish(ClusterMessage.of(ClusterMessageType.ITEM_REMOVED, this.sourceId, this.serverName)
                .with(ClusterMessage.KEY_ITEM_ID, item.getId())
                .with(ClusterMessage.KEY_SOURCE_STORAGE, sourceStorageType)
                .with(ClusterMessage.KEY_DESTINATION_STORAGE, destinationStorageType)
                .with(ClusterMessage.KEY_SELLER_UUID, item.getSellerUniqueId())
                .build());
    }

    @Override
    public boolean isDistributed() {
        return true;
    }

    private CompletableFuture<Void> publish(ClusterMessage message) {
        return CompletableFuture.runAsync(() -> {
            try (var jedis = this.connection.getResource()) {
                jedis.publish(this.connection.getConfiguration().channel(), message.encode());
            }
        }, this.executor).exceptionallyCompose(throwable -> {
            // Callers time out and unwind on failure; log the cause here so the reason a server did
            // not converge is visible instead of being hidden behind a generic timeout.
            this.plugin.getLogger().warning("Failed to broadcast " + message.getType() + " to the cluster: " + throwable.getMessage());
            return CompletableFuture.failedFuture(throwable);
        });
    }

    private String lockKey(Item item) {
        return this.connection.getConfiguration().lockKey(item.getId());
    }
}

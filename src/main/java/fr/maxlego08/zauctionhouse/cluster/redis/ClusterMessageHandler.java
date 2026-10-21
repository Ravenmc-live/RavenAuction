package fr.maxlego08.zauctionhouse.cluster.redis;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.cluster.redis.message.ClusterMessage;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

/**
 * Applies events broadcast by the other servers of the cluster to this server's state.
 * <p>
 * Two rules keep the caches trustworthy. First, messages carry identifiers only, so anything that
 * has to be added to a cache is re-read from the shared database and the authoritative row decides
 * the outcome; a stale or out-of-order message can therefore never resurrect a sold item. Second,
 * every cache mutation and every player-facing effect is applied on the main thread, while database
 * reads stay on the listener/async side.
 */
public class ClusterMessageHandler implements Consumer<ClusterMessage> {

    /**
     * Buckets scanned when an item has to be located or purged without knowing where it sits.
     */
    private static final StorageType[] CACHED_STORAGE_TYPES = {StorageType.LISTED, StorageType.EXPIRED, StorageType.PURCHASED};

    private final AuctionPlugin plugin;
    private final String sourceId;
    private final java.util.function.BooleanSupplier current;
    private final Map<Integer, CompletableFuture<Void>> pending = new HashMap<>();

    public ClusterMessageHandler(AuctionPlugin plugin, String sourceId) {
        this(plugin, sourceId, () -> true);
    }

    public ClusterMessageHandler(AuctionPlugin plugin, String sourceId, java.util.function.BooleanSupplier current) {
        this.plugin = plugin;
        this.sourceId = sourceId;
        this.current = current;
    }

    @Override
    public void accept(ClusterMessage message) {

        // Redis delivers our own publications back to us; applying them would undo the local work
        // that produced them.
        if (!this.current.getAsBoolean() || this.sourceId.equals(message.getSourceId())) return;

        if (this.plugin.getConfiguration().isEnableDebug()) {
            this.plugin.getLogger().info("[Cluster] Received " + message);
        }

        int itemId = message.getInt(ClusterMessage.KEY_ITEM_ID, -1);
        if (itemId <= 0) {
            this.plugin.getLogger().warning("[Cluster] Ignored a " + message.getType() + " message without a valid item id.");
            return;
        }

        synchronized (this.pending) {
            CompletableFuture<Void> previous = this.pending.getOrDefault(itemId, CompletableFuture.completedFuture(null));
            CompletableFuture<Void> next = previous.handle((result, error) -> null).thenCompose(ignored -> {
                if (!this.current.getAsBoolean()) return CompletableFuture.completedFuture(null);
                return switch (message.getType()) {
                    case ITEM_LISTED -> handleItemListed(itemId);
                    case ITEM_BOUGHT -> handleItemBought(message, itemId);
                    case ITEM_STATUS_CHANGED -> handleStatusChanged(message, itemId);
                    case ITEM_REMOVED -> handleItemRemoved(message, itemId);
                };
            });
            this.pending.put(itemId, next);
            next.whenComplete((result, error) -> {
                synchronized (this.pending) { this.pending.remove(itemId, next); }
            });
        }
    }

    /**
     * A remote server listed an item: load it and publish it in the local listings.
     */
    private CompletableFuture<Void> handleItemListed(int itemId) {

        var manager = this.plugin.getAuctionManager();
        if (manager.getItem(StorageType.LISTED, itemId) != null) return CompletableFuture.completedFuture(null);

        return selectItem(itemId, dbItem -> {

            // Only genuinely listed rows are shown. An item sold or cancelled between the broadcast
            // and this read must not appear here.
            if (dbItem == null || dbItem.getStatus() != ItemStatus.AVAILABLE || dbItem.isExpired()) return CompletableFuture.completedFuture(null);

            return runSync(() -> {
                if (manager.getItem(StorageType.LISTED, itemId) != null) return;

                // selectItem does not categorize, and an uncategorized item disappears from every
                // category view.
                this.plugin.getCategoryManager().applyCategories(dbItem);

                manager.addItem(StorageType.LISTED, dbItem);
                manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
                manager.updateListedItems(dbItem, true, null);
            });
        });
    }

    /**
     * A remote server sold an item: drop it from the listings, hand the purchased copy to the buyer
     * when this server hosts them, and tell the seller in real time when they are connected here.
     */
    private CompletableFuture<Void> handleItemBought(ClusterMessage message, int itemId) {

        var manager = this.plugin.getAuctionManager();
        var sellerUniqueId = message.getUniqueId(ClusterMessage.KEY_SELLER_UUID);
        var buyerUniqueId = message.getUniqueId(ClusterMessage.KEY_BUYER_UUID);
        var buyerName = message.getString(ClusterMessage.KEY_BUYER_NAME);

        // Captured before the removal so the seller notification can still describe the item even
        // when the buying server deleted the row (give-item mode).
        var cached = manager.getItem(StorageType.LISTED, itemId);

        return selectItem(itemId, dbItem -> runSync(() -> {

            var listed = manager.getItem(StorageType.LISTED, itemId);
            if (listed != null) {
                listed.setStatus(ItemStatus.PURCHASED);
                manager.removeItem(StorageType.LISTED, itemId);
                manager.updateListedItems(listed, false, null);
            }
            manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);

            // give-item=false keeps the item in purchased storage until the buyer collects it, so it
            // has to exist in this server's cache too if the buyer connects here.
            if (dbItem != null && dbItem.getStatus() == ItemStatus.PURCHASED) {
                manager.addItem(StorageType.PURCHASED, dbItem);
            }

            var buyer = buyerUniqueId == null ? null : Bukkit.getPlayer(buyerUniqueId);
            if (buyer != null) {
                manager.clearPlayerCache(buyer, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.HISTORY_DATA);
                manager.updateInventory(buyer);
            }

            notifySeller(itemId, sellerUniqueId, cached != null ? cached : dbItem, buyerName);
        }));
    }

    /**
     * Sends the sale message to a seller connected to this server and prevents the offline
     * "while you were away" summary from repeating it on their next join.
     */
    private void notifySeller(int itemId, UUID sellerUniqueId, Item reference, String buyerName) {

        if (sellerUniqueId == null) return;

        var seller = Bukkit.getPlayer(sellerUniqueId);
        if (seller == null) return;

        this.plugin.getScheduler().runAtEntity(seller, task -> {
            if (!this.current.getAsBoolean() || !seller.isOnline()) return;
            var manager = this.plugin.getAuctionManager();
            manager.clearPlayerCache(seller, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.HISTORY_DATA, PlayerCacheKey.PENDING_MONEY_DATA);

            if (reference != null) {
                // Same amount the offline sales summary reports for this sale, so both paths agree.
                String price = this.plugin.getEconomyManager().format(reference.getAuctionEconomy(), reference.getPrice());
                manager.message(seller, Message.ITEM_BOUGHT_SELLER, "%items%", reference.getItemDisplay(), "%price%", price, "%seller%", seller.getName(), "%buyer%", buyerName == null ? "?" : buyerName);
            }

            // The seller has now been told, so the log must not be replayed to them on join.
            this.plugin.getStorageManager().markPurchaseLogAsRead(itemId, sellerUniqueId);

            manager.updateInventory(seller);
        });
    }

    /**
     * Mirrors a lifecycle status change, for instance an item entering the transient
     * "being purchased" state on another server so it stops being clickable here.
     */
    private CompletableFuture<Void> handleStatusChanged(ClusterMessage message, int itemId) {

        var newStatus = message.getEnum(ClusterMessage.KEY_NEW_STATUS, ItemStatus.class);
        if (newStatus == null) return CompletableFuture.completedFuture(null);

        var manager = this.plugin.getAuctionManager();

        return runSync(() -> {
            // The item may sit in any bucket: a status change for an expired or purchased entry is
            // just as meaningful as one for a listing.
            for (StorageType storageType : CACHED_STORAGE_TYPES) {
                var item = manager.getItem(storageType, itemId);
                if (item == null) continue;

                item.setStatus(newStatus);

                if (storageType == StorageType.LISTED) {
                    manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
                    // AVAILABLE means the remote operation was rolled back and the item is for sale
                    // again; anything else takes it out of the buyable listings.
                    manager.updateListedItems(item, newStatus == ItemStatus.AVAILABLE, null);
                }
            }
        });
    }

    /**
     * Applies a removal. The explicit destination sent by the origin server is preferred, and the
     * database is used both to confirm it and as the fallback for older messages that carry none.
     */
    private CompletableFuture<Void> handleItemRemoved(ClusterMessage message, int itemId) {

        var source = message.getEnum(ClusterMessage.KEY_SOURCE_STORAGE, StorageType.class);
        var destination = message.getEnum(ClusterMessage.KEY_DESTINATION_STORAGE, StorageType.class);
        var manager = this.plugin.getAuctionManager();

        return selectItem(itemId, dbItem -> runSync(() -> {

            var removed = source == null ? null : manager.getItem(source, itemId);
            if (source != null) {
                manager.removeItem(source, itemId);
            }

            // The row is gone (or DELETED, which selectItem filters out): the item is finished
            // everywhere, so purge any copy that survived in another bucket.
            if (dbItem == null) {
                for (StorageType storageType : CACHED_STORAGE_TYPES) {
                    manager.removeItem(storageType, itemId);
                }
            } else if (destination == null || destination == StorageType.EXPIRED || destination == StorageType.PURCHASED) {
                // Move it to the bucket the database actually reports rather than the one the message
                // claims, which keeps a lost or reordered message from creating a claimable ghost.
                StorageType actual = switch (dbItem.getStatus()) {
                    case REMOVED -> StorageType.EXPIRED;
                    case PURCHASED -> StorageType.PURCHASED;
                    default -> null;
                };
                if (actual != null && actual != source) {
                    manager.addItem(actual, dbItem);
                }
            }

            manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SEARCH);

            if (source == StorageType.LISTED && removed != null) {
                manager.updateListedItems(removed, false, null);
            }
        }));
    }

    /**
     * Reads an item from the shared database off the main thread, logging instead of propagating a
     * failure so one bad row cannot break the listener.
     *
     * @param itemId   item to read
     * @param consumer receives the row, or {@code null} when it no longer exists
     */
    private CompletableFuture<Void> selectItem(int itemId, Function<Item, CompletableFuture<Void>> consumer) {
        return this.plugin.getStorageManager().selectItem(itemId).orTimeout(10, TimeUnit.SECONDS)
                .thenCompose(item -> this.current.getAsBoolean() ? consumer.apply(item) : CompletableFuture.completedFuture(null))
                .exceptionally(throwable -> {
                    this.plugin.getLogger().warning("[Cluster] Failed to read item " + itemId + " from the database: " + throwable.getClass().getSimpleName());
                    return null;
                });
    }

    private CompletableFuture<Void> runSync(Runnable runnable) {
        CompletableFuture<Void> applied = new CompletableFuture<>();
        try {
            this.plugin.getScheduler().runNextTick(wrappedTask -> {
                if (applied.isDone()) return;
                try {
                    if (this.current.getAsBoolean()) runnable.run();
                    applied.complete(null);
                } catch (Exception exception) { applied.completeExceptionally(exception); }
            });
        } catch (Exception exception) { applied.completeExceptionally(exception); }
        return applied.orTimeout(10, TimeUnit.SECONDS);
    }
}

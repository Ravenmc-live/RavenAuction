package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.purchase.AuctionPrePurchaseItemEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.api.services.AuctionPurchaseService;
import fr.maxlego08.zauctionhouse.api.services.result.PurchaseFailReason;
import fr.maxlego08.zauctionhouse.api.services.result.PurchaseResult;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class PurchaseService extends AuctionService implements AuctionPurchaseService {

    private final AuctionPlugin plugin;

    public PurchaseService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<PurchaseResult> purchaseItem(Player player, Item item) {

        var event = new AuctionPrePurchaseItemEvent(item, player);
        if (!event.callEvent()) {
            return CompletableFuture.completedFuture(PurchaseResult.failure("Event cancelled", PurchaseFailReason.EVENT_CANCELLED));
        }

        var auctionManager = this.plugin.getAuctionManager();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var logger = this.plugin.getLogger();
        var auctionEconomy = item.getAuctionEconomy();

        var price = item.getPrice();
        var taxConfig = auctionEconomy.getTaxConfiguration();
        final BigDecimal requiredBalance;
        if (taxConfig.isEnabled() && taxConfig.getTaxType() == TaxType.CAPITALISM) {
            var taxResult = auctionEconomy.calculatePurchaseTax(player, price, null);
            requiredBalance = taxResult.hasTax() ? taxResult.finalPrice() : price;
        } else {
            requiredBalance = price;
        }

        var configuration = this.plugin.getConfiguration().getActions().purchased();
        if (configuration.giveItem() && configuration.freeSpace() && !item.canReceiveItem(player)) {
            message(this.plugin, player, Message.NOT_ENOUGH_SPACE);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Not enough space", PurchaseFailReason.INSUFFICIENT_SPACE));
        }

        // 1. Vérifier si l'item est expiré
        if (item.isExpired()) {
            auctionManager.getCache(player).remove(PlayerCacheKey.ITEMS_LISTED);
            auctionManager.openMainAuction(player);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Item expired", PurchaseFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.IS_PURCHASE_CONFIRM) {
            auctionManager.openMainAuction(player);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Item not in purchase state", PurchaseFailReason.ITEM_NOT_IN_PURCHASE_STATE));
        }

        // Store the lock token for cleanup on exception
        final AtomicReference<LockToken> tokenHolder = new AtomicReference<>(null);
        final AtomicReference<PurchaseResult> resultHolder = new AtomicReference<>(null);
        final AtomicReference<ItemStatus> previousStatusHolder = new AtomicReference<>(item.getStatus());

        // Load timeout configuration
        var performanceConfig = this.plugin.getConfiguration().getPerformance();

        // 2. Vérifier si l'item est lock (with timeout)
        return clusterBridge.checkAvailability(item)
                .orTimeout(performanceConfig.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
                .thenCompose(available -> {

                    if (!available) {
                        inventoryManager.updateInventory(player);
                        resultHolder.set(PurchaseResult.failure("Item not available", PurchaseFailReason.ITEM_NOT_AVAILABLE));
                        return failedFuture(new IllegalStateException("Item indisponible"));
                    }

                    return clusterBridge.lockItem(item, player.getUniqueId(), StorageType.LISTED)
                            .orTimeout(performanceConfig.lockItemTimeoutMs(), TimeUnit.MILLISECONDS);

                }).thenCompose(token -> {
                    // Store token for exception cleanup
                    tokenHolder.set(token);

                    // Check if lock was acquired (noop token means lock failed)
                    if (LockToken.noop().value().equals(token.value())) {
                        inventoryManager.updateInventory(player);
                        resultHolder.set(PurchaseResult.failure("Lock failed", PurchaseFailReason.LOCK_FAILED));
                        return failedFuture(new IllegalStateException("Item déjà en cours d'achat"));
                    }

                    // Set status AFTER acquiring lock to ensure atomicity
                    item.setStatus(ItemStatus.IS_BEING_PURCHASED);
                    return clusterBridge.notifyItemStatusChange(item, previousStatusHolder.get(), ItemStatus.IS_BEING_PURCHASED)
                            .orTimeout(performanceConfig.notifyStatusChangeTimeoutMs(), TimeUnit.MILLISECONDS)
                            // Authoritative re-validation UNDER LOCK before charging anything.
                            // The in-memory item may be stale across servers; in particular, a
                            // purchase that completed on another server but crashed before its SOLD
                            // broadcast leaves a stale lock that lock recovery would let us re-acquire.
                            // selectItem() returns null when the row is DELETED (give-item=true sold)
                            // and a non-null item with a buyer set when it is PURCHASED
                            // (give-item=false sold). Either case means the item is already sold:
                            // abort cleanly, nothing has been withdrawn yet.
                            .thenCompose(v -> this.plugin.getStorageManager().selectItem(item.getId())
                                    .orTimeout(performanceConfig.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS))
                            .thenCompose(dbItem -> {
                                if (dbItem == null || dbItem.getBuyerUniqueId() != null) {
                                    inventoryManager.updateInventory(player);
                                    resultHolder.set(PurchaseResult.failure("Item already sold", PurchaseFailReason.ITEM_NOT_AVAILABLE));
                                    return failedFuture(new IllegalStateException("Item already sold on another server"));
                                }
                                return auctionEconomy.has(player.getUniqueId(), requiredBalance);
                            });

                }).thenCompose(hasMoney -> {

                    var token = tokenHolder.get();

                    if (hasMoney) {
                        return auctionManager.purchaseItem(player, item)
                                .thenCompose(v -> clusterBridge.notifyItemBought(player, item)
                                        .orTimeout(performanceConfig.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS)
                                        .exceptionally(error -> null))
                                .thenCompose(v -> clusterBridge.unlockItem(item, token, StorageType.LISTED)
                                        .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                                        .exceptionally(error -> null))
                                .thenApply(v -> {
                                    resultHolder.set(PurchaseResult.success("Purchase successful", true));
                                    return resultHolder.get();
                                });
                    }

                    // Insufficient funds - unlock, restore status, and notify
                    fr.maxlego08.zauctionhouse.utils.PurchaseFeedback.insufficientFunds(this.plugin, player);
                    resultHolder.set(PurchaseResult.failure("Insufficient funds", PurchaseFailReason.INSUFFICIENT_FUNDS));
                    var previousStatus = previousStatusHolder.get();
                    item.setStatus(previousStatus);
                    return clusterBridge.notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, previousStatus)
                            .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                            .thenCompose(v -> clusterBridge.unlockItem(item, token, StorageType.LISTED)
                                    .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS))
                            .thenApply(v -> resultHolder.get());

                }).exceptionally(e -> {
                    // Handle timeout exceptions specifically
                    if (e.getCause() instanceof TimeoutException) {
                        logger.warning("Purchase operation timed out for item " + item.getId());
                    } else if (e.getCause() instanceof IllegalStateException) {
                        logger.warning("Purchase unavailable for item " + item.getId() + ": " + e.getMessage());
                    } else {
                        logger.severe("Error during purchase for item " + item.getId() + ": " + e.getMessage());
                    }

                    // Ensure lock is released on any exception
                    var token = tokenHolder.get();
                    if (token != null && !LockToken.noop().value().equals(token.value())) {
                        clusterBridge.unlockItem(item, token, StorageType.LISTED).exceptionally(unlockError -> {
                            logger.severe("Failed to unlock item after error: " + unlockError.getMessage());
                            return null;
                        });
                    }

                    // Restore item status only if it was changed (lock was acquired)
                    if (item.getStatus() == ItemStatus.IS_BEING_PURCHASED) {
                        var previousStatus = previousStatusHolder.get();
                        item.setStatus(previousStatus);
                        clusterBridge.notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, previousStatus)
                                .exceptionally(restoreError -> {
                                    logger.severe("Failed to restore item status for item " + item.getId() + ": " + restoreError.getMessage());
                                    return null;
                                });
                    }

                    // Return the previously set result or a generic error
                    var result = resultHolder.get();
                    return result != null ? result : PurchaseResult.failure("Internal error", PurchaseFailReason.INTERNAL_ERROR);
                });
    }
}

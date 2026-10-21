package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cluster.*;
import fr.maxlego08.zauctionhouse.api.event.events.purchase.AuctionPrePurchaseItemEvent;
import fr.maxlego08.zauctionhouse.api.item.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchaseClusterFailureTest {
    @Test void aFailedPostCommitBroadcastOrUnlockDoesNotTurnASuccessfulPurchaseIntoAFailure() {
        verifyPurchase(true);
    }
    @Test void aFailedPreCommitRedisLockDoesNotChargeOrPurchaseAnything() {
        verifyPurchase(false);
    }
    private void verifyPurchase(boolean lockWorks) {
        AuctionPlugin plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Item item = mock(Item.class, RETURNS_DEEP_STUBS); when(item.getPrice()).thenReturn(BigDecimal.TEN);
        AtomicReference<ItemStatus> status = new AtomicReference<>(ItemStatus.IS_PURCHASE_CONFIRM);
        when(item.getStatus()).thenAnswer(call -> status.get());
        doAnswer(call -> { status.set(call.getArgument(0)); return null; }).when(item).setStatus(any());
        AuctionClusterBridge bridge = mock(AuctionClusterBridge.class);
        when(plugin.getAuctionClusterBridge()).thenReturn(bridge);
        when(bridge.checkAvailability(item)).thenReturn(CompletableFuture.completedFuture(true));
        LockToken token = new LockToken("test-lock");
        when(bridge.lockItem(eq(item), any(), any())).thenReturn(lockWorks ? CompletableFuture.completedFuture(token)
                : CompletableFuture.failedFuture(new IllegalStateException("Redis unavailable")));
        when(bridge.notifyItemStatusChange(eq(item), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(bridge.notifyItemBought(player, item)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("network loss after DB commit")));
        when(bridge.unlockItem(eq(item), any(), any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Redis disconnected")));
        Item fresh = mock(Item.class);
        when(plugin.getStorageManager().selectItem(anyInt())).thenReturn(CompletableFuture.completedFuture(fresh));
        var economy = item.getAuctionEconomy();
        when(economy.has(any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        var performance = plugin.getConfiguration().getPerformance();
        when(performance.checkAvailabilityTimeoutMs()).thenReturn(1000L);
        when(performance.lockItemTimeoutMs()).thenReturn(1000L);
        when(performance.notifyStatusChangeTimeoutMs()).thenReturn(1000L);
        when(performance.notifyItemActionTimeoutMs()).thenReturn(1000L);
        when(performance.unlockItemTimeoutMs()).thenReturn(1000L);
        var manager = plugin.getAuctionManager();
        when(manager.purchaseItem(player, item)).thenAnswer(call -> {
            status.set(ItemStatus.PURCHASED); return CompletableFuture.completedFuture(null);
        });
        try (var events = mockConstruction(AuctionPrePurchaseItemEvent.class,
                (event, context) -> when(event.callEvent()).thenReturn(true))) {
            var result = new PurchaseService(plugin).purchaseItem(player, item).join();
            assertEquals(lockWorks, result.success());
            if (lockWorks) {
                verify(manager).purchaseItem(player, item);
                assertEquals(ItemStatus.PURCHASED, status.get());
                verify(bridge).notifyItemBought(player, item);
            } else {
                verify(manager, never()).purchaseItem(any(), any());
                verify(economy, never()).has(any(), any());
            }
        }
    }
}

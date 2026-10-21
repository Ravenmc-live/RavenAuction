package fr.maxlego08.zauctionhouse.loader;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.utils.PerformanceDebug;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;

class ShulkerPurchaseRoutingTest {
    @Test
    void singleShulkerUsesSixRowDialog() throws Exception {
        check(Material.SHULKER_BOX, Inventories.PURCHASE_CONFIRM, "shulker-purchase-confirm");
    }

    @Test
    void normalItemKeepsTheExistingThreeRowDialog() throws Exception {
        check(Material.DIAMOND_SWORD, Inventories.PURCHASE_CONFIRM, "purchase-confirm");
    }

    @Test
    void otherConfirmationTypesAreNotRedirected() throws Exception {
        check(Material.RED_SHULKER_BOX, Inventories.REMOVE_CONFIRM, "remove-confirm");
    }

    @Test
    void callsFromAnotherRegionAreScheduledExactlyOnce() throws Exception {
        check(Material.DIAMOND_SWORD, Inventories.PURCHASE_CONFIRM, "purchase-confirm", false);
    }

    private void check(Material material, Inventories type, String expected) throws Exception {
        check(material, type, expected, true);
    }

    private void check(Material material, Inventories type, String expected, boolean owned) throws Exception {
        var plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        var manager = mock(InventoryManager.class);
        var player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        when(scheduler.runAtEntity(eq(player), any())).thenAnswer(call -> {
            ((Consumer<WrappedTask>) call.getArgument(1)).accept(null);
            return CompletableFuture.completedFuture(null);
        });
        var item = mock(AuctionItem.class);
        var stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(item.getItemStacks()).thenReturn(List.of(stack));
        when(plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ITEM_SHOW)).thenReturn(item);
        var definition = mock(Inventory.class);
        when(manager.getInventory(plugin, expected)).thenReturn(Optional.of(definition));
        var loader = mock(ZInventoriesLoader.class, CALLS_REAL_METHODS);
        set(loader, "plugin", plugin);
        set(loader, "inventoryManager", manager);
        set(loader, "performanceDebug", mock(PerformanceDebug.class));
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.isOwnedByCurrentRegion(player)).thenReturn(owned);
            loader.openInventory(player, type, 1);
            if (owned) verify(scheduler, never()).runAtEntity(any(), any());
            else verify(scheduler).runAtEntity(eq(player), any());
        }
        verify(manager).openInventoryWithOldInventories(player, definition, 1);
    }

    private void set(ZInventoriesLoader loader, String name, Object value) throws Exception {
        var field = ZInventoriesLoader.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(loader, value);
    }
}

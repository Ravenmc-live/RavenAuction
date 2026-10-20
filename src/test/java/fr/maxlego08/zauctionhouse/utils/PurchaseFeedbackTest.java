package fr.maxlego08.zauctionhouse.utils;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseFeedbackTest {
    @Test
    void insufficientFundsOnlyPlaysVillagerSoundOnPlayerRegionAndNeverReplacesListing() throws Exception {
        AuctionPlugin plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        List<Consumer<WrappedTask>> tasks = new ArrayList<>();
        when(scheduler.runAtEntity(any(), any())).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return CompletableFuture.completedFuture(null);
        });
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        Location at = new Location(mock(World.class), 1, 64, 2);
        when(player.getLocation()).thenReturn(at);
        Item item = mock(Item.class, RETURNS_DEEP_STUBS);
        when(item.getPrice()).thenReturn(BigDecimal.TEN);
        when(item.getAuctionEconomy().has(player.getUniqueId(), BigDecimal.TEN)).thenReturn(CompletableFuture.completedFuture(false));
        when(plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.PURCHASE_ITEM)).thenReturn(false);
        ListedItemsButton button = mock(ListedItemsButton.class, CALLS_REAL_METHODS);
        Field field = ListedItemsButton.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(button, plugin);
        InventoryEngine inventory = mock(InventoryEngine.class);
        var method = ListedItemsButton.class.getDeclaredMethod("processPurchase", Player.class, InventoryEngine.class,
                int.class, Item.class, ItemStack.class, Inventories.class);
        method.setAccessible(true);
        method.invoke(button, player, inventory, 4, item, mock(ItemStack.class), Inventories.PURCHASE_CONFIRM);
        verifyNoInteractions(inventory);
        verify(player, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
        assertEquals(1, tasks.size());
        tasks.getFirst().accept(null);
        verify(player).playSound(at, "minecraft:entity.villager.no", 0.6f, 1.0f);
        verify(player, never()).sendMessage(anyString());
        verify(plugin.getAuctionManager(), never()).message(eq(player), any(), any(Object[].class));
        verify(plugin.getAuctionManager().getCache(player)).set(PlayerCacheKey.PURCHASE_ITEM, false);
    }
}

package fr.maxlego08.zauctionhouse.services;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;

class SaleItemRecoveryTest {
    AuctionPlugin plugin;
    PlatformScheduler scheduler;
    Player player;
    World world;
    Location location;
    PlayerInventory inventory;
    ItemStack item;
    ItemStack copy;

    @BeforeEach
    void setup() {
        plugin = mock(AuctionPlugin.class);
        scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        player = mock(Player.class);
        world = mock(World.class);
        location = new Location(world, 2, 64, 4);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        when(player.isOnline()).thenReturn(true);
        item = mock(ItemStack.class);
        copy = mock(ItemStack.class);
        when(item.clone()).thenReturn(copy);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        when(scheduler.runAtLocation(eq(location), any())).thenAnswer(call -> {
            ((Consumer<WrappedTask>) call.getArgument(1)).accept(null);
            return CompletableFuture.completedFuture(null);
        });
        when(scheduler.runAtEntityWithFallback(eq(player), any(), any())).thenAnswer(call -> {
            ((Consumer<WrappedTask>) call.getArgument(1)).accept(null);
            return CompletableFuture.completedFuture(null);
        });
    }

    @Test
    void onlinePlayerReceivesClonedItemsOnEntityThread() {
        SaleItemRecovery.returnItems(plugin, player, location, List.of(item));
        verify(inventory).addItem(copy);
        verifyNoInteractions(world);
    }

    @Test
    void onlyOverflowIsDroppedWhenInventoryIsFull() {
        ItemStack overflow = mock(ItemStack.class);
        when(inventory.addItem(copy)).thenReturn(new HashMap<>(Map.of(0, overflow)));
        SaleItemRecovery.returnItems(plugin, player, location, List.of(item));
        verify(world).dropItemNaturally(location, overflow);
    }

    @Test
    void disconnectedPlayerItemsDropOnTheCapturedLocationRegion() {
        when(player.isOnline()).thenReturn(false);
        SaleItemRecovery.returnItems(plugin, player, location, List.of(item));
        verify(scheduler).runAtLocation(eq(location), any());
        verify(world).dropItemNaturally(location, copy);
        verifyNoInteractions(inventory);
    }

    @Test
    void retiredEntityFallbackCannotDuplicateReturns() {
        when(scheduler.runAtEntityWithFallback(eq(player), any(), any())).thenAnswer(call -> {
            ((Runnable) call.getArgument(2)).run();
            ((Consumer<WrappedTask>) call.getArgument(1)).accept(null);
            return CompletableFuture.completedFuture(null);
        });
        SaleItemRecovery.returnItems(plugin, player, location, List.of(item));
        verify(world).dropItemNaturally(location, copy);
        verifyNoInteractions(inventory);
    }
}

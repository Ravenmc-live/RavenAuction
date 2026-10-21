package fr.maxlego08.zauctionhouse.services;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.services.result.SellFailReason;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellServiceTest {
    AuctionPlugin plugin;
    AuctionManager manager;
    AuctionEconomy economy;
    PlatformScheduler scheduler;
    Player player;
    PlayerInventory inventory;
    ItemStack item;
    Location location;
    World world;
    SellService service;
    CompletableFuture<AuctionItem> creation;

    @BeforeEach
    void setup() {
        plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        manager = mock(AuctionManager.class);
        economy = mock(AuctionEconomy.class, RETURNS_DEEP_STUBS);
        scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        location = new Location(world, 1, 64, 2);
        when(player.getLocation()).thenReturn(location);
        when(player.getWorld()).thenReturn(world);
        item = mock(ItemStack.class);
        when(item.getType()).thenReturn(mock(Material.class));
        when(item.getAmount()).thenReturn(1);
        when(item.clone()).thenReturn(item);
        when(item.isSimilar(item)).thenReturn(true);
        when(inventory.getItem(0)).thenReturn(item);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        when(economy.getMinPrice(ItemType.AUCTION)).thenReturn(BigDecimal.ONE);
        when(economy.getMaxPrice(ItemType.AUCTION)).thenReturn(BigDecimal.valueOf(1000));
        when(plugin.getConfiguration().getPermission().getLimit(ItemType.AUCTION, player)).thenReturn(10);
        creation = new CompletableFuture<>();
        when(plugin.getStorageManager().createAuctionItem(eq(player), any(), anyLong(), anyList(), eq(economy))).thenReturn(creation);
        when(scheduler.runAtEntityWithFallback(eq(player), any(), any())).thenAnswer(call -> {
            ((Consumer<WrappedTask>) call.getArgument(1)).accept(null);
            return CompletableFuture.completedFuture(null);
        });
        when(scheduler.runAtLocation(any(), any())).thenAnswer(call -> {
            ((Consumer<WrappedTask>) call.getArgument(1)).accept(null);
            return CompletableFuture.completedFuture(null);
        });
        service = new SellService(plugin, manager);
    }

    @Test
    void failedDatabaseWriteRestoresItemsAndDropsOverflow() {
        ItemStack overflow = mock(ItemStack.class);
        when(inventory.addItem(item)).thenReturn(new HashMap<>(Map.of(0, overflow)));
        var result = service.sellAuctionItems(player, BigDecimal.TEN, 0, Map.of(0, item), economy);
        verify(inventory).setItem(0, (ItemStack) null);
        creation.completeExceptionally(new IllegalStateException("storage failed"));
        assertEquals(SellFailReason.DATABASE_ERROR, result.join().failReason());
        verify(inventory).addItem(item);
        verify(world).dropItemNaturally(location, overflow);
    }

    @Test
    void notificationFailureAfterPersistingMustNotRefundOrDuplicateTheItems() {
        AuctionItem saved = mock(AuctionItem.class);
        var categories = plugin.getCategoryManager();
        doThrow(new IllegalStateException("notification failed")).when(categories).applyCategories(saved);
        var result = service.sellAuctionItems(player, BigDecimal.TEN, 0, Map.of(0, item), economy);
        creation.complete(saved);
        assertTrue(result.join().success());
        verify(inventory, never()).addItem(any(ItemStack.class));
        verify(economy, never()).deposit(any(), any(), any());
    }

    @Test
    void synchronousStorageExceptionAlsoReturnsItems() {
        when(plugin.getStorageManager().createAuctionItem(eq(player), any(), anyLong(), anyList(), eq(economy)))
                .thenThrow(new IllegalStateException("storage failed immediately"));
        var result = service.sellAuctionItems(player, BigDecimal.TEN, 0, Map.of(0, item), economy);
        assertEquals(SellFailReason.DATABASE_ERROR, result.join().failReason());
        verify(inventory).addItem(item);
    }
    @Test
    void minecartSoundPlaysOnlyAfterTheListingHasBeenSaved() {
        when(scheduler.runAtEntity(eq(player), any())).thenAnswer(call -> {
            ((Consumer<WrappedTask>) call.getArgument(1)).accept(null);
            return CompletableFuture.completedFuture(null);
        });
        service = new SellService(plugin, manager) {
            @Override
            protected void message(AuctionPlugin plugin, org.bukkit.command.CommandSender sender,
                                   fr.maxlego08.zauctionhouse.api.messages.Message message, Object... arguments) {
            }
        };
        AuctionItem saved = mock(AuctionItem.class);
        when(saved.getItemStacks()).thenReturn(List.of());
        var result = service.sellAuctionItems(player, BigDecimal.TEN, 0, Map.of(0, item), economy);
        assertFalse(result.isDone());
        verify(player, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
        creation.complete(saved);
        assertTrue(result.join().success());
        verify(player).playSound(location, "minecraft:entity.minecart.riding", 0.35f, 1.0f);
    }

    @Test
    void failedSaveNeverPlaysTheSuccessfulListingSound() {
        var result = service.sellAuctionItems(player, BigDecimal.TEN, 0, Map.of(0, item), economy);
        creation.completeExceptionally(new IllegalStateException("database unavailable"));
        assertFalse(result.join().success());
        verify(player, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
    }
}

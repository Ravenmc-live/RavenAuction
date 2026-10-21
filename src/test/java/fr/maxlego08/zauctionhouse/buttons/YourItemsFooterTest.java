package fr.maxlego08.zauctionhouse.buttons;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.engine.ItemButton;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YourItemsFooterTest {
    AuctionPlugin plugin;
    Player player;
    InventoryEngine engine;
    ItemMeta meta;
    Map<Integer, ItemButton> buttons;
    YourItemsFooter footer;

    @BeforeEach
    void setup() {
        plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.isOnline()).thenReturn(true);
        engine = mock(InventoryEngine.class, RETURNS_DEEP_STUBS);
        when(engine.getMenuInventory().getPlugin()).thenReturn(plugin);
        when(engine.getMenuInventory().getFileName()).thenReturn("combined-items");
        when(engine.getSpigotInventory().getSize()).thenReturn(54);
        var top = engine.getSpigotInventory();
        when(player.getOpenInventory().getTopInventory()).thenReturn(top);
        buttons = new HashMap<>();
        buttons.put(49, mock(ItemButton.class));
        when(engine.getItems()).thenReturn(buttons);
        when(engine.getOldInventories()).thenReturn(List.of());
        meta = mock(ItemMeta.class);
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        when(scheduler.runAtEntity(eq(player), any())).thenAnswer(call -> {
            ((Consumer<WrappedTask>)call.getArgument(1)).accept(null);
            return CompletableFuture.completedFuture(null);
        });
        footer = new YourItemsFooter(plugin);
    }

    @Test
    void pageOneReplacesTheOldFooterWithARedReturnPaneAndAnEmptyMiddle() {
        when(engine.getPage()).thenReturn(1);
        when(plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.CURRENT_PAGE, 1)).thenReturn(3);
        var auction = mock(Inventory.class);
        var manager = plugin.getInventoriesLoader().getInventoryManager();
        when(manager.getInventory(plugin, "auction")).thenReturn(Optional.of(auction));
        try (var construction = mockConstruction(ItemStack.class, (item, context) -> {
            assertEquals(Material.RED_STAINED_GLASS_PANE, context.arguments().getFirst());
            when(item.getItemMeta()).thenReturn(meta);
        })) {
            footer.onInventoryPostOpen(player, engine);
            assertFalse(buttons.containsKey(49));
            verify(engine.getSpigotInventory()).setItem(49, null);
            assertSame(construction.constructed().getFirst(), buttons.get(45).getDisplayItem());
            var name = ArgumentCaptor.forClass(Component.class);
            verify(meta).displayName(name.capture());
            assertEquals("Return", PlainTextComponentSerializer.plainText().serialize(name.getValue()));
            assertEquals(0xFF1F1F, name.getValue().color().value());
            assertEquals(net.kyori.adventure.text.format.TextDecoration.State.FALSE,
                    name.getValue().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC));
            var lore = ArgumentCaptor.forClass(List.class);
            verify(meta).lore(lore.capture());
            assertEquals(2, lore.getValue().size());
            Component line = (Component) lore.getValue().get(1);
            assertEquals("Return to the auction house.", PlainTextComponentSerializer.plainText().serialize(line));
            assertEquals(net.kyori.adventure.text.format.TextDecoration.State.FALSE,
                    line.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC));
            buttons.get(45).onClick(mock(InventoryClickEvent.class));
            verify(manager).openInventory(player, auction, 3, List.of());
        }
    }

    @Test
    void laterPagesUseAnArrowThatActuallyOpensThePreviousPage() {
        when(engine.getPage()).thenReturn(3);
        var history = mock(Inventory.class);
        when(engine.getOldInventories()).thenReturn(List.of(history));
        try (var construction = mockConstruction(ItemStack.class, (item, context) -> {
            assertEquals(Material.ARROW, context.arguments().getFirst());
            when(item.getItemMeta()).thenReturn(meta);
        })) {
            footer.apply(player, engine);
            assertEquals(1, construction.constructed().size());
            buttons.get(45).onClick(mock(InventoryClickEvent.class));
            verify(plugin.getInventoriesLoader().getInventoryManager())
                    .openInventory(player, engine.getMenuInventory(), 2, List.of(history));
            assertFalse(buttons.containsKey(49));
        }
    }

    @Test
    void mainAuctionFooterIsNotChanged() {
        when(engine.getMenuInventory().getFileName()).thenReturn("auction");
        footer.apply(player, engine);
        assertTrue(buttons.containsKey(49));
        verify(engine.getSpigotInventory(), never()).setItem(anyInt(), any());
    }
    @Test
    void preOpenReadsTheDefinitionArgumentBeforeZMenuInitializesTheNewEngine() {
        var definition = engine.getMenuInventory();
        var source = mock(InventoryEngine.class, RETURNS_DEEP_STUBS);
        when(source.getMenuInventory().getPlugin()).thenReturn(plugin);
        when(source.getMenuInventory().getFileName()).thenReturn("auction");
        when(source.getPage()).thenReturn(4);
        when(player.getOpenInventory().getTopInventory().getHolder()).thenReturn(source);
        footer.onInventoryPreOpen(player, mock(InventoryEngine.class), 1, definition, List.of());
        verify(plugin.getAuctionManager().getCache(player)).set(PlayerCacheKey.CURRENT_PAGE, 4);
    }
}

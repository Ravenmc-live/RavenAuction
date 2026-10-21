package fr.maxlego08.zauctionhouse.services;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShulkerViewServiceTest {
    ZAuctionPlugin plugin;
    InventoryManager manager;
    PlatformScheduler scheduler;
    Player player;
    InventoryView view;
    InventoryEngine origin;
    Inventory source;
    Inventory history;
    Inventory preview;
    org.bukkit.inventory.Inventory sourceTop;
    org.bukkit.inventory.Inventory previewTop;
    org.bukkit.inventory.Inventory crafting;
    ItemStack shulker;
    ShulkerViewService service;
    List<Consumer<WrappedTask>> queued;

    private static org.mockito.MockedStatic<io.papermc.paper.registry.RegistryAccess> registryAccess;

    @org.junit.jupiter.api.BeforeAll
    static void initializePaperMenuRegistry() {
        registryAccess = mockStatic(io.papermc.paper.registry.RegistryAccess.class);
        var access = mock(io.papermc.paper.registry.RegistryAccess.class, RETURNS_DEEP_STUBS);
        registryAccess.when(io.papermc.paper.registry.RegistryAccess::registryAccess).thenReturn(access);
        var menus = org.bukkit.Registry.MENU;
        doAnswer(call -> mock(org.bukkit.inventory.MenuType.Typed.class))
                .when(menus).getOrThrow(any(org.bukkit.NamespacedKey.class));
        InventoryType.values();
    }

    @org.junit.jupiter.api.AfterAll
    static void closePaperMenuRegistry() {
        if (registryAccess != null) registryAccess.close();
    }

    @BeforeEach
    void setup() {
        plugin = mock(ZAuctionPlugin.class, RETURNS_DEEP_STUBS);
        manager = mock(InventoryManager.class);
        when(plugin.getInventoriesLoader().getInventoryManager()).thenReturn(manager);
        scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        queued = new ArrayList<>();
        when(scheduler.runAtEntity(any(), any())).thenAnswer(call -> { queued.add(call.getArgument(1)); return null; });
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(mock(org.bukkit.Location.class));
        view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        sourceTop = mock(org.bukkit.inventory.Inventory.class);
        previewTop = mock(org.bukkit.inventory.Inventory.class);
        crafting = mock(org.bukkit.inventory.Inventory.class);
        when(crafting.getType()).thenReturn(InventoryType.CRAFTING);
        when(view.getTopInventory()).thenReturn(sourceTop);
        origin = mock(InventoryEngine.class);
        source = mock(Inventory.class);
        history = mock(Inventory.class);
        when(origin.getMenuInventory()).thenReturn(source);
        when(origin.getSpigotInventory()).thenReturn(sourceTop);
        when(origin.getPage()).thenReturn(4);
        when(origin.getOldInventories()).thenReturn(List.of(history));
        preview = mock(Inventory.class);
        when(preview.getPlugin()).thenReturn(plugin);
        when(preview.getFileName()).thenReturn("shulker-preview");
        var previewEngine = mock(InventoryEngine.class);
        when(previewEngine.getMenuInventory()).thenReturn(preview);
        when(previewTop.getHolder()).thenReturn(previewEngine);
        when(manager.getInventory(plugin, "shulker-preview")).thenReturn(Optional.of(preview));
        doAnswer(call -> { when(view.getTopInventory()).thenReturn(previewTop); return null; })
                .when(manager).openInventory(player, preview, 1, List.of());
        shulker = mock(ItemStack.class);
        when(shulker.getType()).thenReturn(Material.RED_SHULKER_BOX);
        service = new ShulkerViewService(plugin);
    }

    private void open() {
        service.open(player, shulker, origin);
        assertEquals(1, queued.size());
        queued.removeFirst().accept(null);
        var event = mock(InventoryOpenEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(previewTop);
        service.onOpen(event);
    }

    private void close(InventoryCloseEvent.Reason reason) {
        var event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(previewTop);
        when(event.getReason()).thenReturn(reason);
        service.onClose(event);
    }

    @Test
    void closingReturnsToTheSameDefinitionPageAndHistoryWithoutResettingFilters() {
        open();
        assertEquals("Red Shulker Box", service.title(player));
        assertEquals(27, service.contents(player).length);
        verify(player).playSound(any(org.bukkit.Location.class), eq("minecraft:block.shulker_box.open"), eq(0.5f), eq(1.0f));
        close(InventoryCloseEvent.Reason.PLAYER);
        when(view.getTopInventory()).thenReturn(crafting);
        queued.removeFirst().accept(null);
        verify(manager).openInventory(player, source, 4, List.of(history));
        verify(plugin, never()).getAuctionManager();
        verify(player).playSound(any(org.bukkit.Location.class), eq("minecraft:block.shulker_box.close"), eq(0.5f), eq(1.0f));
    }

    @Test
    void rightClickCannotOpenAnOldPreviewAfterThePlayerChangesMenus() {
        service.open(player, shulker, origin);
        when(view.getTopInventory()).thenReturn(crafting);
        queued.removeFirst().accept(null);
        verify(manager, never()).openInventory(eq(player), any(Inventory.class), anyInt(), anyList());
    }

    @Test
    void switchingToAnotherMenuDoesNotReopenTheAuctionOverIt() {
        open();
        close(InventoryCloseEvent.Reason.OPEN_NEW);
        assertTrue(queued.isEmpty());
    }

    @Test
    void pendingReturnDoesNotReplaceANewerOpenInventory() {
        open();
        close(InventoryCloseEvent.Reason.PLAYER);
        when(view.getTopInventory()).thenReturn(sourceTop);
        queued.removeFirst().accept(null);
        verify(manager, never()).openInventory(player, source, 4, List.of(history));
    }

    @Test
    void quitClearsThePreviewAndPreventsAQueuedReturn() {
        open();
        close(InventoryCloseEvent.Reason.PLAYER);
        var event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        service.onQuit(event);
        when(view.getTopInventory()).thenReturn(crafting);
        queued.removeFirst().accept(null);
        verify(manager, never()).openInventory(player, source, 4, List.of(history));
        assertEquals("Shulker Box", service.title(player));
    }

    @Test
    void disableClearsStateAndStopsPendingReturns() {
        open();
        close(InventoryCloseEvent.Reason.PLAYER);
        service.close();
        when(view.getTopInventory()).thenReturn(crafting);
        queued.removeFirst().accept(null);
        verify(manager, never()).openInventory(player, source, 4, List.of(history));
        assertEquals("Shulker Box", service.title(player));
    }

    @Test
    void everyClickAndDragInThePreviewIsReadOnly() {
        open();
        var click = mock(InventoryClickEvent.class);
        when(click.getView()).thenReturn(view);
        service.onClick(click);
        verify(click).setCancelled(true);
        var drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view);
        service.onDrag(drag);
        verify(drag).setCancelled(true);
    }
    @Test
    void snapshotRemainsAvailableWhileZMenuQueuesTheActualWindowOpen() {
        doNothing().when(manager).openInventory(player, preview, 1, List.of());
        service.open(player, shulker, origin);
        queued.removeFirst().accept(null);
        assertEquals("Red Shulker Box", service.title(player));
        verify(player, never()).playSound(any(org.bukkit.Location.class), anyString(), anyFloat(), anyFloat());
    }
}

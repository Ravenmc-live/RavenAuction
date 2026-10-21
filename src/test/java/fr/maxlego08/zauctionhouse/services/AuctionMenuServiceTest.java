package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.buttons.NextButton;
import fr.maxlego08.menu.api.button.buttons.PreviousButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.sound.SoundOption;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.utils.IntArrayList;
import fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.HashMap;
import java.util.List;
import static org.mockito.Mockito.*;

class AuctionMenuServiceTest {
    @Test
    void cachedSortRebuildsOnlyContentAndControlsWithoutReopeningTheWindow() {
        var plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        var player = mock(Player.class, RETURNS_DEEP_STUBS);
        var engine = mock(InventoryEngine.class, RETURNS_DEEP_STUBS);
        when(player.isOnline()).thenReturn(true);
        var top = engine.getSpigotInventory();
        when(player.getOpenInventory().getTopInventory()).thenReturn(top);
        var listed = mock(ListedItemsButton.class);
        when(listed.getSlots()).thenReturn(List.of(0, 1));
        when(engine.getMenuInventory().getButtons(ListedItemsButton.class)).thenReturn(List.of(listed));
        when(engine.getPage()).thenReturn(1);
        when(engine.getMaxPage()).thenReturn(1);
        when(engine.getItems()).thenReturn(new HashMap<>());
        var ids = new IntArrayList(); ids.add(10); ids.add(20);
        when(plugin.getAuctionManager().getItemIdsListedForSale(player)).thenReturn(ids);
        var changed = mock(Button.class);
        var next = mock(NextButton.class);
        when(engine.getButtons()).thenReturn(List.of(listed, changed, next));
        AuctionMenuService.refreshListings(plugin, player, engine, changed);
        verify(engine).buildButton(listed);
        verify(engine).buildButton(changed);
        verify(engine).buildButton(next);
        verify(top).setItem(0, null);
        verify(top).setItem(1, null);
        verify(plugin.getInventoriesLoader().getInventoryManager(), never()).updateInventory(any());
        verify(plugin.getInventoriesLoader().getInventoryManager(), never())
                .openInventory(any(), any(fr.maxlego08.menu.api.Inventory.class), anyInt(), anyList());
    }

    @Test
    void installedPaginationButtonsAndTheirBoundaryButtonsUseThePageTurnSound() {
        var plugin = mock(AuctionPlugin.class);
        var player = mock(Player.class);
        var location = mock(Location.class);
        when(player.getLocation()).thenReturn(location);
        var engine = mock(InventoryEngine.class, RETURNS_DEEP_STUBS);
        when(engine.getMenuInventory().getPlugin()).thenReturn(plugin);
        var next = mock(NextButton.class);
        var previous = mock(PreviousButton.class);
        var boundary = mock(Button.class);
        when(next.hasElseButton()).thenReturn(true);
        when(next.getElseButton()).thenReturn(boundary);
        when(engine.getButtons()).thenReturn(List.of(next, previous));
        new AuctionMenuService(plugin).onInventoryPostOpen(player, engine);
        var sound = ArgumentCaptor.forClass(SoundOption.class);
        verify(next).setSoundOption(sound.capture());
        verify(previous).setSoundOption(sound.getValue());
        verify(boundary).setSoundOption(sound.getValue());
        sound.getValue().play(player);
        verify(player).playSound(location, "minecraft:item.book.page_turn", 0.35f, 1.0f);
    }
}

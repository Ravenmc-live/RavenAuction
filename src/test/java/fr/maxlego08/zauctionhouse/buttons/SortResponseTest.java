package fr.maxlego08.zauctionhouse.buttons;

import com.tcoded.folialib.impl.PlatformScheduler;
import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.api.utils.IntArrayList;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

class SortResponseTest {
    @Test
    void normalSortingDoesNotQueueAsyncWorkOrAnotherEntityTick() {
        var plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        var player = mock(Player.class, RETURNS_DEEP_STUBS);
        var cache = plugin.getAuctionManager().getCache(player);
        when(plugin.getConfiguration().getSort().defaultSort()).thenReturn(SortItem.DECREASING_DATE);
        when(cache.get(PlayerCacheKey.ITEM_SORT_LOADING)).thenReturn(false);
        when(cache.get(PlayerCacheKey.ITEM_SORT, SortItem.DECREASING_DATE)).thenReturn(SortItem.DECREASING_DATE);
        when(cache.get(PlayerCacheKey.SEARCH_QUERY)).thenReturn(null);
        var engine = mock(InventoryEngine.class, RETURNS_DEEP_STUBS);
        var top = engine.getSpigotInventory();
        when(player.getOpenInventory().getTopInventory()).thenReturn(top);
        when(player.isOnline()).thenReturn(true);
        var listed = mock(fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton.class);
        when(listed.getSlots()).thenReturn(List.of(0, 1));
        when(engine.getMenuInventory().getButtons(fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton.class))
                .thenReturn(List.of(listed));
        when(engine.getPage()).thenReturn(1);
        when(engine.getMaxPage()).thenReturn(1);
        when(engine.getItems()).thenReturn(new java.util.HashMap<>());
        when(engine.getButtons()).thenReturn(List.of(listed));
        var ids = new IntArrayList(); ids.add(10); ids.add(20);
        when(plugin.getAuctionManager().getItemIdsListedForSale(player)).thenReturn(ids);
        var button = new SortButton(plugin, "", "", mock(MenuItemStack.class),
                List.of(SortItem.DECREASING_DATE, SortItem.ASCENDING_DATE));
        button.setPlugin(mock(fr.maxlego08.menu.api.MenuPlugin.class, RETURNS_DEEP_STUBS));
        var event = mock(InventoryClickEvent.class);
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            button.onClick(player, event, engine, 51, new Placeholders());
        }
        verify(cache).set(PlayerCacheKey.ITEM_SORT, SortItem.ASCENDING_DATE);
        verify(cache).set(PlayerCacheKey.ITEM_SORT_LOADING, false);
        verify(event).setCancelled(true);
        verify(engine).buildButton(listed);
        verify(engine).buildButton(button);
        verifyNoInteractions(scheduler);
        verify(plugin.getInventoriesLoader().getInventoryManager(), never()).updateInventory(any());
    }
}

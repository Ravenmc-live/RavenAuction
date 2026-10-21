package fr.maxlego08.zauctionhouse.buttons;

import com.tcoded.folialib.impl.PlatformScheduler;
import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.utils.IntArrayList;
import fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import static org.mockito.Mockito.*;

class RefreshResponseTest {
    @Test
    void refreshUpdatesTheExistingWindowWithoutTaskHops() {
        var plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        var player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.isOnline()).thenReturn(true);
        var cache = plugin.getAuctionManager().getCache(player);
        when(cache.get(PlayerCacheKey.REFRESH_LOADING)).thenReturn(false);
        when(cache.get(PlayerCacheKey.SEARCH_QUERY)).thenReturn(null);
        var engine = mock(InventoryEngine.class, RETURNS_DEEP_STUBS);
        var top = engine.getSpigotInventory();
        when(player.getOpenInventory().getTopInventory()).thenReturn(top);
        var listed = mock(ListedItemsButton.class);
        when(listed.getSlots()).thenReturn(List.of(0, 1));
        when(engine.getMenuInventory().getButtons(ListedItemsButton.class)).thenReturn(List.of(listed));
        when(engine.getPage()).thenReturn(1);
        when(engine.getMaxPage()).thenReturn(1);
        when(engine.getItems()).thenReturn(new HashMap<>());
        when(engine.getButtons()).thenReturn(List.of(listed));
        var ids = new IntArrayList(); ids.add(10);
        when(plugin.getAuctionManager().getItemIdsListedForSale(player)).thenReturn(ids);
        var button = new RefreshButton(plugin, mock(MenuItemStack.class));
        button.setPlugin(mock(fr.maxlego08.menu.api.MenuPlugin.class, RETURNS_DEEP_STUBS));
        var event = mock(InventoryClickEvent.class);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            button.onClick(player, event, engine, 49, new Placeholders());
        }
        verify(cache).set(PlayerCacheKey.REFRESH_LOADING, false);
        verify(engine).buildButton(listed);
        verify(engine).buildButton(button);
        verify(event).setCancelled(true);
        verifyNoInteractions(scheduler);
        verify(plugin.getInventoriesLoader().getInventoryManager(), never()).updateInventory(any());
    }
}

package fr.maxlego08.zauctionhouse;

import com.tcoded.folialib.impl.PlatformScheduler;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.utils.cache.SortedItemsCache;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;

class ViewerUpdateTest {
    @Test
    void ignoredPlayerDoesNotStopOtherViewersUpdating() throws Exception {
        AuctionPlugin plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getConfiguration().getActions().updateInventoryOnAction()).thenReturn(true);
        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        Player ignored = mock(Player.class);
        Player viewer = mock(Player.class);
        org.bukkit.Server server = plugin.getServer();
        doReturn(List.of(ignored, viewer)).when(server).getOnlinePlayers();
        ZAuctionManager manager = mock(ZAuctionManager.class, CALLS_REAL_METHODS);
        Field pluginField = ZAuctionManager.class.getDeclaredField("plugin");
        pluginField.setAccessible(true);
        pluginField.set(manager, plugin);
        SortedItemsCache cache = mock(SortedItemsCache.class);
        when(cache.ensureCacheValidAsync()).thenReturn(CompletableFuture.completedFuture(null));
        Field cacheField = ZAuctionManager.class.getDeclaredField("sortedItemsCache");
        cacheField.setAccessible(true);
        cacheField.set(manager, cache);
        manager.updateListedItems(mock(Item.class), true, ignored);
        verify(scheduler, never()).runAtEntity(eq(ignored), any());
        verify(scheduler).runAtEntity(eq(viewer), any());
    }
}

package fr.maxlego08.zauctionhouse.utils.cache;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceConfiguration;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SortedItemsCacheTest {
    private AuctionPlugin plugin() {
        var plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getConfiguration().getPerformance()).thenReturn(new PerformanceConfiguration(10000, 5000, 1, 1, 1, 1, 1, 1));
        return plugin;
    }

    @Test
    void readsAndExplicitWaitersShareOneQueuedRebuild() {
        var plugin = plugin();
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        var queued = new ArrayList<Consumer<WrappedTask>>();
        when(scheduler.runAsync(any())).thenAnswer(call -> { queued.add(call.getArgument(0)); return null; });
        var cache = new SortedItemsCache(plugin, List::of);
        try {
            cache.getSortedIds(SortItem.ASCENDING_DATE);
            var future = cache.ensureCacheValidAsync();
            assertSame(future, cache.ensureCacheValidAsync());
            cache.rebuildAsync();
            assertEquals(1, queued.size());
            assertFalse(future.isDone());
            queued.removeFirst().accept(null);
            assertTrue(future.isDone());
            assertFalse(cache.isDirty());
        } finally { cache.shutdown(); }
    }

    @Test
    void invalidationDuringSnapshotCollectionIsNotLost() {
        var plugin = plugin();
        AtomicReference<SortedItemsCache> reference = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        var cache = new SortedItemsCache(plugin, () -> {
            if (calls.incrementAndGet() == 1) reference.get().invalidate();
            return List.of();
        });
        reference.set(cache);
        try {
            cache.rebuild();
            assertTrue(cache.isDirty());
            cache.rebuild();
            assertFalse(cache.isDirty());
            assertEquals(2, calls.get());
        } finally { cache.shutdown(); }
    }

    @Test
    void paginatedCopiesCannotMutateTheSharedCacheAndHandleBoundaryLimits() {
        var plugin = plugin();
        var items = new ArrayList<Item>();
        for (int id = 0; id < 5; id++) {
            var item = mock(Item.class);
            when(item.getId()).thenReturn(id);
            when(item.getStatus()).thenReturn(ItemStatus.AVAILABLE);
            when(item.getExpiredAt()).thenReturn(new java.util.Date(id));
            when(item.getPrice()).thenReturn(BigDecimal.valueOf(id));
            items.add(item);
        }
        var cache = new SortedItemsCache(plugin, () -> items);
        try {
            cache.rebuild();
            var page = cache.getPage(SortItem.ASCENDING_PRICE, 2, 2);
            assertEquals(2, page.size());
            assertEquals(2, page.getInt(0));
            page.clear();
            assertEquals(2, cache.getPage(SortItem.ASCENDING_PRICE, 2, 2).size());
            assertTrue(cache.getPage(SortItem.ASCENDING_PRICE, 0, -1).isEmpty());
            assertEquals(5, cache.getPage(SortItem.ASCENDING_PRICE, -5, Integer.MAX_VALUE).size());
            assertEquals(3, cache.getPage(SortItem.ASCENDING_PRICE, 2, Integer.MAX_VALUE).size());
        } finally { cache.shutdown(); }
    }
    @Test
    void asyncWaitersFinishOnlyAfterAnInvalidationDuringRebuildHasBeenIncluded() {
        var plugin = plugin();
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        var queued = new ArrayList<Consumer<WrappedTask>>();
        when(scheduler.runAsync(any())).thenAnswer(call -> { queued.add(call.getArgument(0)); return null; });
        var calls = new AtomicInteger();
        var reference = new AtomicReference<SortedItemsCache>();
        var cache = new SortedItemsCache(plugin, () -> {
            assertTrue(reference.get().isDirty());
            if (calls.incrementAndGet() == 1) reference.get().invalidate();
            return List.of();
        });
        reference.set(cache);
        try {
            var future = cache.ensureCacheValidAsync();
            queued.removeFirst().accept(null);
            assertTrue(future.isDone());
            assertFalse(future.isCompletedExceptionally());
            assertEquals(2, calls.get());
            assertFalse(cache.isDirty());
        } finally { cache.shutdown(); }
    }
}

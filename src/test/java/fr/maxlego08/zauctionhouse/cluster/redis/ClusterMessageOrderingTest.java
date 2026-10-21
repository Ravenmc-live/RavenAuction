package fr.maxlego08.zauctionhouse.cluster.redis;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.*;
import fr.maxlego08.zauctionhouse.cluster.redis.message.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClusterMessageOrderingTest {
    AuctionPlugin plugin;
    Map<Integer, Item> listed;
    Queue<Consumer<WrappedTask>> nextTicks;
    ClusterMessageHandler handler;
    AtomicBoolean current;

    @BeforeEach void setup() {
        plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        var scheduler = mock(PlatformScheduler.class); when(plugin.getScheduler()).thenReturn(scheduler);
        nextTicks = new ArrayDeque<>();
        doAnswer(call -> { nextTicks.add(call.getArgument(0)); return null; }).when(scheduler).runNextTick(any());
        listed = new HashMap<>(); var manager = plugin.getAuctionManager();
        when(manager.getItem(eq(StorageType.LISTED), anyInt())).thenAnswer(call -> listed.get(call.getArgument(1)));
        when(manager.getItem(eq(StorageType.EXPIRED), anyInt())).thenReturn(null);
        when(manager.getItem(eq(StorageType.PURCHASED), anyInt())).thenReturn(null);
        doAnswer(call -> { Item item = call.getArgument(1); listed.put(item.getId(), item); return null; })
                .when(manager).addItem(eq(StorageType.LISTED), any());
        doAnswer(call -> { listed.remove(call.getArgument(1)); return null; }).when(manager).removeItem(eq(StorageType.LISTED), anyInt());
        current = new AtomicBoolean(true); handler = new ClusterMessageHandler(plugin, "local-instance", current::get);
    }
    ClusterMessage event(ClusterMessageType type, int id) {
        return ClusterMessage.of(type, "remote-instance", "same-server-name").with(ClusterMessage.KEY_ITEM_ID, id).build();
    }
    Item item(int id) {
        Item item = mock(Item.class); when(item.getId()).thenReturn(id); when(item.getStatus()).thenReturn(ItemStatus.AVAILABLE); return item;
    }
    @Test void aSlowListingReadCannotRunAfterALaterRemovalAndResurrectTheListing() {
        CompletableFuture<Item> initial = new CompletableFuture<>();
        when(plugin.getStorageManager().selectItem(2)).thenReturn(initial, CompletableFuture.completedFuture(null));
        handler.accept(event(ClusterMessageType.ITEM_LISTED, 2));
        handler.accept(event(ClusterMessageType.ITEM_REMOVED, 2));
        verify(plugin.getStorageManager(), times(1)).selectItem(2);
        initial.complete(item(2)); assertEquals(1, nextTicks.size());
        nextTicks.remove().accept(null);
        verify(plugin.getStorageManager(), times(2)).selectItem(2);
        nextTicks.remove().accept(null);
        assertFalse(listed.containsKey(2));
    }
    @Test void unrelatedItemsDoNotWaitForASlowDatabaseRead() {
        when(plugin.getStorageManager().selectItem(3)).thenReturn(new CompletableFuture<>());
        Item fresh4 = item(4);
        when(plugin.getStorageManager().selectItem(4)).thenReturn(CompletableFuture.completedFuture(fresh4));
        handler.accept(event(ClusterMessageType.ITEM_LISTED, 3));
        handler.accept(event(ClusterMessageType.ITEM_LISTED, 4));
        nextTicks.remove().accept(null);
        assertTrue(listed.containsKey(4));
    }
    @Test void reloadedOrDisabledSessionsCannotApplyAlreadyQueuedEvents() {
        Item fresh5 = item(5);
        when(plugin.getStorageManager().selectItem(5)).thenReturn(CompletableFuture.completedFuture(fresh5));
        handler.accept(event(ClusterMessageType.ITEM_LISTED, 5)); current.set(false);
        nextTicks.remove().accept(null);
        assertFalse(listed.containsKey(5));
    }
    @Test void messagesFromThisRuntimeAreIgnoredEvenWhenOtherRuntimesShareItsConfiguredServerName() {
        var own = ClusterMessage.of(ClusterMessageType.ITEM_LISTED, "local-instance", "same-server-name").with("item", 6).build();
        handler.accept(own);
        verify(plugin.getStorageManager(), never()).selectItem(anyInt());
        Item fresh6 = item(6);
        when(plugin.getStorageManager().selectItem(6)).thenReturn(CompletableFuture.completedFuture(fresh6));
        handler.accept(event(ClusterMessageType.ITEM_LISTED, 6)); nextTicks.remove().accept(null);
        assertTrue(listed.containsKey(6));
    }
}

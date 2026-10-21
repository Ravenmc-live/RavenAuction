package fr.maxlego08.zauctionhouse.cluster.redis;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.records.RedisConfiguration;
import fr.maxlego08.zauctionhouse.api.item.*;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClusterCacheReconcilerTest {
    AuctionPlugin plugin;
    RedisConnection connection;
    Jedis jedis;
    ItemRepository rows;
    Map<StorageType, Map<Integer, Item>> cache;
    AtomicBoolean current;
    ClusterCacheReconciler recovery;

    @BeforeEach void setup() {
        plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(executor).execute(any());
        when(plugin.getExecutorService()).thenReturn(executor);
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        doAnswer(call -> { ((Consumer<WrappedTask>) call.getArgument(0)).accept(null); return null; })
                .when(scheduler).runNextTick(any());
        rows = mock(ItemRepository.class);
        var storage = plugin.getStorageManager();
        doReturn(rows).when(storage).with(ItemRepository.class);
        when(rows.select()).thenReturn(List.of());
        cache = new EnumMap<>(StorageType.class);
        var manager = plugin.getAuctionManager();
        for (StorageType type : List.of(StorageType.LISTED, StorageType.EXPIRED, StorageType.PURCHASED)) {
            cache.put(type, new HashMap<>());
            when(manager.getItems(type)).thenAnswer(call -> new ArrayList<>(cache.get(type).values()));
        }
        when(manager.getItem(any(), anyInt())).thenAnswer(call -> cache.get(call.getArgument(0)).get(call.getArgument(1)));
        doAnswer(call -> { cache.get(call.getArgument(0)).remove(call.getArgument(1)); return null; })
                .when(manager).removeItem(any(), anyInt());
        doAnswer(call -> { Item item = call.getArgument(1); cache.get(call.getArgument(0)).put(item.getId(), item); return null; })
                .when(manager).addItem(any(), any());
        connection = mock(RedisConnection.class);
        var config = mock(RedisConfiguration.class);
        when(config.lockTtlMs()).thenReturn(10000L);
        when(config.lockKey(anyInt())).thenAnswer(call -> "test:lock:" + call.getArgument(0));
        when(connection.getConfiguration()).thenReturn(config);
        jedis = mock(Jedis.class); when(connection.getResource()).thenReturn(jedis);
        when(jedis.set(anyString(), anyString(), any(SetParams.class))).thenReturn("OK");
        current = new AtomicBoolean(true);
        recovery = new ClusterCacheReconciler(plugin, connection, current::get);
    }

    Item item(int id, ItemStatus status) {
        Item item = mock(Item.class); when(item.getId()).thenReturn(id); when(item.getStatus()).thenReturn(status); return item;
    }
    ItemDTO row(int id, StorageType storage) {
        return new ItemDTO(id, ItemType.AUCTION, UUID.randomUUID(), null, java.math.BigDecimal.ONE,
                "vault", storage, "same-name", new Date(), new Date(), new Date());
    }

    @Test void missingListingIsLoadedFromTheSharedDatabaseUnderADistributedLock() {
        Item fresh = item(3, ItemStatus.AVAILABLE);
        when(rows.select()).thenReturn(List.of(row(3, StorageType.LISTED)));
        when(plugin.getStorageManager().selectItem(3)).thenReturn(CompletableFuture.completedFuture(fresh));
        recovery.reconcile().join();
        assertSame(fresh, cache.get(StorageType.LISTED).get(3));
        verify(plugin.getCategoryManager()).applyCategories(fresh);
        verify(jedis).eval(anyString(), eq(List.of("test:lock:3")), anyList());
    }
    @Test void deletedListingIsRemovedAfterAMissedRedisMessage() {
        cache.get(StorageType.LISTED).put(7, item(7, ItemStatus.AVAILABLE));
        when(plugin.getStorageManager().selectItem(7)).thenReturn(CompletableFuture.completedFuture(null));
        recovery.reconcile().join();
        assertTrue(cache.get(StorageType.LISTED).isEmpty());
    }
    @Test void soldListingMovesToPurchasedStorage() {
        cache.get(StorageType.LISTED).put(8, item(8, ItemStatus.AVAILABLE));
        Item bought = item(8, ItemStatus.PURCHASED);
        when(rows.select()).thenReturn(List.of(row(8, StorageType.PURCHASED)));
        when(plugin.getStorageManager().selectItem(8)).thenReturn(CompletableFuture.completedFuture(bought));
        recovery.reconcile().join();
        assertTrue(cache.get(StorageType.LISTED).isEmpty());
        assertSame(bought, cache.get(StorageType.PURCHASED).get(8));
    }
    @Test void busyItemsAndInProgressPurchasesAreNotOverwritten() {
        Item buying = item(9, ItemStatus.IS_BEING_PURCHASED);
        cache.get(StorageType.LISTED).put(9, buying);
        when(plugin.getStorageManager().selectItem(9)).thenReturn(CompletableFuture.completedFuture(null));
        recovery.reconcile().join();
        assertSame(buying, cache.get(StorageType.LISTED).get(9));
        when(jedis.set(anyString(), anyString(), any(SetParams.class))).thenReturn(null);
        recovery.reconcile().join();
        assertSame(buying, cache.get(StorageType.LISTED).get(9));
    }
    @Test void aLocalChangeWhileReadingTheDatabaseIsNotReplacedByAnOldSnapshot() {
        Item old = item(10, ItemStatus.AVAILABLE);
        Item replacement = item(10, ItemStatus.PURCHASED);
        cache.get(StorageType.LISTED).put(10, old);
        CompletableFuture<Item> read = new CompletableFuture<>();
        when(plugin.getStorageManager().selectItem(10)).thenReturn(read);
        CompletableFuture<Void> result = recovery.reconcile();
        assertSame(result, recovery.reconcile());
        cache.get(StorageType.LISTED).remove(10);
        cache.get(StorageType.PURCHASED).put(10, replacement);
        read.complete(null); result.join();
        assertSame(replacement, cache.get(StorageType.PURCHASED).get(10));
    }
    @Test void reloadOrDisableInvalidatesPendingCacheWritesAndReleasesTheLock() {
        cache.get(StorageType.LISTED).put(11, item(11, ItemStatus.AVAILABLE));
        CompletableFuture<Item> read = new CompletableFuture<>();
        when(plugin.getStorageManager().selectItem(11)).thenReturn(read);
        CompletableFuture<Void> result = recovery.reconcile(); current.set(false);
        read.complete(null); result.join();
        assertTrue(cache.get(StorageType.LISTED).containsKey(11));
        verify(jedis).eval(anyString(), eq(List.of("test:lock:11")), anyList());
    }
    @Test void stableCachesDoNotDeserializeOrLockEveryListing() {
        cache.get(StorageType.LISTED).put(12, item(12, ItemStatus.AVAILABLE));
        when(rows.select()).thenReturn(List.of(row(12, StorageType.LISTED)));
        recovery.reconcile().join();
        verifyNoInteractions(jedis);
        verify(plugin.getStorageManager(), never()).selectItem(anyInt());
    }
}

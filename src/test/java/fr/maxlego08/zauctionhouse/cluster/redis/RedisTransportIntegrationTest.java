package fr.maxlego08.zauctionhouse.cluster.redis;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cluster.*;
import fr.maxlego08.zauctionhouse.api.configuration.records.RedisConfiguration;
import fr.maxlego08.zauctionhouse.api.item.*;
import org.junit.jupiter.api.*;
import redis.clients.jedis.args.ClientType;
import redis.clients.jedis.params.ClientKillParams;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisTransportIntegrationTest {
    private RedisConfiguration configuration(int database, String prefix) {
        String port = System.getenv("RAVEN_REDIS_TEST_PORT");
        Assumptions.assumeTrue(port != null, "Set RAVEN_REDIS_TEST_PORT for an isolated Redis with password raven-test-only");
        return new RedisConfiguration(true, RedisMode.STANDALONE, "127.0.0.1", Integer.parseInt(port), "", "raven-test-only",
                database, false, 1000, prefix, 10000, 8, 8, 0, "mymaster", List.of());
    }
    private Logger quiet() { Logger logger = Logger.getAnonymousLogger(); logger.setLevel(Level.OFF); return logger; }

    @Test void passwordAuthenticationAndLogicalDatabaseSelectionWorkThroughTheActualPool() {
        String key = "auction-test-" + UUID.randomUUID();
        RedisConnection first = new RedisConnection(configuration(2, "test"));
        RedisConnection other = new RedisConnection(configuration(3, "test"));
        try (var a = first.getResource(); var b = other.getResource()) {
            assertEquals("PONG", a.ping());
            a.set(key, "private-test-value");
            assertEquals("private-test-value", a.get(key));
            assertNull(b.get(key));
            a.del(key);
        } finally { first.close(); other.close(); }
    }

    @Test void twoClientsWithTheSameConfiguredServerNameShareLocksAndReceiveActualListingUpdates() throws Exception {
        String prefix = "auction-test-" + UUID.randomUUID();
        RedisConnection a = new RedisConnection(configuration(2, prefix));
        RedisConnection b = new RedisConnection(configuration(2, prefix));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        RedisSubscriber subscriber = null;
        try {
            AuctionPlugin first = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
            AuctionPlugin second = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
            when(first.getExecutorService()).thenReturn(executor); when(second.getExecutorService()).thenReturn(executor);
            when(first.getConfiguration().getServerName()).thenReturn("same-name");
            when(second.getConfiguration().getServerName()).thenReturn("same-name");
            var scheduler = mock(PlatformScheduler.class); when(second.getScheduler()).thenReturn(scheduler);
            doAnswer(call -> { ((Consumer<WrappedTask>)call.getArgument(0)).accept(null); return null; }).when(scheduler).runNextTick(any());
            Item item = mock(Item.class); when(item.getId()).thenReturn(42); when(item.getStatus()).thenReturn(ItemStatus.AVAILABLE);
            when(second.getStorageManager().selectItem(42)).thenReturn(CompletableFuture.completedFuture(item));
            CountDownLatch cached = new CountDownLatch(1), subscribed = new CountDownLatch(1);
            var manager = second.getAuctionManager();
            when(manager.getItem(StorageType.LISTED, 42)).thenReturn(null);
            doAnswer(call -> { cached.countDown(); return null; }).when(manager).addItem(StorageType.LISTED, item);
            subscriber = new RedisSubscriber(b, quiet(), new ClusterMessageHandler(second, "instance-B"), subscribed::countDown, () -> {});
            subscriber.start(); assertTrue(subscribed.await(3, TimeUnit.SECONDS));
            var origin = new RedisAuctionClusterBridge(first, a, "instance-A");
            var remote = new RedisAuctionClusterBridge(second, b, "instance-B");
            LockToken token = origin.lockItem(item, UUID.randomUUID(), StorageType.LISTED).get(3, TimeUnit.SECONDS);
            assertNotEquals(LockToken.noop(), token);
            assertEquals(LockToken.noop(), remote.lockItem(item, UUID.randomUUID(), StorageType.LISTED).get(3, TimeUnit.SECONDS));
            remote.unlockItem(item, new LockToken("wrong-owner"), StorageType.LISTED).get(3, TimeUnit.SECONDS);
            assertFalse(origin.checkAvailability(item).get(3, TimeUnit.SECONDS));
            origin.unlockItem(item, token, StorageType.LISTED).get(3, TimeUnit.SECONDS);
            assertTrue(remote.checkAvailability(item).get(3, TimeUnit.SECONDS));
            origin.notifyItemListed(item).get(3, TimeUnit.SECONDS);
            assertTrue(cached.await(3, TimeUnit.SECONDS));
            verify(second.getCategoryManager()).applyCategories(item);
        } finally {
            if (subscriber != null) subscriber.stop();
            executor.shutdownNow(); a.close(); b.close();
        }
    }

    @Test void aKilledSubscriptionReconnectsAndStillReceivesMessagesAndStopsCleanly() throws Exception {
        String prefix = "auction-test-" + UUID.randomUUID();
        RedisConnection connection = new RedisConnection(configuration(2, prefix));
        CountDownLatch first = new CountDownLatch(1), subscriptions = new CountDownLatch(2);
        CountDownLatch disconnected = new CountDownLatch(1), received = new CountDownLatch(1);
        RedisSubscriber listener = new RedisSubscriber(connection, quiet(), message -> received.countDown(),
                () -> { first.countDown(); subscriptions.countDown(); }, disconnected::countDown);
        try {
            listener.start(); assertTrue(first.await(3, TimeUnit.SECONDS));
            try (var jedis = connection.getResource()) {
                assertTrue(jedis.clientKill(ClientKillParams.clientKillParams().type(ClientType.PUBSUB)) >= 1);
            }
            assertTrue(disconnected.await(3, TimeUnit.SECONDS));
            assertTrue(subscriptions.await(6, TimeUnit.SECONDS));
            var message = fr.maxlego08.zauctionhouse.cluster.redis.message.ClusterMessage.of(
                    fr.maxlego08.zauctionhouse.cluster.redis.message.ClusterMessageType.ITEM_LISTED, "other-instance", "same-name")
                    .with("item", 44).build();
            try (var jedis = connection.getResource()) { jedis.publish(connection.getConfiguration().channel(), message.encode()); }
            assertTrue(received.await(3, TimeUnit.SECONDS));
        } finally { listener.stop(); connection.close(); }
        assertFalse(listener.isSubscribed());
    }
}

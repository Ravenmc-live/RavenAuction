package fr.maxlego08.zauctionhouse.cluster.redis;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.records.RedisConfiguration;
import fr.maxlego08.zauctionhouse.cluster.LocalAuctionClusterBridge;
import fr.maxlego08.zauctionhouse.cluster.UnavailableAuctionClusterBridge;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisClusterServiceTest {
    AuctionPlugin plugin;
    RedisConfiguration config;
    ScheduledExecutorService worker;
    Function<RedisConfiguration, RedisConnection> connections;
    List<Runnable> ticks;
    RedisClusterService service;

    @BeforeEach void setup() {
        plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        config = mock(RedisConfiguration.class); when(config.enabled()).thenReturn(true);
        when(plugin.getConfiguration().getRedis()).thenReturn(config);
        var yaml = new YamlConfiguration(); yaml.set("storage-type", "MYSQL");
        when(plugin.getConfig()).thenReturn(yaml);
        worker = mock(ScheduledExecutorService.class); ticks = new ArrayList<>();
        when(worker.scheduleWithFixedDelay(any(), anyLong(), anyLong(), any())).thenAnswer(call -> { ticks.add(call.getArgument(0)); return null; });
        doAnswer(call -> { ((Runnable)call.getArgument(0)).run(); return null; }).when(worker).execute(any());
        var executor = mock(ExecutorService.class);
        doAnswer(call -> { ((Runnable)call.getArgument(0)).run(); return null; }).when(executor).execute(any());
        when(plugin.getExecutorService()).thenReturn(executor);
        var scheduler = mock(PlatformScheduler.class); when(plugin.getScheduler()).thenReturn(scheduler);
        doAnswer(call -> { ((Consumer<WrappedTask>)call.getArgument(0)).accept(null); return null; }).when(scheduler).runNextTick(any());
        var rows = mock(ItemRepository.class); when(rows.select()).thenReturn(List.of());
        var storage = plugin.getStorageManager();
        doReturn(rows).when(storage).with(ItemRepository.class);
        connections = mock(Function.class);
        service = new RedisClusterService(plugin, connections, () -> worker);
    }

    @Test void startNeverPerformsRedisIoOnTheCallingServerThread() {
        assertTrue(service.start());
        verifyNoInteractions(connections);
        assertFalse(service.isActive());
        verify(plugin, atLeastOnce()).setAuctionClusterBridge(isA(UnavailableAuctionClusterBridge.class));
    }
    @Test void startupFailureRetriesThenBecomesActiveOnlyAfterSubscribingAndRecovering() {
        RedisConnection first = mock(RedisConnection.class), second = mock(RedisConnection.class);
        when(second.getConfiguration()).thenReturn(config);
        doThrow(new IllegalStateException("unreachable test endpoint")).when(first).ping();
        when(connections.apply(config)).thenReturn(first, second);
        List<Runnable> subscribed = new ArrayList<>();
        try (var subscribers = mockConstruction(RedisSubscriber.class, (mock, context) -> {
            subscribed.add(() -> { when(mock.isSubscribed()).thenReturn(true); ((Runnable)context.arguments().get(3)).run(); });
        })) {
            service.start(); ticks.getFirst().run();
            assertFalse(service.isActive()); verify(first).close();
            ticks.getFirst().run(); assertFalse(service.isActive());
            subscribed.getFirst().run(); assertTrue(service.isActive());
            verify(plugin).setAuctionClusterBridge(isA(RedisAuctionClusterBridge.class));
            service.stop(); assertFalse(service.isActive());
            verify(second).close(); verify(subscribers.constructed().getFirst()).stop();
        }
    }
    @Test void reloadUsesChangedCredentialsAndAnOldSubscriptionCannotReactivateItself() {
        RedisConnection first = mock(RedisConnection.class), second = mock(RedisConnection.class);
        when(first.getConfiguration()).thenReturn(config);
        RedisConfiguration changed = mock(RedisConfiguration.class); when(changed.enabled()).thenReturn(true);
        when(second.getConfiguration()).thenReturn(changed);
        when(connections.apply(config)).thenReturn(first); when(connections.apply(changed)).thenReturn(second);
        List<Runnable> subscribed = new ArrayList<>();
        try (var ignored = mockConstruction(RedisSubscriber.class, (mock, context) -> {
            subscribed.add(() -> { when(mock.isSubscribed()).thenReturn(true); ((Runnable)context.arguments().get(3)).run(); });
        })) {
            service.start(); ticks.getFirst().run(); subscribed.getFirst().run(); assertTrue(service.isActive());
            when(plugin.getConfiguration().getRedis()).thenReturn(changed);
            service.start(); subscribed.getFirst().run(); assertFalse(service.isActive());
            ticks.get(1).run(); subscribed.get(1).run(); assertTrue(service.isActive());
            verify(connections).apply(changed); verify(first).close(); service.stop();
        }
    }
    @Test void sqliteIsNotAllowedToSilentlyFallBackToLocalPurchaseLocks() {
        plugin.getConfig().set("storage-type", "SQLITE");
        assertFalse(service.start()); verifyNoInteractions(connections);
        verify(plugin, atLeastOnce()).setAuctionClusterBridge(isA(UnavailableAuctionClusterBridge.class));
    }
    @Test void explicitlyDisablingRedisRetainsNormalSingleServerBehavior() {
        when(config.enabled()).thenReturn(false);
        assertFalse(service.start());
        verify(plugin).setAuctionClusterBridge(argThat(bridge -> bridge != null && bridge.getClass() == LocalAuctionClusterBridge.class));
        verifyNoInteractions(connections);
    }
}

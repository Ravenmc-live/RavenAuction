package fr.maxlego08.zauctionhouse.cluster.redis;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.records.RedisConfiguration;
import fr.maxlego08.zauctionhouse.cluster.LocalAuctionClusterBridge;
import fr.maxlego08.zauctionhouse.cluster.UnavailableAuctionClusterBridge;
import java.util.UUID;
import java.util.concurrent.*;

public class RedisClusterService {
    private final AuctionPlugin plugin;
    private final String instanceId = UUID.randomUUID().toString();
    private volatile Session session;
    private final java.util.function.Function<RedisConfiguration, RedisConnection> connections;
    private final java.util.function.Supplier<ScheduledExecutorService> workers;

    public RedisClusterService(AuctionPlugin plugin) {
        this(plugin, RedisConnection::new, () -> Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "RavenAuction-Redis-Recovery");
            thread.setDaemon(true);
            return thread;
        }));
    }

    RedisClusterService(AuctionPlugin plugin, java.util.function.Function<RedisConfiguration, RedisConnection> connections,
                        java.util.function.Supplier<ScheduledExecutorService> workers) {
        this.plugin = plugin;
        this.connections = connections;
        this.workers = workers;
    }

    public synchronized boolean start() {
        stop();
        RedisConfiguration configuration = this.plugin.getConfiguration().getRedis();
        if (!configuration.enabled()) {
            this.plugin.setAuctionClusterBridge(new LocalAuctionClusterBridge());
            return false;
        }
        this.plugin.setAuctionClusterBridge(new UnavailableAuctionClusterBridge());
        String storage = this.plugin.getConfig().getString("storage-type", "SQLITE");
        if (!"MYSQL".equalsIgnoreCase(storage) && !"MARIADB".equalsIgnoreCase(storage)) {
            this.plugin.getLogger().severe("Redis requires shared MYSQL or MARIADB storage; clustered purchases are unavailable.");
            return false;
        }
        Session next = new Session(configuration);
        this.session = next;
        next.worker.scheduleWithFixedDelay(next, 0, 5, TimeUnit.SECONDS);
        this.plugin.getLogger().info("Redis synchronization starting for " + configuration.host() + ":" + configuration.port()
                + "; connection retries and cache recovery are automatic.");
        return true;
    }

    public synchronized void stop() {
        Session previous = this.session;
        this.session = null;
        this.plugin.setAuctionClusterBridge(new UnavailableAuctionClusterBridge());
        if (previous != null) previous.stop();
    }

    public boolean isActive() {
        Session current = this.session;
        return current != null && current.ready;
    }

    private final class Session implements Runnable {
        final RedisConfiguration configuration;
        final ScheduledExecutorService worker = RedisClusterService.this.workers.get();
        volatile boolean stopped;
        volatile boolean ready;
        volatile RedisConnection connection;
        volatile RedisSubscriber subscriber;
        volatile ClusterCacheReconciler reconciler;
        volatile long lastRecovery;
        boolean failureReported;
        boolean enabledReported;

        Session(RedisConfiguration configuration) { this.configuration = configuration; }
        boolean current() { return !this.stopped && RedisClusterService.this.session == this; }

        @Override public void run() {
            if (!current()) return;
            try {
                if (this.connection == null) connect();
                else this.connection.ping();
                if (this.subscriber != null && this.subscriber.isSubscribed()
                        && System.nanoTime() - this.lastRecovery > TimeUnit.SECONDS.toNanos(30)) recover();
            } catch (Exception failure) {
                if (!this.failureReported && current()) {
                    this.failureReported = true;
                    RedisClusterService.this.plugin.getLogger().warning("Redis is unavailable (" + failure.getClass().getSimpleName()
                            + "); coordinated purchases are paused while the connection retries automatically.");
                }
                unavailable();
                closeConnection();
            }
        }

        void connect() {
            RedisConnection opened = RedisClusterService.this.connections.apply(this.configuration);
            this.connection = opened;
            opened.ping();
            this.reconciler = new ClusterCacheReconciler(RedisClusterService.this.plugin, opened,
                    () -> current() && this.connection == opened);
            var handler = new ClusterMessageHandler(RedisClusterService.this.plugin, RedisClusterService.this.instanceId,
                    () -> current() && this.connection == opened);
            this.subscriber = new RedisSubscriber(opened, RedisClusterService.this.plugin.getLogger(), handler,
                    () -> { if (current() && this.connection == opened) recover(); },
                    () -> { if (current() && this.connection == opened) unavailable(); });
            if (!current()) { closeConnection(); return; }
            this.subscriber.start();
        }

        void recover() {
            RedisConnection opened = this.connection;
            ClusterCacheReconciler recovery = this.reconciler;
            if (!current() || opened == null || recovery == null) return;
            this.lastRecovery = System.nanoTime();
            recovery.reconcile().whenComplete((result, error) -> {
                synchronized (RedisClusterService.this) {
                    if (!current() || this.connection != opened || this.subscriber == null || !this.subscriber.isSubscribed()) return;
                    if (error == null) {
                        RedisClusterService.this.plugin.setAuctionClusterBridge(new RedisAuctionClusterBridge(
                                RedisClusterService.this.plugin, opened, RedisClusterService.this.instanceId));
                        this.ready = true;
                        if (!this.enabledReported) {
                            this.enabledReported = true;
                            RedisClusterService.this.plugin.getLogger().info("Redis synchronization enabled; auction caches recovered from shared storage.");
                        }
                    }
                }
            });
        }

        void unavailable() {
            synchronized (RedisClusterService.this) {
                this.ready = false;
                if (current()) RedisClusterService.this.plugin.setAuctionClusterBridge(new UnavailableAuctionClusterBridge());
            }
        }

        void closeConnection() {
            RedisSubscriber listener = this.subscriber;
            RedisConnection opened = this.connection;
            this.subscriber = null;
            this.connection = null;
            this.reconciler = null;
            if (listener != null) listener.stop();
            if (opened != null) try { opened.close(); } catch (Exception ignored) {}
        }

        void stop() {
            this.stopped = true;
            this.ready = false;
            this.worker.execute(this::closeConnection);
            this.worker.shutdown();
        }
    }
}

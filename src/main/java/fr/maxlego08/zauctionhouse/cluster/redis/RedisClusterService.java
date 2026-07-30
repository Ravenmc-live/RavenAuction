package fr.maxlego08.zauctionhouse.cluster.redis;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cluster.RedisMode;
import fr.maxlego08.zauctionhouse.cluster.LocalAuctionClusterBridge;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns the lifecycle of the Redis-backed multi-server synchronization.
 * <p>
 * When {@code redis.enabled} is off, or when the connection cannot be established, the plugin keeps
 * running on the in-memory {@link LocalAuctionClusterBridge}: a Redis outage degrades the auction
 * house to single-server behaviour instead of breaking it.
 */
public class RedisClusterService {

    private final AuctionPlugin plugin;

    /**
     * Identifies this JVM for the lifetime of the process. Two servers sharing the same configured
     * {@code server-name} would otherwise discard each other's messages as their own.
     */
    private final String instanceId = UUID.randomUUID().toString();

    private RedisConnection connection;
    private RedisSubscriber subscriber;
    private boolean active;

    public RedisClusterService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Connects to Redis and installs the cluster bridge.
     *
     * @return {@code true} when multi-server synchronization is now active
     */
    public boolean start() {

        var configuration = this.plugin.getConfiguration().getRedis();
        if (!configuration.enabled()) return false;

        var logger = this.plugin.getLogger();

        // Every server must read and write the same rows for cross-server sync to mean anything;
        // with a local SQLite file each server would broadcast about items the others cannot see.
        String storageType = this.plugin.getConfig().getString("storage-type", "SQLITE");
        if (storageType != null && storageType.equalsIgnoreCase("SQLITE")) {
            logger.severe("Redis synchronization is enabled but storage-type is SQLITE, which is a local file.");
            logger.severe("Switch storage-type to MYSQL or MARIADB and point every server at the same database.");
            logger.severe("Multi-server synchronization stays disabled.");
            return false;
        }

        try {

            this.connection = new RedisConnection(configuration);
            this.connection.ping();

            var handler = new ClusterMessageHandler(this.plugin, this.instanceId);
            this.subscriber = new RedisSubscriber(this.connection, logger, handler);
            this.subscriber.start();

            this.plugin.setAuctionClusterBridge(new RedisAuctionClusterBridge(this.plugin, this.connection, this.instanceId));
            this.active = true;

            String target = configuration.mode() == RedisMode.SENTINEL
                    ? "sentinel master '" + configuration.sentinelMasterName() + "' via " + configuration.sentinelNodes()
                    : configuration.host() + ":" + configuration.port();
            logger.info("Multi-server synchronization enabled as '" + this.plugin.getConfiguration().getServerName() + "' (Redis: " + target + ").");

            return true;

        } catch (Exception exception) {

            logger.log(Level.SEVERE, "Could not connect to Redis, multi-server synchronization is disabled. The auction house keeps running on this server only.", exception);
            stop();
            return false;
        }
    }

    /**
     * Tears the synchronization down and restores single-server behaviour.
     */
    public void stop() {

        if (this.subscriber != null) {
            this.subscriber.stop();
            this.subscriber = null;
        }

        if (this.connection != null) {
            try {
                this.connection.close();
            } catch (Exception exception) {
                this.plugin.getLogger().log(Level.WARNING, "Failed to close the Redis connection pool cleanly.", exception);
            }
            this.connection = null;
        }

        if (this.active) {
            // Anything still holding the plugin instance must stop believing it is clustered.
            this.plugin.setAuctionClusterBridge(new LocalAuctionClusterBridge());
            this.active = false;
        }
    }

    /**
     * @return {@code true} while multi-server synchronization is running
     */
    public boolean isActive() {
        return this.active;
    }
}

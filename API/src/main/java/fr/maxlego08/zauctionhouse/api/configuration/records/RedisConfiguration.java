package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cluster.RedisMode;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Configuration for the Redis connection used to synchronize the auction house across multiple
 * servers. When {@link #enabled()} is {@code false} the plugin keeps its single-server behaviour
 * and never opens a connection.
 *
 * @param enabled            whether multi-server synchronization over Redis is active
 * @param mode               connection topology (standalone or sentinel)
 * @param host               hostname of the Redis server (standalone mode)
 * @param port               port of the Redis server (standalone mode)
 * @param username           ACL username, or blank when using legacy password-only auth
 * @param password           password, or blank when the server requires no authentication
 * @param database           Redis logical database index
 * @param ssl                whether the connection must be established over TLS
 * @param timeoutMs          connection and socket timeout in milliseconds
 * @param channelPrefix      prefix applied to every pub/sub channel and lock key
 * @param lockTtlMs          expiry of a distributed item lock, so a crashed server cannot hold it forever
 * @param maxTotal           maximum number of pooled connections
 * @param maxIdle            maximum number of idle pooled connections
 * @param minIdle            minimum number of idle pooled connections kept warm
 * @param sentinelMasterName name of the monitored master (sentinel mode)
 * @param sentinelNodes      sentinel nodes in {@code host:port} form (sentinel mode)
 */
public record RedisConfiguration(
        boolean enabled,
        RedisMode mode,
        String host,
        int port,
        String username,
        String password,
        int database,
        boolean ssl,
        int timeoutMs,
        String channelPrefix,
        long lockTtlMs,
        int maxTotal,
        int maxIdle,
        int minIdle,
        String sentinelMasterName,
        List<String> sentinelNodes
) {

    /**
     * Default expiry of a distributed lock (10 seconds). Long enough for a purchase to complete,
     * short enough that a crashed server does not block an item for long.
     */
    public static final long DEFAULT_LOCK_TTL_MS = 10_000;

    /**
     * Default connection and socket timeout (2 seconds).
     */
    public static final int DEFAULT_TIMEOUT_MS = 2000;

    /**
     * Default prefix used for channels and lock keys.
     */
    public static final String DEFAULT_CHANNEL_PREFIX = "zauctionhouse";

    public static RedisConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {

        boolean enabled = configuration.getBoolean("redis.enabled", false);

        String rawMode = configuration.getString("redis.mode", "standalone");
        RedisMode mode;
        try {
            mode = RedisMode.valueOf(rawMode.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid redis.mode '" + rawMode + "', falling back to STANDALONE. Valid values: standalone, sentinel.");
            mode = RedisMode.STANDALONE;
        }

        String host = configuration.getString("redis.host", "127.0.0.1");
        int port = configuration.getInt("redis.port", 6379);
        String username = configuration.getString("redis.username", "");
        String password = configuration.getString("redis.password", "");
        int database = configuration.getInt("redis.database", 0);
        boolean ssl = configuration.getBoolean("redis.ssl", false);
        int timeoutMs = configuration.getInt("redis.timeout-ms", DEFAULT_TIMEOUT_MS);
        String channelPrefix = configuration.getString("redis.channel-prefix", DEFAULT_CHANNEL_PREFIX);
        long lockTtlMs = configuration.getLong("redis.lock-ttl-ms", DEFAULT_LOCK_TTL_MS);

        int maxTotal = configuration.getInt("redis.pool.max-total", 8);
        int maxIdle = configuration.getInt("redis.pool.max-idle", 8);
        int minIdle = configuration.getInt("redis.pool.min-idle", 1);

        String sentinelMasterName = configuration.getString("redis.sentinel.master-name", "mymaster");
        List<String> sentinelNodes = configuration.getStringList("redis.sentinel.nodes");

        return new RedisConfiguration(enabled, mode, host, port, username, password, database, ssl, timeoutMs, channelPrefix, lockTtlMs, maxTotal, maxIdle, minIdle, sentinelMasterName, sentinelNodes);
    }

    /**
     * @return {@code true} when an ACL username was configured
     */
    public boolean hasUsername() {
        return this.username != null && !this.username.isBlank();
    }

    /**
     * @return {@code true} when a password was configured
     */
    public boolean hasPassword() {
        return this.password != null && !this.password.isBlank();
    }

    /**
     * Builds the pub/sub channel name used to broadcast auction updates.
     *
     * @return fully qualified channel name
     */
    public String channel() {
        return this.channelPrefix + ":cluster";
    }

    /**
     * Builds the Redis key holding the distributed lock of an item.
     *
     * @param itemId identifier of the locked item
     * @return fully qualified lock key
     */
    public String lockKey(int itemId) {
        return this.channelPrefix + ":lock:item:" + itemId;
    }
}

package fr.maxlego08.zauctionhouse.cluster.redis;

import fr.maxlego08.zauctionhouse.api.cluster.RedisMode;
import fr.maxlego08.zauctionhouse.api.configuration.records.RedisConfiguration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.util.Pool;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Owns the Jedis connection pool used by the cluster bridge. Supports a single Redis instance and
 * a Sentinel-managed master, so a failover does not require editing the plugin configuration.
 */
public class RedisConnection {

    private final RedisConfiguration configuration;
    private final Pool<Jedis> pool;

    public RedisConnection(RedisConfiguration configuration) {
        this.configuration = configuration;
        this.pool = createPool(configuration);
    }

    private static Pool<Jedis> createPool(RedisConfiguration configuration) {

        var poolConfig = new GenericObjectPoolConfig<Jedis>();
        poolConfig.setMaxTotal(configuration.maxTotal());
        poolConfig.setMaxIdle(configuration.maxIdle());
        poolConfig.setMinIdle(configuration.minIdle());
        poolConfig.setTestWhileIdle(true);

        var clientConfig = clientConfig(configuration, configuration.database());

        if (configuration.mode() == RedisMode.SENTINEL) {

            Set<HostAndPort> sentinels = new LinkedHashSet<>();
            for (String node : configuration.sentinelNodes()) {
                sentinels.add(HostAndPort.from(node.trim()));
            }

            if (sentinels.isEmpty()) {
                throw new IllegalStateException("redis.mode is SENTINEL but redis.sentinel.nodes is empty");
            }

            // Sentinel nodes themselves are not part of the logical database and normally use
            // their own (or no) credentials, so they get a dedicated config without the database index.
            var sentinelClientConfig = clientConfig(configuration, 0);

            return new JedisSentinelPool(configuration.sentinelMasterName(), sentinels, poolConfig, clientConfig, sentinelClientConfig);
        }

        return new JedisPool(poolConfig, new HostAndPort(configuration.host(), configuration.port()), clientConfig);
    }

    private static JedisClientConfig clientConfig(RedisConfiguration configuration, int database) {

        var builder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(configuration.timeoutMs())
                .socketTimeoutMillis(configuration.timeoutMs())
                .database(database)
                .ssl(configuration.ssl());

        // Jedis sends an ACL AUTH when a username is present and a legacy AUTH otherwise, so blank
        // values must stay null instead of being passed through as empty strings.
        if (configuration.hasUsername()) {
            builder.user(configuration.username());
        }
        if (configuration.hasPassword()) {
            builder.password(configuration.password());
        }

        return builder.build();
    }

    /**
     * Borrows a connection from the pool. Callers must close it, ideally with try-with-resources.
     *
     * @return a pooled connection
     */
    public Jedis getResource() {
        return this.pool.getResource();
    }

    /**
     * Verifies the server is reachable and answering commands.
     *
     * @throws RuntimeException when the connection cannot be established
     */
    public void ping() {
        try (var jedis = getResource()) {
            jedis.ping();
        }
    }

    /**
     * @return the configuration this connection was built from
     */
    public RedisConfiguration getConfiguration() {
        return this.configuration;
    }

    /**
     * Closes the pool and every connection it holds.
     */
    public void close() {
        this.pool.close();
    }
}

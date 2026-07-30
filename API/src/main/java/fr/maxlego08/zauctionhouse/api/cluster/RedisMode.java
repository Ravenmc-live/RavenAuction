package fr.maxlego08.zauctionhouse.api.cluster;

/**
 * Connection topology used to reach the Redis server backing the cluster bridge.
 */
public enum RedisMode {

    /**
     * A single Redis instance reached directly by host and port.
     */
    STANDALONE,

    /**
     * A highly available setup where the master is discovered through Redis Sentinel nodes,
     * allowing automatic failover without reconfiguring the plugin.
     */
    SENTINEL,

    ;
}

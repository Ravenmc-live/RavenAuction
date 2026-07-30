package fr.maxlego08.zauctionhouse.cluster.redis.message;

/**
 * Kinds of events broadcast between servers sharing an auction house.
 */
public enum ClusterMessageType {

    /**
     * A new item was listed for sale and must appear on every server.
     */
    ITEM_LISTED,

    /**
     * An item was bought; other servers drop it from their listings and notify the seller when
     * they happen to host them.
     */
    ITEM_BOUGHT,

    /**
     * The lifecycle status of an item changed (for example it entered the transient
     * "being purchased" state) and other servers must mirror it.
     */
    ITEM_STATUS_CHANGED,

    /**
     * An item left a storage bucket, either deleted or moved (typically to expired storage).
     */
    ITEM_REMOVED,

    ;
}

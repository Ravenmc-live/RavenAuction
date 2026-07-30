package fr.maxlego08.zauctionhouse.cluster.redis.message;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single event exchanged over the Redis pub/sub channel.
 * <p>
 * Messages are encoded as a flat, self-describing text payload rather than JSON so no extra
 * serialization dependency is shaded into the plugin. Fields are separated by {@code U+001F}
 * (unit separator) and keys from values by {@code U+001E} (record separator); both are control
 * characters that cannot appear in item data, player names or configured server names.
 * <p>
 * The leading protocol version lets a future format change be detected and ignored instead of
 * being mis-parsed by servers still running an older build.
 */
public final class ClusterMessage {

    /**
     * Current protocol version. Bump whenever the payload layout changes incompatibly.
     */
    public static final int PROTOCOL_VERSION = 1;

    private static final String FIELD_SEPARATOR = String.valueOf((char) 0x1F);
    private static final String KEY_VALUE_SEPARATOR = String.valueOf((char) 0x1E);

    public static final String KEY_ITEM_ID = "item";
    public static final String KEY_OLD_STATUS = "old";
    public static final String KEY_NEW_STATUS = "new";
    public static final String KEY_SOURCE_STORAGE = "from";
    public static final String KEY_DESTINATION_STORAGE = "to";
    public static final String KEY_BUYER_UUID = "buyer";
    public static final String KEY_BUYER_NAME = "buyerName";
    public static final String KEY_SELLER_UUID = "seller";

    private final ClusterMessageType type;
    private final String sourceId;
    private final String sourceServer;
    private final Map<String, String> data;

    private ClusterMessage(ClusterMessageType type, String sourceId, String sourceServer, Map<String, String> data) {
        this.type = type;
        this.sourceId = sourceId;
        this.sourceServer = sourceServer;
        this.data = data;
    }

    /**
     * Starts building an outgoing message.
     *
     * @param type         event kind
     * @param sourceId     unique identifier of the emitting server instance
     * @param sourceServer configured name of the emitting server, for logs
     * @return a mutable builder
     */
    public static Builder of(ClusterMessageType type, String sourceId, String sourceServer) {
        return new Builder(type, sourceId, sourceServer);
    }

    /**
     * Parses a payload received from Redis.
     *
     * @param payload raw channel message
     * @return the decoded message, or {@code null} when the payload is malformed or was produced by
     * an incompatible protocol version
     */
    public static ClusterMessage decode(String payload) {

        if (payload == null || payload.isEmpty()) return null;

        String[] parts = payload.split(FIELD_SEPARATOR, -1);
        if (parts.length < 4) return null;

        int version;
        try {
            version = Integer.parseInt(parts[0]);
        } catch (NumberFormatException exception) {
            return null;
        }
        if (version != PROTOCOL_VERSION) return null;

        ClusterMessageType type;
        try {
            type = ClusterMessageType.valueOf(parts[1]);
        } catch (IllegalArgumentException exception) {
            // Unknown event kind: emitted by a newer build, safe to ignore.
            return null;
        }

        Map<String, String> data = new LinkedHashMap<>();
        for (int index = 4; index < parts.length; index++) {
            String field = parts[index];
            if (field.isEmpty()) continue;
            int separator = field.indexOf(KEY_VALUE_SEPARATOR);
            if (separator <= 0) continue;
            data.put(field.substring(0, separator), field.substring(separator + 1));
        }

        return new ClusterMessage(type, parts[2], parts[3], data);
    }

    /**
     * @return the wire representation of this message
     */
    public String encode() {
        var builder = new StringBuilder();
        builder.append(PROTOCOL_VERSION)
                .append(FIELD_SEPARATOR).append(this.type.name())
                .append(FIELD_SEPARATOR).append(this.sourceId)
                .append(FIELD_SEPARATOR).append(this.sourceServer);

        for (var entry : this.data.entrySet()) {
            builder.append(FIELD_SEPARATOR).append(entry.getKey()).append(KEY_VALUE_SEPARATOR).append(entry.getValue());
        }

        return builder.toString();
    }

    public ClusterMessageType getType() {
        return this.type;
    }

    /**
     * @return unique identifier of the emitting server instance, used to ignore our own broadcasts
     */
    public String getSourceId() {
        return this.sourceId;
    }

    public String getSourceServer() {
        return this.sourceServer;
    }

    public String getString(String key) {
        return this.data.get(key);
    }

    public int getInt(String key, int fallback) {
        var value = this.data.get(key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public UUID getUniqueId(String key) {
        var value = this.data.get(key);
        if (value == null || value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Reads an enum field, tolerating values this build does not know about.
     *
     * @param key       field name
     * @param enumClass expected enum type
     * @param <T>       enum type
     * @return the parsed constant, or {@code null} when absent or unknown
     */
    public <T extends Enum<T>> T getEnum(String key, Class<T> enumClass) {
        var value = this.data.get(key);
        if (value == null || value.isEmpty()) return null;
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "ClusterMessage{type=" + this.type + ", server=" + this.sourceServer + ", data=" + this.data + "}";
    }

    /**
     * Fluent builder for outgoing messages.
     */
    public static final class Builder {

        private final ClusterMessageType type;
        private final String sourceId;
        private final String sourceServer;
        private final Map<String, String> data = new LinkedHashMap<>();

        private Builder(ClusterMessageType type, String sourceId, String sourceServer) {
            this.type = type;
            this.sourceId = sourceId;
            this.sourceServer = sourceServer;
        }

        public Builder with(String key, String value) {
            if (value != null && !value.isEmpty()) {
                this.data.put(key, value);
            }
            return this;
        }

        public Builder with(String key, int value) {
            this.data.put(key, Integer.toString(value));
            return this;
        }

        public Builder with(String key, UUID value) {
            return value == null ? this : with(key, value.toString());
        }

        public Builder with(String key, Enum<?> value) {
            return value == null ? this : with(key, value.name());
        }

        public ClusterMessage build() {
            return new ClusterMessage(this.type, this.sourceId, this.sourceServer, this.data);
        }
    }
}

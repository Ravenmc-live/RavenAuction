package fr.maxlego08.zauctionhouse.cluster.redis;

import fr.maxlego08.zauctionhouse.cluster.redis.message.ClusterMessage;
import redis.clients.jedis.JedisPubSub;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens to the cluster channel on a dedicated thread and forwards decoded messages to a handler.
 * <p>
 * A pub/sub subscription blocks its connection for as long as it is active, so it runs outside the
 * pooled request path. If the connection drops (Redis restart, network blip, Sentinel failover) the
 * thread reconnects with an exponential backoff instead of leaving the servers silently desynced.
 */
public class RedisSubscriber {

    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final RedisConnection connection;
    private final Logger logger;
    private final Consumer<ClusterMessage> handler;
    private final String channel;

    private volatile boolean running;
    private volatile Thread thread;
    private volatile JedisPubSub pubSub;

    public RedisSubscriber(RedisConnection connection, Logger logger, Consumer<ClusterMessage> handler) {
        this.connection = connection;
        this.logger = logger;
        this.handler = handler;
        this.channel = connection.getConfiguration().channel();
    }

    /**
     * Starts the listener thread. Does nothing when already running.
     */
    public void start() {
        if (this.running) return;

        this.running = true;
        this.thread = new Thread(this::listen, "RavenAuction-Redis-Subscriber");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private void listen() {

        long backoff = INITIAL_BACKOFF_MS;

        while (this.running) {
            try (var jedis = this.connection.getResource()) {

                this.pubSub = new ChannelListener();

                // Reaching this point means the connection is healthy, so the next failure starts
                // over from the shortest delay instead of inheriting an old long one.
                backoff = INITIAL_BACKOFF_MS;

                // Borrowing a connection can take a moment; a stop() that landed in the meantime
                // must not be followed by a subscription nobody is going to cancel.
                if (!this.running) break;

                this.logger.info("Redis cluster listener subscribed to '" + this.channel + "'.");

                // Blocks until unsubscribed or the connection fails.
                jedis.subscribe(this.pubSub, this.channel);

            } catch (Exception exception) {

                if (!this.running) break;

                this.logger.warning("Redis cluster listener lost its connection (" + exception.getMessage() + "), reconnecting in " + (backoff / 1000) + "s.");

                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }

                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);

            } finally {
                this.pubSub = null;
            }
        }
    }

    /**
     * Stops listening and releases the subscription connection.
     */
    public void stop() {

        this.running = false;

        var currentPubSub = this.pubSub;
        if (currentPubSub != null) {
            try {
                currentPubSub.unsubscribe();
            } catch (Exception exception) {
                // Already disconnected: the blocking read has thrown on its own and the thread is
                // unwinding, so there is nothing left to cancel.
                this.logger.log(Level.FINE, "Failed to unsubscribe cleanly from the Redis cluster channel.", exception);
            }
        }

        // Unsubscribing is what ends the blocking read and lets the thread return its connection to
        // the pool cleanly. If Redis is already unreachable the read has thrown anyway, and the
        // daemon thread cannot outlive the pool the caller closes right after this.
        var currentThread = this.thread;
        if (currentThread != null) {
            // Aborts a reconnection backoff immediately instead of waiting it out.
            currentThread.interrupt();
            try {
                currentThread.join(2000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            this.thread = null;
        }
    }

    private class ChannelListener extends JedisPubSub {

        @Override
        public void onMessage(String channel, String payload) {

            var message = ClusterMessage.decode(payload);
            if (message == null) {
                RedisSubscriber.this.logger.warning("Ignored an unreadable message on the Redis cluster channel.");
                return;
            }

            try {
                RedisSubscriber.this.handler.accept(message);
            } catch (Exception exception) {
                // A failure while applying one message must never kill the listener thread.
                RedisSubscriber.this.logger.log(Level.SEVERE, "Failed to apply a cluster message: " + message, exception);
            }
        }
    }
}

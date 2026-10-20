package fr.maxlego08.zauctionhouse.search;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatSearchListener implements Listener, AutoCloseable {
    private final AuctionPlugin plugin;
    private final Set<UUID> waitingForInput = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> opening = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public ChatSearchListener(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void startSearch(Player player) {
        if (closed) return;
        cancel(player);
        UUID request = UUID.randomUUID();
        opening.put(player.getUniqueId(), request);
        this.plugin.getScheduler().runAtEntity(player, task -> {
            if (!opening.remove(player.getUniqueId(), request) || closed || !player.isOnline()) return;
            Location location = player.getLocation().toBlockLocation();
            location.setY(Math.max(player.getWorld().getMinHeight(),
                    Math.min(location.getBlockY() + 1, player.getWorld().getMaxHeight() - 1)));
            Session session = new Session(player, location, location.getBlock().getState(), new AtomicBoolean());
            sessions.put(player.getUniqueId(), session);
            try {
                player.closeInventory();
                Sign sign = (Sign) Material.OAK_SIGN.createBlockData().createBlockState();
                String[] lines = {"", "^^^^^^^^^^^^^", "Search", ""};
                for (int i = 0; i < 4; i++) sign.getSide(Side.FRONT).line(i, Component.text(lines[i]));
                player.sendBlockChange(location, sign.getBlockData());
                player.sendBlockUpdate(location, sign);
                player.openVirtualSign(Position.block(location), Side.FRONT);
            } catch (RuntimeException failure) {
                sessions.remove(player.getUniqueId(), session);
                restore(session);
                waitingForInput.add(player.getUniqueId());
                this.plugin.sendMessage(player, Message.SEARCH_START);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(UncheckedSignChangeEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null || event.getSide() != Side.FRONT
                || !session.location().getWorld().equals(player.getWorld())
                || !Position.block(session.location()).equals(event.getEditedBlockPosition())) return;
        event.setCancelled(true);
        if (!session.submitted().compareAndSet(false, true)) return;
        String query = event.lines().isEmpty() ? ""
                : PlainTextComponentSerializer.plainText().serialize(event.lines().getFirst()).trim();
        this.plugin.getScheduler().runAtEntity(player, task -> {
            if (!sessions.remove(player.getUniqueId(), session)) return;
            if (!session.location().getWorld().equals(player.getWorld())) return;
            restore(session);
            if (!closed && player.isOnline()) submit(player, query);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!waitingForInput.remove(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        String query = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();
        this.plugin.getScheduler().runAtEntity(player, task -> {
            if (!closed && player.isOnline()) submit(player, query);
        });
    }

    private void submit(Player player, String query) {
        this.plugin.getAuctionManager().startSearch(player, query.equalsIgnoreCase("cancel") ? "" : query);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        opening.remove(id);
        waitingForInput.remove(id);
        sessions.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        cancel(event.getPlayer());
    }

    private void cancel(Player player) {
        UUID id = player.getUniqueId();
        opening.remove(id);
        waitingForInput.remove(id);
        Session session = sessions.remove(id);
        if (session != null) restore(session);
    }

    private void restore(Session session) {
        if (!session.player().isOnline()) return;
        session.player().sendBlockChange(session.location(), session.original().getBlockData());
        if (session.original() instanceof TileState tile) session.player().sendBlockUpdate(session.location(), tile);
    }

    @Override
    public void close() {
        closed = true;
        opening.clear();
        waitingForInput.clear();
        sessions.values().forEach(session -> {
            if (this.plugin.getScheduler().isOwnedByCurrentRegion(session.player())) restore(session);
            else this.plugin.getScheduler().runAtEntity(session.player(), task -> restore(session));
        });
        sessions.clear();
    }

    private record Session(Player player, Location location, BlockState original, AtomicBoolean submitted) {}
}

package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.utils.ShulkerHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShulkerViewService implements Listener, AutoCloseable {

    private final ZAuctionPlugin plugin;
    private final Map<UUID, Preview> previews = new ConcurrentHashMap<>();
    private final Map<UUID, Object> pendingReturns = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public ShulkerViewService(ZAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ItemStack shulker, InventoryEngine source) {
        if (this.closed || !ShulkerHelper.isShulkerBox(shulker)) return;
        var manager = this.plugin.getInventoriesLoader().getInventoryManager();
        var definition = manager.getInventory(this.plugin, "shulker-preview");
        if (definition.isEmpty()) return;
        var preview = new Preview(definition.get(), source.getMenuInventory(), source.getPage(),
                List.copyOf(source.getOldInventories()), ShulkerHelper.title(shulker),
                ShulkerHelper.getPositionalContents(shulker));
        this.plugin.getScheduler().runAtEntity(player, task -> {
            if (this.closed || !player.isOnline()
                    || player.getOpenInventory().getTopInventory() != source.getSpigotInventory()) return;
            this.pendingReturns.remove(player.getUniqueId());
            this.previews.put(player.getUniqueId(), preview);
            manager.openInventory(player, preview.definition(), 1, List.of());
        });
    }

    public String title(Player player) {
        var preview = this.previews.get(player.getUniqueId());
        return preview == null ? "Shulker Box" : preview.title();
    }

    public ItemStack[] contents(Player player) {
        var preview = this.previews.get(player.getUniqueId());
        ItemStack[] contents = new ItemStack[27];
        if (preview != null) {
            for (int slot = 0; slot < contents.length; slot++) {
                var stack = preview.contents()[slot];
                if (stack != null) contents[slot] = stack.clone();
            }
        }
        return contents;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (isPreview(event.getView().getTopInventory().getHolder())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (isPreview(event.getView().getTopInventory().getHolder())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !isPreview(event.getInventory().getHolder())) return;
        var preview = this.previews.remove(player.getUniqueId());
        if (this.closed || preview == null || !player.isOnline()) return;
        player.playSound(player.getLocation(), "minecraft:block.shulker_box.close", 0.5f, 1.0f);
        if (event.getReason() != InventoryCloseEvent.Reason.PLAYER) return;
        Object token = new Object();
        this.pendingReturns.put(player.getUniqueId(), token);
        this.plugin.getScheduler().runAtEntity(player, task -> {
            if (!this.pendingReturns.remove(player.getUniqueId(), token) || this.closed || !player.isOnline()
                    || player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) return;
            this.plugin.getInventoriesLoader().getInventoryManager().openInventory(player,
                    preview.source(), preview.page(), preview.history());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        var id = event.getPlayer().getUniqueId();
        this.pendingReturns.remove(id);
        if (event.isCancelled() || !isPreview(event.getInventory().getHolder())) {
            this.previews.remove(id);
        } else if (!this.closed && this.previews.containsKey(id) && event.getPlayer() instanceof Player player) {
            player.playSound(player.getLocation(), "minecraft:block.shulker_box.open", 0.5f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        this.pendingReturns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.previews.remove(event.getPlayer().getUniqueId());
        this.pendingReturns.remove(event.getPlayer().getUniqueId());
    }

    private boolean isPreview(Object holder) {
        return holder instanceof InventoryEngine engine
                && engine.getMenuInventory().getPlugin() == this.plugin
                && engine.getMenuInventory().getFileName().equals("shulker-preview");
    }

    @Override
    public void close() {
        this.closed = true;
        this.previews.clear();
        this.pendingReturns.clear();
    }

    private record Preview(Inventory definition, Inventory source, int page, List<Inventory> history,
                           String title, ItemStack[] contents) {
    }
}

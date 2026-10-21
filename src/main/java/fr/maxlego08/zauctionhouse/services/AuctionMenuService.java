package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.menu.api.InventoryListener;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.buttons.NextButton;
import fr.maxlego08.menu.api.button.buttons.PreviousButton;
import fr.maxlego08.menu.api.engine.BaseInventory;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.sound.SoundOption;
import fr.maxlego08.menu.hooks.xseries.XSound;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton;
import fr.maxlego08.zauctionhouse.utils.cache.ListingIconCache;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AuctionMenuService implements InventoryListener, Listener, AutoCloseable {

    private final AuctionPlugin plugin;
    private final ListingIconCache icons = new ListingIconCache();
    private static final SoundOption PAGE_SOUND = new SoundOption() {
        public XSound getSound() { return XSound.ITEM_BOOK_PAGE_TURN; }
        public float getPitch() { return 1.0f; }
        public float getVolume() { return 0.35f; }
        public boolean isCustom() { return false; }
        public void play(Entity entity) {
            if (entity instanceof Player player) {
                player.playSound(player.getLocation(), "minecraft:item.book.page_turn", getVolume(), getPitch());
            }
        }
    };

    public AuctionMenuService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public ListingIconCache getIcons() {
        return this.icons;
    }

    @Override
    public void onInventoryPostOpen(Player player, BaseInventory inventory) {
        if (!(inventory instanceof InventoryEngine engine) || engine.getMenuInventory().getPlugin() != this.plugin) return;
        for (var button : engine.getButtons()) {
            if (button instanceof NextButton || button instanceof PreviousButton) {
                button.setSoundOption(PAGE_SOUND);
                if (button.hasElseButton()) button.getElseButton().setSoundOption(PAGE_SOUND);
            }
        }
    }

    public static void refreshListings(AuctionPlugin plugin, Player player, InventoryEngine inventory, Button changed) {
        if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory.getSpigotInventory()) return;
        var buttons = inventory.getMenuInventory().getButtons(ListedItemsButton.class);
        if (buttons.size() != 1 || buttons.getFirst().isPlayerInventory()) {
            plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
            return;
        }
        var listed = buttons.getFirst();
        int pageSize = listed.getSlots().size();
        if (pageSize == 0) return;
        int count = plugin.getAuctionManager().getItemIdsListedForSale(player).size();
        int maxPage = Math.max(1, (int) (((long) count + pageSize - 1) / pageSize));
        var manager = plugin.getInventoriesLoader().getInventoryManager();
        if (inventory.getPage() > maxPage || inventory.getMaxPage() != maxPage) {
            manager.openInventory(player, inventory.getMenuInventory(), Math.min(inventory.getPage(), maxPage), inventory.getOldInventories());
            return;
        }
        inventory.setMaxPage(maxPage);
        manager.setPlayerPage(player, inventory.getPage(), maxPage);
        for (int slot : listed.getSlots()) {
            inventory.getItems().remove(slot);
            inventory.getSpigotInventory().setItem(slot, null);
        }
        inventory.buildButton(listed);
        inventory.buildButton(changed);
        for (var button : inventory.getButtons()) {
            if (button instanceof NextButton || button instanceof PreviousButton) inventory.buildButton(button);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getReason() != InventoryCloseEvent.Reason.OPEN_NEW) this.icons.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof InventoryEngine engine)
                || engine.getMenuInventory().getPlugin() != this.plugin) this.icons.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.icons.clear(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        this.icons.close();
    }
}

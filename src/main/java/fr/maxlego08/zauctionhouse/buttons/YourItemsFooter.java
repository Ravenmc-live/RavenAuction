package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.InventoryListener;
import fr.maxlego08.menu.api.engine.BaseInventory;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.engine.ItemButton;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class YourItemsFooter implements InventoryListener {

    private final AuctionPlugin plugin;

    public YourItemsFooter(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onInventoryPreOpen(Player player, BaseInventory inventory, int page, Object... objects) {
        if (objects.length > 0 && objects[0] instanceof Inventory definition
                && definition.getPlugin() == this.plugin
                && definition.getFileName().equals("combined-items")
                && player.getOpenInventory().getTopInventory().getHolder() instanceof InventoryEngine source
                && source.getMenuInventory().getPlugin() == this.plugin
                && source.getMenuInventory().getFileName().equals("auction")) {
            this.plugin.getAuctionManager().getCache(player).set(PlayerCacheKey.CURRENT_PAGE, source.getPage());
        }
    }

    @Override
    public void onInventoryPostOpen(Player player, BaseInventory inventory) {
        if (inventory instanceof InventoryEngine engine) apply(player, engine);
    }

    public void apply(Player player, InventoryEngine engine) {
        if (engine.getMenuInventory().getPlugin() != this.plugin
                || !engine.getMenuInventory().getFileName().equals("combined-items")
                || engine.getSpigotInventory().getSize() != 54) return;
        engine.getItems().remove(49);
        engine.getSpigotInventory().setItem(49, null);
        boolean firstPage = engine.getPage() <= 1;
        var item = new ItemStack(firstPage ? Material.RED_STAINED_GLASS_PANE : Material.ARROW);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(firstPage ? "Return" : "Previous Page", TextColor.color(0xFF1F1F))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.empty(), Component.text(firstPage
                ? "Return to the auction house." : "Return to the previous page.", TextColor.color(0xFFFFFF))
                .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        var button = new ItemButton(item, 45, false, engine).setClick(event -> {
            event.setCancelled(true);
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != engine.getSpigotInventory()) return;
            player.playSound(player.getLocation(), firstPage ? "minecraft:ui.button.click" : "minecraft:item.book.page_turn", 0.35f, 1.05f);
            if (firstPage) {
                int page = this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.CURRENT_PAGE, 1);
                var manager = this.plugin.getInventoriesLoader().getInventoryManager();
                var history = engine.getOldInventories();
                var target = manager.getInventory(this.plugin, Inventories.AUCTION.getFileName());
                target.ifPresent(inventory -> manager.openInventory(player, inventory, page,
                        history.isEmpty() ? List.of() : List.copyOf(history.subList(0, history.size() - 1))));
            } else {
                this.plugin.getInventoriesLoader().getInventoryManager().openInventory(player,
                        engine.getMenuInventory(), engine.getPage() - 1, List.copyOf(engine.getOldInventories()));
            }
        });
        engine.getItems().put(45, button);
        engine.getSpigotInventory().setItem(45, item);
    }
}

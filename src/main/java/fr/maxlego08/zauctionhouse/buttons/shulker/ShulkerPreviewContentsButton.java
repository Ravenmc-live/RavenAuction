package fr.maxlego08.zauctionhouse.buttons.shulker;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class ShulkerPreviewContentsButton extends Button {

    private final ZAuctionPlugin plugin;

    public ShulkerPreviewContentsButton(Plugin plugin) {
        this.plugin = (ZAuctionPlugin) plugin;
    }

    @Override
    public boolean hasSpecialRender() {
        return true;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventory) {
        render(inventory, this.plugin.getShulkerViews().contents(player), 0);
    }

    static void render(InventoryEngine inventory, ItemStack[] contents, int offset) {
        for (int slot = 0; slot < 27; slot++) {
            int target = offset + slot;
            var stack = contents[slot];
            if (stack == null || stack.getType().isAir()) {
                inventory.getItems().remove(target);
                inventory.getSpigotInventory().setItem(target, null);
            } else {
                var button = inventory.addItem(target, stack.clone());
                if (button != null) button.setClick(event -> event.setCancelled(true));
            }
        }
    }
}

package fr.maxlego08.zauctionhouse.buttons.shulker;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.utils.ShulkerHelper;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ShulkerPurchaseContentsButton extends Button {

    private final AuctionPlugin plugin;

    public ShulkerPurchaseContentsButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public boolean hasSpecialRender() {
        return true;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventory) {
        var shulker = ShulkerHelper.singleShulker(this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ITEM_SHOW));
        ShulkerPreviewContentsButton.render(inventory, ShulkerHelper.getPositionalContents(shulker), 27);
    }
}

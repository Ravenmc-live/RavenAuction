package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.entity.Player;

public final class PurchaseFeedback {
    private PurchaseFeedback() {}

    public static void insufficientFunds(AuctionPlugin plugin, Player player) {
        plugin.getScheduler().runAtEntity(player, task -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), "minecraft:entity.villager.no", 0.6f, 1.0f);
            }
        });
    }
}

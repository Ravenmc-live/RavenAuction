package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class SaleItemRecovery {
    private SaleItemRecovery() {}

    static void returnItems(AuctionPlugin plugin, Player player, Location location, List<ItemStack> items) {
        AtomicBoolean returned = new AtomicBoolean();
        Runnable drop = () -> plugin.getScheduler().runAtLocation(location, task -> {
            if (!returned.compareAndSet(false, true)) return;
            for (ItemStack item : items) location.getWorld().dropItemNaturally(location, item.clone());
        });
        plugin.getScheduler().runAtEntityWithFallback(player, task -> {
            if (!player.isOnline()) {
                drop.run();
                return;
            }
            if (!returned.compareAndSet(false, true)) return;
            for (ItemStack item : items) {
                player.getInventory().addItem(item.clone()).values().forEach(left ->
                        player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        }, drop).exceptionally(failure -> {
            drop.run();
            return null;
        });
    }
}

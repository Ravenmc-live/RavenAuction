package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.services.AuctionMenuService;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class RefreshButton extends Button {

    private final AuctionPlugin plugin;
    private final MenuItemStack loadingItemStack;

    public RefreshButton(AuctionPlugin plugin, MenuItemStack loadingItemStack) {
        this.plugin = plugin;
        this.loadingItemStack = loadingItemStack;
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);

        if (cache.get(PlayerCacheKey.REFRESH_LOADING)) {
            return this.loadingItemStack.build(player, false, placeholders);
        }

        return this.getItemStack().build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var cache = this.plugin.getAuctionManager().getCache(player);

        if (cache.get(PlayerCacheKey.REFRESH_LOADING)) return;

        cache.set(PlayerCacheKey.REFRESH_LOADING, true);

        event.setCancelled(true);
        cache.remove(PlayerCacheKey.ITEMS_LISTED);
        cache.remove(PlayerCacheKey.ITEMS_SEARCH);
        if (this.plugin instanceof ZAuctionPlugin auctionPlugin) {
            auctionPlugin.getMenuService().getIcons().clear(player.getUniqueId());
        }
        String query = cache.get(PlayerCacheKey.SEARCH_QUERY);
        boolean searching = query != null && !query.isBlank();
        Runnable refresh = () -> {
            cache.set(PlayerCacheKey.REFRESH_LOADING, false);
            AuctionMenuService.refreshListings(this.plugin, player, inventory, this);
        };
        if (!searching) {
            refresh.run();
        } else {
            var itemStack = this.loadingItemStack.build(player);
            for (Integer newSlot : getSlots()) inventory.getSpigotInventory().setItem(newSlot, itemStack);
            this.plugin.getScheduler().runAsync(task -> {
                try {
                    this.plugin.getAuctionManager().getItemIdsListedForSale(player);
                    this.plugin.getScheduler().runAtEntity(player, entityTask -> refresh.run());
                } catch (RuntimeException exception) {
                    cache.set(PlayerCacheKey.REFRESH_LOADING, false);
                    this.plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to refresh auction search results", exception);
                }
            });
        }
    }
}

package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.services.AuctionMenuService;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class SortButton extends Button {

    private final AuctionPlugin plugin;
    private final String enableText;
    private final String disableText;
    private final MenuItemStack loadingItemStack;
    private final List<SortItem> sortItems; // Immutable copy to prevent concurrent modification

    public SortButton(AuctionPlugin plugin, String enableText, String disableText, MenuItemStack loadingItemStack, List<SortItem> sortItems) {
        this.plugin = plugin;
        this.enableText = enableText;
        this.disableText = disableText;
        this.loadingItemStack = loadingItemStack;
        this.sortItems = List.copyOf(sortItems); // Create immutable copy to prevent concurrent modification
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {

        var config = this.plugin.getConfiguration().getSort();
        var cache = this.plugin.getAuctionManager().getCache(player);

        MenuItemStack itemStack = this.getItemStack();

        this.sortItems.forEach(sort -> {
            SortItem currentSort = cache.get(PlayerCacheKey.ITEM_SORT, config.defaultSort());
            placeholders.register(sort.name(), (sort == currentSort ? this.enableText : this.disableText).replace("%sorting%", config.sortItems().get(sort)));
        });

        return itemStack.build(player, false, placeholders);

    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var config = this.plugin.getConfiguration().getSort();
        var cache = this.plugin.getAuctionManager().getCache(player);

        if (cache.get(PlayerCacheKey.ITEM_SORT_LOADING)) return;

        if (this.sortItems.isEmpty()) return;

        SortItem currentSort = cache.get(PlayerCacheKey.ITEM_SORT, config.defaultSort());

        int index = this.sortItems.indexOf(currentSort);
        if (index == -1) return;

        cache.set(PlayerCacheKey.ITEM_SORT_LOADING, true);

        int direction = event.isRightClick() ? -1 : 1;
        int size = this.sortItems.size();

        int nextIndex = (index + direction + size) % size;
        SortItem nextSort = this.sortItems.get(nextIndex);

        event.setCancelled(true);
        String query = cache.get(PlayerCacheKey.SEARCH_QUERY);
        boolean searching = query != null && !query.isBlank();
        if (searching) {
            var itemStack = this.loadingItemStack.build(player);
            for (Integer newSlot : getSlots()) inventory.getSpigotInventory().setItem(newSlot, itemStack);
        }

        cache.set(PlayerCacheKey.ITEM_SORT, nextSort);
        cache.remove(PlayerCacheKey.ITEMS_LISTED);
        cache.remove(PlayerCacheKey.ITEMS_SEARCH);

        Runnable refresh = () -> {
            try {
                AuctionMenuService.refreshListings(this.plugin, player, inventory, this);
            } finally {
                cache.set(PlayerCacheKey.ITEM_SORT_LOADING, false);
            }
        };
        if (!searching) {
            refresh.run();
        } else {
            this.plugin.getScheduler().runAsync(task -> {
                try {
                    this.plugin.getAuctionManager().getItemIdsListedForSale(player);
                    this.plugin.getScheduler().runAtEntity(player, entityTask -> refresh.run());
                } catch (RuntimeException exception) {
                    cache.set(PlayerCacheKey.ITEM_SORT_LOADING, false);
                    this.plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to sort auction search results", exception);
                }
            });
        }
    }

}

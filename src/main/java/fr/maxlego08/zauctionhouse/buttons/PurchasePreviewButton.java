package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.Arrays;

public final class PurchasePreviewButton extends Button {
    private final AuctionPlugin plugin;

    public PurchasePreviewButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public @Nullable ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        Item item = this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ITEM_SHOW);
        return item == null ? null : item.buildItemStack(player, List.of("",
                "&fPrice: &#FF1F1F%price%", "&fSeller: &#FF1F1F%seller%", "&fExpires: &#FF1F1F%time-remaining%"));
    }

    public static String titleName(Item item) {
        if (!(item instanceof AuctionItem auction) || auction.getItemStacks() == null || auction.getItemStacks().isEmpty()) return "Item";
        if (auction.getItemStacks().size() > 1) return "Items";
        String name = auction.getItemStacks().getFirst().getType().name().toLowerCase(Locale.ROOT);
        return Arrays.stream(name.split("_")).map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}

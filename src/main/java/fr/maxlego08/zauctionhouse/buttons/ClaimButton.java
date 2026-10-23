package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Button that allows players to claim their pending money from transactions.
 * <p>
 * Displays per-economy pending amounts as placeholders in the lore:
 * <ul>
 *     <li>{@code %pending_total%} - total pending money formatted with the first economy</li>
 *     <li>{@code %pending_<economy_name>%} - pending money for a specific economy</li>
 *     <li>{@code %has_pending%} - "true" or "false"</li>
 * </ul>
 * Shows a configurable loading item while data is being fetched.
 */
public class ClaimButton extends Button {

    private final AuctionPlugin plugin;
    private final MenuItemStack loadingItemStack;

    public ClaimButton(AuctionPlugin plugin, MenuItemStack loadingItemStack) {
        this.plugin = plugin;
        this.loadingItemStack = loadingItemStack;
    }

    @Override
    public void onInventoryOpen(@NotNull Player player, @NotNull InventoryEngine inventory, @NotNull Placeholders placeholders) {
        super.onInventoryOpen(player, inventory, placeholders);

        var cache = this.plugin.getAuctionManager().getCache(player);

        if (cache.has(PlayerCacheKey.PENDING_MONEY_DATA)) {
            return;
        }

        Boolean isLoading = cache.get(PlayerCacheKey.PENDING_MONEY_LOADING, false);
        if (isLoading) {
            return;
        }

        cache.set(PlayerCacheKey.PENDING_MONEY_LOADING, true);

        this.plugin.getAuctionManager().getClaimService().getPendingMoneyByEconomy(player.getUniqueId()).thenAccept(pendingByEconomy -> {
            cache.set(PlayerCacheKey.PENDING_MONEY_DATA, pendingByEconomy);
            cache.set(PlayerCacheKey.PENDING_MONEY_LOADING, false);

            this.plugin.getScheduler().runAtEntity(player, task -> {
                if (player.isOnline()) {
                    inventory.getSpigotInventory().setItem(getSlot(), this.getCustomItemStack(player, false, new Placeholders()));
                }
            });
        }).exceptionally(throwable -> {
            // Without this the button kept its loading item forever.
            cache.set(PlayerCacheKey.PENDING_MONEY_LOADING, false);
            this.plugin.getLogger().warning("Could not load pending money for " + player.getName() + ": " + throwable.getMessage());
            return null;
        });
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        var economyManager = this.plugin.getEconomyManager();

        if (cache.get(PlayerCacheKey.PENDING_MONEY_LOADING, false)) {
            return this.loadingItemStack.build(player, false, placeholders);
        }

        Map<String, BigDecimal> pendingData = cache.get(PlayerCacheKey.PENDING_MONEY_DATA);
        BigDecimal total = BigDecimal.ZERO;

        if (pendingData != null && !pendingData.isEmpty()) {
            total = pendingData.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Register per-economy placeholders
        for (var economy : economyManager.getEconomies()) {
            BigDecimal amount = pendingData != null ? pendingData.getOrDefault(economy.getName(), BigDecimal.ZERO) : BigDecimal.ZERO;
            placeholders.register("pending_" + economy.getName(), economyManager.format(economy, amount));
        }

        // Register total placeholder using the first economy format, or plain number
        var economies = economyManager.getEconomies();
        if (!economies.isEmpty()) {
            var firstEconomy = economies.iterator().next();
            placeholders.register("pending_total", economyManager.format(firstEconomy, total));
        } else {
            placeholders.register("pending_total", total.toPlainString());
        }

        placeholders.register("has_pending", String.valueOf(total.compareTo(BigDecimal.ZERO) > 0));

        return this.getItemStack().build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        Map<String, BigDecimal> pendingData = cache.get(PlayerCacheKey.PENDING_MONEY_DATA);

        // The amounts are loaded asynchronously: a click before they arrive used to do nothing
        // at all, which players read as "the claim button is broken".
        BigDecimal total = pendingData == null ? BigDecimal.ZERO
                : pendingData.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0 && cache.get(PlayerCacheKey.PENDING_MONEY_LOADING, false)) {
            return;
        }

        cache.remove(PlayerCacheKey.PENDING_MONEY_DATA, PlayerCacheKey.PENDING_MONEY_LOADING);

        this.plugin.getAuctionManager().getClaimService().claimMoney(player).thenRun(() -> {
            this.plugin.getScheduler().runAtEntity(player, task -> {
                if (player.isOnline()) {
                    this.plugin.getAuctionManager().updateInventory(player);
                }
            });
        }).exceptionally(throwable -> {
            this.plugin.getLogger().warning("Failed to claim money for " + player.getName() + ": " + throwable.getMessage());
            return null;
        });
    }
}

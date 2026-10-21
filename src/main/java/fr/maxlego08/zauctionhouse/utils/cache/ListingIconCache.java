package fr.maxlego08.zauctionhouse.utils.cache;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class ListingIconCache implements AutoCloseable {

    private final Map<UUID, LinkedHashMap<Integer, Icon>> viewers = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile boolean closed;

    public ListingIconCache() {
        this(System::nanoTime);
    }

    ListingIconCache(LongSupplier clock) {
        this.clock = clock;
    }

    public ItemStack get(Player player, Item item, Object configuration) {
        if (this.closed) return item.buildItemStack(player);
        var icons = this.viewers.computeIfAbsent(player.getUniqueId(), id -> new LinkedHashMap<>(90, 0.75f, true));
        long now = this.clock.getAsLong();
        String remaining = item.getRemainingTime();
        synchronized (icons) {
            if (this.closed) {
                this.viewers.remove(player.getUniqueId(), icons);
                return item.buildItemStack(player);
            }
            var icon = icons.get(item.getId());
            if (icon != null && icon.item() == item && icon.configuration() == configuration
                    && now - icon.created() < 2_000_000_000L && icon.status() == item.getStatus()
                    && Objects.equals(icon.price(), item.getPrice()) && icon.economy() == item.getAuctionEconomy()
                    && Objects.equals(icon.remaining(), remaining)) return icon.stack().clone();
            var stack = item.buildItemStack(player);
            icons.put(item.getId(), new Icon(item, configuration, item.getStatus(), item.getPrice(),
                    item.getAuctionEconomy(), remaining, now, stack.clone()));
            if (icons.size() > 90) icons.remove(icons.keySet().iterator().next());
            return stack;
        }
    }

    public void clear(UUID player) {
        this.viewers.remove(player);
    }

    public void clear() {
        this.viewers.clear();
    }

    @Override
    public void close() {
        this.closed = true;
        this.viewers.clear();
    }

    private record Icon(Item item, Object configuration, ItemStatus status, BigDecimal price, Object economy,
                        String remaining, long created, ItemStack stack) {
    }
}

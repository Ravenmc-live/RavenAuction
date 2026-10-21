package fr.maxlego08.zauctionhouse.utils.cache;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingIconCacheTest {
    AtomicLong clock;
    ListingIconCache cache;
    Player player;
    Item item;
    ItemStack built;
    ItemStack stored;
    ItemStack copy;
    Object config;

    @BeforeEach
    void setup() {
        clock = new AtomicLong();
        cache = new ListingIconCache(clock::get);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        item = mock(Item.class);
        when(item.getStatus()).thenReturn(ItemStatus.AVAILABLE);
        when(item.getPrice()).thenReturn(BigDecimal.TEN);
        when(item.getRemainingTime()).thenReturn("10m");
        built = mock(ItemStack.class);
        stored = mock(ItemStack.class);
        copy = mock(ItemStack.class);
        when(item.buildItemStack(player)).thenReturn(built);
        when(built.clone()).thenReturn(stored);
        when(stored.clone()).thenReturn(copy);
        config = new Object();
    }

    @Test
    void repeatedPageRendersReuseTheIconButReturnAnIndependentCopy() {
        assertSame(built, cache.get(player, item, config));
        assertSame(copy, cache.get(player, item, config));
        verify(item, times(1)).buildItemStack(player);
    }

    @Test
    void expiryLoreChangesInvalidateTheIconImmediately() {
        cache.get(player, item, config);
        when(item.getRemainingTime()).thenReturn("9m");
        assertSame(built, cache.get(player, item, config));
        verify(item, times(2)).buildItemStack(player);
    }

    @Test
    void priceStatusAndReloadedConfigurationInvalidateTheIcon() {
        cache.get(player, item, config);
        when(item.getPrice()).thenReturn(BigDecimal.ONE);
        cache.get(player, item, config);
        when(item.getStatus()).thenReturn(ItemStatus.IS_PURCHASE_CONFIRM);
        cache.get(player, item, config);
        cache.get(player, item, new Object());
        verify(item, times(4)).buildItemStack(player);
    }

    @Test
    void snapshotsExpireAndCanBeClearedOnQuitOrDisable() {
        cache.get(player, item, config);
        clock.set(2_000_000_000L);
        cache.get(player, item, config);
        cache.clear(player.getUniqueId());
        cache.get(player, item, config);
        cache.clear();
        cache.get(player, item, config);
        verify(item, times(4)).buildItemStack(player);
    }

    @Test
    void eachViewerHasAtMostNinetyRecentlyUsedIcons() {
        cache.get(player, item, config);
        for (int id = 1; id <= 90; id++) {
            var next = mock(Item.class);
            when(next.getId()).thenReturn(id);
            when(next.buildItemStack(player)).thenReturn(built);
            cache.get(player, next, config);
        }
        cache.get(player, item, config);
        verify(item, times(2)).buildItemStack(player);
    }
    @Test
    void disabledCachesDoNotRetainIconsOrResumeCaching() {
        cache.close();
        cache.get(player, item, config);
        cache.get(player, item, config);
        verify(item, times(2)).buildItemStack(player);
    }
}

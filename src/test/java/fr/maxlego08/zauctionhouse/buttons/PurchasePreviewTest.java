package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PurchasePreviewTest {
    @Test
    void titleUsesTheSelectedMaterialInsteadOfAStaticExampleOrRawToken() {
        AuctionItem item = mock(AuctionItem.class);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(item.getItemStacks()).thenReturn(List.of(stack));
        assertEquals("Diamond Sword", PurchasePreviewButton.titleName(item));
        when(item.getItemStacks()).thenReturn(List.of(stack, stack));
        assertEquals("Items", PurchasePreviewButton.titleName(item));
        assertEquals("Item", PurchasePreviewButton.titleName(null));
    }
    @Test
    void previewRequestsOnlyPriceSellerAndExpiryLoreWithLeadingSpace() throws Exception {
        AuctionPlugin plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        Player player = mock(Player.class);
        Item item = mock(Item.class);
        when(plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ITEM_SHOW)).thenReturn(item);
        PurchasePreviewButton button = mock(PurchasePreviewButton.class, CALLS_REAL_METHODS);
        Field field = PurchasePreviewButton.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(button, plugin);
        button.getCustomItemStack(player, false, new Placeholders());
        verify(item).buildItemStack(player, List.of("", "&fPrice: &#FF1F1F%price%", "&fSeller: &#FF1F1F%seller%", "&fExpires: &#FF1F1F%time-remaining%"));
    }
}

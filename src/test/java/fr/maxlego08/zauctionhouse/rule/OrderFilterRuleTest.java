package fr.maxlego08.zauctionhouse.rule;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.rule.loaders.OrderFilterRuleLoader;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderFilterRuleTest {
    private boolean matches(String filter, Material material) {
        ItemRuleContext context = mock(ItemRuleContext.class);
        when(context.getMaterial()).thenReturn(material);
        return new OrderFilterRuleLoader().load(Map.of("filter", filter)).matches(context);
    }
    @Test
    void toolsCombatAndUtilitiesUseTheSameGroupsAsOrder() {
        assertTrue(matches("tools", Material.DIAMOND_PICKAXE));
        assertTrue(matches("combat", Material.DIAMOND_CHESTPLATE));
        assertTrue(matches("utilities", Material.COMPASS));
        assertFalse(matches("combat", Material.COMPASS));
        assertFalse(matches("utilities", Material.DIAMOND_PICKAXE));
    }
    @Test
    void potionBookAndIngredientGroupsAreNotJustDisplayLabels() {
        assertTrue(matches("potions", Material.SPLASH_POTION));
        assertTrue(matches("books", Material.ENCHANTED_BOOK));
        assertTrue(matches("ingredients", Material.IRON_INGOT));
        assertFalse(matches("potions", Material.BOOK));
        assertFalse(matches("books", Material.IRON_INGOT));
    }
    @Test
    void blocksAndFoodUseTheActualMaterialProperties() {
        Material material = mock(Material.class);
        when(material.isBlock()).thenReturn(true);
        assertTrue(matches("blocks", material));
        assertFalse(matches("food", material));
        when(material.isEdible()).thenReturn(true);
        assertTrue(matches("food", material));
        assertNull(new OrderFilterRuleLoader().load(Map.of("filter", "unknown")));
    }
}

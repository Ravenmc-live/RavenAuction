package fr.maxlego08.zauctionhouse.buttons;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FooterPurchaseTest {
    private YamlConfiguration load(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }
    @Test
    void footerUsesRequestedColumnsAndAlwaysVisibleBoundaryArrows() {
        var menu = load("inventories/auction.yml");
        assertEquals(46, menu.getInt("items.your-items.slot"));
        assertEquals(47, menu.getInt("items.search.slot"));
        assertEquals(49, menu.getInt("items.refresh.slot"));
        assertEquals("ANVIL", menu.getString("items.refresh.item.material"));
        assertEquals("ZAUCTIONHOUSE_REFRESH", menu.getString("items.refresh.type"));
        assertEquals(51, menu.getInt("items.sort.slot"));
        assertEquals(52, menu.getInt("items.filter.slot"));
        assertEquals("HOPPER", menu.getString("items.filter.item.material"));
        var nav = load("patterns/pagination.yml");
        assertEquals(45, nav.getInt("items.previous.slot"));
        assertEquals(53, nav.getInt("items.next.slot"));
        for (String key : List.of("previous", "next")) {
            assertEquals("ARROW", nav.getString("items." + key + ".else.item.material"));
            assertEquals("NONE", nav.getString("items." + key + ".else.type"));
        }
    }
    @Test
    void filterContainsTheSameGroupsAsOrderWithMatchingRules() {
        var menu = load("inventories/auction.yml");
        var defs = load("categories/raven-order-filters.yml");
        var groups = menu.getStringList("items.filter.categories");
        assertEquals(List.of("main", "raven-filter-blocks", "raven-filter-tools", "raven-filter-food", "raven-filter-combat",
                "raven-filter-potions", "raven-filter-books", "raven-filter-ingredients", "raven-filter-utilities"), groups);
        for (String key : groups.subList(1, groups.size())) {
            var rule = defs.getMapList(key + ".rules").getFirst();
            assertEquals("raven-order-filter", rule.get("type"));
            assertEquals(key.substring("raven-filter-".length()), rule.get("filter"));
        }
    }
    @Test
    void purchaseUsesThreeSeparatedButtonsAndCleanLoreWithNoFillersOrShulkers() {
        var menu = load("inventories/confirms/purchase-confirm.yml");
        assertEquals(27, menu.getInt("size"));
        assertEquals(11, menu.getInt("items.confirm.slot"));
        assertEquals("LIME_STAINED_GLASS_PANE", menu.getString("items.confirm.item.material"));
        assertEquals(13, menu.getInt("items.show.slot"));
        assertEquals("RAVENAUCTION_PURCHASE_PREVIEW", menu.getString("items.show.type"));
        assertEquals(15, menu.getInt("items.deny.slot"));
        assertEquals("RED_STAINED_GLASS_PANE", menu.getString("items.deny.item.material"));
        assertEquals(3, menu.getConfigurationSection("items").getKeys(false).size());
        for (String key : List.of("confirm", "deny")) {
            var lore = menu.getStringList("items." + key + ".item.lore");
            assertEquals("", lore.getFirst());
            assertFalse(lore.stream().anyMatch(line -> line.toLowerCase().matches(".*(auctions|description|click).*")));
        }
        var config = load("config.yml");
        assertFalse(config.getBoolean("action.purchased-item.money-item.enable"));
        assertFalse(config.getBoolean("action.purchased-item.money-message"));
        assertEquals("minecraft:entity.villager.no", config.getString("action.purchased-item.money-sound.sound"));
    }
}

package fr.maxlego08.zauctionhouse.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StyleMigrationTest {
    @TempDir Path directory;
    private InputStream resource(String name) { return getClass().getResourceAsStream("/" + name); }
    private void write(String name, String text) throws Exception {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    @Test
    void appliesStyleAndBacksUpOriginalsWhilePreservingOperationalSettings() throws Exception {
        String original = "database-configuration:\n  host: private-host\n  password: private-value\nstorage-type: MYSQL\nredis:\n  enable: false\naction:\n  purchased-item:\n    give-item: false\nitem-lore:\n  listed-auction-item: [old]\n";
        write("config.yml", original);
        write("messages.yml", "old messages");
        write("inventories/auction.yml", "old inventory");
        write("database.db", "database bytes");
        write("economies.yml", "custom economies");
        write("rules.yml", "custom rules");
        StyleMigration.apply(directory, this::resource);
        YamlConfiguration after = YamlConfiguration.loadConfiguration(directory.resolve("config.yml").toFile());
        assertEquals("private-host", after.getString("database-configuration.host"));
        assertEquals("private-value", after.getString("database-configuration.password"));
        assertEquals("MYSQL", after.getString("storage-type"));
        assertFalse(after.getBoolean("action.purchased-item.give-item"));
        assertFalse(after.getBoolean("action.purchased-item.money-item.enable"));
        assertFalse(after.getBoolean("action.purchased-item.money-message"));
        assertEquals("minecraft:entity.villager.no", after.getString("action.purchased-item.money-sound.sound"));
        assertEquals(original, Files.readString(directory.resolve(StyleMigration.BACKUP).resolve("config.yml")));
        assertEquals("old messages", Files.readString(directory.resolve(StyleMigration.BACKUP).resolve("messages.yml")));
        assertEquals("old inventory", Files.readString(directory.resolve(StyleMigration.BACKUP).resolve("inventories/auction.yml")));
        assertEquals("database bytes", Files.readString(directory.resolve("database.db")));
        assertEquals("custom economies", Files.readString(directory.resolve("economies.yml")));
        assertEquals("custom rules", Files.readString(directory.resolve("rules.yml")));
        assertTrue(Files.isRegularFile(directory.resolve(StyleMigration.MARKER)));
    }

    @Test
    void laterStartsNeverOverwriteEditsOrReadResourcesAgain() throws Exception {
        StyleMigration.apply(directory, this::resource);
        write("messages.yml", "my edited messages");
        write("config.yml", "my edited config");
        write("inventories/auction.yml", "my edited menu");
        StyleMigration.apply(directory, name -> { fail("Already applied"); return null; });
        assertEquals("my edited messages", Files.readString(directory.resolve("messages.yml")));
        assertEquals("my edited config", Files.readString(directory.resolve("config.yml")));
        assertEquals("my edited menu", Files.readString(directory.resolve("inventories/auction.yml")));
    }

    @Test
    void missingResourceMakesNoChangesAndCanBeRetried() throws Exception {
        write("messages.yml", "original");
        assertThrows(java.io.IOException.class, () -> StyleMigration.apply(directory,
                name -> name.equals("messages.yml") ? null : resource(name)));
        assertEquals("original", Files.readString(directory.resolve("messages.yml")));
        assertFalse(Files.exists(directory.resolve(StyleMigration.MARKER)));
        StyleMigration.apply(directory, this::resource);
        assertTrue(Files.isRegularFile(directory.resolve(StyleMigration.MARKER)));
    }

    @Test
    void invalidExistingConfigurationIsNeverOverwritten() throws Exception {
        write("config.yml", "config: [invalid");
        write("messages.yml", "original");
        assertThrows(java.io.IOException.class, () -> StyleMigration.apply(directory, this::resource));
        assertEquals("config: [invalid", Files.readString(directory.resolve("config.yml")));
        assertEquals("original", Files.readString(directory.resolve("messages.yml")));
        assertFalse(Files.exists(directory.resolve(StyleMigration.MARKER)));
    }

    @Test
    void allBundledMenusParseHaveLeadingLoreSpaceAndNoClickHints() throws Exception {
        StyleMigration.apply(directory, this::resource);
        List<String> names;
        try (InputStream stream = resource("raven-style/files.txt")) {
            names = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
        for (String name : names) {
            YamlConfiguration menu = new YamlConfiguration();
            menu.load(directory.resolve(name).toFile());
            for (var entry : menu.getValues(true).entrySet()) {
                if (entry.getKey().endsWith("lore") && entry.getValue() instanceof List<?> lines && !lines.isEmpty()) {
                    assertEquals("", lines.getFirst(), name + ":" + entry.getKey());
                    for (Object line : lines) assertFalse(line.toString().toLowerCase().contains("click"), name + ":" + line);
                }
            }
        }
        var messages = YamlConfiguration.loadConfiguration(directory.resolve("messages.yml").toFile());
        assertEquals("", messages.getString("prefix"));
    }

    @Test
    void revisedLayoutAppliesOnceEvenWhenThePreviousStyleWasAlreadyApplied() throws Exception {
        write(".red-gui-ff1f1f-v1.applied", "previous style done");
        write("inventories/auction.yml", "old layout");
        StyleMigration.apply(directory, this::resource);
        assertEquals("old layout", Files.readString(directory.resolve(StyleMigration.BACKUP).resolve("inventories/auction.yml")));
        var menu = YamlConfiguration.loadConfiguration(directory.resolve("inventories/auction.yml").toFile());
        assertFalse(menu.contains("items.categories"));
        assertFalse(menu.contains("items.expired-items"));
        assertEquals("ZAUCTIONHOUSE_CHANGE_SORT", menu.getString("items.sort.type"));
        assertTrue(Files.exists(directory.resolve(StyleMigration.MARKER)));
        write("inventories/auction.yml", "later edits");
        StyleMigration.apply(directory, name -> { fail("Must apply only once"); return null; });
        assertEquals("later edits", Files.readString(directory.resolve("inventories/auction.yml")));
    }

    @Test
    void sixthRowUsesFunctionalSortYourItemsAndSignSearchWithoutExpiredOrCategoryButtons() throws Exception {
        StyleMigration.apply(directory, this::resource);
        var menu = YamlConfiguration.loadConfiguration(directory.resolve("inventories/auction.yml").toFile());
        assertEquals(54, menu.getInt("size"));
        assertEquals(List.of("0-44"), menu.getStringList("items.display-items.slots"));
        assertEquals("ZAUCTIONHOUSE_CHANGE_SORT", menu.getString("items.sort.type"));
        assertEquals(51, menu.getInt("items.sort.slot"));
        assertEquals(46, menu.getInt("items.your-items.slot"));
        assertEquals("combined-items", menu.getString("items.your-items.inventory"));
        assertEquals(47, menu.getInt("items.search.slot"));
        assertEquals("OAK_SIGN", menu.getString("items.search.item.material"));
        assertFalse(menu.contains("items.expired-items"));
        assertFalse(menu.contains("items.categories"));
        var nav = YamlConfiguration.loadConfiguration(directory.resolve("patterns/pagination.yml").toFile());
        assertEquals(45, nav.getInt("items.previous.slot"));
        assertEquals(53, nav.getInt("items.next.slot"));
        for (String key : List.of("sort", "your-items", "search")) {
            assertTrue(menu.getString("items." + key + ".item.name").startsWith("&r&#FF1F1F"));
            assertEquals("", menu.getStringList("items." + key + ".item.lore").getFirst());
        }
        var mine = YamlConfiguration.loadConfiguration(directory.resolve("inventories/combined-items.yml").toFile());
        assertTrue(mine.getBoolean("items.items.include-expired"));
        assertTrue(mine.getBoolean("items.items.include-purchased"));
        assertTrue(mine.getBoolean("items.items.include-selling"));
    }
}

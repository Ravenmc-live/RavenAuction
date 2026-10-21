package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstalledConfigurationUpdateTest {
    @TempDir Path root;
    private static final String DEFAULTS = "redis:\n  enabled: true\n  mode: standalone\n  host: redis.test\n  port: 6379\n  username: ''\n  password: 'test-password'\n  database: 0\n  ssl: false\n";

    @Test void existingPurchaseFilesUpdateOnceWithBackupsAndObsoleteMenusArePreservedOutsideTheLoader() throws Exception {
        AuctionPlugin plugin = mock(AuctionPlugin.class);
        when(plugin.getDataFolder()).thenReturn(root.toFile());
        when(plugin.getResource(anyString())).thenAnswer(call -> {
            String name = call.getArgument(0);
            if (name.equals("config.yml")) return new ByteArrayInputStream(DEFAULTS.getBytes(StandardCharsets.UTF_8));
            return getClass().getResourceAsStream("/" + name);
        });
        Path normal = root.resolve("inventories/confirms/purchase-confirm.yml");
        Files.createDirectories(normal.getParent());
        Files.writeString(normal, "name: Old\nsize: 54\n");
        String configuration = "# Keep comments\r\nstorage-type: MYSQL\r\ndatabase-configuration:\r\n  password: private-db\r\n  table-prefix: custom_\r\nredis:\r\n  host: old.test\r\n  password: old-redis\r\n  channel-prefix: network\r\n  pool:\r\n    max-total: 12\r\n# After Redis\r\nauto-claim:\r\n  enable: true\r\n";
        Files.writeString(root.resolve("config.yml"), configuration);
        Path obsolete = root.resolve("inventories/admin/server-item-dropper.yml");
        Files.createDirectories(obsolete.getParent());
        Files.writeString(obsolete, "items:\n  old:\n    type: RAVENAUCTION_SERVER_ITEM_DROPPER\n");
        InstalledConfigurationUpdate.apply(plugin);
        assertEquals(27, YamlConfiguration.loadConfiguration(normal.toFile()).getInt("size"));
        assertEquals(54, YamlConfiguration.loadConfiguration(root.resolve("inventories/confirms/shulker-purchase-confirm.yml").toFile()).getInt("size"));
        assertFalse(Files.exists(obsolete));
        Path backup = root.resolve("backups/" + InstalledConfigurationUpdate.ID);
        assertTrue(Files.exists(backup.resolve("inventories/admin/server-item-dropper.yml")));
        assertEquals("name: Old\nsize: 54\n", Files.readString(backup.resolve(root.relativize(normal))));
        assertEquals(configuration, Files.readString(backup.resolve("config.yml")));
        String updated = Files.readString(root.resolve("config.yml"));
        assertTrue(updated.contains("# Keep comments\r\n"));
        assertTrue(updated.contains("  password: private-db\r\n"));
        assertTrue(updated.contains("# After Redis\r\n"));
        var yaml = YamlConfiguration.loadConfiguration(root.resolve("config.yml").toFile());
        assertEquals("network", yaml.getString("redis.channel-prefix"));
        assertEquals(12, yaml.getInt("redis.pool.max-total"));
        assertEquals("redis.test", yaml.getString("redis.host"));
        assertTrue(yaml.getBoolean("redis.enabled"));
        Files.writeString(normal, "name: My edit\n");
        InstalledConfigurationUpdate.apply(plugin);
        assertEquals("name: My edit\n", Files.readString(normal));
    }

    @Test void missingRedisSectionIsAddedWithoutChangingDatabaseOrOtherSections() throws Exception {
        String original = "database-configuration:\n  host: db.test\n  password: db-secret\nfoo: keep\n";
        String result = InstalledConfigurationUpdate.updateRedis(original, DEFAULTS);
        assertTrue(result.startsWith(original));
        var yaml = new YamlConfiguration(); yaml.loadFromString(result);
        assertEquals("test-password", yaml.getString("redis.password"));
        assertEquals("db-secret", yaml.getString("database-configuration.password"));
    }

    @Test void unreadableDefaultsFailBeforeChangingAnyPurchaseFile() throws Exception {
        AuctionPlugin plugin = mock(AuctionPlugin.class);
        when(plugin.getDataFolder()).thenReturn(root.toFile());
        Path normal = root.resolve("inventories/confirms/purchase-confirm.yml");
        Files.createDirectories(normal.getParent()); Files.writeString(normal, "original");
        assertThrows(java.io.IOException.class, () -> InstalledConfigurationUpdate.apply(plugin));
        assertEquals("original", Files.readString(normal));
        assertFalse(Files.exists(root.resolve("." + InstalledConfigurationUpdate.ID + ".applied")));
    }

    @Test void anUnrelatedCustomAdminFileIsNotRemoved() throws Exception {
        AuctionPlugin plugin = mock(AuctionPlugin.class);
        when(plugin.getDataFolder()).thenReturn(root.toFile());
        when(plugin.getResource(anyString())).thenAnswer(call -> getClass().getResourceAsStream("/" + call.getArgument(0)));
        Path custom = root.resolve("inventories/admin/server-item-dropper.yml");
        Files.createDirectories(custom.getParent()); Files.writeString(custom, "name: Custom\nitems: {}\n");
        InstalledConfigurationUpdate.apply(plugin);
        assertTrue(Files.exists(custom));
    }
}

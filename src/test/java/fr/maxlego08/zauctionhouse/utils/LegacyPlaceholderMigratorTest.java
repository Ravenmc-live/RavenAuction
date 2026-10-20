package fr.maxlego08.zauctionhouse.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LegacyPlaceholderMigratorTest {
    @TempDir Path directory;
    @Test
    void replacesLegacyPrefixOnceAndPreservesUnicode() throws Exception {
        Path path = directory.resolve("menu.yml");
        Files.writeString(path, "name: 'بحث %zauctionhouse_category_name% %player_name%'\n");
        assertTrue(LegacyPlaceholderMigrator.migrate(path));
        assertEquals("name: 'بحث %ravenauction_category_name% %player_name%'\n", Files.readString(path));
        assertFalse(LegacyPlaceholderMigrator.migrate(path));
    }
}

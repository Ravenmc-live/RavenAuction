package fr.maxlego08.zauctionhouse.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LegacyPlaceholderMigrator {
    private LegacyPlaceholderMigrator() {}

    public static boolean migrate(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        String updated = content.replace("%zauctionhouse_", "%ravenauction_");
        if (updated.equals(content)) return false;
        Files.writeString(path, updated, StandardCharsets.UTF_8);
        return true;
    }
}

package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InstalledConfigurationUpdate {
    static final String ID = "purchase-redis-v1";
    private static final List<String> SCREENS = List.of(
            "inventories/confirms/purchase-confirm.yml",
            "inventories/confirms/purchase-inventory-confirm.yml",
            "inventories/confirms/shulker-purchase-confirm.yml");
    private static final List<String> OBSOLETE = List.of(
            "inventories/admin/server-item-dropper.yml",
            "inventories/admin/server-item-dropper-items.yml",
            "inventories/admin/server-item-dropper-history.yml");

    private InstalledConfigurationUpdate() {}

    public static void apply(AuctionPlugin plugin) throws IOException {
        Path root = plugin.getDataFolder().toPath();
        Path marker = root.resolve("." + ID + ".applied");
        if (Files.exists(marker)) return;
        Files.createDirectories(root);
        Map<Path, byte[]> changes = new LinkedHashMap<>();
        for (String name : SCREENS) changes.put(root.resolve(name), resource(plugin, name));
        Path config = root.resolve("config.yml");
        if (Files.exists(config)) {
            String updated = updateRedis(Files.readString(config), new String(resource(plugin, "config.yml"), StandardCharsets.UTF_8));
            changes.put(config, updated.getBytes(StandardCharsets.UTF_8));
        }
        for (String name : OBSOLETE) {
            Path path = root.resolve(name);
            if (Files.exists(path)) {
                String text = Files.readString(path);
                if (text.contains("RAVENAUCTION_SERVER_ITEM_DROPPER") || text.contains("RAVENAUCTION_DROPPER_HISTORY")) {
                    changes.put(path, null);
                }
            }
        }
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        for (var change : changes.entrySet()) {
            Path path = change.getKey();
            byte[] before = Files.exists(path) ? Files.readAllBytes(path) : null;
            if (Arrays.equals(before, change.getValue())) continue;
            originals.put(path, before);
            if (before != null) {
                Path backup = root.resolve("backups/" + ID).resolve(root.relativize(path));
                Files.createDirectories(backup.getParent());
                if (!Files.exists(backup)) Files.write(backup, before, StandardOpenOption.CREATE_NEW);
            }
        }
        try {
            for (Path path : originals.keySet()) {
                byte[] bytes = changes.get(path);
                if (bytes == null) Files.deleteIfExists(path);
                else atomicWrite(path, bytes);
            }
            atomicWrite(marker, new byte[0]);
        } catch (IOException exception) {
            for (var original : originals.entrySet()) {
                try {
                    if (original.getValue() == null) Files.deleteIfExists(original.getKey());
                    else atomicWrite(original.getKey(), original.getValue());
                } catch (IOException rollback) { exception.addSuppressed(rollback); }
            }
            throw exception;
        }
    }

    static String updateRedis(String current, String defaults) throws IOException {
        var yaml = new YamlConfiguration();
        try { yaml.loadFromString(defaults); } catch (Exception exception) { throw new IOException("Invalid bundled configuration", exception); }
        String newline = current.contains("\r\n") ? "\r\n" : "\n";
        String normalized = current.replace("\r\n", "\n");
        Matcher header = Pattern.compile("(?m)^redis:[ \t]*(?:#.*)?$").matcher(normalized);
        int start;
        int end;
        String block;
        if (header.find()) {
            start = header.start();
            Matcher next = Pattern.compile("(?m)^[A-Za-z0-9_-]+:").matcher(normalized);
            end = next.find(header.end()) ? next.start() : normalized.length();
            block = normalized.substring(start, end);
        } else {
            if (!normalized.endsWith("\n")) normalized += "\n";
            start = end = normalized.length();
            block = "redis:\n";
        }
        for (String key : List.of("enabled", "mode", "host", "port", "username", "password", "database", "ssl")) {
            Object value = yaml.get("redis." + key);
            if (value == null) throw new IOException("Missing bundled Redis setting: " + key);
            String encoded = value instanceof String text ? "'" + text.replace("'", "''") + "'" : value.toString();
            String line = "  " + key + ": " + encoded;
            Matcher field = Pattern.compile("(?m)^  " + Pattern.quote(key) + ":[^\n]*").matcher(block);
            block = field.find() ? field.replaceFirst(Matcher.quoteReplacement(line))
                    : (block.endsWith("\n") ? block : block + "\n") + line + "\n";
        }
        String result = normalized.substring(0, start) + block + normalized.substring(end);
        try { new YamlConfiguration().loadFromString(result); }
        catch (Exception exception) { throw new IOException("Invalid installed configuration; left unchanged", exception); }
        return result.replace("\n", newline);
    }

    private static byte[] resource(AuctionPlugin plugin, String name) throws IOException {
        try (var stream = plugin.getResource(name)) {
            if (stream == null) throw new IOException("Missing bundled resource: " + name);
            return stream.readAllBytes();
        }
    }

    private static void atomicWrite(Path path, byte[] content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".raven-update-", ".tmp");
        try {
            Files.write(temp, content);
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}

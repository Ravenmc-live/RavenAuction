package fr.maxlego08.zauctionhouse.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class StyleMigration {
    static final String MARKER = ".red-gui-ff1f1f-v3.applied";
    static final String BACKUP = "backups/red-gui-ff1f1f-v3";

    private StyleMigration() {}

    public static synchronized void apply(Path directory, Function<String, InputStream> resources) throws IOException {
        if (Files.exists(directory.resolve(MARKER))) return;
        List<String> names = new ArrayList<>(new String(read(resources, "raven-style/files.txt"), StandardCharsets.UTF_8)
                .lines().filter(line -> !line.isBlank()).toList());
        names.add("config.yml");
        Map<String, byte[]> replacements = new LinkedHashMap<>();
        Map<String, byte[]> originals = new LinkedHashMap<>();
        for (String name : names) {
            Path target = directory.resolve(name);
            byte[] original = Files.exists(target) ? Files.readAllBytes(target) : null;
            originals.put(name, original);
            replacements.put(name, name.equals("config.yml")
                    ? mergePresentation(original == null ? read(resources, name) : original,
                            read(resources, "raven-style/presentation.yml"))
                    : read(resources, name));
        }
        for (var entry : originals.entrySet()) {
            if (entry.getValue() == null) continue;
            Path backup = directory.resolve(BACKUP).resolve(entry.getKey());
            Files.createDirectories(backup.getParent());
            if (!Files.exists(backup)) Files.write(backup, entry.getValue(), StandardOpenOption.CREATE_NEW);
        }
        List<String> attempted = new ArrayList<>();
        try {
            for (var entry : replacements.entrySet()) {
                attempted.add(entry.getKey());
                replace(directory.resolve(entry.getKey()), entry.getValue());
            }
            replace(directory.resolve(MARKER), "RavenAuction red GUI style applied.\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            for (String name : attempted.reversed()) {
                try {
                    byte[] original = originals.get(name);
                    if (original == null) Files.deleteIfExists(directory.resolve(name));
                    else replace(directory.resolve(name), original);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    static byte[] mergePresentation(byte[] original, byte[] presentation) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        YamlConfiguration style = new YamlConfiguration();
        try {
            config.loadFromString(new String(original, StandardCharsets.UTF_8));
            style.loadFromString(new String(presentation, StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException ex) {
            throw new IOException("Invalid YAML in style migration", ex);
        }
        for (String path : List.of("item-lore", "message-colors", "special-items", "item-display",
                "action.purchased-item.money-sound", "action.purchased-item.money-item.enable",
                "action.purchased-item.money-message", "sort-items.translations")) {
            Object value = style.get(path);
            if (value != null) config.set(path, copy(value));
        }
        return config.saveToString().getBytes(StandardCharsets.UTF_8);
    }

    private static Object copy(Object value) {
        if (value instanceof ConfigurationSection section) {
            Map<String, Object> result = new LinkedHashMap<>();
            section.getValues(false).forEach((key, child) -> result.put(key, copy(child)));
            return result;
        }
        return value;
    }

    private static byte[] read(Function<String, InputStream> resources, String name) throws IOException {
        try (InputStream stream = resources.apply(name)) {
            if (stream == null) throw new IOException("Missing bundled style resource: " + name);
            return stream.readAllBytes();
        }
    }

    private static void replace(Path target, byte[] contents) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".raven-style-", ".tmp");
        try {
            Files.write(temporary, contents);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

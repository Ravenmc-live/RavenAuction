package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.buttons.admin.LogTypeFilterButton;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class LogTypeFilterLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public LogTypeFilterLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_ADMIN_LOGS_FILTER_TYPE");
        this.plugin = plugin;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        var enableText = configuration.getString(path + "enable-text", "&a%type%");
        var disableText = configuration.getString(path + "disable-text", "&7%type%");
        var allTypesName = configuration.getString(path + "all-types-name", "All");

        List<LogType> logTypes = new ArrayList<>();
        for (String type : configuration.getStringList(path + "types")) {
            try {
                logTypes.add(LogType.valueOf(type.toUpperCase()));
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Invalid log type: " + type + ", skipping...");
            }
        }

        // Default to all log types if none specified
        if (logTypes.isEmpty()) {
            logTypes.addAll(List.of(LogType.values()));
        }

        // Load custom type names
        Map<LogType, String> typeNames = new EnumMap<>(LogType.class);
        ConfigurationSection namesSection = configuration.getConfigurationSection(path + "type-names");
        if (namesSection != null) {
            for (String key : namesSection.getKeys(false)) {
                try {
                    LogType logType = LogType.valueOf(key.toUpperCase());
                    typeNames.put(logType, namesSection.getString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Use default names for any not specified
        for (LogType logType : LogType.values()) {
            typeNames.putIfAbsent(logType, logType.getDefaultDisplayName());
        }

        return new LogTypeFilterButton(plugin, enableText, disableText, logTypes, typeNames, allTypesName);
    }
}

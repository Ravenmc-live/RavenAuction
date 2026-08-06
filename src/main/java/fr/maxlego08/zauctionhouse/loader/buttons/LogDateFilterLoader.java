package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.filter.DateFilter;
import fr.maxlego08.zauctionhouse.buttons.admin.LogDateFilterButton;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class LogDateFilterLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public LogDateFilterLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_ADMIN_LOGS_FILTER_DATE");
        this.plugin = plugin;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        var enableText = configuration.getString(path + "enable-text", "&a%date%");
        var disableText = configuration.getString(path + "disable-text", "&7%date%");

        List<DateFilter> dateFilters = new ArrayList<>();
        for (String filter : configuration.getStringList(path + "filters")) {
            try {
                dateFilters.add(DateFilter.valueOf(filter.toUpperCase()));
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Invalid date filter: " + filter + ", skipping...");
            }
        }

        // Default to all date filters if none specified
        if (dateFilters.isEmpty()) {
            dateFilters.addAll(List.of(DateFilter.values()));
        }

        // Load custom filter names
        Map<DateFilter, String> filterNames = new EnumMap<>(DateFilter.class);
        ConfigurationSection namesSection = configuration.getConfigurationSection(path + "filter-names");
        if (namesSection != null) {
            for (String key : namesSection.getKeys(false)) {
                try {
                    DateFilter dateFilter = DateFilter.valueOf(key.toUpperCase());
                    filterNames.put(dateFilter, namesSection.getString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Use default names for any not specified
        for (DateFilter dateFilter : DateFilter.values()) {
            filterNames.putIfAbsent(dateFilter, dateFilter.getDisplayName());
        }

        return new LogDateFilterButton(plugin, enableText, disableText, dateFilters, filterNames);
    }
}

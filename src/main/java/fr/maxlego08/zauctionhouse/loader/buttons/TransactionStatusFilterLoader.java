package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.buttons.admin.TransactionStatusFilterButton;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class TransactionStatusFilterLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public TransactionStatusFilterLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_ADMIN_TRANSACTIONS_FILTER_STATUS");
        this.plugin = plugin;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        var enableText = configuration.getString(path + "enable-text", "&a%status%");
        var disableText = configuration.getString(path + "disable-text", "&7%status%");
        var allStatusesName = configuration.getString(path + "all-statuses-name", "All");

        List<TransactionStatus> statuses = new ArrayList<>();
        for (String status : configuration.getStringList(path + "statuses")) {
            try {
                statuses.add(TransactionStatus.valueOf(status.toUpperCase()));
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Invalid transaction status: " + status + ", skipping...");
            }
        }

        // Default to all statuses if none specified
        if (statuses.isEmpty()) {
            statuses.addAll(List.of(TransactionStatus.values()));
        }

        // Load custom status names
        Map<TransactionStatus, String> statusNames = new EnumMap<>(TransactionStatus.class);
        ConfigurationSection namesSection = configuration.getConfigurationSection(path + "status-names");
        if (namesSection != null) {
            for (String key : namesSection.getKeys(false)) {
                try {
                    TransactionStatus status = TransactionStatus.valueOf(key.toUpperCase());
                    statusNames.put(status, namesSection.getString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Use default names for any not specified
        for (TransactionStatus status : TransactionStatus.values()) {
            statusNames.putIfAbsent(status, status.getDefaultDisplayName());
        }

        return new TransactionStatusFilterButton(plugin, enableText, disableText, statuses, statusNames, allStatusesName);
    }
}

package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import fr.maxlego08.zauctionhouse.buttons.option.OptionToggleButton;
import org.bukkit.configuration.file.YamlConfiguration;

public class OptionToggleLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public OptionToggleLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_OPTION_TOGGLE");
        this.plugin = plugin;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        String optionName = configuration.getString(path + "option-name", "broadcast_sell");
        String enableText = configuration.getString(path + "enable-text", "<green>Enabled");
        String disableText = configuration.getString(path + "disable-text", "<red>Disabled");

        PlayerOption option = PlayerOption.fromKey(optionName);
        if (option == null) {
            this.plugin.getLogger().warning("Unknown player option: " + optionName + ", defaulting to BROADCAST_SELL");
            option = PlayerOption.BROADCAST_SELL;
        }

        return new OptionToggleButton(this.plugin, option, enableText, disableText);
    }
}

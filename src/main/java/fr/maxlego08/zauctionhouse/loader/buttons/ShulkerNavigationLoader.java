package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.buttons.shulker.ShulkerNavigationButton;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loader for ShulkerNavigationButton.
 * Reads the "direction" configuration to determine if this is a next or previous button.
 */
public class ShulkerNavigationLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public ShulkerNavigationLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_SHULKER_NAVIGATION");
        this.plugin = plugin;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        String direction = configuration.getString(path + "direction", "next");
        boolean isNext = direction.equalsIgnoreCase("next");
        return new ShulkerNavigationButton(plugin, isNext);
    }
}

package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.buttons.list.CombinedItemsButton;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CombinedItemsLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public CombinedItemsLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_COMBINED_ITEMS");
        this.plugin = plugin;
    }

    @Override
    public @Nullable Button load(@NotNull YamlConfiguration configuration, @NotNull String path, @NotNull DefaultButtonValue defaultButtonValue) {
        int emptySlot = configuration.getInt(path + "empty-slot", -1);
        boolean includeSelling = configuration.getBoolean(path + "include-selling", true);
        boolean includeExpired = configuration.getBoolean(path + "include-expired", true);
        boolean includePurchased = configuration.getBoolean(path + "include-purchased", true);

        return new CombinedItemsButton(plugin, emptySlot, includeSelling, includeExpired, includePurchased);
    }
}

package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.buttons.SellLimitButton;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SellLimitLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public SellLimitLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_SELL_LIMIT");
        this.plugin = plugin;
    }

    @Override
    public @Nullable Button load(@NotNull YamlConfiguration configuration, @NotNull String path, @NotNull DefaultButtonValue defaultButtonValue) {

        List<String> typeNames = configuration.getStringList(path + "types");
        List<ItemType> itemTypes = new ArrayList<>();

        for (String typeName : typeNames) {
            try {
                itemTypes.add(ItemType.valueOf(typeName.toUpperCase()));
            } catch (IllegalArgumentException exception) {
                this.plugin.getLogger().warning("Invalid item type '" + typeName + "' in sell limit button configuration");
            }
        }

        if (itemTypes.isEmpty()) {
            itemTypes.add(ItemType.AUCTION);
        }

        return new SellLimitButton(plugin, itemTypes);
    }
}

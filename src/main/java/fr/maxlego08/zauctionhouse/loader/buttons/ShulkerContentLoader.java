package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.buttons.shulker.ShulkerContentButton;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShulkerContentLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public ShulkerContentLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_SHULKER_CONTENT");
        this.plugin = plugin;
    }

    @Override
    public @Nullable Button load(@NotNull YamlConfiguration configuration, @NotNull String path, @NotNull DefaultButtonValue defaultButtonValue) {
        int emptySlot = configuration.getInt(path + ".empty-slot", 0);
        return new ShulkerContentButton(this.plugin, emptySlot);
    }
}

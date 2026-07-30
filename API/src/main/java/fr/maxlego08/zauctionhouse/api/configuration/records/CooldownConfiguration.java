package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for command cooldowns to prevent command spam.
 *
 * @param defaultCooldown     default cooldown in milliseconds for all commands (0 to disable)
 * @param bypassPermission    permission to bypass the cooldown
 * @param perCommandCooldowns per-command cooldown overrides (command name -> cooldown in ms)
 */
public record CooldownConfiguration(
        long defaultCooldown,
        String bypassPermission,
        Map<String, Long> perCommandCooldowns
) {

    public static final long DEFAULT_COOLDOWN = 100;
    public static final String DEFAULT_BYPASS_PERMISSION = "ravenauction.cooldown.command.bypass";

    public static CooldownConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        long defaultCooldown = configuration.getLong("commands.cooldown.default-cooldown", DEFAULT_COOLDOWN);
        String bypassPermission = configuration.getString("commands.cooldown.bypass-permission", DEFAULT_BYPASS_PERMISSION);

        Map<String, Long> perCommand = new HashMap<>();
        ConfigurationSection section = configuration.getConfigurationSection("commands.cooldown.per-command");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                perCommand.put(key.toLowerCase(), section.getLong(key));
            }
        }

        if (defaultCooldown == 0 && perCommand.isEmpty()) {
            plugin.getLogger().warning("Command cooldown is disabled (set to 0). It is recommended to keep a cooldown (e.g., 100ms) to prevent command spam.");
        }

        return new CooldownConfiguration(defaultCooldown, bypassPermission, perCommand);
    }

    /**
     * Gets the cooldown for a specific command. Returns the per-command value if configured,
     * otherwise returns the default cooldown.
     *
     * @param commandName the first alias of the command
     * @return the cooldown in milliseconds
     */
    public long getCooldownForCommand(String commandName) {
        return perCommandCooldowns.getOrDefault(commandName.toLowerCase(), defaultCooldown);
    }
}

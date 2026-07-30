package fr.maxlego08.zauctionhouse.permissions;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.records.ExpirationConfiguration;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.Bukkit;
import org.bukkit.permissions.PermissionDefault;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PermissionRegistrar {

    private final AuctionPlugin plugin;
    private final Set<String> registeredPermissions = new HashSet<>();

    public PermissionRegistrar(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        unregister();

        registerStaticPermissions();
        registerCooldownPermissions();
        registerListingLimitPermissions();
        registerExpirationPermissions();
        registerInventoryCommandPermissions();
        registerEconomyPermissions();
        registerWildcardPermission();

        this.plugin.getLogger().info("Registered " + this.registeredPermissions.size() + " permissions in Bukkit.");
    }

    public void unregister() {
        var pluginManager = Bukkit.getPluginManager();
        for (String perm : this.registeredPermissions) {
            var existing = pluginManager.getPermission(perm);
            if (existing != null) {
                pluginManager.removePermission(existing);
            }
        }
        this.registeredPermissions.clear();
    }

    private void registerStaticPermissions() {
        for (Permission perm : Permission.values()) {
            boolean isAdmin = perm.name().startsWith("RAVENAUCTION_ADMIN") || perm == Permission.ZAUCTIONHOUSE_RELOAD;
            addPermission(perm.asPermission(), perm.getDescription(), isAdmin ? PermissionDefault.OP : PermissionDefault.TRUE);
        }
    }

    private void registerCooldownPermissions() {
        var cooldown = this.plugin.getConfiguration().getCooldown();
        if (cooldown != null && cooldown.bypassPermission() != null && !cooldown.bypassPermission().isEmpty()) {
            addPermission(cooldown.bypassPermission(), "Bypasses command cooldown restrictions", PermissionDefault.OP);
        }
    }

    private void registerListingLimitPermissions() {
        var permConfig = this.plugin.getConfiguration().getPermission();
        if (permConfig == null) return;

        for (var entry : permConfig.permissions().entrySet()) {
            for (var permEntry : entry.getValue().entrySet()) {
                addPermission(permEntry.getKey(), "Listing limit of " + permEntry.getValue() + " for " + entry.getKey().name().toLowerCase(), PermissionDefault.FALSE);
            }
        }
    }

    private void registerExpirationPermissions() {
        var config = this.plugin.getConfiguration();
        registerExpirationPermissions(config.getSellExpiration(), "auction");
        registerExpirationPermissions(config.getRentExpiration(), "rent");
        registerExpirationPermissions(config.getBidExpiration(), "bid");
        registerExpirationPermissions(config.getPurchaseExpiration(), "purchase");
        registerExpirationPermissions(config.getExpireExpiration(), "expire");
    }

    private void registerExpirationPermissions(ExpirationConfiguration config, String type) {
        if (config != null && config.enablePermission()) {
            for (String perm : config.expirations().keySet()) {
                addPermission(perm, "Extended " + type + " expiration time", PermissionDefault.FALSE);
            }
        }
    }

    private void registerInventoryCommandPermissions() {
        for (var cmd : this.plugin.getConfiguration().getInventoryCommands()) {
            if (cmd.permission() != null && !cmd.permission().isEmpty()) {
                addPermission(cmd.permission(), "Access to inventory command", PermissionDefault.TRUE);
            }
        }
    }

    private void registerEconomyPermissions() {
        for (var economy : this.plugin.getEconomyManager().getEconomies()) {
            if (economy.getPermission() != null && !economy.getPermission().isEmpty()) {
                addPermission(economy.getPermission(), "Access to " + economy.getDisplayName() + " economy", PermissionDefault.TRUE);
            }

            var tax = economy.getTaxConfiguration();
            if (tax != null && tax.isEnabled()) {
                var bypass = tax.getBypassPermission();
                if (bypass != null && !bypass.isEmpty()) {
                    addPermission(bypass, "Bypasses tax for " + economy.getDisplayName() + " economy", PermissionDefault.FALSE);
                }
                for (var reduction : tax.getReductions()) {
                    if (reduction.permission() != null && !reduction.permission().isEmpty()) {
                        addPermission(reduction.permission(), reduction.percentage() + "% tax reduction for " + economy.getDisplayName() + " economy", PermissionDefault.FALSE);
                    }
                }
            }
        }
    }

    private void registerWildcardPermission() {
        Map<String, Boolean> children = new HashMap<>();
        for (String perm : this.registeredPermissions) {
            children.put(perm, true);
        }
        addPermission("ravenauction.*", "Gives access to all RavenAuction permissions", PermissionDefault.OP, children);
    }

    private void addPermission(String name, String description, PermissionDefault defaultValue) {
        addPermission(name, description, defaultValue, null);
    }

    private void addPermission(String name, String description, PermissionDefault defaultValue, Map<String, Boolean> children) {
        if (name == null || name.isEmpty()) return;

        var pluginManager = Bukkit.getPluginManager();
        if (pluginManager.getPermission(name) != null) return;

        try {
            var permission = children != null && !children.isEmpty()
                    ? new org.bukkit.permissions.Permission(name, description, defaultValue, children)
                    : new org.bukkit.permissions.Permission(name, description, defaultValue);
            pluginManager.addPermission(permission);
            this.registeredPermissions.add(name);
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Failed to register permission '" + name + "': " + exception.getMessage());
        }
    }
}

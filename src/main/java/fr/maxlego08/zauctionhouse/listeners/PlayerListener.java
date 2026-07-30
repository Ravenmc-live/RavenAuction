package fr.maxlego08.zauctionhouse.listeners;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final AuctionPlugin plugin;

    public PlayerListener(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConnect(PlayerJoinEvent event) {
        var player = event.getPlayer();
        this.plugin.getStorageManager().upsertPlayer(player);

        // Handle pending money claim on join
        this.plugin.getAuctionManager().getClaimService().handlePlayerJoin(player);

        // Handle sales notification on join
        this.plugin.getAuctionManager().getHistoryService().handlePlayerJoin(player);

        // Load player options
        this.plugin.getAuctionManager().getOptionService().loadPlayerOptions(player.getUniqueId());

        if (player.getName().equals("Maxlego08")) {
            this.plugin.getScheduler().runLater(task -> {
                if (player.isOnline()) {
                    ComponentMessageHelper.componentMessage.sendMessage(player, "<#24d65d>RavenAuction <#656665>• <#e6fff3>Ce serveur utilise <#24d65d>RavenAuction <white>v" + this.plugin.getDescription().getVersion());
                }
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.getAuctionManager().removeCache(event.getPlayer());
        this.plugin.getAuctionManager().getOptionService().clearPlayerOptions(event.getPlayer().getUniqueId());
        this.plugin.getCommandManager().clearCooldowns(event.getPlayer().getUniqueId());
    }

}

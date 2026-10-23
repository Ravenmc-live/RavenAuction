package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.commands.admin.CommandAuctionAdmin;
import org.bukkit.entity.Player;

public class CommandAuction extends VCommand {

    public CommandAuction(AuctionPlugin plugin) {
        super(plugin);

        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION);

        this.addSubCommand(new CommandAuctionSell(plugin));
        this.addSubCommand(new CommandAuctionPage(plugin));
        this.addSubCommand(new CommandAuctionSearch(plugin));
        this.addSubCommand(new CommandAuctionOption(plugin));
        this.addSubCommand(new CommandAuctionAdmin(plugin));

        plugin.getConfiguration().getInventoryCommands().forEach((configuration) -> this.addSubCommand(new CommandAuctionInventories(plugin, configuration)));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        if (!hasPermission(this.sender, Permission.ZAUCTIONHOUSE_USE.asPermission())) {
            message(plugin, this.sender, Message.COMMAND_NO_PERMISSION);
            return CommandType.DEFAULT;
        }

        if (this.sender instanceof Player playerSender) {
            this.auctionManager.openMainAuction(playerSender);
        } else {
            this.syntaxMessage(this.sender);
        }

        return CommandType.SUCCESS;
    }
}

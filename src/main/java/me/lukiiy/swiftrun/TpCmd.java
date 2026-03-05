package me.lukiiy.swiftrun;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;

public class TpCmd implements BasicCommand {

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] strings) {
        CommandSender sender = commandSourceStack.getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Cmd.INGAME_USAGE);
            return;
        }

        if (Swiftrun.getInstance().getState() == RunState.INACTIVE) {
            sender.sendMessage(Cmd.NO_RUN);
            return;
        }

        if (strings.length < 2) {
            sender.sendMessage(Cmd.INVALID_ARG);
            return;
        }

        Player target = Bukkit.getPlayerExact(strings[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
            return;
        }

        RunData data = Swiftrun.getInstance().getRunMap().get(target);
        if (data == null) {
            sender.sendMessage(Component.text("No run data found for this player.").color(NamedTextColor.RED));
            return;
        }

        String id = strings[1];
        Location loc = data.getLocations().get(strings[1]);
        if (loc == null) {
            sender.sendMessage(Component.text("Location not found.").color(NamedTextColor.RED));
            return;
        }

        player.teleport(loc);
        sender.sendMessage(Component.text("Teleported to ").append(target.displayName()).append(Component.text("'s ")).append(Component.text(id).color(NamedTextColor.YELLOW)).append(Component.text(" location.")));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        if (Swiftrun.getInstance().getState() == RunState.INACTIVE) return Collections.emptyList();

        if (args.length == 1) return Swiftrun.getInstance().getRunMap().keySet().stream().map(Player::getName).toList();

        if (args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) return Collections.emptyList();

            RunData data = Swiftrun.getInstance().getRunMap().get(target);
            if (data == null) return Collections.emptyList();

            return data.getLocations().keySet().stream().toList();
        }

        return Collections.emptyList();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        if (Swiftrun.getInstance().getState() == RunState.INACTIVE) return false;

        return BasicCommand.super.canUse(sender);
    }
}

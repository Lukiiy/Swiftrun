package me.lukiiy.swiftrun;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TpCmd implements BasicCommand {
    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] strings) {
        CommandSender sender = commandSourceStack.getSender();

        if (Swiftrun.getInstance().getState() == RunState.INACTIVE) {
            sender.sendMessage(Bukkit.permissionMessage());
            return;
        }

        sender.sendMessage(Component.text("TuDo"));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        if (Swiftrun.getInstance().getState() == RunState.INACTIVE) return Collections.emptyList();

        if (args.length == 0) return Swiftrun.getInstance().getRunMap().keySet().stream().map(Player::getName).toList();
        if (args.length == 1) return List.of("to", "do", "i", "guess");

        return BasicCommand.super.suggest(commandSourceStack, args);
    }

    @Override
    public boolean canUse(CommandSender sender) {
        if (Swiftrun.getInstance().getState() == RunState.INACTIVE) return false;

        return BasicCommand.super.canUse(sender);
    }
}

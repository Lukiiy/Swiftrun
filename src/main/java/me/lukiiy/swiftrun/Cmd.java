package me.lukiiy.swiftrun;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Cmd implements BasicCommand {
    private static final Component NO_RUN = Component.text("No active run!").color(NamedTextColor.RED);

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        RunState state = Swiftrun.getInstance().getState();

        switch (args[0].toLowerCase()) {
            case "start" -> {
                List<Player> players = new ArrayList<>();

                for (int i = 1; i < args.length; i++) {
                    Player p = Bukkit.getPlayer(args[i]);

                    if (p != null && p.isOnline()) players.add(p);
                    else sender.sendMessage(Component.text("Player not found: " + args[i]).color(NamedTextColor.RED));
                }

                if (players.isEmpty()) {
                    sender.sendMessage(Component.text("No valid players found!").color(NamedTextColor.RED));
                    return;
                }

                sender.sendMessage(Component.text("Starting a new run...").color(NamedTextColor.GREEN));
                Swiftrun.getInstance().startRun(players);
            }

            case "stop" -> {
                if (state == RunState.INACTIVE) {
                    sender.sendMessage(NO_RUN);
                    return;
                }

                if (args.length > 1) {
                    Player target = Bukkit.getPlayer(args[1]);

                    if (target == null) {
                        sender.sendMessage(Component.text("Player not found: " + args[1]).color(NamedTextColor.RED));
                        return;
                    }

                    Swiftrun.getInstance().stopRun(target);
                } else {
                    Swiftrun.getInstance().stopRun(null);
                }
            }

            case "pause" -> {
                if (state == RunState.INACTIVE) {
                    sender.sendMessage(NO_RUN);
                    return;
                }

                Swiftrun.getInstance().togglePause();
            }

            default -> sender.sendMessage(Component.text("Unknown subcommand " + args[0]).color(NamedTextColor.RED));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) { // TODO
        List<String> tab = new ArrayList<>();

        if (args.length == 1) {
            tab.add("start");
            tab.add("stop");
            tab.add("pause");
        }

        if (args.length == 2) {
            if (args[1].equalsIgnoreCase("start") || args[1].equalsIgnoreCase("stop")) tab.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }

        return tab;
    }
}

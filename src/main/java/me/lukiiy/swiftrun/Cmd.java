package me.lukiiy.swiftrun;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Cmd implements BasicCommand {
    private static final Component INVALID_ARG = Component.text("Invalid subcommand.").color(NamedTextColor.RED);
    private static final Component NO_RUN = Component.text("No active run!").color(NamedTextColor.RED);
    private static final Component ONGOING_RUN = Component.text("There's an ongoing run!").color(NamedTextColor.RED);
    private static final Component NOT_PARTICIPATING = Component.text("You are not in a run.").color(NamedTextColor.RED);
    private static final Component INGAME_USAGE = Component.text("This subcommand can only be used in-game!").color(NamedTextColor.RED);

    @Override
    public void execute(CommandSourceStack stack, String[] args) { // TODO: Add draw, seed change & forfeit
        CommandSender sender = stack.getSender();
        RunState state = Swiftrun.getInstance().getState();

        switch (args[0].toLowerCase()) {
            case "draw" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(INGAME_USAGE);
                    return;
                }

                if (Swiftrun.getInstance().getState() == RunState.INACTIVE) {
                    sender.sendMessage(NO_RUN);
                    return;
                }

                if (Swiftrun.getInstance().getRunMap().containsKey(p)) {
                    sender.sendMessage(NOT_PARTICIPATING);
                    return;
                }

                Bukkit.broadcast(Component.empty().append(p.displayName()).append(Component.text(" has voted for a draw.").color(NamedTextColor.YELLOW)));
                Swiftrun.getInstance().drawVote(p);
                return;
            }

            case "forfeit" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(INGAME_USAGE);
                    return;
                }

                if (Swiftrun.getInstance().getState() == RunState.INACTIVE) {
                    sender.sendMessage(NO_RUN);
                    return;
                }

                if (Swiftrun.getInstance().getRunMap().containsKey(p)) {
                    sender.sendMessage(NOT_PARTICIPATING);
                    return;
                }

                Bukkit.broadcast(Component.empty().append(p.displayName()).append(Component.text(" has forfeited!")));
                Swiftrun.getInstance().leave(p);
                p.setHealth(0);
                return;
            }

            default -> sender.sendMessage(INVALID_ARG);
        }

        if (!sender.hasPermission("swiftrun.admin")) {
            sender.sendMessage(INVALID_ARG);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (state != RunState.INACTIVE) {
                    sender.sendMessage(NO_RUN);
                    return;
                }

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
                sender.sendMessage(Component.text("Pausing...").color(NamedTextColor.YELLOW));
            }

            default -> sender.sendMessage(INVALID_ARG);
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, String[] args) { // TODO
        List<String> tab = new ArrayList<>();

        if (!stack.getSender().hasPermission("swiftrun.admin")) return List.of();

        if (args.length == 1) {
            tab.add("start");
            if (Swiftrun.getInstance().getState() == RunState.INACTIVE) {
                tab.add("stop");
                tab.add("pause");
            }
        }

        if (args.length > 1) {
            if (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop")) tab.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }

        return tab;
    }
}

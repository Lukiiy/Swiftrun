package me.lukiiy.swiftrun;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class Cmd implements BasicCommand {
    public static final Component INVALID_ARG = Component.text("Invalid subcommand.").color(NamedTextColor.RED);
    public static final Component NO_RUN = Component.text("No active run!").color(NamedTextColor.RED);
    public static final Component ONGOING_RUN = Component.text("There's an ongoing run!").color(NamedTextColor.RED);
    public static final Component NOT_PARTICIPATING = Component.text("You are not in a run.").color(NamedTextColor.RED);
    public static final Component INGAME_USAGE = Component.text("This subcommand can only be used in-game!").color(NamedTextColor.RED);

    @Override
    public void execute(CommandSourceStack stack, String[] args) { // TODO: Add draw, seed change & forfeit
        CommandSender sender = stack.getSender();
        Swiftrun.RunState state = Swiftrun.getInstance().getState();
        String arg0 = args.length == 0 ? "" : args[0].toLowerCase();

        switch (arg0) {
            case "draw" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(INGAME_USAGE);
                    return;
                }

                if (Swiftrun.getInstance().getState() == Swiftrun.RunState.INACTIVE) {
                    sender.sendMessage(NO_RUN);
                    return;
                }

                if (!Swiftrun.getInstance().getRunMap().containsKey(p)) {
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

                if (Swiftrun.getInstance().getState() == Swiftrun.RunState.INACTIVE) {
                    sender.sendMessage(NO_RUN);
                    return;
                }

                if (!Swiftrun.getInstance().getRunMap().containsKey(p)) {
                    sender.sendMessage(NOT_PARTICIPATING);
                    return;
                }

                Bukkit.broadcast(Component.empty().append(p.displayName()).append(Component.text(" has forfeited!")));
                Swiftrun.getInstance().leave(p);
                p.setHealth(0);
                return;
            }

            case "quicktp" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(INGAME_USAGE);
                    return;
                }

                if (Swiftrun.getInstance().getState() != Swiftrun.RunState.INACTIVE) {
                    sender.sendMessage(Cmd.ONGOING_RUN);
                    return;
                }

                if (args.length < 2) {
                    sender.sendMessage(INVALID_ARG);
                    return;
                }

                String[] parts = args[1].split(";");

                World world = Bukkit.getWorld(parts[0]);
                if (world == null) {
                    sender.sendMessage(INVALID_ARG);
                    return;
                }

                sender.sendMessage(Component.text("Teleported!").color(NamedTextColor.GREEN));
                p.teleport(new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
                return;
            }

            default -> {}
        }

        if (!sender.hasPermission("swiftrun.admin")) {
            sender.sendMessage(INVALID_ARG);
            return;
        }

        switch (arg0) {
            case "start" -> {
                if (state != Swiftrun.RunState.INACTIVE) {
                    sender.sendMessage(ONGOING_RUN);
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
                if (state == Swiftrun.RunState.INACTIVE) {
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
                } else Swiftrun.getInstance().stopRun(null);
            }

            case "pause" -> {
                if (state == Swiftrun.RunState.INACTIVE) {
                    sender.sendMessage(NO_RUN);
                    return;
                }

                Swiftrun.getInstance().togglePause();
                sender.sendMessage(Component.text(state == Swiftrun.RunState.PAUSED ? "Pausing..." : "Resuming...").color(NamedTextColor.YELLOW));
            }

            default -> sender.sendMessage(INVALID_ARG);
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        List<String> tab = new ArrayList<>();

        if (sender.hasPermission("swiftrun.admin")) tab.addAll(List.of("start", "stop", "pause"));
        if (sender instanceof Player) tab.addAll(List.of("draw", "forfeit", "quicktp"));

        if (args.length == 0) return tab;

        var last = args[args.length - 1].toLowerCase();
        if (args.length == 1) return tab.stream().filter(c -> c.startsWith(args[args.length - 1].toLowerCase())).toList();

        return switch (args[0].toLowerCase()) {
            case "start" -> {
                if (!sender.hasPermission("swiftrun.admin")) yield List.of();

                var used = Arrays.stream(args).skip(1).limit(args.length - 2).map(String::toLowerCase).collect(Collectors.toSet());

                yield Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(p -> !used.contains(p.toLowerCase())).filter(p -> p.toLowerCase().startsWith(last)).toList();
            }

            case "stop" -> {
                if (!sender.hasPermission("swiftrun.admin")) yield List.of();

                yield Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(p -> p.toLowerCase().startsWith(last)).toList();
            }

            default -> List.of();
        };
    }
}

package me.lukiiy.swiftrun;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Swiftrun extends JavaPlugin {
    private final Map<Player, RunData> runMap = new ConcurrentHashMap<>();
    private ScheduledTask mainTask;
    private RunState state = RunState.INACTIVE;
    private long startTime = 0;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new Listen(), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register("run", new Cmd());
        });
    }

    public static Swiftrun getInstance() {
        return JavaPlugin.getPlugin(Swiftrun.class);
    }

    public void startRun(List<Player> players) {
        state = RunState.ACTIVE;
        runMap.clear();

        startTime = System.currentTimeMillis();
        Scoreboard board = setupBoard();

        for (Player p : players) {
            if (p == null) continue;

            runMap.put(p, new RunData());
            p.setScoreboard(board);
        }

        mainTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            runMap.keySet().forEach(runner -> runner.sendActionBar(Component.text("• " + getFormattedTime(startTime) + " •").shadowColor(ShadowColor.shadowColor(255, 255, 0, 128))));
        }, 1L, 20L);

        Bukkit.broadcast(Component.text("The run has started!").color(NamedTextColor.YELLOW));
    }

    public void stopRun(Player winner) {
        if (state != RunState.INACTIVE) return;
        String time = getFormattedTime(System.currentTimeMillis() - startTime);

        mainTask.cancel();
        if (winner != null && runMap.containsKey(winner)) {

            for (Player p : Bukkit.getOnlinePlayers()) {
                NamedTextColor color = (p.equals(winner) || !runMap.containsKey(p)) ? NamedTextColor.YELLOW : NamedTextColor.RED;

                p.showTitle(Title.title(Component.text("Run Over!").color(color).decorate(TextDecoration.BOLD), Component.text("Winner: ").color(color).append(winner.displayName()).append(Component.text("! ")).append(Component.text(time).color(color)), Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(500))));
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

            RunData data = runMap.get(p);
            if (data == null) return;

            // TODO: formatted msg
            p.sendMessage(Component.text("Your final time: " + time));
            p.sendMessage(Component.text("Nether time: " + getFormattedTime(data.netherTime)));
            p.sendMessage(Component.text("Bastion time: " + getFormattedTime(data.bastionTime)));
            p.sendMessage(Component.text("Stronghold time: " + getFormattedTime(data.strongholdTime)));
            p.sendMessage(Component.text("End time: " + getFormattedTime(data.endTime)));
        }

        Bukkit.broadcast(Component.text("The run has ended!").color(NamedTextColor.YELLOW));
        runMap.clear();
    }

    public void togglePause() {
        if (state == RunState.INACTIVE) return;

        String title;

        if (state == RunState.ACTIVE) {
            state = RunState.PAUSED;
            title = "Paused";
        } else {
            state = RunState.ACTIVE;
            title = "Resumed";
        }

        Bukkit.getOnlinePlayers().forEach(p -> {
            p.showTitle(Title.title(Component.empty(), Component.text(title).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD), Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        });
    }

    public Map<Player, RunData> getRunMap() {
        return Collections.unmodifiableMap(runMap);
    }

    public RunState getState() {
        return state;
    }

    public long getStartTime() {
        return startTime;
    }

    private Scoreboard setupBoard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("run", Criteria.DUMMY, Component.text("Run").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());
        return board;
    }

    public void updateBoards() {
        runMap.keySet().forEach(runner -> {
            Scoreboard board = runner.getScoreboard();
            Objective obj = board.getObjective("run");
            if (obj == null) return;

            board.getEntries().forEach(board::resetScores);
            AtomicInteger line = new AtomicInteger(15);

            Score empty = obj.getScore("empty");
            empty.setScore(line.getAndDecrement());
            empty.customName(Component.empty());

            List<Player> players = runMap.keySet().stream().filter(Player::isOnline).toList();

            players.forEach(other -> {
                if (!other.isOnline()) return;

                RunData data = runMap.get(other);
                if (data == null) return;

                Component name = other.displayName();
                UUID uuid = other.getUniqueId();
                if (other.getProtocolVersion() >= 773) name = Component.object(ObjectContents.playerHead(uuid)).appendSpace().append(name);

                Score displayName = obj.getScore(uuid + "1");
                displayName.setScore(line.getAndDecrement());
                displayName.customName(name);

                Score displayAction = obj.getScore(uuid + "2");
                displayAction.setScore(line.getAndDecrement());
                displayAction.customName(Component.text("└ ").append(Component.text(data.boardAct).color(TextColor.color(0xD0D0D0))));

                if (players.size() > 1) {
                    Score spacer = obj.getScore(uuid + "space");
                    spacer.setScore(line.getAndDecrement());
                    spacer.customName(Component.empty());
                }
            });
        });
    }

    public String getFormattedTime(long time) {
        if (time == 0) return "0:00";

        long elapsed = System.currentTimeMillis() - time;
        long seconds = elapsed / 1000;
        long minutes = seconds / 60;
        seconds %= 60;

        return String.format("%d:%02d", minutes, seconds);
    }

    public void sendTipMsg(Player p, String msg) {
        p.sendMessage(Component.empty().append(Component.text("[Debug]:").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)).appendSpace().append(MiniMessage.miniMessage().deserialize(msg)));
    }
}

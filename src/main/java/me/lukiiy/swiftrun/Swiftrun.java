package me.lukiiy.swiftrun;

import com.viaversion.viaversion.api.Via;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class Swiftrun extends JavaPlugin {
    private final Map<Player, RunData> runMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> protocolCache = new ConcurrentHashMap<>();
    private final Set<Player> drawVoted = new HashSet<>();

    private ScheduledTask voteTask;
    private ScheduledTask mainTask;
    private RunState state = RunState.INACTIVE;

    private long startTime = 0;
    private long voteTime = 15;
    private float votesPercentage = 1f;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new Listen(), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register("run", new Cmd()));
    }

    public static Swiftrun getInstance() {
        return JavaPlugin.getPlugin(Swiftrun.class);
    }

    public void startRun(List<Player> players) {
        state = RunState.ACTIVE;
        runMap.clear();

        startTime = System.currentTimeMillis();

        for (Player p : players) {
            if (p == null) continue;

            join(p);
        }

        AtomicInteger displayIdx = new AtomicInteger();
        AtomicLong lastSwitch = new AtomicLong();

        mainTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> runMap.keySet().forEach(runner -> {
            List<Player> others = runMap.keySet().stream().filter(Player::isOnline).toList();
            if (others.isEmpty()) return;

            long now = System.currentTimeMillis();
            if (now - lastSwitch.get() >= 6000) { // 3s
                displayIdx.set((displayIdx.get() + 1) % others.size());
                lastSwitch.set(now);
            }

            Player current = others.get(displayIdx.get());

            for (Player viewer : others) {
                if (!viewer.isOnline()) continue;

                RunData data = runMap.get(current);
                if (data == null) continue;

                Component formattedCurrent = getProtocol(viewer) >= 773 ? Component.object(ObjectContents.playerHead(current.getUniqueId())).appendSpace().append(current.displayName()) : current.displayName();
                viewer.sendActionBar(Component.text("• " + getFormattedTime(startTime) + " • ").shadowColor(ShadowColor.shadowColor(0, 0, 0, 255)).append(formattedCurrent).append(Component.text(": ")).append(Component.text(data.boardAct).color(TextColor.color(0xD0D0D0)).decorate(TextDecoration.ITALIC)).append(Component.text(" •")));
            }
        }), 1L, 20L);

        Bukkit.broadcast(Component.text("The run has started!").color(NamedTextColor.YELLOW));
    }

    public void stopRun(Player winner) {
        if (state == RunState.INACTIVE || mainTask.isCancelled()) return;
        if (state == RunState.PAUSED) togglePause();

        String time = getFormattedTime(startTime);

        mainTask.cancel();
        if (voteTask != null && !voteTask.isCancelled()) voteTask.cancel();

        Bukkit.broadcast(Component.text("The run has ended!").color(NamedTextColor.YELLOW).append(Component.text(" Global final time: ").append(Component.text(time).color(NamedTextColor.YELLOW))));

        if (winner != null && runMap.containsKey(winner)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                NamedTextColor color = (p.equals(winner) || !runMap.containsKey(p)) ? NamedTextColor.YELLOW : NamedTextColor.RED;

                p.showTitle(Title.title(Component.text("Game Over!").color(color).decorate(TextDecoration.BOLD), Component.text("Winner: ").append(winner.displayName().color(color)).appendSpace().append(Component.text("(" + time + ")")), Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(500))));
            }
        }

        Bukkit.broadcast(Component.empty().appendNewline().append(Component.text("ʜᴏᴠᴇʀ ᴛʜᴇ ᴘʟᴀʏᴇʀѕ ᴛᴏ ᴄʜᴇᴄᴋ ᴛʜᴇɪʀ ᴛɪᴍᴇѕ").color(NamedTextColor.YELLOW)));

        List<Component> standing = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

            RunData data = runMap.get(p);
            if (data == null) return;

            p.setGameMode(GameMode.SPECTATOR);

            Component display = Component.object(ObjectContents.playerHead(p.getUniqueId())).appendSpace().append(p.displayName());
            if (winner == p) display = Component.empty().append(Component.text("✦ ").color(NamedTextColor.AQUA)).append(display).append(Component.text(" ✦").color(NamedTextColor.AQUA));

            Map<String, String> times = new LinkedHashMap<>();
            times.put("Nether", getFormattedTime(data.netherTime));
            times.put("Bastion", getFormattedTime(data.bastionTime));
            times.put("Stronghold", getFormattedTime(data.strongholdTime));
            times.put("End", getFormattedTime(data.endTime));

            List<Component> hoverLines = times.entrySet().stream().map(entry -> Component.text(entry.getKey() + " @ ").color(NamedTextColor.WHITE).append(Component.text(entry.getValue()).color(NamedTextColor.YELLOW))).collect(Collectors.toList());
            hoverLines.add(Component.empty());
            hoverLines.add(Component.text("Click to copy!").color(NamedTextColor.AQUA).decorate(TextDecoration.ITALIC));

            standing.add(display.hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.separator(Component.newline()), hoverLines))).clickEvent(ClickEvent.copyToClipboard(times.entrySet().stream().map(e -> e.getKey() + " @ " + e.getValue()).collect(Collectors.joining("; ")))));
        }

        Bukkit.broadcast(Component.join(JoinConfiguration.builder().separator(Component.newline()).build(), standing).appendNewline());
        runMap.clear();
    }

    public void togglePause() {
        if (state == RunState.INACTIVE) return;

        String title;
        ServerTickManager tick = Bukkit.getServerTickManager();

        if (state == RunState.ACTIVE) {
            state = RunState.PAUSED;
            title = "Paused";
            tick.setFrozen(true);
        } else {
            state = RunState.ACTIVE;
            title = "Resumed";
            tick.setFrozen(false);
        }

        Bukkit.getOnlinePlayers().forEach(p -> {
            p.showTitle(Title.title(Component.empty(), Component.text(title).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD), Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        });
    }

    public void drawVote(Player player) {
        if (state == RunState.INACTIVE) return;

        drawVoted.add(player);
        if (voteTask != null && !voteTask.isCancelled()) voteTask.cancel();

        Set<Player> participants = runMap.keySet().stream().filter(Player::isOnline).collect(Collectors.toSet());
        if (!participants.isEmpty()) {
            long matches = participants.stream().filter(drawVoted::contains).count();
            double ratio = (double) matches / participants.size();

            if (ratio >= votesPercentage) {
                Bukkit.broadcast(Component.text("Enough participants have voted for a draw.").color(NamedTextColor.RED));
                stopRun(null);
                return;
            }
        }

        voteTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            voteTime--;

            if (voteTime <= 0) {
                drawVoted.clear();
                Bukkit.broadcast(Component.text("The draw vote has expired.").color(NamedTextColor.YELLOW));
                task.cancel();
            }
        }, 1, 20);
    }

    public void join(Player player) {
        runMap.put(player, new RunData());
    }

    public void leave(Player player) {
        runMap.remove(player);

        if (state != RunState.INACTIVE && runMap.size() == 1) stopRun(runMap.keySet().iterator().next());
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

    public String getFormattedTime(long time) {
        if (time == 0) return "0:00";

        long elapsed = System.currentTimeMillis() - time;
        long totalSeconds = elapsed / 1000;

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
        else return String.format("%d:%02d", minutes, seconds);
    }


    public void sendTipMsg(Player p, String msg) {
        p.sendMessage(Component.empty().append(Component.text("[Debug]:").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)).appendSpace().append(MiniMessage.miniMessage().deserialize(msg)));
    }

    public int getProtocol(Player player) {
        return protocolCache.computeIfAbsent(player.getUniqueId(), id -> {
            try {
                return Via.getAPI().getPlayerVersion(id);
            } catch (Exception ignored) {
                return player.getProtocolVersion();
            }
        });
    }

    public void resetProtocol(Player player) {
        protocolCache.remove(player.getUniqueId());
    }
}

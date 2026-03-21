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

    private boolean globalTeam = false;

    private long startTime = 0;
    private long voteTime = 15;
    private long pauseTime = 0;
    private long pauseLast = 0;
    private long resumeLast = 0;
    private float votesPercentage = 1f;

    private static final Component RUN_USEFUL = Component.text("[Run]:").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new Listen(), this);
        if (isFolia()) getServer().getPluginManager().registerEvents(new FoliaListen(), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register("swiftrun", Set.of("run"), new Cmd()));
    }

    public static Swiftrun getInstance() {
        return JavaPlugin.getPlugin(Swiftrun.class);
    }

    public void startRun(List<Player> players) {
        state = RunState.ACTIVE;
        startTime = System.currentTimeMillis();

        runMap.clear();

        for (Player p : players) {
            if (p == null) continue;

            join(p);
        }

        AtomicInteger displayIdx = new AtomicInteger();
        AtomicLong lastSwitch = new AtomicLong();

        mainTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            if (runMap.isEmpty()) return;

            List<Player> others = new ArrayList<>(runMap.keySet());

            if (!globalTeam) {
                long now = System.currentTimeMillis();
                if (now - lastSwitch.get() >= 6000) { // 3s
                    displayIdx.set((displayIdx.get() + 1) % others.size());
                    lastSwitch.set(now);
                }

                Player current = others.get(displayIdx.get());
                RunData data = runMap.get(current);
                if (data == null) return;

                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    boolean canSeeObjects = getProtocol(viewer) >= 773;
                    Component formattedCurrent = canSeeObjects ? Component.object(ObjectContents.playerHead(current.getUniqueId())).appendSpace().append(current.displayName()) : current.displayName();

                    viewer.sendActionBar(Component.text("• " + getFormattedTime(startTime) + " • ").shadowColor(ShadowColor.shadowColor(0, 0, 0, 255)).append(formattedCurrent).append(Component.text(": ")).append(MiniMessage.miniMessage().deserialize(data.board).color(TextColor.color(0xD0D0D0)).decorate(TextDecoration.ITALIC)).append(Component.text(" •")));
                }
            } else {
                List<Component> parts = runMap.entrySet().stream().map(entry -> entry.getKey().displayName().append(Component.text(": ")).append(MiniMessage.miniMessage().deserialize(entry.getValue().board).color(TextColor.color(0xD0D0D0)).decorate(TextDecoration.ITALIC))).collect(Collectors.toList());
                Component bar = Component.text("• " + getFormattedTime(startTime) + " • ").shadowColor(ShadowColor.shadowColor(0, 0, 0, 255)).append(Component.join(JoinConfiguration.separator(Component.text(" | ").color(NamedTextColor.GRAY)), parts)).append(Component.text(" •"));

                for (Player viewer : Bukkit.getOnlinePlayers()) viewer.sendActionBar(bar);
            }
        }, 1L, 20L);

        Bukkit.broadcast(Component.text("The run has started!").color(NamedTextColor.YELLOW));
    }

    public void startGlobalRun() {
        globalTeam = true;

        startRun(Bukkit.getOnlinePlayers().stream().filter(p -> p.getGameMode() == GameMode.SURVIVAL).collect(Collectors.toList()));
    }

    public void stopRun(Player winner) {
        if (state == RunState.INACTIVE || mainTask.isCancelled()) return;
        if (state == RunState.PAUSED) togglePause();

        globalTeam = false;

        String time = getFormattedTime(startTime);

        mainTask.cancel();
        if (voteTask != null && !voteTask.isCancelled()) voteTask.cancel();

        Bukkit.broadcast(Component.text("The run has ended!").color(NamedTextColor.YELLOW).append(Component.text(" Global final time: ").append(Component.text(time).color(NamedTextColor.YELLOW))));

        if (winner != null && runMap.containsKey(winner)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String title = p == winner ? "Victory!" : "Game Over!";
                Sound sound = p == winner ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_ENDER_DRAGON_DEATH;
                NamedTextColor color = (p.equals(winner) || !runMap.containsKey(p)) ? NamedTextColor.YELLOW : NamedTextColor.RED;

                p.showTitle(Title.title(Component.text(title).color(color).decorate(TextDecoration.BOLD), Component.text("Winner: ").append(winner.displayName().color(color)).appendSpace().append(Component.text("(" + time + ")")), Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(500))));
                p.playSound(p, sound, SoundCategory.MASTER, 1, 1);
            }
        }

        Bukkit.broadcast(Component.empty().appendNewline().append(Component.text("ʜᴏᴠᴇʀ ᴛʜᴇ ᴘʟᴀʏᴇʀѕ ᴛᴏ ᴄʜᴇᴄᴋ ᴛʜᴇɪʀ ᴛɪᴍᴇѕ").color(NamedTextColor.YELLOW)));

        List<Component> standing = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            RunData data = runMap.get(p);
            if (data == null) continue;

            p.setGameMode(GameMode.SPECTATOR);
            Component display = Component.object(ObjectContents.playerHead(p.getUniqueId())).appendSpace().append(p.displayName());

            if (winner == p) display = Component.empty().append(Component.text("⭐ ").color(NamedTextColor.AQUA)).append(display);
            Component line = display;

            for (Map.Entry<String, Long> entry : data.getTimes().entrySet()) {
                String key = entry.getKey();
                if (!data.getLocations().containsKey(key)) continue;

                Location loc = data.getLocations().get(key);
                if (loc.getWorld() == null) continue;

                String coords = loc.blockX() + " " + loc.blockY() + " " + loc.blockZ();

                Component hover = Component.join(JoinConfiguration.separator(Component.newline()),
                        Component.text(getFormattedTime(entry.getValue())),
                        Component.text(coords),
                        Component.empty(),
                        Component.text("Click to teleport!").color(NamedTextColor.GREEN)
                );

                Component tag = Component.text(" [" + key + "]", NamedTextColor.YELLOW).hoverEvent(HoverEvent.showText(hover)).clickEvent(ClickEvent.runCommand("/swiftrun quicktp " + loc.getWorld().getName() + ";" + coords.replace(' ', ';')));

                line = line.appendSpace().append(tag);
            }

            standing.add(line);
        }

        Bukkit.broadcast(Component.join(JoinConfiguration.builder().separator(Component.newline()).build(), standing).appendNewline());
        runMap.clear();
        state = RunState.INACTIVE;
    }

    public enum RunState {
        ACTIVE,
        PAUSED,
        INACTIVE
    }

    public void togglePause() {
        if (state == RunState.INACTIVE) return;

        String title;
        ServerTickManager tick = Bukkit.getServerTickManager();

        if (state == RunState.ACTIVE) {
            state = RunState.PAUSED;
            title = "Paused";
            pauseLast = System.currentTimeMillis();
            tick.setFrozen(true);
        } else {
            state = RunState.ACTIVE;
            title = "Resumed";
            pauseTime += System.currentTimeMillis() - pauseLast;
            resumeLast = System.currentTimeMillis();
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
                Bukkit.broadcast(Component.text("The draw vote has expired.").color(NamedTextColor.RED));
                task.cancel();
            }
        }, 1, 20);
    }

    public void join(Player player) {
        RunData data = new RunData();

        data.setLocation("start", player.getLocation());
        runMap.put(player, data);
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

    public long getElapsedTime() {
        if (state == RunState.PAUSED) return pauseLast - startTime - pauseTime;

        return System.currentTimeMillis() - startTime - pauseTime;
    }

    public String getFormattedTime(long time) {
        if (time == 0) return "0:00";

        long total = getElapsedTime() / 1000;
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;

        if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
        else return String.format("%d:%02d", minutes, seconds);
    }

    public long getLastResume() {
        return resumeLast;
    }

    public void sendUsefulMsg(Player p, Component msg) {
        p.sendMessage(Component.empty().append(RUN_USEFUL).appendSpace().append(msg));
    }

    public void spectatorsUsefulMsg(Component msg) {
        Bukkit.getOnlinePlayers().stream().filter(player -> !runMap.containsKey(player)).forEach(p -> p.sendMessage(Component.empty().append(RUN_USEFUL).appendSpace().append(msg)));
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

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

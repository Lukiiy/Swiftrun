package me.lukiiy.swiftrun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;

import java.util.Comparator;
import java.util.stream.IntStream;

public class Listen implements Listener {
    @EventHandler
    public void advancement(PlayerAdvancementDoneEvent e) {
        if (Swiftrun.getInstance().getState() == Swiftrun.RunState.INACTIVE) return;

        Player p = e.getPlayer();
        RunData data = Swiftrun.getInstance().getRunMap().get(p);
        if (data == null) return;

        String advKey = e.getAdvancement().getKey().getKey();
        long now = Swiftrun.getInstance().getElapsedTime();
        Location location = p.getLocation();

        switch (advKey) {
            case "story/enter_the_nether" -> { // We Need To Go Deeper
                data.setTime("nether", now);
                data.setLocation("nether", location);
                data.board = "<sprite:blocks:block/netherrack> In Nether";
            }

            case "nether/find_bastion" -> { // Those Were the Days
                data.setTime("bastion", now);
                data.setLocation("bastion", location);
                data.board = "<sprite:blocks:block/gold_block> In Bastion";
            }

            case "nether/find_fortress" -> { // A Terrible Fortress
                data.setTime("fortress", now);
                data.setLocation("fortress", location);
                data.board = "<sprite:blocks:block/nether_bricks> In Fortress";
            }

            case "story/follow_ender_eye" -> { // Eye Spy
                data.setTime("stronghold", now);
                data.setLocation("stronghold", location);
                data.board = "<sprite:blocks:block/stone_bricks> In Stronghold";
            }

            case "end/kill_dragon" -> data.board = "<sprite:items:item/ender_dragon_spawn_egg> In The End";

            case "story/enter_the_end" -> { // The End?
                data.setTime("end", now);
                data.setLocation("end", location);
                data.board = "<sprite:blocks:block/end_stone> In The End";
            }
        }
    }

    @EventHandler
    public void spawn(EntitySpawnEvent e) {
        if (Swiftrun.getInstance().getState() != Swiftrun.RunState.ACTIVE || !(e.getEntity() instanceof EnderSignal signal) || signal.getEntitySpawnReason() != CreatureSpawnEvent.SpawnReason.DEFAULT) return;

        Location target = signal.getTargetLocation();
        if (target == null) return;

        Player thrower = signal.getLocation().getWorld().getPlayers().stream().min(Comparator.comparingDouble(p -> p.getLocation().distance(signal.getLocation()))).orElse(null);
        if (thrower == null) return;

        Swiftrun.getInstance().sendUsefulMsg(thrower, Component.text("Throw registered! Make sure you precisely look at the eye."));

        signal.getScheduler().runAtFixedRate(Swiftrun.getInstance(), task -> {
            if (!signal.isValid()) {
                task.cancel();
                return;
            }

            if (signal.getVelocity().length() < 0.05 || signal.getLocation().distance(target) < 1.5) {
                Location playerLoc = thrower.getLocation();

                if (signal.getLocation().toVector().subtract(playerLoc.toVector()).normalize().dot(playerLoc.getDirection().normalize()) < 0.75) return;

                signal.customName(Component.text("Calculating..."));
                signal.setCustomNameVisible(true);

                thrower.getScheduler().execute(Swiftrun.getInstance(), () -> {
                    StructureSearchResult result = signal.getLocation().getWorld().locateNearestStructure(signal.getLocation(), Structure.STRONGHOLD, 300, false);
                    if (result == null) {
                        Swiftrun.getInstance().sendUsefulMsg(thrower, MiniMessage.miniMessage().deserialize("<red>No stronghold found nearby!</red>"));
                        return;
                    }

                    int radius = 8;
                    Location loc = IntStream.rangeClosed(-radius, radius).boxed()
                            .flatMap(x -> IntStream.rangeClosed(-radius, radius).boxed().flatMap(y -> IntStream.rangeClosed(-radius, radius).mapToObj(z -> result.getLocation().clone().add(x, y, z))))
                            .filter(searchLoc -> searchLoc.getBlock().getType().name().contains("STONE_BRICK"))
                            .findFirst()
                            .orElse(result.getLocation());

                    Swiftrun.getInstance().sendUsefulMsg(thrower, MiniMessage.miniMessage().deserialize("A stronghold is near: <green>" + loc.getBlockX() + " " + loc.getBlockZ() + "</green>; Nether coords: <green>" + Math.round(loc.getBlockX() / 8.0) + " " + Math.round(loc.getBlockZ() / 8.0) + "</green>"));
                }, null, 20L);

                task.cancel();
            }
        }, null, 1L, 5L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        Swiftrun.getInstance().resetProtocol(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void portal(EntityPortalEnterEvent e) {
        if (!(e.getEntity() instanceof Player p) || Swiftrun.getInstance().getState() == Swiftrun.RunState.INACTIVE || !Swiftrun.getInstance().getRunMap().containsKey(p)) return;
        Location loc = e.getLocation();

        if (e.getPortalType() == PortalType.ENDER && loc.getWorld().getEnvironment() == World.Environment.THE_END) {
            Swiftrun.getInstance().stopRun(p);

            e.setCancelled(true);
            p.setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler
    public void move(PlayerMoveEvent e) {
        if (Swiftrun.getInstance().getState() == Swiftrun.RunState.PAUSED) e.setCancelled(true);
    }

    @EventHandler
    public void dmg(EntityDamageEvent e) {
        Swiftrun.RunState state = Swiftrun.getInstance().getState();

        if (state == Swiftrun.RunState.PAUSED) {
            e.setCancelled(true);
            return;
        }

        if (state == Swiftrun.RunState.ACTIVE) {
            if (e.getEntity() instanceof EnderCrystal) Swiftrun.getInstance().spectatorsUsefulMsg(Component.text("A crystal has been destroyed!"));
        }
    }

    @EventHandler
    public void kick(PlayerKickEvent e) {
        if (Swiftrun.getInstance().getState() == Swiftrun.RunState.PAUSED && (e.getCause() == PlayerKickEvent.Cause.FLYING_PLAYER || e.getCause() == PlayerKickEvent.Cause.FLYING_VEHICLE)) {
            long resume = Swiftrun.getInstance().getLastResume();
            if (resume < 1 || System.currentTimeMillis() - resume < 1000) return;

            e.setCancelled(true);
        }
    }

    @EventHandler
    public void dragonPhase(EnderDragonChangePhaseEvent e) {
        if (Swiftrun.getInstance().getState() != Swiftrun.RunState.ACTIVE || e.getCurrentPhase() != EnderDragon.Phase.FLY_TO_PORTAL) return;

        Swiftrun.getInstance().spectatorsUsefulMsg(Component.translatable("entity.minecraft.ender_dragon", "Ender Dragon").append(Component.text(" is now perching!")));
    }
}

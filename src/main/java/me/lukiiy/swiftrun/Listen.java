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

public class Listen implements Listener {
    @EventHandler
    public void advancement(PlayerAdvancementDoneEvent e) {
        if (Swiftrun.getInstance().getState() == RunState.INACTIVE) return;

        Player p = e.getPlayer();
        RunData data = Swiftrun.getInstance().getRunMap().get(p);
        if (data == null) return;

        String advKey = e.getAdvancement().getKey().getKey();
        long now = System.currentTimeMillis() - Swiftrun.getInstance().getStartTime();

        switch (advKey) {
            case "story/enter_the_nether" -> { // We Need To Go Deeper
                data.times.put("nether", now);
                data.boardAct = "In Nether";
                data.boardActShow = "blocks:netherrack";
            }

            case "nether/find_bastion" -> { // Those Were the Days
                data.times.put("bastion", now);
                data.boardAct = "In Bastion";
                data.boardActShow = "items:gold_ingot";
            }

            case "nether/find_fortress" -> { // A Terrible Fortress
                data.times.put("fortress", now);
                data.boardAct = "In Fortress";
                data.boardActShow = "blocks:nether_bricks";
            }

            case "story/follow_ender_eye" -> { // Eye Spy
                data.times.put("stronghold", now);
                data.boardAct = "In Stronghold";
                data.boardActShow = "items:ender_eye";
            }

            case "story/enter_the_end" -> { // The End?
                data.times.put("end", now);
                data.boardAct = "In The End";
                data.boardActShow = "blocks:end_stone";
            }

            case "end/kill_dragon" -> data.boardActShow = "items:ender_dragon_spawn_egg";
        }
    }

    @EventHandler
    public void spawn(EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof EnderSignal signal) || signal.getEntitySpawnReason() != CreatureSpawnEvent.SpawnReason.DEFAULT) return;

        Location target = signal.getTargetLocation();
        if (target == null) return;

        Player thrower = signal.getLocation().getWorld().getPlayers().stream().min(Comparator.comparingDouble(p -> p.getLocation().distance(signal.getLocation()))).orElse(null);
        if (thrower == null) return;

        Swiftrun.getInstance().sendUsefulMsg(thrower, Component.text("Throw registered! Make sure you record precise XYZ and angle!"));

        signal.getScheduler().runAtFixedRate(Swiftrun.getInstance(), task -> {
            if (!signal.isValid()) {
                task.cancel();
                return;
            }

            if (signal.getVelocity().length() < 0.05 || signal.getLocation().distance(target) < 1.5) {
                Location playerLoc = thrower.getLocation();
                if (signal.getLocation().toVector().subtract(playerLoc.toVector()).normalize().dot(playerLoc.getDirection().normalize()) < 0.995) return;

                signal.customName(Component.text("Calculating..."));
                signal.setCustomNameVisible(true);

                thrower.getScheduler().execute(Swiftrun.getInstance(), () -> {
                    StructureSearchResult result = signal.getLocation().getWorld().locateNearestStructure(signal.getLocation(), Structure.STRONGHOLD, 300, false);
                    if (result == null) {
                        Swiftrun.getInstance().sendUsefulMsg(thrower, MiniMessage.miniMessage().deserialize("<red>No stronghold found nearby!</red>"));
                        return;
                    }

                    Location loc = result.getLocation();
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
        if (!(e.getEntity() instanceof Player p) || Swiftrun.getInstance().getState() == RunState.INACTIVE || !Swiftrun.getInstance().getRunMap().containsKey(p)) return;
        Location loc = e.getLocation();

        if (e.getPortalType() == PortalType.ENDER && loc.getWorld().getEnvironment() == World.Environment.THE_END) {
            Swiftrun.getInstance().stopRun(p);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void move(PlayerMoveEvent e) {
        if (Swiftrun.getInstance().getState() == RunState.PAUSED) e.setCancelled(true);
    }

    @EventHandler
    public void dmg(EntityDamageEvent e) {
        RunState state = Swiftrun.getInstance().getState();

        if (state == RunState.PAUSED) {
            e.setCancelled(true);
            return;
        }

        if (state == RunState.ACTIVE) {
            if (e.getEntity() instanceof EnderCrystal) Swiftrun.getInstance().spectatorsUsefulMsg(Component.text("A crystal has been destroyed!"));
        }
    }

    @EventHandler
    public void kick(PlayerKickEvent e) {
        if (Swiftrun.getInstance().getState() == RunState.PAUSED && e.getCause() == PlayerKickEvent.Cause.FLYING_PLAYER || e.getCause() == PlayerKickEvent.Cause.FLYING_VEHICLE) e.setCancelled(true);
    }

    @EventHandler
    public void dragonPhase(EnderDragonChangePhaseEvent e) {
        if (Swiftrun.getInstance().getState() != RunState.ACTIVE || e.getCurrentPhase() != EnderDragon.Phase.FLY_TO_PORTAL) return;

        Bukkit.getOnlinePlayers().forEach(p -> {
            if (Swiftrun.getInstance().getRunMap().containsKey(p) || !p.hasPermission("swiftrun.dragon")) return;
            p.sendMessage(Component.translatable("entity.minecraft.ender_dragon", "Ender Dragon").append(Component.text(" is now perching!")));
        });
    }
}

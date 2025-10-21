package me.lukiiy.swiftrun;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.AsyncStructureGenerateEvent;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.util.BlockTransformer;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.StructureSearchResult;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class Listen implements Listener {
    private final Set<Player> eyeThrowCache = new HashSet<>();

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
                data.bastionTime = now;
                data.boardAct = "In Nether";
            }

            case "nether/find_bastion" -> { // Those Were the Days
                data.bastionTime = now;
                data.boardAct = "In Bastion";
            }

            case "nether/find_fortress" -> { // A Terrible Fortress
                data.bastionTime = now;
                data.boardAct = "In Fortress";
            }

            case "story/follow_ender_eye" -> { // Eye Spy
                data.strongholdTime = now;
                data.boardAct = "In Stronghold";
            }

            case "story/enter_the_end" -> { // The End?
                data.endTime = now;
                data.boardAct = "In The End";
            }
        }
    }

    @EventHandler
    public void entitySpawn(EntitySpawnEvent e) { // TODO
        if (e.getEntity() instanceof EnderSignal signal) {
            Location target = signal.getTargetLocation();
            if (target == null) return;

            signal.getScheduler().execute(Swiftrun.getInstance(), () -> {
                Player thrower = signal.getLocation().getWorld().getPlayers().stream()
                        .min(Comparator.comparingDouble(p -> p.getLocation().distance(signal.getLocation())))
                        .orElse(null);

                if (thrower == null) return;

                Swiftrun.getInstance().sendTipMsg(thrower, "Keep looking at it!");

                signal.getScheduler().runAtFixedRate(Swiftrun.getInstance(), task -> {
                    if (signal.getLocation().distance(target) < 2 && playerLookingAtEye(thrower, signal)) {

                        if (!eyeThrowCache.contains(thrower)) {
                            eyeThrowCache.add(thrower);

                            Swiftrun.getInstance().sendTipMsg(thrower, "Throw another!");
                        } else {
                            eyeThrowCache.remove(thrower);
                            signal.setCustomNameVisible(true);
                            signal.customName(Component.text("Searching..."));

                            StructureSearchResult result = signal.getLocation().getWorld().locateNearestStructure(target, StructureType.STRONGHOLD, 300, false);
                            thrower.getScheduler().execute(Swiftrun.getInstance(), () -> {
                                if (result == null) {
                                    Swiftrun.getInstance().sendTipMsg(thrower, "Couldn't find any, try somewhere else.");
                                    return;
                                }

                                Location loc = result.getLocation();
                                Swiftrun.getInstance().sendTipMsg(thrower, "A stronghold can be found at: <green>" + loc.getBlockX() + " " + loc.getBlockZ() + "</green>; Nether coords: <green>" + Math.round((double) loc.getBlockX() / 8) + " " + Math.round((double) loc.getBlockZ() / 8) + "</green>");
                            }, null, 20L);
                        }

                        task.cancel();
                        return;
                    }

                    if (!signal.isValid()) task.cancel();
                }, null, 1L, 10L);
            }, null, 1L);
        }
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
            p.teleportAsync(loc);
        }
    }

    private boolean playerLookingAtEye(Player p, EnderSignal eye) {
        Vector toEye = eye.getLocation().toVector().subtract(p.getEyeLocation().toVector()).normalize();
        Vector pDir = p.getEyeLocation().getDirection();

        return toEye.dot(pDir) > 0.98;
    }
}

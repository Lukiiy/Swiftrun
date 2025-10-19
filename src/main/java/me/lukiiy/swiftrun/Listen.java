package me.lukiiy.swiftrun;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.generator.structure.StructureType;
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

        Swiftrun.getInstance().updateBoards();
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

    private boolean playerLookingAtEye(Player p, EnderSignal eye) {
        Vector toEye = eye.getLocation().toVector().subtract(p.getEyeLocation().toVector()).normalize();
        Vector pDir = p.getEyeLocation().getDirection();

        return toEye.dot(pDir) > 0.98;
    }
}

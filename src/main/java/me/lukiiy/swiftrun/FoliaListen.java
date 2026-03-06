package me.lukiiy.swiftrun;

import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class FoliaListen implements Listener { // TODO
    public void pauseCancel(Cancellable e) {
        if (Swiftrun.getInstance().getState() == Swiftrun.RunState.PAUSED) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockFromTo(BlockFromToEvent e) {
        pauseCancel(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityDmg(EntityDamageEvent e) {
        pauseCancel(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerFood(FoodLevelChangeEvent e) {
        pauseCancel(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityMove(EntityMoveEvent e) {
        pauseCancel(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityAir(EntityAirChangeEvent e) {
        pauseCancel(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityTarget(EntityTargetEvent e) {
        pauseCancel(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityPot(EntityPotionEffectEvent e) {
        pauseCancel(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerMove(PlayerMoveEvent e) {
        pauseCancel(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerInteract(PlayerInteractEvent e) {
        pauseCancel(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerEntityInteract(PlayerInteractEntityEvent e) {
        pauseCancel(e);
    }
}

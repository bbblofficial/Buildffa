package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class High implements Listener {
  private JavaPlugin plugin;
  
  private double highLimit;
  
  public High(JavaPlugin plugin) {
    this.plugin = plugin;
    loadConfiguration();
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  private void loadConfiguration() {
    FileConfiguration config = this.plugin.getConfig();
    this.highLimit = config.getDouble("high-limit", 100.0D);
  }
  
  @EventHandler
  public void onProjectileLaunch(ProjectileLaunchEvent event) {
    if (event.getEntity().getShooter() instanceof LivingEntity) {
      LivingEntity shooter = (LivingEntity) event.getEntity().getShooter();
      if (shooter instanceof Player) {
        Player player = (Player) shooter;
        if (player.getLocation().getY() > this.highLimit && player.getGameMode() != GameMode.CREATIVE) {
          event.setCancelled(true);
        }
      }
    }
  }
  
  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    if (player.getLocation().getY() > this.highLimit && player.getGameMode() != GameMode.CREATIVE) {
      event.setCancelled(true);
    }
  }
  
  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (player.getLocation().getY() > this.highLimit && player.getGameMode() != GameMode.CREATIVE) {
      event.setCancelled(true);
    }
  }
  
  @EventHandler
  public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    if (event.getEntity() instanceof Player) {
      Player player = (Player) event.getEntity();
      if (player.getLocation().getY() > this.highLimit && player.getGameMode() != GameMode.CREATIVE) {
        event.setCancelled(true);
      }
    }
  }
}

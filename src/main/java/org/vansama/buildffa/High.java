package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
  
  public void reloadConfig() {
    loadConfiguration();
  }
  
  @EventHandler
  public void onProjectileLaunch(ProjectileLaunchEvent event) {
    if (event.getEntity().getShooter() instanceof LivingEntity) {
      LivingEntity shooter = (LivingEntity) event.getEntity().getShooter();
      if (shooter instanceof Player) {
        Player player = (Player) shooter;
        if (player.getGameMode() != GameMode.CREATIVE
            && player.getLocation().getY() >= this.highLimit) {
          event.setCancelled(true);
        }
      }
    }
  }
  
  /**
   * Block placement is blocked if:
   *   - The BLOCK's Y position is at or above the high limit
   *   - OR the player is at or above the high limit (they're in the restricted zone)
   *
   * The block check prevents the "pillar up" glitch (player stands below limit,
   * looks up, and places a block that ends up above the limit).
   */
  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    if (player.getGameMode() == GameMode.CREATIVE) return;
    
    Location blockLoc = event.getBlockPlaced().getLocation();
    double blockY = blockLoc.getY();
    double playerY = player.getLocation().getY();
    
    // Block Y check — this is the important one for the pillar glitch
    if (blockY >= this.highLimit) {
      event.setCancelled(true);
      player.sendMessage(ChatColor.translateAlternateColorCodes('&',
          "&cYou cannot place blocks above Y &e" + (int) this.highLimit + "&c!"));
      return;
    }
    
    // Player Y check — belt & suspenders
    if (playerY >= this.highLimit) {
      event.setCancelled(true);
      player.sendMessage(ChatColor.translateAlternateColorCodes('&',
          "&cYou are above the build limit (Y &e" + (int) this.highLimit + "&c)!"));
    }
  }
  
  /**
   * Block break is blocked if the block is above the high limit.
   */
  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (player.getGameMode() == GameMode.CREATIVE) return;
    
    Location blockLoc = event.getBlock().getLocation();
    if (blockLoc.getY() >= this.highLimit) {
      event.setCancelled(true);
      player.sendMessage(ChatColor.translateAlternateColorCodes('&',
          "&cYou cannot break blocks above Y &e" + (int) this.highLimit + "&c!"));
    }
  }
  
  @EventHandler
  public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    if (event.getEntity() instanceof Player) {
      Player player = (Player) event.getEntity();
      if (player.getGameMode() != GameMode.CREATIVE
          && player.getLocation().getY() >= this.highLimit) {
        event.setCancelled(true);
      }
    }
  }
}
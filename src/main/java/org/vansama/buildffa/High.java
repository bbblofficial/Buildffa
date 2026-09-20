package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
  
  // Cooldown: only send the "cannot place blocks here" message once per second
  // per player, so the player never sees it twice in a row.
  private final Map<UUID, Long> lastMessageTime = new HashMap<UUID, Long>();
  private static final long MESSAGE_COOLDOWN_MS = 1000L;
  
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
  
  private void sendMessageOnce(Player player, String message) {
    UUID id = player.getUniqueId();
    long now = System.currentTimeMillis();
    long last = this.lastMessageTime.containsKey(id) ? this.lastMessageTime.get(id).longValue() : 0L;
    if (now - last < MESSAGE_COOLDOWN_MS) {
      return;
    }
    this.lastMessageTime.put(id, Long.valueOf(now));
    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
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
  
  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    if (player.getGameMode() == GameMode.CREATIVE) return;
    
    Location blockLoc = event.getBlockPlaced().getLocation();
    if (blockLoc.getY() >= this.highLimit) {
      event.setCancelled(true);
      sendMessageOnce(player, "&cYou cannot place blocks here!");
    }
  }
  
  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (player.getGameMode() == GameMode.CREATIVE) return;
    
    Location blockLoc = event.getBlock().getLocation();
    if (blockLoc.getY() >= this.highLimit) {
      event.setCancelled(true);
      sendMessageOnce(player, "&cYou cannot break blocks here!");
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
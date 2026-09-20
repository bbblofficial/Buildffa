package org.vansama.buildffa;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Void implements Listener {
  private JavaPlugin plugin;
  private double killHeight;
  
  // Players we've already killed via void — don't kill them again until they respawn
  private final Set<UUID> dyingPlayers = new HashSet<UUID>();
  
  public Void(JavaPlugin plugin) {
    this.plugin = plugin;
    loadConfiguration();
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  private void loadConfiguration() {
    FileConfiguration config = this.plugin.getConfig();
    this.killHeight = config.getDouble("kill-height", 0.0D);
  }
  
  public void reloadConfig() {
    loadConfiguration();
  }
  
  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    Location to = event.getTo();
    
    // Ignore if player is already dead / in the dying set
    if (player.isDead()) return;
    if (this.dyingPlayers.contains(player.getUniqueId())) return;
    if (player.getHealth() <= 0) return;
    
    if (to.getY() < this.killHeight) {
      // Kill once, then blacklist until respawn
      this.dyingPlayers.add(player.getUniqueId());
      player.setHealth(0.0D);
    }
  }
  
  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    // Remove from blacklist so they can be killed by void again next time
    this.dyingPlayers.remove(event.getPlayer().getUniqueId());
  }
  
  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    event.setDeathMessage(null);
  }
}
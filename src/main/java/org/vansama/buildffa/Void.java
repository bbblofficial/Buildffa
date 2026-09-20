package org.vansama.buildffa;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Void implements Listener {
  private JavaPlugin plugin;
  private double killHeight;
  private String voidMessage;
  
  private final Set<UUID> dyingPlayers = new HashSet<UUID>();
  
  public Void(JavaPlugin plugin) {
    this.plugin = plugin;
    loadConfiguration();
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  private void loadConfiguration() {
    FileConfiguration config = this.plugin.getConfig();
    this.killHeight = config.getDouble("kill-height", 0.0D);
    this.voidMessage = config.getString("void-message", "&c%player% &7fell into the void");
  }
  
  public void reloadConfig() {
    loadConfiguration();
  }
  
  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    Location to = event.getTo();
    
    if (player.isDead() || player.getHealth() <= 0) return;
    if (this.dyingPlayers.contains(player.getUniqueId())) return;
    
    if (to.getY() < this.killHeight) {
      this.dyingPlayers.add(player.getUniqueId());
      player.setHealth(0.0D);
    }
  }
  
  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    event.setDeathMessage(null);
    
    Player player = event.getEntity();
    
    if (player.getKiller() == null && this.dyingPlayers.remove(player.getUniqueId())) {
      if (this.voidMessage != null && !this.voidMessage.isEmpty()) {
        String msg = ChatColor.translateAlternateColorCodes('&', this.voidMessage.replace("%player%", player.getName()));
        Bukkit.broadcastMessage(msg);
      }
    }
  }
}
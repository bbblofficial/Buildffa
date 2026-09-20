package org.vansama.buildffa;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Welcome implements Listener {
  private final JavaPlugin plugin;
  
  private int onlinePlayers = 0;
  
  public Welcome(JavaPlugin plugin) {
    this.plugin = plugin;
  }
  
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    player.setGameMode(GameMode.SURVIVAL);
    this.plugin.saveDefaultConfig();
    FileConfiguration config = this.plugin.getConfig();
    event.setJoinMessage(null);
    String joinMessage = config.getString("join-message");
    Bukkit.broadcastMessage(colorize(joinMessage).replaceAll("%player%", player.getName()).replaceAll("%online%", String.valueOf(++this.onlinePlayers)));
  }
  
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    this.plugin.saveDefaultConfig();
    event.setQuitMessage(null);
    String quitMessage = this.plugin.getConfig().getString("quit-message");
    Bukkit.broadcastMessage(colorize(quitMessage).replaceAll("%player%", player.getName()).replaceAll("%online%", String.valueOf(--this.onlinePlayers)));
  }
  
  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}

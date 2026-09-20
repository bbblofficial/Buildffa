package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class KillListener implements Listener {
  private Map<UUID, Integer> killCounts = new HashMap<>();
  
  private Map<UUID, Long> lastKillTimes = new ConcurrentHashMap<>();
  
  private JavaPlugin plugin;
  
  private FileConfiguration config;
  
  public KillListener(JavaPlugin plugin) {
    this.plugin = plugin;
    this.config = plugin.getConfig();
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  @EventHandler
  public void onPlayerKill(PlayerDeathEvent event) {
    Player deathPlayer = event.getEntity();
    Player killer = deathPlayer.getKiller();
    if (killer != null) {
      long currentTime = System.currentTimeMillis();
      long lastKillTime = ((Long) this.lastKillTimes.getOrDefault(killer.getUniqueId(), Long.valueOf(0L))).longValue();
      if (currentTime - lastKillTime < 80L) {
        return;
      }
      this.lastKillTimes.put(killer.getUniqueId(), Long.valueOf(currentTime));
      UUID killerId = killer.getUniqueId();
      int kills = ((Integer) this.killCounts.getOrDefault(killerId, Integer.valueOf(0))).intValue() + 1;
      this.killCounts.put(killerId, Integer.valueOf(kills));
      String titleSuffix = this.config.getString("Title-Suffix");
      String subTitleKill = this.config.getString("SubTitle-kill");
      killer.sendTitle("+ kills + titleSuffix", subTitleKill, 0, 40, 0);
      killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
    }
  }
  
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    resetKillCount(event.getPlayer());
  }
  
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    resetKillCount(event.getPlayer());
  }
  
  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    resetKillCount(event.getEntity());
  }
  
  private void resetKillCount(Player player) {
    this.killCounts.remove(player.getUniqueId());
    this.lastKillTimes.remove(player.getUniqueId());
  }
  
  public int getKillCount(Player player) {
    return ((Integer) this.killCounts.getOrDefault(player.getUniqueId(), Integer.valueOf(0))).intValue();
  }
  
  public void updateKillCount(Player player, int increment) {
    UUID playerId = player.getUniqueId();
    int currentKills = ((Integer) this.killCounts.getOrDefault(playerId, Integer.valueOf(0))).intValue();
    this.killCounts.put(playerId, Integer.valueOf(currentKills + increment));
  }
}

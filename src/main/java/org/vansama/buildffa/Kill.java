package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class Kill implements Listener {
  private JavaPlugin plugin;
  private KillListener killListener;
  
  // Killer cooldown (anti spam)
  private Map<UUID, Long> lastKillTimestamps = new HashMap<UUID, Long>();
  // Victim cooldown (anti-spam when a player dies repeatedly e.g. void)
  private Map<UUID, Long> lastVictimDeathTimestamps = new HashMap<UUID, Long>();
  
  public Kill(JavaPlugin plugin, KillListener killListener) {
    this.plugin = plugin;
    this.killListener = killListener;
  }
  
  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player deathPlayer = event.getEntity();
    UUID victimId = deathPlayer.getUniqueId();
    
    // Anti-spam: if this victim died less than 3 seconds ago, ignore
    long now = System.currentTimeMillis();
    long lastVictimDeath = ((Long) this.lastVictimDeathTimestamps.getOrDefault(victimId, Long.valueOf(0L))).longValue();
    if (now - lastVictimDeath < 3000L) {
      return;
    }
    this.lastVictimDeathTimestamps.put(victimId, Long.valueOf(now));
    
    if (deathPlayer.getKiller() == null) return;
    
    Player killer = deathPlayer.getKiller();
    UUID killerId = killer.getUniqueId();
    
    // Anti-spam: if this killer killed someone less than 1 second ago, ignore
    long lastKillTime = ((Long) this.lastKillTimestamps.getOrDefault(killerId, Long.valueOf(0L))).longValue();
    if (now - lastKillTime < 1000L) {
      return;
    }
    this.lastKillTimestamps.put(killerId, Long.valueOf(now));
    
    int killCount = this.killListener.getKillCount(killer);
    
    killer.getInventory().addItem(new ItemStack[] { new ItemStack(Material.GOLDEN_APPLE, 1) });
    killer.getInventory().addItem(new ItemStack[] { new ItemStack(Material.ENDER_PEARL, 1) });
    killer.getInventory().addItem(new ItemStack[] { new ItemStack(Material.ARROW, 8) });
    
    String joinMessage = this.plugin.getConfig().getString("kill");
    if (joinMessage != null) {
      String broadcastMessage = colorize(joinMessage)
          .replaceAll("%killer%", killer.getName())
          .replaceAll("%loser%", deathPlayer.getName())
          .replaceAll("%killcount%", String.valueOf(killCount));
      Bukkit.broadcastMessage(broadcastMessage);
    }
  }
  
  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}
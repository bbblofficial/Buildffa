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
  
  private Map<UUID, Long> lastKillTimestamps = new HashMap<UUID, Long>();
  
  public Kill(JavaPlugin plugin, KillListener killListener) {
    this.plugin = plugin;
    this.killListener = killListener;
  }
  
  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player deathPlayer = event.getEntity();
    if (deathPlayer.getKiller() != null) {
      Player killer = deathPlayer.getKiller();
      UUID killerId = killer.getUniqueId();
      long currentTime = System.currentTimeMillis();
      long lastKillTime = ((Long) this.lastKillTimestamps.getOrDefault(killerId, Long.valueOf(0L))).longValue();
      if (currentTime - lastKillTime < 1000L) {
        return;
      }
      int killCount = this.killListener.getKillCount(killer);
      this.lastKillTimestamps.put(killerId, Long.valueOf(currentTime));
      
      killer.getInventory().addItem(new ItemStack[] { new ItemStack(Material.GOLDEN_APPLE, 1) });
      killer.getInventory().addItem(new ItemStack[] { new ItemStack(Material.ENDER_PEARL, 1) });
      killer.getInventory().addItem(new ItemStack[] { new ItemStack(Material.ARROW, 8) });
      
      String joinMessage = this.plugin.getConfig().getString("kill");
      if (joinMessage != null) {
        String broadcastMessage = colorize(joinMessage).replaceAll("%killer%", killer.getName()).replaceAll("%loser%", deathPlayer.getName()).replaceAll("%killcount%", String.valueOf(killCount));
        Bukkit.broadcastMessage(broadcastMessage);
      }
    }
  }
  
  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}

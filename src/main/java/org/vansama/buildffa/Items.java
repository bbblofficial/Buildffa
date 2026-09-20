package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Items implements Listener {
  private JavaPlugin plugin;
  
  public Items(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  // In 1.8.8, BlockBreakEvent has no setDropItems() method.
  // Block restoration is handled by the Blocks listener.
  
  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    event.setCancelled(true);
  }
}
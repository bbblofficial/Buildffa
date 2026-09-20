package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Items implements Listener {
  private JavaPlugin plugin;
  
  public Items(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  // Item pickup is ENABLED (removed cancel) so players can collect blocks
  
  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    event.setDropItems(false);
  }
  
  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    event.setCancelled(true);
  }
}

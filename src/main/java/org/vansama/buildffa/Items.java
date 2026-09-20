package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Items implements Listener {
  private JavaPlugin plugin;
  
  public Items(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  // No item pickups
  @EventHandler
  public void onPlayerPickupItem(PlayerPickupItemEvent event) {
    event.setCancelled(true);
  }
  
  // No item drops
  @EventHandler
  public void onPlayerDropItem(PlayerDropItemEvent event) {
    event.setCancelled(true);
  }
  
  // No creature spawns
  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    event.setCancelled(true);
  }
}

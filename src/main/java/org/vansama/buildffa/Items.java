package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Items implements Listener {
  private JavaPlugin plugin;
  private KitEditor kitEditor;
  
  public Items(JavaPlugin plugin, KitEditor kitEditor) {
    this.plugin = plugin;
    this.kitEditor = kitEditor;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  // No item pickups (except while editing kit)
  @EventHandler
  public void onPlayerPickupItem(PlayerPickupItemEvent event) {
    Player player = event.getPlayer();
    if (this.kitEditor.isEditing(player)) {
      return; // allow during kit editing
    }
    event.setCancelled(true);
  }
  
  // No item drops (except while editing kit)
  @EventHandler
  public void onPlayerDropItem(PlayerDropItemEvent event) {
    Player player = event.getPlayer();
    if (this.kitEditor.isEditing(player)) {
      return; // allow during kit editing so players can rearrange items
    }
    event.setCancelled(true);
  }
  
  // No creature spawns
  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    event.setCancelled(true);
  }
}
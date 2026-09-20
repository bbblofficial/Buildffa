package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Items implements Listener {
  private JavaPlugin plugin;
  
  private Map<UUID, Long> droppedItems = new HashMap<>();
  
  public Items(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  @EventHandler
  public void onPlayerPickupItem(PlayerPickupItemEvent event) {
    event.setCancelled(true);
  }
  
  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    event.setDropItems(false);
  }
  
  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    event.setCancelled(true);
  }
}

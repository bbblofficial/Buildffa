package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Blocks implements Listener {
  private JavaPlugin plugin;
  
  private Map<Location, Long> placedBlocks = new HashMap<Location, Long>();
  
  public Blocks(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
      return;
    }
    
    final Block block = event.getBlockPlaced();
    if (block.getType() == Material.AIR) {
      return;
    }
    
    final Location loc = block.getLocation().clone();
    final Material originalType = block.getType();
    
    this.placedBlocks.put(loc, Long.valueOf(System.currentTimeMillis()));
    
    // Decay after 5 seconds (100 ticks)
    Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) this.plugin, new Runnable() {
      @Override
      public void run() {
        if (placedBlocks.containsKey(loc)) {
          Block b = loc.getBlock();
          if (b.getType() != Material.AIR && b.getType() == originalType) {
            b.setType(Material.AIR);
          }
          placedBlocks.remove(loc);
        }
      }
    }, 100L);
  }
  
  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
      return;
    }
    
    final Block block = event.getBlock();
    final Location loc = block.getLocation().clone();
    final Material blockType = block.getType();
    
    if (this.placedBlocks.containsKey(loc)) {
      this.placedBlocks.remove(loc);
      return;
    }
    
    // Restore natural blocks after breaking
    Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) this.plugin, new Runnable() {
      @Override
      public void run() {
        Block b = loc.getBlock();
        if (b.getType() == Material.AIR) {
          if (blockType != Material.REDSTONE_BLOCK && blockType != Material.BEDROCK) {
            b.setType(blockType);
          }
        } else {
          Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) Blocks.this.plugin, this, 10L);
        }
      }
    }, 360L);
  }
  
  public void onDisable() {
    for (Location loc : this.placedBlocks.keySet()) {
      Block b = loc.getBlock();
      if (b.getType() != Material.AIR) {
        b.setType(Material.AIR);
      }
    }
    this.placedBlocks.clear();
  }
}

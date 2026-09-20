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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Blocks implements Listener {
  private JavaPlugin plugin;
  private Map<Location, Long> placedBlocks = new HashMap<Location, Long>();
  
  // 9 seconds = 180 ticks for natural block restore
  private static final long RESTORE_DELAY_TICKS = 180L;
  // Player-placed blocks decay after 5 seconds
  private static final long DECAY_DELAY_TICKS = 100L;
  
  public Blocks(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent event) {
    if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
    
    final Block block = event.getBlockPlaced();
    if (block.getType() == Material.AIR) return;
    
    final Location loc = block.getLocation().clone();
    final Material originalType = block.getType();
    final byte originalData = block.getData();
    
    this.placedBlocks.put(loc, Long.valueOf(System.currentTimeMillis()));
    
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
    }, DECAY_DELAY_TICKS);
  }
  
  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
    
    final Block block = event.getBlock();
    final Location loc = block.getLocation().clone();
    final Material blockType = block.getType();
    final byte blockData = block.getData();
    
    event.setCancelled(true);
    block.setType(Material.AIR);
    
    if (this.placedBlocks.containsKey(loc)) {
      this.placedBlocks.remove(loc);
      return;
    }
    
    Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) this.plugin, new Runnable() {
      @Override
      public void run() {
        Block b = loc.getBlock();
        if (b.getType() == Material.AIR) {
          if (blockType != Material.REDSTONE_BLOCK
              && blockType != Material.BEDROCK
              && blockType != Material.AIR
              && blockType != Material.WATER
              && blockType != Material.STATIONARY_WATER
              && blockType != Material.LAVA
              && blockType != Material.STATIONARY_LAVA) {
            b.setType(blockType);
            b.setData(blockData);
          }
        } else {
          Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) Blocks.this.plugin, this, 10L);
        }
      }
    }, RESTORE_DELAY_TICKS);
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
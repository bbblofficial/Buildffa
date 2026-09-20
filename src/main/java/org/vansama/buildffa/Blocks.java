package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Blocks implements Listener {
  private JavaPlugin plugin;
  private Map<Location, Long> placedBlocks = new HashMap<Location, Long>();
  
  // 9 seconds = 180 ticks (20 ticks per second)
  private static final long RESTORE_DELAY_TICKS = 180L;
  // Player-placed blocks decay faster (5 seconds)
  private static final long DECAY_DELAY_TICKS = 100L;
  
  public Blocks(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
    
    final Block block = event.getBlockPlaced();
    if (block.getType() == Material.AIR) return;
    
    final Location loc = block.getLocation().clone();
    final Material originalType = block.getType();
    
    this.placedBlocks.put(loc, Long.valueOf(System.currentTimeMillis()));
    
    // Player-placed blocks decay after 5 seconds
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
    
    // ============================================================
    // 1) Cancel the event so NO items drop from the broken block.
    // 2) Manually remove the block so it disappears visually.
    // ============================================================
    event.setCancelled(true);
    block.setType(Material.AIR);
    
    // If the broken block was a player-placed one, just remove it from tracking
    // (it wouldn't have been restored anyway).
    if (this.placedBlocks.containsKey(loc)) {
      this.placedBlocks.remove(loc);
      return;
    }
    
    // ============================================================
    // 3) Natural block — schedule restore after 9 seconds (180 ticks)
    // ============================================================
    Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) this.plugin, new Runnable() {
      @Override
      public void run() {
        Block b = loc.getBlock();
        if (b.getType() == Material.AIR) {
          // Restore the block exactly where it was
          // Skip some materials that shouldn't come back (safety)
          if (blockType != Material.REDSTONE_BLOCK
              && blockType != Material.BEDROCK
              && blockType != Material.AIR
              && blockType != Material.WATER
              && blockType != Material.STATIONARY_WATER
              && blockType != Material.LAVA
              && blockType != Material.STATIONARY_LAVA) {
            b.setType(blockType);
          }
        } else {
          // Block was replaced by something else (e.g. player placed a block there).
          // Retry every 10 ticks until it's AIR again, then restore.
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
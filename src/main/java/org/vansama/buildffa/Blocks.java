package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
  
  /**
   * Track player-placed blocks so they decay after 5 seconds.
   * Uses MONITOR priority + ignoreCancelled to avoid interfering with
   * other listeners (like High.java) that decide whether the place is allowed.
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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
  
  /**
   * On break:
   *   - Cancel the event so no items drop
   *   - Manually remove the block
   *   - If it was a player-placed block: just remove (no restore)
   *   - If it was a natural block: schedule restore after 9 seconds (180 ticks)
   *     preserving both the Material AND the data value (color/wood type/slab shape)
   */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
    
    final Block block = event.getBlock();
    final Location loc = block.getLocation().clone();
    final Material blockType = block.getType();
    final byte blockData = block.getData();
    
    // Cancel so no drops are created
    event.setCancelled(true);
    
    // Manually remove the block
    block.setType(Material.AIR);
    
    // If it was a player-placed block, just remove from tracking — no restore
    if (this.placedBlocks.containsKey(loc)) {
      this.placedBlocks.remove(loc);
      return;
    }
    
    // Natural block — restore after 9 seconds with original data value
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
          // Block was replaced — retry every 10 ticks
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
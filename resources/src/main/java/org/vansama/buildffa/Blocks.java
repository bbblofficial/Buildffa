package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
  
  private Map<Block, Long> placedBlocks = new HashMap<>();
  
  public Blocks(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
      return;
    }
    Block block = event.getBlockPlaced();
    if (block.getType() != Material.AIR) {
      block.setType(Material.REDSTONE_BLOCK);
      Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) this.plugin, () -> {
        if (this.placedBlocks.containsKey(block) && block.getType() != Material.AIR) {
          block.setType(Material.AIR);
          this.placedBlocks.remove(block);
        }
      }, 50L);
      this.placedBlocks.put(block, Long.valueOf(System.currentTimeMillis()));
    }
  }
  
  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
      return;
    }
    Player player = event.getPlayer();
    final Block block = event.getBlock();
    final Material blockType = block.getType();
    if (!this.placedBlocks.containsKey(block)) {
      Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) this.plugin, new Runnable() {
        @Override
        public void run() {
          if (block.getType() == Material.AIR) {
            if (blockType != Material.REDSTONE_BLOCK) {
              block.setType(blockType);
            }
          } else {
            Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) Blocks.this.plugin, this, 10L);
          }
        }
      }, 360L);
    }
  }
  
  public void onDisable() {
    for (Block block : this.placedBlocks.keySet()) {
      block.setType(Material.AIR);
    }
    this.placedBlocks.clear();
  }
}

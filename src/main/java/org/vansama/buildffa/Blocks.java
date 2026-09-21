package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
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

  // Loaded from config.yml (in seconds), converted to ticks internally
  private long naturalRestoreTicks = 180L;   // default 9 sec
  private long placedDecayTicks = 100L;      // default 5 sec

  public Blocks(JavaPlugin plugin) {
    this.plugin = plugin;
    loadConfiguration();
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }

  public void loadConfiguration() {
    FileConfiguration config = this.plugin.getConfig();

    if (!config.contains("blocks.natural-restore-seconds")) {
      config.set("blocks.natural-restore-seconds", Integer.valueOf(9));
      this.plugin.saveConfig();
    }
    if (!config.contains("blocks.placed-decay-seconds")) {
      config.set("blocks.placed-decay-seconds", Integer.valueOf(5));
      this.plugin.saveConfig();
    }

    int naturalSeconds = config.getInt("blocks.natural-restore-seconds", 9);
    int placedSeconds = config.getInt("blocks.placed-decay-seconds", 5);

    if (naturalSeconds < 1) naturalSeconds = 1;
    if (naturalSeconds > 600) naturalSeconds = 600;
    if (placedSeconds < 1) placedSeconds = 1;
    if (placedSeconds > 600) placedSeconds = 600;

    this.naturalRestoreTicks = naturalSeconds * 20L;
    this.placedDecayTicks = placedSeconds * 20L;

    this.plugin.getLogger().info("BuildFFA block timings loaded: natural=" + naturalSeconds
            + "s, placed=" + placedSeconds + "s");
  }

  public void reloadConfig() {
    loadConfiguration();
  }

  /**
   * Returns true if this player is currently in Build Mode.
   * Build Mode players get their placed blocks SAVED (no decay),
   * and their broken blocks are NOT restored.
   */
  private boolean isBuildMode(Player player) {
    return BuildModeManager.isInBuildMode(player);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    if (player.getGameMode() == GameMode.CREATIVE) return;

    final Block block = event.getBlockPlaced();
    if (block.getType() == Material.AIR) return;

    final Location loc = block.getLocation().clone();
    final Material originalType = block.getType();

    // ============================================================
    //  BUILD MODE → place block permanently (never decays)
    // ============================================================
    if (isBuildMode(player)) {
      // Do NOT track it in placedBlocks, so it will never decay.
      // The block just stays there forever until someone breaks it.
      return;
    }
    // ============================================================

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
    }, placedDecayTicks);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (player.getGameMode() == GameMode.CREATIVE) return;

    final Block block = event.getBlock();
    final Location loc = block.getLocation().clone();
    final Material blockType = block.getType();
    final byte blockData = block.getData();

    // ============================================================
    //  BUILD MODE → break block permanently (no restore)
    // ============================================================
    if (isBuildMode(player)) {
      // Cancel the event so we control what drops (nothing)
      event.setCancelled(true);
      // Remove the block
      block.setType(Material.AIR);
      // Remove from tracking if it was there
      this.placedBlocks.remove(loc);
      return;
    }
    // ============================================================

    // Normal behavior: cancel (no drops) and restore later
    event.setCancelled(true);
    block.setType(Material.AIR);

    // If it was a player-placed block, just remove — no restore
    if (this.placedBlocks.containsKey(loc)) {
      this.placedBlocks.remove(loc);
      return;
    }

    // Natural block — restore after the configured time
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
    }, naturalRestoreTicks);
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
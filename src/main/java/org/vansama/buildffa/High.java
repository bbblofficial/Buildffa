package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class High implements Listener {
  private JavaPlugin plugin;
  private double highLimit;

  private final Map<UUID, Long> lastMessageTime = new HashMap<UUID, Long>();
  private static final long MESSAGE_COOLDOWN_MS = 1000L;

  // If high-limit in config.yml is below this, it's almost certainly a
  // mistake (e.g. someone accidentally saved 0.0) and would block every
  // non-OP player from building anywhere on the map. Fall back to the
  // default in that case instead of bricking the whole map.
  private static final double MIN_HIGH_LIMIT = 5.0D;
  private static final double DEFAULT_HIGH_LIMIT = 100.0D;

  public High(JavaPlugin plugin) {
    this.plugin = plugin;
    loadConfiguration();
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }

  private void loadConfiguration() {
    FileConfiguration config = this.plugin.getConfig();
    double value = config.getDouble("high-limit", DEFAULT_HIGH_LIMIT);

    if (value < MIN_HIGH_LIMIT) {
      this.plugin.getLogger().warning(
          "high-limit is set to " + value + " in config.yml — that would block "
              + "building almost everywhere. Using " + DEFAULT_HIGH_LIMIT + " instead. "
              + "Fix it with /buildffa sethighlimit <y> or edit config.yml directly.");
      this.highLimit = DEFAULT_HIGH_LIMIT;

      // Write the corrected value back so the bad value doesn't keep
      // re-triggering this warning (and so /buildffa reload doesn't
      // silently re-read the broken number) on every server restart.
      config.set("high-limit", Double.valueOf(DEFAULT_HIGH_LIMIT));
      this.plugin.saveConfig();
    } else {
      this.highLimit = value;
    }

    this.plugin.getLogger().info("BuildFFA high-limit loaded: Y >= " + this.highLimit + " is restricted");
  }

  public void reloadConfig() {
    loadConfiguration();
  }

  public double getHighLimit() {
    return this.highLimit;
  }

  private void sendMessageOnce(Player player, String message) {
    UUID id = player.getUniqueId();
    long now = System.currentTimeMillis();
    long last = this.lastMessageTime.containsKey(id) ? this.lastMessageTime.get(id).longValue() : 0L;
    if (now - last < MESSAGE_COOLDOWN_MS) return;
    this.lastMessageTime.put(id, Long.valueOf(now));
    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
  }

  /**
   * Bypass only for:
   *   - Creative mode
   *   - OP players
   *   - Players explicitly granted "buildffa.highlimit.bypass" (admin-only)
   */
  private boolean shouldBypass(Player player) {
    if (player.getGameMode() == GameMode.CREATIVE) return true;
    if (player.isOp()) return true;
    if (player.hasPermission("buildffa.highlimit.bypass")) return true;
    return false;
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    if (shouldBypass(player)) return;

    Location blockLoc = event.getBlockPlaced().getLocation();
    if (blockLoc.getY() >= this.highLimit) {
      event.setCancelled(true);
      sendMessageOnce(player, "&cYou cannot place blocks here!");
    }
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (shouldBypass(player)) return;

    Location blockLoc = event.getBlock().getLocation();
    if (blockLoc.getY() >= this.highLimit) {
      event.setCancelled(true);
      sendMessageOnce(player, "&cYou cannot break blocks here!");
    }
  }
}
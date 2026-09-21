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
  private String bypassPermission;

  private final Map<UUID, Long> lastMessageTime = new HashMap<UUID, Long>();
  private static final long MESSAGE_COOLDOWN_MS = 1000L;

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

    this.bypassPermission = config.getString("permissions.highlimit-bypass", "buildffa.highlimit.bypass");

    if (value < MIN_HIGH_LIMIT) {
      this.plugin.getLogger().warning("high-limit is set to " + value + " — too low! Auto-fixing to " + DEFAULT_HIGH_LIMIT);
      this.highLimit = DEFAULT_HIGH_LIMIT;

      config.set("high-limit", DEFAULT_HIGH_LIMIT);
      this.plugin.saveConfig();
    } else {
      this.highLimit = value;
    }

    this.plugin.getLogger().info("BuildFFA high-limit loaded: Y >= " + this.highLimit + " is restricted");
  }

  public void reloadConfig() {
    loadConfiguration();
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
   *   - Players with explicit bypass permission
   *   - Players in Build Mode (toggled with /buildffa buildmode)
   *
   * OP alone does NOT bypass — they must enable buildmode.
   */
  private boolean shouldBypass(Player player) {
    if (player.getGameMode() == GameMode.CREATIVE) return true;

    if (this.bypassPermission != null && !this.bypassPermission.isEmpty()
            && player.hasPermission(this.bypassPermission)) {
      return true;
    }

    if (BuildModeManager.isInBuildMode(player)) {
      return true;
    }

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
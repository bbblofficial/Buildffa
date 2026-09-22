package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class SpawnManager implements Listener {

  private final JavaPlugin plugin;

  public SpawnManager(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }

  public Location getSpawn() {
    FileConfiguration config = this.plugin.getConfig();
    if (!config.contains("spawn.world")) {
      return null;
    }
    String worldName = config.getString("spawn.world");
    World world = Bukkit.getWorld(worldName);
    if (world == null) {
      return null;
    }
    double x = config.getDouble("spawn.x");
    double y = config.getDouble("spawn.y");
    double z = config.getDouble("spawn.z");
    float yaw = (float) config.getDouble("spawn.yaw");
    float pitch = (float) config.getDouble("spawn.pitch");
    return new Location(world, x, y, z, yaw, pitch);
  }

  public void setSpawn(Location loc) {
    FileConfiguration config = this.plugin.getConfig();
    config.set("spawn.world", loc.getWorld().getName());
    config.set("spawn.x", Double.valueOf(loc.getX()));
    config.set("spawn.y", Double.valueOf(loc.getY()));
    config.set("spawn.z", Double.valueOf(loc.getZ()));
    config.set("spawn.yaw", Float.valueOf(loc.getYaw()));
    config.set("spawn.pitch", Float.valueOf(loc.getPitch()));
    this.plugin.saveConfig();
  }

  // ============================================================
  //  RESPAWN — force spawn location  // ============================================================
  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    Location spawn = getSpawn();
    if (spawn != null) {
      event.setRespawnLocation(spawn);
    }

    final Player player = event.getPlayer();
    Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (!player.isOnline()) return;
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
      }
    }, 1L);
  }

  // ============================================================
  //  JOIN — teleport to spawn 1 tick later
  // ============================================================
  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(final PlayerJoinEvent event) {
    final Location spawn = getSpawn();
    if (spawn == null) return;

    Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        Player player = event.getPlayer();
        if (player.isOnline()) {
          player.teleport(spawn);
        }
      }
    }, 1L);
  }
}
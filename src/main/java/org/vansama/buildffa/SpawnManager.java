package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class SpawnManager implements Listener {
  
  private final JavaPlugin plugin;
  
  public SpawnManager(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  /**
   * Get the saved spawn Location from config.
   * Returns null if not set.
   */
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
  
  /**
   * Save a Location as the spawn point.
   */
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
  
  /**
   * Force a player to respawn at the custom spawn location.
   * Uses PlayerRespawnEvent.setRespawnLocation() which is available in 1.8.8.
   */
  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    Location spawn = getSpawn();
    if (spawn != null) {
      event.setRespawnLocation(spawn);
    }
  }
}
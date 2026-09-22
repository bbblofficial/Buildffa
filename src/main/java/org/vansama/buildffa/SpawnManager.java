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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class SpawnManager implements Listener {

  private final JavaPlugin plugin;

  public SpawnManager(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }

  // ============================================================
  //  GET SPAWN
  // ============================================================
  public Location getSpawn() {
    FileConfiguration config = this.plugin.getConfig();
    if (!config.contains("spawn.world")) {
      return null;
    }
    String worldName = config.getString("spawn.world");
    if (worldName == null) return null;

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

  // ============================================================
  //  SET SPAWN
  // ============================================================
  public void setSpawn(Location loc) {
    if (loc == null || loc.getWorld() == null) return;

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
  //  JOIN — teleport to spawn 1 tick later
  //
  //  NOTE: Respawn handling is intentionally NOT here.
  //  KitRestore.onPlayerRespawn() owns the respawn flow
  //  (auto-respawn, heal, kit restore, teleport).
  //  Keeping it in one place avoids double-heal / double-teleport.
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
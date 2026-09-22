package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class KitRestore implements Listener {

  private final JavaPlugin plugin;
  private final Equip equip;
  private final KitEditor kitEditor;

  public KitRestore(JavaPlugin plugin, Equip equip, KitEditor kitEditor) {
    this.plugin = plugin;
    this.equip = equip;
    this.kitEditor = kitEditor;
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }

  // ============================================================
  //  ON RESPAWN — teleport to spawn + full heal + kit restore
  // ============================================================
  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    final Player player = event.getPlayer();

    // 1) Force the respawn location to the configured spawn
    Location spawn = getSpawnLocation();
    if (spawn != null) {
      event.setRespawnLocation(spawn);
    }

    // 2) One tick later: heal + reset inventory + give kit
    Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (!player.isOnline()) return;
        if (kitEditor.isEditing(player)) return;

        // Full heal
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setLevel(0);
        player.setExp(0.0F);

        // Clear inventory + armor + cursor
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setItemOnCursor(null);

        // Give kit (custom kit if exists, otherwise default)
        equip.giveDiamondArmor(player);

        // Ensure they're actually at spawn (some plugins override respawn)
        Location spawn = getSpawnLocation();
        if (spawn != null) {
          player.teleport(spawn);
        }
      }
    }, 1L);
  }

  // ============================================================
  //  ON DEATH — clear drops and XP
  // ============================================================
  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerDeath(PlayerDeathEvent event) {
    event.getDrops().clear();
    event.setDroppedExp(0);
    event.setDeathMessage(null);
  }

  // ============================================================
  //  /clear → restore kit + heal + teleport to spawn
  // ============================================================
  @EventHandler
  public void onCommand(PlayerCommandPreprocessEvent event) {
    String message = event.getMessage().toLowerCase();
    if (message.startsWith("/clear") || message.contains(" clear ")) {
      final Player player = event.getPlayer();

      Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
        @Override
        public void run() {
          if (!kitEditor.isEditing(player) && player.isOnline()
              && player.getGameMode() != GameMode.CREATIVE) {
            if (isEmpty(player)) {
              // Heal
              player.setHealth(player.getMaxHealth());
              player.setFoodLevel(20);
              player.setSaturation(20.0F);
              player.setExhaustion(0.0F);
              player.setFireTicks(0);
              player.setFallDistance(0.0F);

              // Give kit
              equip.giveDiamondArmor(player);

              player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                  "&aYour kit has been restored after /clear."));
            }
          }
        }
      }, 5L);
    }
  }

  // ============================================================
  //  Helpers
  // ============================================================
  private Location getSpawnLocation() {
    if (!this.plugin.getConfig().contains("spawn.world")) return null;
    String worldName = this.plugin.getConfig().getString("spawn.world");
    if (worldName == null) return null;

    World world = Bukkit.getWorld(worldName);
    if (world == null) return null;

    double x = this.plugin.getConfig().getDouble("spawn.x");
    double y = this.plugin.getConfig().getDouble("spawn.y");
    double z = this.plugin.getConfig().getDouble("spawn.z");
    float yaw = (float) this.plugin.getConfig().getDouble("spawn.yaw");
    float pitch = (float) this.plugin.getConfig().getDouble("spawn.pitch");

    return new Location(world, x, y, z, yaw, pitch);
  }

  private boolean isEmpty(Player player) {
    if (player.getInventory().getHelmet() != null) return false;
    if (player.getInventory().getChestplate() != null) return false;
    if (player.getInventory().getLeggings() != null) return false;
    if (player.getInventory().getBoots() != null) return false;
    for (ItemStack item : player.getInventory().getContents()) {
      if (item != null && item.getType() != Material.AIR) {
        return false;
      }
    }
    return true;
  }
}
package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class YPvP implements Listener {
  
  private JavaPlugin plugin;
  private double yPvPLimit;
  private boolean enabled;
  private String bypassPermission;
  
  public YPvP(JavaPlugin plugin) {
    this.plugin = plugin;
    loadConfiguration();
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  private void loadConfiguration() {
    FileConfiguration config = this.plugin.getConfig();
    this.enabled = config.getBoolean("ypvp.enabled", false);
    this.yPvPLimit = config.getDouble("ypvp.y-level", 150.0D);
    this.bypassPermission = config.getString("permissions.ypvp-bypass", "buildffa.ypvp.bypass");
    
    this.plugin.getLogger().info("BuildFFA ypvp loaded: " + (this.enabled ? "ENABLED at Y >= " + this.yPvPLimit : "DISABLED"));
  }
  
  public void reloadConfig() {
    loadConfiguration();
  }
  
  /**
   * Check if a player is above the YPvP limit (and YPvP is enabled).
   */
  private boolean isAboveLimit(Player player) {
    if (!this.enabled) return false;
    return player.getLocation().getY() >= this.yPvPLimit;
  }
  
  /**
   * Check if a player has bypass permission.
   */
  private boolean shouldBypass(Player player) {
    if (this.bypassPermission != null && !this.bypassPermission.isEmpty() && player.hasPermission(this.bypassPermission)) {
      return true;
    }
    return false;
  }
  
  /**
   * Cancel PvP damage if attacker OR victim is above the YPvP limit.
   * This handles melee, bow, snowball, egg, etc. (all EntityDamageByEntityEvent).
   */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    if (!this.enabled) return;
    
    // Only care about player vs player
    if (!(event.getEntity() instanceof Player)) return;
    
    Player victim = (Player) event.getEntity();
    Player attacker = null;
    
    // Direct melee hit
    if (event.getDamager() instanceof Player) {
      attacker = (Player) event.getDamager();
    }
    // Projectile (arrow, snowball, egg) — get shooter
    else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
      org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
      if (proj.getShooter() instanceof Player) {
        attacker = (Player) proj.getShooter();
      }
    }
    
    if (attacker == null) return;
    
    // Bypass for OP/permission players
    if (shouldBypass(attacker) && shouldBypass(victim)) return;
    
    // Cancel if attacker is above limit (can't hit anyone)
    if (isAboveLimit(attacker) && !shouldBypass(attacker)) {
      event.setCancelled(true);
      return;
    }
    
    // Cancel if victim is above limit (can't be hit by anyone)
    if (isAboveLimit(victim) && !shouldBypass(victim)) {
      event.setCancelled(true);
      return;
    }
  }
  
  // ============================================================
  //  Public API for command usage
  // ============================================================
  
  public boolean isEnabled() {
    return this.enabled;
  }
  
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    FileConfiguration config = this.plugin.getConfig();
    config.set("ypvp.enabled", Boolean.valueOf(enabled));
    this.plugin.saveConfig();
  }
  
  public double getYPvPLimit() {
    return this.yPvPLimit;
  }
  
  public void setYPvPLimit(double y) {
    this.yPvPLimit = y;
    FileConfiguration config = this.plugin.getConfig();
    config.set("ypvp.y-level", Double.valueOf(y));
    this.plugin.saveConfig();
  }
}
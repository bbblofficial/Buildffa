package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class YPvP implements Listener {

    private JavaPlugin plugin;
    private double yPvPLimit;
    private boolean enabled;
    private String bypassPermission;
    private boolean blockProjectiles;

    private final Map<UUID, Long> lastMessageTime = new HashMap<UUID, Long>();
    private static final long MESSAGE_COOLDOWN = 1500L;

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
        this.blockProjectiles = config.getBoolean("ypvp.block-projectiles", true);

        this.plugin.getLogger().info("BuildFFA ypvp loaded: " +
                (this.enabled ? "ENABLED at Y >= " + this.yPvPLimit : "DISABLED"));
    }

    public void reloadConfig() {
        loadConfiguration();
    }

    private boolean isAboveLimit(Player player) {
        if (!this.enabled) return false;
        if (player == null || !player.isOnline()) return false;
        return player.getLocation().getY() >= this.yPvPLimit;
    }

    private boolean shouldBypass(Player player) {
        if (player == null) return true;
        if (this.bypassPermission != null && !this.bypassPermission.isEmpty()
                && player.hasPermission(this.bypassPermission)) {
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamageLowest(EntityDamageByEntityEvent event) {
        applyProtection(event, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamageHighest(EntityDamageByEntityEvent event) {
        applyProtection(event, false);
    }

    private void applyProtection(EntityDamageByEntityEvent event, boolean firstPass) {
        if (!this.enabled) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = getAttacker(event.getDamager());

        if (attacker == null) {
            if (!shouldBypass(victim) && isAboveLimit(victim)) {
                event.setCancelled(true);
                event.setDamage(0);
            }
            return;
        }

        boolean attackerBypass = shouldBypass(attacker);
        boolean victimBypass = shouldBypass(victim);

        if (attackerBypass && victimBypass) return;

        boolean shouldBlock = false;

        if (!attackerBypass && isAboveLimit(attacker)) {
            shouldBlock = true;
        } else if (!victimBypass && isAboveLimit(victim)) {
            shouldBlock = true;
        }

        if (!shouldBlock) return;

        event.setCancelled(true);
        event.setDamage(0);

        if (firstPass && attacker != null) {
            sendMessage(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!this.enabled) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (event instanceof EntityDamageByEntityEvent) return;

        Player victim = (Player) event.getEntity();
        if (shouldBypass(victim)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            if (isAboveLimit(victim)) {
                event.setCancelled(true);
                event.setDamage(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!this.enabled) return;
        if (!this.blockProjectiles) return;

        Projectile proj = event.getEntity();
        if (!(proj.getShooter() instanceof Player)) return;

        Player shooter = (Player) proj.getShooter();
        if (shouldBypass(shooter)) return;

        if (isAboveLimit(shooter)) {
            event.setCancelled(true);
            sendMessage(shooter);
        }
    }

    private Player getAttacker(Entity damager) {
        if (damager == null) return null;

        if (damager instanceof Player) {
            return (Player) damager;
        }

        if (damager instanceof Projectile) {
            Projectile proj = (Projectile) damager;
            if (proj.getShooter() instanceof Player) {
                return (Player) proj.getShooter();
            }
        }

        return null;
    }

    private void sendMessage(Player player) {
        if (player == null || !player.isOnline()) return;

        long now = System.currentTimeMillis();
        long last = this.lastMessageTime.containsKey(player.getUniqueId())
                ? this.lastMessageTime.get(player.getUniqueId()).longValue() : 0L;

        if (now - last < MESSAGE_COOLDOWN) return;
        this.lastMessageTime.put(player.getUniqueId(), Long.valueOf(now));

        player.sendMessage("");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "  &6&lYPvP"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "  &7You cannot PvP above &eY=" + (int) this.yPvPLimit));
        player.sendMessage("");
    }

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
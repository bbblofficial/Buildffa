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

    private final Map<UUID, Double> lastHealth = new HashMap<UUID, Double>();

    public YPvP(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
        Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
        startHealthTracker();
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

    private void startHealthTracker() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, new Runnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    lastHealth.put(p.getUniqueId(), p.getHealth());
                }
            }
        }, 1L, 1L);
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
                if (!firstPass) restoreHealth(victim);
            }
            return;
        }

        boolean attackerBypass = shouldBypass(attacker);
        boolean victimBypass = shouldBypass(victim);

        if (attackerBypass && victimBypass) return;

        boolean shouldBlock = false;
        String reason = null;

        if (!attackerBypass && isAboveLimit(attacker)) {
            shouldBlock = true;
            reason = "&cYou cannot hit players while above Y=" + (int) this.yPvPLimit + "!";
        } else if (!victimBypass && isAboveLimit(victim)) {
            shouldBlock = true;
            reason = "&cThat player is above the YPvP limit!";
        }

        if (!shouldBlock) return;

        event.setCancelled(true);
        event.setDamage(0);

        if (firstPass && attacker != null) {
            sendMessage(attacker, reason);
        }

        if (!firstPass) {
            restoreHealth(victim);
        }
    }

    private void restoreHealth(final Player victim) {
        final UUID id = victim.getUniqueId();
        final Double previous = this.lastHealth.get(id);

        if (previous == null) return;

        if (victim.getHealth() < previous.doubleValue()) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
                @Override
                public void run() {
                    if (victim.isOnline() && victim.getHealth() > 0) {
                        double max = victim.getMaxHealth();
                        double restored = Math.min(previous.doubleValue(), max);
                        if (victim.getHealth() < restored) {
                            victim.setHealth(restored);
                        }
                    }
                }
            }, 1L);
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
                restoreHealth(victim);
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
            sendMessage(shooter, "&cYou cannot shoot projectiles above Y=" + (int) this.yPvPLimit + "!");
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

    private void sendMessage(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
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
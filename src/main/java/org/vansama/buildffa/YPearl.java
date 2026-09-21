package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class YPearl implements Listener {

    private JavaPlugin plugin;
    private double yPearlLimit;
    private boolean enabled;
    private String bypassPermission;
    private String message;

    private final Map<UUID, Long> lastMessageTime = new HashMap<UUID, Long>();
    private static final long MESSAGE_COOLDOWN = 1500L;

    public YPearl(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
        Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    private void loadConfiguration() {
        FileConfiguration config = this.plugin.getConfig();
        this.enabled = config.getBoolean("ypearl.enabled", true);
        this.yPearlLimit = config.getDouble("ypearl.y-level", 61.5D);
        this.bypassPermission = config.getString("permissions.ypearl-bypass", "buildffa.ypearl.bypass");

        String msg = config.getString("ypearl.message", "&cYou cannot throw pearls here!");
        if (msg == null) msg = "";
        this.message = msg;

        this.plugin.getLogger().info("BuildFFA ypearl loaded: " +
                (this.enabled ? "ENABLED at Y >= " + this.yPearlLimit : "DISABLED"));
    }

    public void reloadConfig() {
        loadConfiguration();
    }

    private boolean shouldBypass(Player player) {
        if (player == null) return true;
        if (this.bypassPermission != null && !this.bypassPermission.isEmpty()
                && player.hasPermission(this.bypassPermission)) {
            return true;
        }
        return false;
    }

    // ============================================================
    //  LET THE PEARL FLY — track it every tick.
    //  The moment it crosses above the Y limit, delete it and
    //  send the configured message.
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!this.enabled) return;
        if (!(event.getEntity() instanceof EnderPearl)) return;
        if (!(event.getEntity().getShooter() instanceof Player)) return;

        final EnderPearl pearl = (EnderPearl) event.getEntity();
        final Player shooter = (Player) pearl.getShooter();

        if (shouldBypass(shooter)) return;

        // If the player is already above the limit, delete instantly
        if (shooter.getLocation().getY() >= this.yPearlLimit) {
            pearl.remove();
            sendMessage(shooter);
            return;
        }

        // Otherwise track the pearl while it flies
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pearl.isDead() || !pearl.isValid()) {
                    cancel();
                    return;
                }

                if (pearl.getLocation().getY() >= yPearlLimit) {
                    pearl.remove();
                    sendMessage(shooter);
                    cancel();
                }
            }
        }.runTaskTimer(this.plugin, 1L, 1L);
    }

    // ============================================================
    //  SAFETY NET: cancel the actual ender-pearl teleport if the
    //  tracker somehow missed a tick.
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (!this.enabled) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;

        Player player = event.getPlayer();
        if (shouldBypass(player)) return;

        if (event.getTo().getY() >= this.yPearlLimit) {
            event.setCancelled(true);
            sendMessage(player);
        }
    }

    private void sendMessage(final Player player) {
        if (player == null || !player.isOnline()) return;

        // If the admin disabled the message, don't send anything
        if (this.message == null || this.message.isEmpty()) return;

        long now = System.currentTimeMillis();
        long last = this.lastMessageTime.containsKey(player.getUniqueId())
                ? this.lastMessageTime.get(player.getUniqueId()).longValue() : 0L;

        if (now - last < MESSAGE_COOLDOWN) return;
        this.lastMessageTime.put(player.getUniqueId(), Long.valueOf(now));

        String out = this.message.replace("%y%", String.valueOf((int) this.yPearlLimit));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', out));
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        FileConfiguration config = this.plugin.getConfig();
        config.set("ypearl.enabled", Boolean.valueOf(enabled));
        this.plugin.saveConfig();
    }

    public double getYPearlLimit() {
        return this.yPearlLimit;
    }

    public void setYPearlLimit(double y) {
        this.yPearlLimit = y;
        FileConfiguration config = this.plugin.getConfig();
        config.set("ypearl.y-level", Double.valueOf(y));
        this.plugin.saveConfig();
    }
}
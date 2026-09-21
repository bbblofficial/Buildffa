package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.entity.EnderPearl;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class YPearl implements Listener {

    private JavaPlugin plugin;
    private double yPearlLimit;
    private boolean enabled;
    private String bypassPermission;

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

        this.plugin.getLogger().info("BuildFFA ypearl loaded: " +
                (this.enabled ? "ENABLED at Y >= " + this.yPearlLimit : "DISABLED"));
    }

    public void reloadConfig() {
        loadConfiguration();
    }

    private boolean isAboveLimit(Player player) {
        if (!this.enabled) return false;
        if (player == null || !player.isOnline()) return false;
        return player.getLocation().getY() >= this.yPearlLimit;
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
    //  BLOCK ENDER PEARL THROW ABOVE Y LIMIT
    //  The pearl is consumed and the player is told they can't.
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!this.enabled) return;
        if (!(event.getEntity() instanceof EnderPearl)) return;
        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player shooter = (Player) event.getEntity().getShooter();
        if (shouldBypass(shooter)) return;

        if (isAboveLimit(shooter)) {
            // Remove the pearl entirely — exactly like the request says
            event.setCancelled(true);
            // Manually remove the projectile entity in case the event
            // was already fired and the pearl is in the world.
            event.getEntity().remove();

            sendMessage(shooter);
        }
    }

    private void sendMessage(Player player) {
        if (player == null || !player.isOnline()) return;

        long now = System.currentTimeMillis();
        long last = this.lastMessageTime.containsKey(player.getUniqueId())
                ? this.lastMessageTime.get(player.getUniqueId()).longValue() : 0L;

        if (now - last < MESSAGE_COOLDOWN) return;
        this.lastMessageTime.put(player.getUniqueId(), Long.valueOf(now));

        player.sendMessage("");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "  &6&lYPearl"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "  &7You cannot throw Ender Pearls above &eY=" + (int) this.yPearlLimit));
        player.sendMessage("");
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
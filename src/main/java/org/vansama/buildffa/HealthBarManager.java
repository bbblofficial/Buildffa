package org.vansama.buildffa;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ⚠️  DISABLED BY DEFAULT.
 *
 * This class used to show the player's OWN HP in the ActionBar.
 * It has been fully disabled because you now use {@link EnemyHealthBar}
 * for all PvP-related health display.
 *
 * To re-enable, uncomment the line in BuildFFA.onEnable() and set
 * healthbar.enabled: true in config.yml.
 */
public class HealthBarManager implements Listener {

    private final JavaPlugin plugin;

    private String nmsVersion;
    private Class<?> craftPlayerClass;
    private Class<?> chatComponentClass;
    private Class<?> packetChatClass;
    private Class<?> iChatBaseClass;
    private Class<?> packetClass;
    private Constructor<?> packetChatConstructor;
    private Method getHandleMethod;
    private Method sendPacketMethod;

    private boolean nmsReady = false;
    private int taskId = -1;

    public HealthBarManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupNMS();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTask();
    }

    private void setupNMS() {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            this.nmsVersion = packageName.substring(packageName.lastIndexOf('.') + 1);

            this.craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".entity.CraftPlayer");
            this.chatComponentClass = Class.forName("net.minecraft.server." + nmsVersion + ".ChatComponentText");
            this.packetChatClass = Class.forName("net.minecraft.server." + nmsVersion + ".PacketPlayOutChat");
            this.iChatBaseClass = Class.forName("net.minecraft.server." + nmsVersion + ".IChatBaseComponent");
            this.packetClass = Class.forName("net.minecraft.server." + nmsVersion + ".Packet");

            this.packetChatConstructor = packetChatClass.getConstructor(iChatBaseClass, byte.class);
            this.getHandleMethod = craftPlayerClass.getMethod("getHandle");
            this.sendPacketMethod = null;

            this.nmsReady = true;
        } catch (Throwable t) {
            this.nmsReady = false;
            plugin.getLogger().warning("HealthBar NMS setup failed: " + t.getMessage());
        }
    }

    private void startTask() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
        }

        int interval = plugin.getConfig().getInt("healthbar.update-interval", 5);
        if (interval < 1) interval = 5;

        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("healthbar.enabled", false)) return;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isDead()) continue;
                    sendHealthBar(player);
                }
            }
        }, interval, interval);
    }

    private void sendHealthBar(Player player) {
        String message = buildMessage(player);
        if (message == null || message.isEmpty()) return;
        sendActionBar(player, message);
    }

    private String buildMessage(Player player) {
        double health = player.getHealth();
        double maxHealth = player.getMaxHealth();

        if (health < 0) health = 0;
        if (health > maxHealth) health = maxHealth;

        String mode = plugin.getConfig().getString("healthbar.mode", "NUMERIC");
        if (mode == null) mode = "NUMERIC";

        if (mode.equalsIgnoreCase("NUMERIC")) {
            String format = plugin.getConfig().getString("healthbar.format", "&c❤ &f%current%&7/&f%max%");
            return colorize(format
                    .replace("%current%", formatNumber(health))
                    .replace("%max%", formatNumber(maxHealth))
                    .replace("%player%", player.getName()));
        }

        if (mode.equalsIgnoreCase("HEARTS")) {
            int totalHearts = (int) Math.ceil(maxHealth / 2.0);
            int filledHearts = (int) Math.ceil(health / 2.0);
            int emptyHearts = totalHearts - filledHearts;
            if (emptyHearts < 0) emptyHearts = 0;

            String filledColor = plugin.getConfig().getString("healthbar.filled-color", "&c");
            String emptyColor = plugin.getConfig().getString("healthbar.empty-color", "&7");
            String heartChar = plugin.getConfig().getString("healthbar.heart-char", "❤");

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < filledHearts; i++) sb.append(filledColor).append(heartChar);
            for (int i = 0; i < emptyHearts; i++) sb.append(emptyColor).append(heartChar);
            sb.append(" &f").append(formatNumber(health)).append("&7/&f").append(formatNumber(maxHealth));

            return colorize(sb.toString());
        }

        if (mode.equalsIgnoreCase("PERCENT")) {
            double percent = (health / maxHealth) * 100.0;
            String format = plugin.getConfig().getString("healthbar.format", "&c❤ &f%percent%% &7(&f%current%&7/&f%max%&7)");
            return colorize(format
                    .replace("%percent%", String.format("%.0f", percent))
                    .replace("%current%", formatNumber(health))
                    .replace("%max%", formatNumber(maxHealth)));
        }

        return colorize("&c❤ &f" + formatNumber(health) + "&7/&f" + formatNumber(maxHealth));
    }

    private String formatNumber(double d) {
        if (d == Math.floor(d)) return String.valueOf((int) d);
        return String.format("%.1f", d);
    }

    private void sendActionBar(Player player, String message) {
        try {
            Method sendActionBar = player.getClass().getMethod("sendActionBar", String.class);
            sendActionBar.invoke(player, message);
            return;
        } catch (Throwable ignored) {}

        if (!this.nmsReady) {
            player.sendMessage(message);
            return;
        }

        try {
            Object craftPlayer = craftPlayerClass.cast(player);
            Object entityPlayer = getHandleMethod.invoke(craftPlayer);
            Object playerConnection = entityPlayer.getClass()
                    .getField("playerConnection").get(entityPlayer);

            Object chatComponent = chatComponentClass
                    .getConstructor(String.class).newInstance(message);

            Object packet = packetChatConstructor.newInstance(chatComponent, (byte) 2);

            if (this.sendPacketMethod == null) {
                this.sendPacketMethod = playerConnection.getClass()
                        .getMethod("sendPacket", packetClass);
            }

            sendPacketMethod.invoke(playerConnection, packet);
        } catch (Throwable t) {
            // Silent fail
        }
    }

    public void reloadConfig() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
            this.taskId = -1;
        }
        startTask();
    }

    public void shutdown() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
            this.taskId = -1;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // nothing to clean up
    }

    private String colorize(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
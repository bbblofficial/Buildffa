package org.vansama.buildffa;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Displays HP below each player's name using DisplaySlot.BELOW_NAME.
 *
 * ✅ Sends integer value (compatible with Lunar / Feather / vanilla)
 * ✅ Uses PacketPlayOutScoreboardObjective + PacketPlayOutScoreboardScore
 * ✅ Per-viewer scoreboard (no conflict with sidebar)
 */
public class NametagManager implements Listener {

    private final JavaPlugin plugin;

    // NMS handles
    private String nmsVersion;
    private Class<?> craftPlayerClass;
    private Class<?> scoreboardClass;
    private Class<?> scoreboardObjectiveClass;
    private Class<?> scoreboardScoreClass;
    private Class<?> packetObjectiveClass;
    private Class<?> packetDisplayObjectiveClass;
    private Class<?> packetScoreClass;
    private Class<?> enumScoreboardActionClass;
    private Class<?> iScoreboardCriteriaClass;

    private Constructor<?> packetObjectiveConstructor;
    private Constructor<?> packetDisplayConstructor;
    private Constructor<?> packetScoreConstructor;

    private boolean nmsReady = false;

    private int taskId = -1;

    public NametagManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupNMS();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTask();
    }

    // ============================================================
    //  NMS SETUP
    // ============================================================
    private void setupNMS() {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            this.nmsVersion = packageName.substring(packageName.lastIndexOf('.') + 1);

            this.craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".entity.CraftPlayer");
            this.scoreboardClass = Class.forName("net.minecraft.server." + nmsVersion + ".Scoreboard");
            this.scoreboardObjectiveClass = Class.forName("net.minecraft.server." + nmsVersion + ".ScoreboardObjective");
            this.scoreboardScoreClass = Class.forName("net.minecraft.server." + nmsVersion + ".ScoreboardScore");

            this.packetObjectiveClass = Class.forName("net.minecraft.server." + nmsVersion + ".PacketPlayOutScoreboardObjective");
            this.packetDisplayObjectiveClass = Class.forName("net.minecraft.server." + nmsVersion + ".PacketPlayOutScoreboardDisplayObjective");
            this.packetScoreClass = Class.forName("net.minecraft.server." + nmsVersion + ".PacketPlayOutScoreboardScore");
            this.enumScoreboardActionClass = Class.forName("net.minecraft.server." + nmsVersion + ".ScoreboardScore$EnumScoreboardAction");
            this.iScoreboardCriteriaClass = Class.forName("net.minecraft.server." + nmsVersion + ".IScoreboardCriteria");

            this.packetObjectiveConstructor = packetObjectiveClass.getConstructor(scoreboardObjectiveClass, int.class);
            this.packetDisplayConstructor = packetDisplayObjectiveClass.getConstructor(int.class, scoreboardObjectiveClass);
            this.packetScoreConstructor = packetScoreClass.getConstructor(String.class, scoreboardObjectiveClass, int.class, enumScoreboardActionClass);

            this.nmsReady = true;
        } catch (Throwable t) {
            this.nmsReady = false;
            plugin.getLogger().warning("Nametag NMS setup failed: " + t.getMessage());
        }
    }

    // ============================================================
    //  UPDATE TASK
    // ============================================================
    private void startTask() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
        }

        int interval = plugin.getConfig().getInt("nametag.update-interval", 10);
        if (interval < 1) interval = 10;

        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("nametag.enabled", true)) return;
                if (!nmsReady) return;

                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    updateViewer(viewer);
                }
            }
        }, interval, interval);
    }

    // ============================================================
    //  UPDATE VIEWER — send health as INTEGER
    // ============================================================
    private void updateViewer(Player viewer) {
        try {
            // Create a fresh scoreboard for this viewer
            Object scoreboard = scoreboardClass.newInstance();

            // Create objective with "health" criteria
            Object healthCriteria = iScoreboardCriteriaClass.getField("b").get(null); // "health" criteria
            Constructor<?> objCons = scoreboardObjectiveClass.getConstructor(
                    scoreboardClass, String.class, iScoreboardCriteriaClass);
            Object objective = objCons.newInstance(scoreboard, "bffa_hp", healthCriteria);

            // Set display name (the text next to the number)
            String displayName = plugin.getConfig().getString("nametag.display-name", "");
            if (displayName != null && !displayName.isEmpty()) {
                scoreboardObjectiveClass.getMethod("setDisplayName", String.class)
                        .invoke(objective, colorize(displayName));
            }

            // Send objective packet (mode 0 = create)
            Object objPacket = packetObjectiveConstructor.newInstance(objective, 0);
            sendPacket(viewer, objPacket);

            // Display objective below name (slot 2)
            Object displayPacket = packetDisplayConstructor.newInstance(2, objective);
            sendPacket(viewer, displayPacket);

            // Update scores with INTEGER values
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.isDead()) continue;
                int health = (int) Math.ceil(target.getHealth());
                if (health < 0) health = 0;

                Object score = packetScoreConstructor.newInstance(
                        target.getName(), objective, health, getActionEnum("CHANGE"));
                sendPacket(viewer, score);
            }
        } catch (Throwable t) {
            // silent
        }
    }

    private Object getActionEnum(String name) {
        try {
            for (Object constant : enumScoreboardActionClass.getEnumConstants()) {
                if (constant.toString().equalsIgnoreCase(name)) return constant;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ============================================================
    //  SEND PACKET
    // ============================================================
    private void sendPacket(Player player, Object packet) {
        try {
            Object craftPlayer = craftPlayerClass.cast(player);
            Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
            Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);

            Class<?> packetClass = Class.forName("net.minecraft.server." + nmsVersion + ".Packet");
            Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", packetClass);
            sendPacketMethod.invoke(playerConnection, packet);
        } catch (Throwable ignored) {}
    }

    // ============================================================
    //  JOIN / QUIT
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player joined = event.getPlayer();
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!joined.isOnline()) return;
                if (!plugin.getConfig().getBoolean("nametag.enabled", true)) return;
                if (!nmsReady) return;

                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    updateViewer(viewer);
                }
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // nothing to clean
    }

    public void reloadConfig() {
        startTask();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateViewer(viewer);
        }
    }

    public void shutdown() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
            this.taskId = -1;
        }
    }

    private String colorize(String msg) {
        if (msg == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }
}
package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Displays live HP below each player's name using a Scoreboard Objective
 * with DisplaySlot.BELOW_NAME.
 *
 * ✅ Uses pure Bukkit API — no NMS, no PacketPlayOutScoreboardTeam
 * ✅ Uses a SEPARATE scoreboard per viewer for BELOW_NAME only
 * ✅ Does NOT touch team prefix/suffix → no "22 > 16" disconnect
 * ✅ Works reliably on 1.8.8 (Carbon, Paper, Spigot)
 */
public class NametagManager implements Listener {

    private final JavaPlugin plugin;

    // viewer UUID -> their personal scoreboard (used ONLY for nametag HP)
    private final Map<UUID, Scoreboard> viewerBoards = new HashMap<UUID, Scoreboard>();

    private int taskId = -1;

    public NametagManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTask();
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
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    updateViewer(viewer);
                }
            }
        }, interval, interval);
    }

    // ============================================================
    //  UPDATE ONE VIEWER
    //  Creates (if needed) a personal scoreboard for this viewer
    //  and updates the BELOW_NAME objective with each player's HP.
    // ============================================================
    private void updateViewer(Player viewer) {
        try {
            // Get or create viewer's personal scoreboard
            Scoreboard board = this.viewerBoards.get(viewer.getUniqueId());
            if (board == null) {
                board = Bukkit.getScoreboardManager().getNewScoreboard();
                this.viewerBoards.put(viewer.getUniqueId(), board);
                viewer.setScoreboard(board);
            }

            // Get or create the health objective
            Objective objective = board.getObjective("bffa_hp");
            if (objective == null) {
                objective = board.registerNewObjective("bffa_hp", "health");
                objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
                objective.setDisplayName(colorize(getDisplayName()));
            } else {
                objective.setDisplayName(colorize(getDisplayName()));
            }

            // Update the score for every online player (including self)
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.isDead()) continue;

                int health = (int) Math.ceil(target.getHealth());
                if (health < 0) health = 0;

                // Set score (this replaces existing if present)
                objective.getScore(target.getName()).setScore(health);
            }

        } catch (Throwable t) {
            // silent fail — don't spam logs
        }
    }

    // ============================================================
    //  DISPLAY NAME FOR THE HEART ICON
    // ============================================================
    private String getDisplayName() {
        String name = plugin.getConfig().getString("nametag.display-name", "&c❤");
        if (name == null || name.isEmpty()) name = "&c❤";
        return name;
    }

    // ============================================================
    //  JOIN — rebuild everyone's scoreboards
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player joined = event.getPlayer();

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!joined.isOnline()) return;
                if (!plugin.getConfig().getBoolean("nametag.enabled", true)) return;

                // Every viewer needs to pick up the new player's score
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    updateViewer(viewer);
                }
            }
        }, 10L);
    }

    // ============================================================
    //  QUIT — clean up
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.viewerBoards.remove(event.getPlayer().getUniqueId());
    }

    // ============================================================
    //  RELOAD — rebuild from scratch
    // ============================================================
    public void reloadConfig() {
        // Remove all personal boards first
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            try {
                viewer.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            } catch (Throwable ignored) {}
        }
        this.viewerBoards.clear();

        // Restart with new interval
        startTask();

        // Rebuild for everyone online
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateViewer(viewer);
        }
    }

    // ============================================================
    //  SHUTDOWN — clean up before disable
    // ============================================================
    public void shutdown() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
            this.taskId = -1;
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            try {
                viewer.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            } catch (Throwable ignored) {}
        }
        this.viewerBoards.clear();
    }

    private String colorize(String msg) {
        if (msg == null) return "";
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
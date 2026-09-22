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
 */
public class NametagManager implements Listener {

    private final JavaPlugin plugin;

    private final Map<UUID, Scoreboard> viewerBoards = new HashMap<UUID, Scoreboard>();

    private int taskId = -1;

    public NametagManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTask();
    }

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

    private void updateViewer(Player viewer) {
        try {
            Scoreboard board = this.viewerBoards.get(viewer.getUniqueId());
            if (board == null) {
                board = Bukkit.getScoreboardManager().getNewScoreboard();
                this.viewerBoards.put(viewer.getUniqueId(), board);
                viewer.setScoreboard(board);
            }

            Objective objective = board.getObjective("bffa_hp");
            if (objective == null) {
                objective = board.registerNewObjective("bffa_hp", "health");
                objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
                objective.setDisplayName(colorize(getDisplayName()));
            } else {
                objective.setDisplayName(colorize(getDisplayName()));
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.isDead()) continue;

                int health = (int) Math.ceil(target.getHealth());
                if (health < 0) health = 0;

                objective.getScore(target.getName()).setScore(health);
            }

        } catch (Throwable t) {
            // silent
        }
    }

    private String getDisplayName() {
        String name = plugin.getConfig().getString("nametag.display-name", "&c❤");
        if (name == null || name.isEmpty()) name = "&c❤";
        return name;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player joined = event.getPlayer();

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!joined.isOnline()) return;
                if (!plugin.getConfig().getBoolean("nametag.enabled", true)) return;

                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    updateViewer(viewer);
                }
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.viewerBoards.remove(event.getPlayer().getUniqueId());
    }

    public void reloadConfig() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            try {
                viewer.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            } catch (Throwable ignored) {}
        }
        this.viewerBoards.clear();

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
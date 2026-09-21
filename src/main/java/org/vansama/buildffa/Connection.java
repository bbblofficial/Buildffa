package org.vansama.buildffa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Connection implements Listener {

    private final JavaPlugin plugin;

    private boolean enabled;
    private int pingThreshold;
    private int checkInterval;
    private int graceSeconds;
    private int warnCooldown;
    private String kickReason;
    private String broadcastMessage;

    private final Map<UUID, Long> highPingSince = new HashMap<UUID, Long>();
    private final Map<UUID, Long> lastWarnTime = new HashMap<UUID, Long>();
    private final Set<UUID> bypassPlayers = new HashSet<UUID>();
    private int taskId = -1;

    public Connection(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
        Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
        startTask();
    }

    public void loadConfiguration() {
        FileConfiguration config = this.plugin.getConfig();
        this.enabled = config.getBoolean("connection-check.enabled", true);
        this.pingThreshold = config.getInt("connection-check.ping-threshold", 150);
        this.checkInterval = config.getInt("connection-check.check-interval", 20);
        this.graceSeconds = config.getInt("connection-check.grace-seconds", 30);
        this.warnCooldown = config.getInt("connection-check.warn-cooldown", 5);
        this.kickReason = config.getString("connection-check.kick-message",
                "&cUnstable connection\n&fYour ping is too high: &e%ping%ms&7/&e%max%ms");
        this.broadcastMessage = config.getString("connection-check.broadcast-message",
                "&c%player% &7was kicked for &eUnstable Connection &7(&c%ping%ms&7)");

        this.plugin.getLogger().info("BuildFFA connection-check loaded: " +
                (this.enabled ? "ENABLED at " + this.pingThreshold + "ms" : "DISABLED"));
    }

    public void reloadConfig() {
        loadConfiguration();
        restartTask();
    }

    private void startTask() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
        }
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, new Runnable() {
            @Override
            public void run() {
                if (!enabled) return;
                tick();
            }
        }, 20L, (long) this.checkInterval);
    }

    private void restartTask() {
        startTask();
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();

            if (this.bypassPlayers.contains(id)) {
                this.highPingSince.remove(id);
                this.lastWarnTime.remove(id);
                continue;
            }

            int ping = getPing(player);

            if (ping >= this.pingThreshold) {
                if (!this.highPingSince.containsKey(id)) {
                    this.highPingSince.put(id, Long.valueOf(now));
                    sendWarning(player, ping);
                    this.lastWarnTime.put(id, Long.valueOf(now));
                    continue;
                }

                long since = this.highPingSince.get(id).longValue();
                long highPingMillis = now - since;

                if (highPingMillis >= (this.graceSeconds * 1000L)) {
                    kickPlayer(player, ping);
                    this.highPingSince.remove(id);
                    this.lastWarnTime.remove(id);
                    continue;
                }

                long lastWarn = this.lastWarnTime.containsKey(id)
                        ? this.lastWarnTime.get(id).longValue() : 0L;
                if (now - lastWarn >= (this.warnCooldown * 1000L)) {
                    sendCountdown(player, ping, highPingMillis);
                    this.lastWarnTime.put(id, Long.valueOf(now));
                }
            } else {
                if (this.highPingSince.containsKey(id)) {
                    this.highPingSince.remove(id);
                    this.lastWarnTime.remove(id);
                    player.sendMessage(colorize("&aYour connection is stable again."));
                }
            }
        }
    }

    private void sendWarning(Player player, int ping) {
        player.sendMessage(colorize("&8&m----------------------------------"));
        player.sendMessage(colorize("&c&lUnstable Connection"));
        player.sendMessage(colorize("&7Your ping is too high: &e" + ping + "ms"));
        player.sendMessage(colorize("&7You have &e" + this.graceSeconds + " seconds &7to stabilize."));
        player.sendMessage(colorize("&8&m----------------------------------"));
        player.playSound(player.getLocation(), Sound.NOTE_BASS, 1.0F, 1.0F);
    }

    private void sendCountdown(Player player, int ping, long highPingMillis) {
        long remaining = (this.graceSeconds * 1000L) - highPingMillis;
        int seconds = (int) Math.ceil(remaining / 1000.0);

        ChatColor color;
        if (seconds <= 3) color = ChatColor.RED;
        else if (seconds <= 10) color = ChatColor.GOLD;
        else color = ChatColor.YELLOW;

        player.sendMessage(colorize("&c[!] " + color + "Kick in " + seconds + "s " +
                "&7(Ping: &c" + ping + "ms&7)"));

        if (seconds <= 3) {
            player.playSound(player.getLocation(), Sound.CLICK, 1.0F, 1.5F);
        }
    }

    private void kickPlayer(Player player, int ping) {
        String reason = this.kickReason
                .replace("%ping%", String.valueOf(ping))
                .replace("%max%", String.valueOf(this.pingThreshold))
                .replace("%player%", player.getName());

        String broadcast = this.broadcastMessage
                .replace("%player%", player.getName())
                .replace("%ping%", String.valueOf(ping))
                .replace("%max%", String.valueOf(this.pingThreshold));

        Bukkit.broadcastMessage(colorize(broadcast));
        player.kickPlayer(colorize(reason));
    }

    private int getPing(Player player) {
        try {
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            return ((Integer) craftPlayer.getClass().getField("ping").get(craftPlayer)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        this.highPingSince.remove(event.getPlayer().getUniqueId());
        this.lastWarnTime.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        this.highPingSince.remove(id);
        this.lastWarnTime.remove(id);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
        FileConfiguration config = this.plugin.getConfig();
        config.set("connection-check.enabled", Boolean.valueOf(value));
        this.plugin.saveConfig();
        if (!value) {
            this.highPingSince.clear();
            this.lastWarnTime.clear();
        }
    }

    public int getPingThreshold() {
        return this.pingThreshold;
    }

    public void addBypass(UUID uuid) {
        this.bypassPlayers.add(uuid);
    }

    public void removeBypass(UUID uuid) {
        this.bypassPlayers.remove(uuid);
    }

    public boolean hasBypass(UUID uuid) {
        return this.bypassPlayers.contains(uuid);
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
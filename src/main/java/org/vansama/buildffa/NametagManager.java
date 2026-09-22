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

/**
 * Displays live HP above each player's head (nametag).
 *
 * How it works:
 *   1) For each ONLINE player A, we create a "view" for each OTHER online player B.
 *   2) Each viewer gets their own Scoreboard Teams (one team per target player).
 *   3) We add B's name to viewer A's team, and set team prefix/suffix with HP.
 *
 * This avoids conflicts with the main Scoreboard (sidebar) since we use
 * a SEPARATE Scoreboard instance for each player.
 *
 * Config: nametag.*
 */
public class NametagManager implements Listener {

    private final JavaPlugin plugin;

    // viewer UUID -> (target UUID -> team name)
    private final Map<UUID, Map<UUID, String>> viewerTeams = new HashMap<UUID, Map<UUID, String>>();

    // Team name generator counter
    private int teamCounter = 0;

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
    //  UPDATE ONE VIEWER (see HP of all OTHER online players)
    // ============================================================
    private void updateViewer(Player viewer) {
        Map<UUID, String> teams = this.viewerTeams.get(viewer.getUniqueId());
        if (teams == null) {
            teams = new HashMap<UUID, String>();
            this.viewerTeams.put(viewer.getUniqueId(), teams);
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            // Don't show your own HP above your head
            if (target.getUniqueId().equals(viewer.getUniqueId())) continue;
            if (target.isDead()) continue;

            String teamName = teams.get(target.getUniqueId());
            if (teamName == null) {
                teamName = nextTeamName();
                teams.put(target.getUniqueId(), teamName);
                createTeam(viewer, teamName, target);
            }

            // Update prefix/suffix
            String[] parts = buildNametag(target);

            // Apply to team (this updates for the VIEWER only)
            applyTeam(viewer, teamName, parts[0], parts[1]);
        }
    }

    // ============================================================
    //  BUILD NAMETAG (prefix before name, suffix after name)
    // ============================================================
    private String[] buildNametag(Player target) {
        double health = target.getHealth();
        double maxHealth = target.getMaxHealth();

        if (health < 0) health = 0;
        if (health > maxHealth) health = maxHealth;

        String mode = plugin.getConfig().getString("nametag.mode", "NUMERIC");
        if (mode == null) mode = "NUMERIC";

        String hpString;

        // ============ NUMERIC ============
        if (mode.equalsIgnoreCase("NUMERIC")) {
            String format = plugin.getConfig().getString("nametag.format", "&c❤ %current%");
            hpString = colorize(format
                    .replace("%current%", formatNumber(health))
                    .replace("%max%", formatNumber(maxHealth)));
        }
        // ============ HEARTS ============
        else if (mode.equalsIgnoreCase("HEARTS")) {
            int totalHearts = (int) Math.ceil(maxHealth / 2.0);
            int filledHearts = (int) Math.ceil(health / 2.0);
            int emptyHearts = totalHearts - filledHearts;
            if (emptyHearts < 0) emptyHearts = 0;

            String filledColor = plugin.getConfig().getString("nametag.filled-color", "&c");
            String emptyColor = plugin.getConfig().getString("nametag.empty-color", "&7");
            String heartChar = plugin.getConfig().getString("nametag.heart-char", "❤");

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < filledHearts; i++) sb.append(filledColor).append(heartChar);
            for (int i = 0; i < emptyHearts; i++) sb.append(emptyColor).append(heartChar);
            hpString = colorize(sb.toString());
        }
        // ============ PERCENT ============
        else if (mode.equalsIgnoreCase("PERCENT")) {
            double percent = (health / maxHealth) * 100.0;
            hpString = colorize("&c❤ &f" + String.format("%.0f", percent) + "%");
        }
        // Fallback
        else {
            hpString = colorize("&c❤ " + formatNumber(health));
        }

        // ============ SUFFIX / PREFIX ============
        boolean useSuffix = plugin.getConfig().getBoolean("nametag.use-suffix", true);

        // ✅ Team prefix max 16 chars
        // ✅ Team suffix max 16 chars
        // Final display = prefix + name + suffix

        if (useSuffix) {
            // "PlayerName ❤ 18"
            return new String[]{"", truncate(hpString, 16)};
        } else {
            // "❤ 18 PlayerName"
            return new String[]{truncate(hpString, 16), ""};
        }
    }

    private String formatNumber(double d) {
        if (d == Math.floor(d)) return String.valueOf((int) d);
        return String.format("%.1f", d);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    // ============================================================
    //  SCOREBOARD TEAM PACKETS
    //  Team prefix/suffix must be < 16 chars in 1.8.8
    // ============================================================
    private void createTeam(Player viewer, String teamName, Player target) {
        sendTeamPacket(viewer, teamName, target.getName(), "", "", false);
    }

    private void applyTeam(Player viewer, String teamName, String prefix, String suffix) {
        sendTeamPacket(viewer, teamName, null, prefix, suffix, true);
    }

    /**
     * Sends a PacketPlayOutScoreboardTeam to a single viewer.
     *
     * Mode 0 = create team
     * Mode 2 = update team
     */
    private void sendTeamPacket(Player viewer, String teamName, String playerName,
                                 String prefix, String suffix, boolean update) {
        try {
            String nmsVersion = getNmsVersion();

            Class<?> packetClass = Class.forName("net.minecraft.server." + nmsVersion + ".PacketPlayOutScoreboardTeam");
            Object packet = packetClass.newInstance();

            // team name
            setField(packet, "a", teamName);

            // display name
            setField(packet, "b", teamName);

            // prefix
            setField(packet, "c", prefix);

            // suffix
            setField(packet, "d", suffix);

            // nameTagVisibility (always = 0, hideForOtherTeams = 1, hideForOwnTeam = 2, never = 3)
            setField(packet, "e", "always");

            // send scoreboard color (0-15) - 21 = RESET
            setField(packet, "f", -1);

            // member names (List<String>)
            java.util.List<String> members = new java.util.ArrayList<String>();
            if (playerName != null) {
                members.add(playerName);
            }
            setField(packet, "g", members);

            // mode: 0=create, 1=remove, 2=update, 3=add_player, 4=remove_player
            setField(packet, "h", update ? 2 : 0);

            // Send packet ONLY to viewer
            sendPacket(viewer, packet);
        } catch (Throwable t) {
            plugin.getLogger().warning("Nametag packet failed: " + t.getMessage());
        }
    }

    private void sendPacket(Player player, Object packet) {
        try {
            String nmsVersion = getNmsVersion();
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);
            Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
            Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);

            Class<?> packetClass = Class.forName("net.minecraft.server." + nmsVersion + ".Packet");
            java.lang.reflect.Method sendPacketMethod = playerConnection.getClass()
                    .getMethod("sendPacket", packetClass);

            sendPacketMethod.invoke(playerConnection, packet);
        } catch (Throwable t) {
            // silent
        }
    }

    private void setField(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Throwable t) {
            // silent
        }
    }

    private String getNmsVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    // ============================================================
    //  HELPER: Next unique team name
    // ============================================================
    private String nextTeamName() {
        this.teamCounter++;
        return "bffa_" + Integer.toHexString(this.teamCounter);
    }

    // ============================================================
    //  JOIN / QUIT
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player joined = event.getPlayer();

        // 5 ticks later — create views for everyone
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!joined.isOnline()) return;

                if (!plugin.getConfig().getBoolean("nametag.enabled", true)) return;

                // Update viewer views
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (viewer.getUniqueId().equals(joined.getUniqueId())) continue;

                    // viewer needs to see joined's nametag
                    Map<UUID, String> teams = viewerTeams.get(viewer.getUniqueId());
                    if (teams == null) {
                        teams = new HashMap<UUID, String>();
                        viewerTeams.put(viewer.getUniqueId(), teams);
                    }

                    if (!teams.containsKey(joined.getUniqueId())) {
                        String teamName = nextTeamName();
                        teams.put(joined.getUniqueId(), teamName);
                        createTeam(viewer, teamName, joined);
                    }
                }

                // joined needs to see everyone else's nametag
                updateViewer(joined);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID quitter = event.getPlayer().getUniqueId();

        // Remove quitter from all viewer maps
        for (Map<UUID, String> teams : viewerTeams.values()) {
            teams.remove(quitter);
        }

        // Remove quitter's own viewer map
        viewerTeams.remove(quitter);
    }

    // ============================================================
    //  RELOAD / SHUTDOWN
    // ============================================================
    public void reloadConfig() {
        // Clear all teams from all viewers (send remove packets)
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Map<UUID, String> teams = viewerTeams.get(viewer.getUniqueId());
            if (teams != null) {
                for (String teamName : teams.values()) {
                    try {
                        String nmsVersion = getNmsVersion();
                        Class<?> packetClass = Class.forName("net.minecraft.server." + nmsVersion + ".PacketPlayOutScoreboardTeam");
                        Object packet = packetClass.newInstance();
                        setField(packet, "a", teamName);
                        setField(packet, "b", teamName);
                        setField(packet, "c", "");
                        setField(packet, "d", "");
                        setField(packet, "e", "always");
                        setField(packet, "f", -1);
                        setField(packet, "g", new java.util.ArrayList<String>());
                        setField(packet, "h", 1); // remove
                        sendPacket(viewer, packet);
                    } catch (Throwable ignored) {}
                }
            }
        }

        viewerTeams.clear();
        teamCounter = 0;

        // Restart task with new interval
        startTask();

        // Reapply for everyone online
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateViewer(viewer);
        }
    }

    public void shutdown() {
        if (this.taskId != -1) {
            Bukkit.getScheduler().cancelTask(this.taskId);
            this.taskId = -1;
        }
        viewerTeams.clear();
    }

    private String colorize(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
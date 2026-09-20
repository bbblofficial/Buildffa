package org.vansama.buildffa;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Void implements Listener {

    private JavaPlugin plugin;
    private double killHeight;
    private String voidMessage;
    private boolean teleportInsteadOfKill;
    private long teleportDelay;
    private String teleportMessage;

    private final Set<UUID> teleportingPlayers = new HashSet<UUID>();

    public Void(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
        Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    private void loadConfiguration() {
        FileConfiguration config = this.plugin.getConfig();
        this.killHeight = config.getDouble("kill-height", 0.0D);
        this.voidMessage = config.getString("void-message", "&c%player% &7fell into the void");
        this.teleportInsteadOfKill = config.getBoolean("void.teleport-instead-of-kill", true);
        this.teleportDelay = config.getLong("void.teleport-delay", 0L);
        this.teleportMessage = config.getString("void.teleport-message",
                "&cYou fell into the void! &7Teleported to spawn.");
    }

    public void reloadConfig() {
        loadConfiguration();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;
        if (player.isDead() || player.getHealth() <= 0) return;
        if (this.teleportingPlayers.contains(player.getUniqueId())) return;

        if (to.getY() < this.killHeight) {
            this.teleportingPlayers.add(player.getUniqueId());

            if (this.teleportInsteadOfKill) {
                teleportToSpawn(player);
            } else {
                player.setHealth(0.0D);
            }
        }
    }

    private void teleportToSpawn(final Player player) {
        final Location spawn = getSpawnLocation();

        Runnable teleportTask = new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    teleportingPlayers.remove(player.getUniqueId());
                    return;
                }

                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20.0F);
                player.setExhaustion(0.0F);
                player.setFireTicks(0);
                player.setFallDistance(0.0F);

                if (spawn != null) {
                    player.teleport(spawn);
                } else {
                    player.teleport(player.getWorld().getSpawnLocation());
                }

                if (teleportMessage != null && !teleportMessage.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            teleportMessage.replace("%player%", player.getName())));
                }

                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        teleportingPlayers.remove(player.getUniqueId());
                    }
                }, 20L);
            }
        };

        if (this.teleportDelay > 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, teleportTask, this.teleportDelay);
        } else {
            teleportTask.run();
        }
    }

    private Location getSpawnLocation() {
        FileConfiguration config = this.plugin.getConfig();
        if (!config.contains("spawn.world")) {
            return null;
        }
        String worldName = config.getString("spawn.world");
        if (worldName == null) return null;

        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = config.getDouble("spawn.x");
        double y = config.getDouble("spawn.y");
        double z = config.getDouble("spawn.z");
        float yaw = (float) config.getDouble("spawn.yaw");
        float pitch = (float) config.getDouble("spawn.pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean isTeleporting(UUID uuid) {
        return this.teleportingPlayers.contains(uuid);
    }
}
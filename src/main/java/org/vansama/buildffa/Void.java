package org.vansama.buildffa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Void implements Listener {

    private JavaPlugin plugin;
    private double killHeight;
    private String voidMessage;
    private String voidKilledByMessage;
    private boolean teleportInsteadOfKill;
    private long teleportDelay;
    private String teleportMessage;

    private final Set<UUID> teleportingPlayers = new HashSet<UUID>();
    private final Set<UUID> dyingPlayers = new HashSet<UUID>();

    // Track who last hit who (UUID victim -> UUID attacker) and when
    private final Map<UUID, UUID> lastDamager = new HashMap<UUID, UUID>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<UUID, Long>();

    // How long after being hit does a void fall still count as a kill
    private static final long DAMAGE_WINDOW_MS = 10000L; // 10 seconds

    // Flag shared with Kill.java
    private static final Set<UUID> voidDeaths = new HashSet<UUID>();

    public Void(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
        Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    private void loadConfiguration() {
        FileConfiguration config = this.plugin.getConfig();
        this.killHeight = config.getDouble("kill-height", 0.0D);
        this.voidMessage = config.getString("void.death-message", "&c%player% &7fell into the void");
        this.voidKilledByMessage = config.getString("void.killed-by-message",
                "&c%player% &7was knocked into the void by &c%killer%");
        this.teleportInsteadOfKill = config.getBoolean("void.teleport-instead-of-kill", true);
        this.teleportDelay = config.getLong("void.teleport-delay", 0L);
        this.teleportMessage = config.getString("void.teleport-message",
                "&c%player% &7fell into the void! &7Teleported to spawn.");
    }

    public void reloadConfig() {
        loadConfiguration();
    }

    public static boolean isVoidDeath(UUID uuid) {
        return voidDeaths.contains(uuid);
    }

    public static void clearVoidDeath(UUID uuid) {
        voidDeaths.remove(uuid);
    }

    /**
     * Records who hit who, so we can credit the kill if they fall into the void.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = null;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        this.lastDamager.put(victim.getUniqueId(), attacker.getUniqueId());
        this.lastDamageTime.put(victim.getUniqueId(), Long.valueOf(System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;
        if (player.isDead() || player.getHealth() <= 0) return;
        if (this.teleportingPlayers.contains(player.getUniqueId())) return;
        if (this.dyingPlayers.contains(player.getUniqueId())) return;

        if (to.getY() < this.killHeight) {
            if (this.teleportInsteadOfKill) {
                this.teleportingPlayers.add(player.getUniqueId());
                registerVoidDeath(player);
                teleportToSpawn(player);
            } else {
                this.dyingPlayers.add(player.getUniqueId());
                player.setHealth(0.0D);
            }
        }
    }

    private void registerVoidDeath(Player player) {
        voidDeaths.add(player.getUniqueId());

        // ==== Figure out if someone knocked them in ====
        Player killer = null;
        UUID damagerId = this.lastDamager.get(player.getUniqueId());
        Long damageTime = this.lastDamageTime.get(player.getUniqueId());

        if (damagerId != null && damageTime != null) {
            long elapsed = System.currentTimeMillis() - damageTime.longValue();
            if (elapsed <= DAMAGE_WINDOW_MS) {
                Player online = Bukkit.getPlayer(damagerId);
                if (online != null && online.isOnline()) {
                    killer = online;
                }
            }
        }

        // ==== Save stats ====
        try {
            BuildFFA bffa = (BuildFFA) this.plugin;
            DatabaseManager db = bffa.getDatabaseManager();
            if (db != null) {
                // victim death
                PlayerData victimData = db.getPlayer(player.getUniqueId());
                if (victimData == null) victimData = db.loadPlayer(player.getUniqueId());
                if (victimData != null) {
                    victimData.addDeath();
                    victimData.resetKillstreak();
                    db.savePlayer(victimData);
                }

                // killer kill (only if credited)
                if (killer != null) {
                    PlayerData killerData = db.getPlayer(killer.getUniqueId());
                    if (killerData == null) killerData = db.loadPlayer(killer.getUniqueId());
                    if (killerData != null) {
                        killerData.addKill();
                        killerData.addKillstreak();
                        db.savePlayer(killerData);
                    }
                }
            }
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Void death save failed: " + t.getMessage());
        }

        // ==== Broadcast message ====
        String finalMessage;
        if (killer != null) {
            finalMessage = this.voidKilledByMessage
                    .replace("%player%", player.getName())
                    .replace("%killer%", killer.getName());
        } else {
            finalMessage = this.voidMessage
                    .replace("%player%", player.getName());
        }

        if (finalMessage != null && !finalMessage.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', finalMessage));
        }

        // ==== Cleanup ====
        this.lastDamager.remove(player.getUniqueId());
        this.lastDamageTime.remove(player.getUniqueId());
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
                    String msg = teleportMessage.replace("%player%", player.getName());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
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

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = config.getDouble("spawn.x");
        double y = config.getDouble("spawn.y");
        double z = config.getDouble("spawn.z");
        float yaw = (float) config.getDouble("spawn.yaw");
        float pitch = (float) config.getDouble("spawn.pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.setDeathMessage(null);

        Player player = event.getEntity();

        if (player.getKiller() == null && this.dyingPlayers.remove(player.getUniqueId())) {
            // Real death (not teleport) — check last damager
            Player killer = null;
            UUID damagerId = this.lastDamager.get(player.getUniqueId());
            Long damageTime = this.lastDamageTime.get(player.getUniqueId());

            if (damagerId != null && damageTime != null) {
                long elapsed = System.currentTimeMillis() - damageTime.longValue();
                if (elapsed <= DAMAGE_WINDOW_MS) {
                    Player online = Bukkit.getPlayer(damagerId);
                    if (online != null && online.isOnline()) {
                        killer = online;
                    }
                }
            }

            String msg;
            if (killer != null) {
                msg = this.voidKilledByMessage
                        .replace("%player%", player.getName())
                        .replace("%killer%", killer.getName());
            } else {
                msg = this.voidMessage.replace("%player%", player.getName());
            }

            if (msg != null && !msg.isEmpty()) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
            }

            this.lastDamager.remove(player.getUniqueId());
            this.lastDamageTime.remove(player.getUniqueId());
        }

        this.teleportingPlayers.remove(player.getUniqueId());
    }

    public boolean isTeleporting(UUID uuid) {
        return this.teleportingPlayers.contains(uuid);
    }
}
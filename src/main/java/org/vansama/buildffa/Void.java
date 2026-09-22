package org.vansama.buildffa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

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

    private final Map<UUID, UUID> lastDamager = new HashMap<UUID, UUID>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<UUID, Long>();

    private static final long DAMAGE_WINDOW_MS = 10000L;

    // Tracks players whose death was caused by the void,
    // so Kill.java knows to skip them (Void already handled the kill).
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

        String msg = config.getString("void.teleport-message", "");
        if (msg == null) msg = "";
        this.teleportMessage = msg;
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

    // ============================================================
    //  Track last hit (for kill credit)
    // ============================================================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageLowest(EntityDamageByEntityEvent event) {
        trackDamage(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageMonitor(EntityDamageByEntityEvent event) {
        trackDamage(event);
    }

    private void trackDamage(EntityDamageByEntityEvent event) {
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

    // ============================================================
    //  Detect falling below kill-height
    // ============================================================
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
                handleVoidFall(player);
                teleportToSpawn(player);
            } else {
                this.dyingPlayers.add(player.getUniqueId());
                voidDeaths.add(player.getUniqueId());
                player.setHealth(0.0D);
            }
        }
    }

    private void handleVoidFall(Player player) {
        voidDeaths.add(player.getUniqueId());

        Player killer = findKiller(player);

        saveStats(player, killer);

        if (killer != null) {
            fullHeal(killer);
            giveKillstreakReward(killer);
        }

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

        this.lastDamager.remove(player.getUniqueId());
        this.lastDamageTime.remove(player.getUniqueId());
    }

    private Player findKiller(Player victim) {
        UUID damagerId = this.lastDamager.get(victim.getUniqueId());
        Long damageTime = this.lastDamageTime.get(victim.getUniqueId());

        if (damagerId == null || damageTime == null) return null;

        long elapsed = System.currentTimeMillis() - damageTime.longValue();
        if (elapsed > DAMAGE_WINDOW_MS) return null;

        Player online = Bukkit.getPlayer(damagerId);
        if (online != null && online.isOnline()) {
            return online;
        }
        return null;
    }

    private void saveStats(Player victim, Player killer) {
        try {
            BuildFFA bffa = (BuildFFA) this.plugin;
            DatabaseManager db = bffa.getDatabaseManager();
            if (db == null) return;

            PlayerData victimData = db.getPlayer(victim.getUniqueId());
            if (victimData == null) victimData = db.loadPlayer(victim.getUniqueId());
            if (victimData != null) {
                victimData.addDeath();
                victimData.resetKillstreak();
                db.savePlayer(victimData);
            }

            if (killer != null) {
                PlayerData killerData = db.getPlayer(killer.getUniqueId());
                if (killerData == null) killerData = db.loadPlayer(killer.getUniqueId());
                if (killerData != null) {
                    killerData.addKill();
                    killerData.addKillstreak();
                    db.savePlayer(killerData);
                }
            }

        } catch (Throwable t) {
            this.plugin.getLogger().warning("Void save stats failed: " + t.getMessage());
        }
    }

    private void giveKillstreakReward(Player killer) {
        if (killer == null) return;

        DatabaseManager db = ((BuildFFA) this.plugin).getDatabaseManager();
        if (db == null) return;

        PlayerData data = db.getPlayer(killer.getUniqueId());
        if (data == null) return;

        int streak = data.getKillstreak();
        if (streak <= 0) {
            killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
            return;
        }

        if (!this.plugin.getConfig().getBoolean("killstreak-rewards.enabled", true)) {
            killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
            return;
        }

        boolean repeatFrom12 = this.plugin.getConfig().getBoolean("killstreak-rewards.repeat-from-12", true);

        int level = resolveLevel(streak, repeatFrom12);
        if (level == -1 || !giveRewardForLevel(killer, level)) {
            killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
        }

        killer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&a&l★ &aKillstreak &e" + streak + " &a— reward received!"));
        try {
            killer.playSound(killer.getLocation(), Sound.LEVEL_UP, 1.0F, 1.5F);
        } catch (Throwable ignored) {}
    }

    private int resolveLevel(int streak, boolean repeatFrom12) {
        if (this.plugin.getConfig().contains("killstreak-rewards.rewards." + streak)) {
            return streak;
        }
        if (repeatFrom12 && streak > 12) {
            int wrapped = ((streak - 1) % 12) + 1;
            if (this.plugin.getConfig().contains("killstreak-rewards.rewards." + wrapped)) {
                return wrapped;
            }
        }
        for (int i = streak - 1; i >= 1; i--) {
            if (this.plugin.getConfig().contains("killstreak-rewards.rewards." + i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean giveRewardForLevel(Player player, int level) {
        String rewardString = this.plugin.getConfig().getString("killstreak-rewards.rewards." + level, "");
        if (rewardString == null || rewardString.isEmpty()) return false;

        String[] parts = rewardString.split(" ");
        boolean gaveAny = false;

        for (String part : parts) {
            if (part.isEmpty()) continue;
            String[] split = part.split(":");
            if (split.length != 2) continue;
            String itemName = split[0].toLowerCase();
            int amount;
            try {
                amount = Integer.parseInt(split[1]);
            } catch (NumberFormatException e) { continue; }
            if (amount <= 0) continue;

            ItemStack item = buildItem(itemName, amount);
            if (item != null) {
                player.getInventory().addItem(item);
                gaveAny = true;
            }
        }
        return gaveAny;
    }

    private ItemStack buildItem(String name, int amount) {
        if (name.equals("gapple") || name.equals("golden_apple") || name.equals("gap"))
            return new ItemStack(Material.GOLDEN_APPLE, amount);
        if (name.equals("fb") || name.equals("fireball") || name.equals("fire_charge"))
            return new ItemStack(Material.FIREBALL, amount);
        if (name.equals("perl") || name.equals("pearl") || name.equals("ender_pearl"))
            return new ItemStack(Material.ENDER_PEARL, amount);
        if (name.equals("feather"))
            return new ItemStack(Material.FEATHER, amount);
        if (name.equals("speed"))
            return makePotion(1, amount);
        if (name.equals("jump"))
            return makePotion(2, amount);
        return null;
    }

    private ItemStack makePotion(int kind, int level) {
        ItemStack potion = new ItemStack(Material.POTION, 1);
        short data;
        if (kind == 1) {
            if (level <= 1) data = 8194;
            else data = 8226;
        } else {
            if (level <= 1) data = 8203;
            else if (level == 2) data = 8235;
            else if (level == 3) data = 8267;
            else if (level == 4) data = 8299;
            else data = 8331;
        }
        potion.setDurability(data);
        return potion;
    }

    private void fullHeal(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    // ============================================================
    //  Teleport to spawn + full reset
    //  (Kit is restored by Equip.giveDiamondArmor)
    // ============================================================
    private void teleportToSpawn(final Player player) {
        final Location spawn = getSpawnLocation();

        Runnable teleportTask = new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    teleportingPlayers.remove(player.getUniqueId());
                    return;
                }

                // Clear inventory + armor + cursor
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.setItemOnCursor(null);

                // Full heal
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20.0F);
                player.setExhaustion(0.0F);
                player.setFireTicks(0);
                player.setFallDistance(0.0F);
                player.setLevel(0);
                player.setExp(0.0F);

                // Restore kit
                try {
                    BuildFFA bffa = (BuildFFA) plugin;
                    if (bffa.getEquip() != null) {
                        bffa.getEquip().giveDiamondArmor(player);
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Void kit restore failed: " + t.getMessage());
                }

                // Teleport
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
        if (!config.contains("spawn.world")) return null;
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

    // ============================================================
    //  DEATH — only handles kill credit + broadcast
    //  (Drops / XP / death message are cleared by KitRestore,
    //   so we don't touch them here to avoid double-handling.)
    // ============================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID victimId = player.getUniqueId();

        // If this was a void death (teleport-instead-of-kill disabled),
        // Void already saved stats + broadcast in handleVoidFall.
        if (voidDeaths.contains(victimId)) {
            // Nothing extra to do — the flow already handled it.
            this.teleportingPlayers.remove(victimId);
            return;
        }

        // If the victim had fallen below kill-height while teleport
        // was enabled, no death event should happen here — but just
        // in case, be safe:
        if (player.getKiller() == null && this.dyingPlayers.remove(victimId)) {
            Player killer = findKiller(player);
            if (killer == null) killer = player.getKiller();

            if (killer != null) {
                fullHeal(killer);
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

            this.lastDamager.remove(victimId);
            this.lastDamageTime.remove(victimId);
        }

        this.teleportingPlayers.remove(victimId);
    }

    public boolean isTeleporting(UUID uuid) {
        return this.teleportingPlayers.contains(uuid);
    }
}
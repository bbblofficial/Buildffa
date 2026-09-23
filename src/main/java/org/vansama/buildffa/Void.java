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
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Void implements Listener {

    private JavaPlugin plugin;
    private double killHeight;
    private String voidMessage;
    private String voidKilledByMessage;
    private boolean teleportInsteadOfKill;
    private long teleportDelay;
    private String teleportMessage;

    // Players currently being teleported to spawn (prevents re-trigger)
    private final Set<UUID> teleportingPlayers = new HashSet<UUID>();

    // Players in the "kill mode" void-death process
    private final Set<UUID> dyingPlayers = new HashSet<UUID>();

    private final Map<UUID, UUID> lastDamager = new HashMap<UUID, UUID>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<UUID, Long>();

    private static final long DAMAGE_WINDOW_MS = 10000L;

    // Players whose PlayerDeathEvent should be ignored by Kill.java
    // (Void already handled stats / heal / reward / broadcast)
    private static final Set<UUID> voidDeaths = new HashSet<UUID>();

    public Void(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
        Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    private void loadConfiguration() {
        FileConfiguration config = this.plugin.getConfig();
        this.killHeight = config.getDouble("kill-height", 0.0D);
        this.voidMessage = config.getString("void.death-message",
                "&c%player% &7fell into the void");
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
    //  Void detection
    // ============================================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        UUID uuid = player.getUniqueId();

        if (to == null) return;
        if (player.isDead() || player.getHealth() <= 0) return;
        if (this.teleportingPlayers.contains(uuid)) return;
        if (this.dyingPlayers.contains(uuid)) return;

        if (to.getY() < this.killHeight) {

            // ✅ Mark as void death so Kill.java skips the death event
            voidDeaths.add(uuid);

            // ✅ Handle stats / heal / reward / broadcast ONCE
            //    Runs in BOTH modes.
            handleVoidFall(player);

            if (this.teleportInsteadOfKill) {
                // Teleport mode — player doesn't actually die
                this.teleportingPlayers.add(uuid);
                teleportToSpawn(player);
            } else {
                // Kill mode — player dies normally, Kill.java will skip it
                this.dyingPlayers.add(uuid);
                player.setHealth(0.0D);
            }
        }
    }

    /**
     * Handles ALL the "you fell in the void" logic:
     *  - saves victim death + killer kill to DB
     *  - full-heals killer
     *  - gives killstreak reward
     *  - broadcasts the correct message
     *
     * ⚠️  Does NOT touch voidDeaths — that's the caller's job.
     *     This prevents the flag getting stuck.
     */
    private void handleVoidFall(Player player) {
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

    // ============================================================
    //  Killstreak rewards
    // ============================================================
    private void giveKillstreakReward(Player killer) {
        if (killer == null || !killer.isOnline()) return;

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

        boolean repeatFrom12 = this.plugin.getConfig()
                .getBoolean("killstreak-rewards.repeat-from-12", true);

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

    // ============================================================
    //  RESOLVE LEVEL — Correct Cycle (1 → 12 → 1)
    // ============================================================
    private int resolveLevel(int streak, boolean repeatFrom12) {
        if (streak <= 0) return -1;

        int level = streak;
        if (repeatFrom12 && streak > 12) {
            level = ((streak - 1) % 12) + 1;
        }

        if (this.plugin.getConfig().contains("killstreak-rewards.rewards." + level)) {
            return level;
        }
        return -1;
    }

    private boolean giveRewardForLevel(Player player, int level) {
        String rewardString = this.plugin.getConfig()
                .getString("killstreak-rewards.rewards." + level, "");
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

    // ============================================================
    //  MAKE POTION — works for ANY level (I through V)
    // ============================================================
    private ItemStack makePotion(int kind, int level) {
        ItemStack potion = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();

        PotionEffectType type;
        if (kind == 1) {
            type = PotionEffectType.SPEED;
        } else {
            type = PotionEffectType.JUMP;
        }

        int amplifier = level - 1;
        if (amplifier < 0) amplifier = 0;
        if (amplifier > 9) amplifier = 9;

        int durationTicks = 180 * 20;

        meta.addCustomEffect(new PotionEffect(type, durationTicks, amplifier), true);

        String name;
        if (kind == 1) {
            name = "&bPotion of Swiftness " + toRoman(level);
        } else {
            name = "&aPotion of Leaping " + toRoman(level);
        }
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        potion.setItemMeta(meta);
        return potion;
    }

    private String toRoman(int num) {
        switch (num) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            case 6: return "VI";
            case 7: return "VII";
            case 8: return "VIII";
            case 9: return "IX";
            case 10: return "X";
            default: return String.valueOf(num);
        }
    }

    private void fullHeal(Player player) {
        if (player == null || !player.isOnline()) return;

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
    //  ✅ Clears teleportingPlayers AND voidDeaths when done.
    // ============================================================
    private void teleportToSpawn(final Player player) {
        final Location spawn = getSpawnLocation();

        Runnable teleportTask = new Runnable() {
            @Override
            public void run() {
                UUID uuid = player.getUniqueId();

                if (!player.isOnline()) {
                    teleportingPlayers.remove(uuid);
                    voidDeaths.remove(uuid);
                    return;
                }

                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.setItemOnCursor(null);

                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20.0F);
                player.setExhaustion(0.0F);
                player.setFireTicks(0);
                player.setFallDistance(0.0F);
                player.setLevel(0);
                player.setExp(0.0F);

                try {
                    BuildFFA bffa = (BuildFFA) plugin;
                    if (bffa.getEquip() != null) {
                        bffa.getEquip().giveDiamondArmor(player);
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Void kit restore failed: " + t.getMessage());
                }

                if (spawn != null) {
                    player.teleport(spawn);
                } else {
                    player.teleport(player.getWorld().getSpawnLocation());
                }

                if (teleportMessage != null && !teleportMessage.isEmpty()) {
                    String msg = teleportMessage.replace("%player%", player.getName());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                }

                // ✅ IMPORTANT: clear BOTH flags after a short delay
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        UUID id = player.getUniqueId();
                        teleportingPlayers.remove(id);
                        // 🔑 FIX: this was the main bug — flag was never cleared
                        voidDeaths.remove(id);
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
    //  Death handling — CLEANUP ONLY
    //
    //  Priority MONITOR → runs AFTER Kill.java (HIGHEST).
    //  So Kill.java sees voidDeaths still set → skips.
    //  Then we clean up all state.
    //
    //  No broadcasts, no heal, no stats here —
    //  handleVoidFall already did all of that.
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID victimId = event.getEntity().getUniqueId();

        this.teleportingPlayers.remove(victimId);
        this.dyingPlayers.remove(victimId);
        this.lastDamager.remove(victimId);
        this.lastDamageTime.remove(victimId);

        // Safety: if Kill.java didn't clear it, clear it now.
        // (Kill.java clears it when it detects isVoidDeath.)
        voidDeaths.remove(victimId);
    }

    public boolean isTeleporting(UUID uuid) {
        return this.teleportingPlayers.contains(uuid);
    }
}
package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Kill implements Listener {

    private JavaPlugin plugin;
    private KillListener killListener;
    private DatabaseManager databaseManager;

    private Map<UUID, Long> lastKillTimestamps = new HashMap<UUID, Long>();
    private Map<UUID, Long> lastVictimDeathTimestamps = new HashMap<UUID, Long>();

    // ✅ کاهش debounce — قبلاً 1000ms بود که باعث از دست رفتن kill دوم می‌شد
    private static final long KILL_DEBOUNCE_MS = 50L;
    // ✅ کاهش debounce قربانی — قبلاً 3000ms بود که برای respawn سریع مشکل ایجاد می‌کرد
    private static final long VICTIM_DEBOUNCE_MS = 200L;

    public Kill(JavaPlugin plugin, KillListener killListener, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.killListener = killListener;
        this.databaseManager = databaseManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deathPlayer = event.getEntity();
        UUID victimId = deathPlayer.getUniqueId();

        // ✅ اگه Void این death رو handle کرده، فقط flag رو پاک کن و برو
        if (Void.isVoidDeath(victimId)) {
            Void.clearVoidDeath(victimId);
            return;
        }

        long now = System.currentTimeMillis();

        // ----- Debounce قربانی -----
        long lastVictimDeath = this.lastVictimDeathTimestamps.containsKey(victimId)
                ? this.lastVictimDeathTimestamps.get(victimId).longValue() : 0L;
        if (now - lastVictimDeath < VICTIM_DEBOUNCE_MS) {
            return;
        }
        this.lastVictimDeathTimestamps.put(victimId, Long.valueOf(now));

        // ----- آمار قربانی -----
        PlayerData victimData = this.databaseManager.getPlayer(victimId);
        if (victimData != null) {
            victimData.addDeath();
            victimData.resetKillstreak();
            this.databaseManager.savePlayer(victimData);
        }

        if (deathPlayer.getKiller() == null) {
            return;
        }

        Player killer = deathPlayer.getKiller();
        UUID killerId = killer.getUniqueId();

        // ----- Debounce killer (خیلی کوتاه — فقط برای جلوگیری از double-fire) -----
        long lastKillTime = this.lastKillTimestamps.containsKey(killerId)
                ? this.lastKillTimestamps.get(killerId).longValue() : 0L;
        if (now - lastKillTime < KILL_DEBOUNCE_MS) {
            return;
        }
        this.lastKillTimestamps.put(killerId, Long.valueOf(now));

        int killCount = this.killListener.getKillCount(killer);

        // ----- آمار killer -----
        PlayerData killerData = this.databaseManager.getPlayer(killerId);
        int newStreak = 0;
        if (killerData != null) {
            killerData.addKill();
            killerData.addKillstreak();
            this.databaseManager.savePlayer(killerData);
            newStreak = killerData.getKillstreak();
        }

        // ✅ همیشه هیل و ریوارد بده
        healOnKill(killer);
        giveKillstreakReward(killer, newStreak);

        // ----- پیام kill -----
        String killMessage = this.plugin.getConfig().getString("kill");
        if (killMessage != null && !killMessage.isEmpty()) {
            String broadcastMessage = colorize(killMessage)
                    .replaceAll("%killer%", killer.getName())
                    .replaceAll("%loser%", deathPlayer.getName())
                    .replaceAll("%killcount%", String.valueOf(killCount));
            Bukkit.broadcastMessage(broadcastMessage);
        }
    }

    // ============================================================
    //  BEDWARS HEAL ON KILL
    // ============================================================
    private void healOnKill(Player killer) {
        if (killer == null || !killer.isOnline()) return;

        boolean healEnabled = this.plugin.getConfig().getBoolean("kill-heal.enabled", true);
        if (!healEnabled) return;

        boolean healFull = this.plugin.getConfig().getBoolean("kill-heal.full-heal", true);
        double healAmount = this.plugin.getConfig().getDouble("kill-heal.amount", 6.0D);

        if (healFull) {
            killer.setHealth(killer.getMaxHealth());
        } else {
            double newHealth = killer.getHealth() + healAmount;
            if (newHealth > killer.getMaxHealth()) newHealth = killer.getMaxHealth();
            killer.setHealth(newHealth);
        }

        killer.setFireTicks(0);
        killer.setFallDistance(0.0F);

        boolean absorptionEnabled = this.plugin.getConfig().getBoolean("kill-heal.absorption.enabled", false);
        if (absorptionEnabled) {
            int absorptionLevel = this.plugin.getConfig().getInt("kill-heal.absorption.level", 1);
            int absorptionSeconds = this.plugin.getConfig().getInt("kill-heal.absorption.duration", 5);

            try {
                killer.addPotionEffect(new PotionEffect(
                    org.bukkit.potion.PotionEffectType.ABSORPTION,
                    absorptionSeconds * 20,
                    absorptionLevel,
                    true,
                    false
                ));
            } catch (Throwable ignored) {}
        }
    }

    // ============================================================
    //  KILLSTREAK REWARDS
    // ============================================================
    private void giveKillstreakReward(Player player, int streak) {
        if (player == null || !player.isOnline()) return;

        if (streak <= 0) {
            player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
            return;
        }

        if (!this.plugin.getConfig().getBoolean("killstreak-rewards.enabled", true)) {
            player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
            return;
        }

        boolean stacking = this.plugin.getConfig().getBoolean("killstreak-rewards.stacking", false);
        boolean repeatFrom12 = this.plugin.getConfig().getBoolean("killstreak-rewards.repeat-from-12", true);

        if (stacking) {
            boolean gaveAny = false;
            for (int i = 1; i <= streak; i++) {
                int level = resolveLevel(i, repeatFrom12);
                if (level == -1) continue;
                if (giveRewardForLevel(player, level)) gaveAny = true;
            }
            if (!gaveAny) {
                player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
            }
        } else {
            int level = resolveLevel(streak, repeatFrom12);
            if (level == -1 || !giveRewardForLevel(player, level)) {
                player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
            }
        }

        player.sendMessage(colorize("&a&l★ &aKillstreak &e" + streak + " &a— reward received!"));
        try {
            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1.0F, 1.5F);
        } catch (Throwable ignored) {}
    }

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
            } catch (NumberFormatException e) {
                continue;
            }
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
        if (name.equals("gapple") || name.equals("golden_apple") || name.equals("gap")) {
            return new ItemStack(Material.GOLDEN_APPLE, amount);
        }
        if (name.equals("fb") || name.equals("fireball") || name.equals("fire_charge")) {
            return new ItemStack(Material.FIREBALL, amount);
        }
        if (name.equals("perl") || name.equals("pearl") || name.equals("ender_pearl")) {
            return new ItemStack(Material.ENDER_PEARL, amount);
        }
        if (name.equals("feather")) {
            return new ItemStack(Material.FEATHER, amount);
        }
        if (name.equals("speed")) {
            return makePotion(1, amount);
        }
        if (name.equals("jump")) {
            return makePotion(2, amount);
        }
        return null;
    }

    // ============================================================
    //  ✅ MAKE POTION — works for ANY level (I through V)
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

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
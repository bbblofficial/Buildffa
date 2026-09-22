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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

public class Kill implements Listener {

    private JavaPlugin plugin;
    private KillListener killListener;
    private DatabaseManager databaseManager;

    private Map<UUID, Long> lastKillTimestamps = new HashMap<UUID, Long>();
    private Map<UUID, Long> lastVictimDeathTimestamps = new HashMap<UUID, Long>();

    public Kill(JavaPlugin plugin, KillListener killListener, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.killListener = killListener;
        this.databaseManager = databaseManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // NOTE: death message / drops / XP clearing is handled by KitRestore.
        // This class only handles stats + kill messages + rewards.

        Player deathPlayer = event.getEntity();
        UUID victimId = deathPlayer.getUniqueId();

        if (Void.isVoidDeath(victimId)) {
            Void.clearVoidDeath(victimId);
            return;
        }

        long now = System.currentTimeMillis();

        long lastVictimDeath = this.lastVictimDeathTimestamps.containsKey(victimId)
                ? this.lastVictimDeathTimestamps.get(victimId).longValue() : 0L;
        if (now - lastVictimDeath < 3000L) {
            return;
        }
        this.lastVictimDeathTimestamps.put(victimId, Long.valueOf(now));

        // ---- Victim stats ----
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

        long lastKillTime = this.lastKillTimestamps.containsKey(killerId)
                ? this.lastKillTimestamps.get(killerId).longValue() : 0L;
        if (now - lastKillTime < 1000L) {
            return;
        }
        this.lastKillTimestamps.put(killerId, Long.valueOf(now));

        int killCount = this.killListener.getKillCount(killer);

        // ---- Killer stats ----
        PlayerData killerData = this.databaseManager.getPlayer(killerId);
        int newStreak = 0;
        if (killerData != null) {
            killerData.addKill();
            killerData.addKillstreak();
            this.databaseManager.savePlayer(killerData);
            newStreak = killerData.getKillstreak();
        }

        // Killer gets full heal + reward
        fullHeal(killer);
        giveKillstreakReward(killer, newStreak);

        // ---- Kill message ----
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
    //  KILLSTREAK REWARDS
    // ============================================================
    private void giveKillstreakReward(Player player, int streak) {
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

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
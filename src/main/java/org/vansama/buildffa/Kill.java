package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
        event.setDeathMessage(null);

        Player deathPlayer = event.getEntity();
        UUID victimId = deathPlayer.getUniqueId();

        // Skip if Void already handled this death
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

        PlayerData victimData = this.databaseManager.getPlayer(victimId);
        if (victimData != null) {
            victimData.addDeath();
            victimData.resetKillstreak();
            this.databaseManager.savePlayer(victimData);
        }

        if (deathPlayer.getKiller() == null) return;

        Player killer = deathPlayer.getKiller();
        UUID killerId = killer.getUniqueId();

        long lastKillTime = this.lastKillTimestamps.containsKey(killerId)
                ? this.lastKillTimestamps.get(killerId).longValue() : 0L;
        if (now - lastKillTime < 1000L) {
            return;
        }
        this.lastKillTimestamps.put(killerId, Long.valueOf(now));

        int killCount = this.killListener.getKillCount(killer);

        PlayerData killerData = this.databaseManager.getPlayer(killerId);
        if (killerData != null) {
            killerData.addKill();
            killerData.addKillstreak();
            this.databaseManager.savePlayer(killerData);
        }

        // ==================== FULL HEAL KILLER ====================
        fullHeal(killer);
        // ==========================================================

        killer.getInventory().addItem(new ItemStack[] { new ItemStack(Material.GOLDEN_APPLE, 1) });

        String killMessage = this.plugin.getConfig().getString("kill");
        if (killMessage != null) {
            String broadcastMessage = colorize(killMessage)
                    .replaceAll("%killer%", killer.getName())
                    .replaceAll("%loser%", deathPlayer.getName())
                    .replaceAll("%killcount%", String.valueOf(killCount));
            Bukkit.broadcastMessage(broadcastMessage);
        }
    }

    /**
     * Fully heals the killer — health, food, saturation, exhaustion,
     * fire, fall distance, and removes all potion effects.
     */
    private void fullHeal(Player player) {
        // Health
        player.setHealth(player.getMaxHealth());

        // Food bar
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);

        // Remove fire / fall damage
        player.setFireTicks(0);
        player.setFallDistance(0.0F);

        // Clear all potion effects (bad or good)
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Reset XP bar state to keep it clean
        // (comment out if you want to keep XP)
        // player.setLevel(0);
        // player.setExp(0.0F);
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
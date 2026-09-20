package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class Kill implements Listener {

    private JavaPlugin plugin;
    private KillListener killListener;
    private DatabaseManager database;

    private Map<UUID, Long> lastKillTimestamps = new HashMap<UUID, Long>();
    private Map<UUID, Long> lastVictimDeathTimestamps = new HashMap<UUID, Long>();

    public Kill(JavaPlugin plugin, KillListener killListener, DatabaseManager database) {
        this.plugin = plugin;
        this.killListener = killListener;
        this.database = database;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deathPlayer = event.getEntity();
        UUID victimId = deathPlayer.getUniqueId();

        long now = System.currentTimeMillis();

        long lastVictimDeath = this.lastVictimDeathTimestamps.containsKey(victimId)
                ? this.lastVictimDeathTimestamps.get(victimId).longValue() : 0L;
        if (now - lastVictimDeath < 3000L) {
            return;
        }
        this.lastVictimDeathTimestamps.put(victimId, Long.valueOf(now));

        PlayerData victimData = this.database.getPlayer(victimId);
        if (victimData != null) {
            victimData.addDeath();
            victimData.resetKillstreak();
            this.database.savePlayer(victimData);
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

        PlayerData killerData = this.database.getPlayer(killerId);
        if (killerData != null) {
            killerData.addKill();
            killerData.addKillstreak();
            this.database.savePlayer(killerData);
        }

        killer.setHealth(killer.getMaxHealth());
        killer.setFoodLevel(20);
        killer.setSaturation(20.0F);

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

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
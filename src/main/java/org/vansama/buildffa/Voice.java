package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Voice implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastSoundTime = new HashMap<UUID, Long>();
    private static final long SOUND_COOLDOWN = 500L;

    private boolean soundsEnabled;
    private boolean killSoundEnabled;
    private boolean deathSoundEnabled;
    private boolean joinSoundEnabled;
    private boolean chatSoundEnabled;

    private float volume;
    private float pitch;

    public Voice(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
        Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
    }

    public void loadConfiguration() {
        FileConfiguration config = this.plugin.getConfig();
        this.soundsEnabled = config.getBoolean("sounds.enabled", true);
        this.killSoundEnabled = config.getBoolean("sounds.kill", true);
        this.deathSoundEnabled = config.getBoolean("sounds.death", true);
        this.joinSoundEnabled = config.getBoolean("sounds.join", true);
        this.chatSoundEnabled = config.getBoolean("sounds.chat", false);
        this.volume = (float) config.getDouble("sounds.volume", 1.0D);
        this.pitch = (float) config.getDouble("sounds.pitch", 1.0D);

        this.plugin.getLogger().info("BuildFFA sounds loaded: " +
                (this.soundsEnabled ? "ENABLED" : "DISABLED"));
    }

    public void reloadConfig() {
        loadConfiguration();
    }

    private boolean canPlay(Player player) {
        if (!this.soundsEnabled) return false;
        if (player == null || !player.isOnline()) return false;

        long now = System.currentTimeMillis();
        long last = this.lastSoundTime.containsKey(player.getUniqueId())
                ? this.lastSoundTime.get(player.getUniqueId()).longValue() : 0L;

        if (now - last < SOUND_COOLDOWN) return false;
        this.lastSoundTime.put(player.getUniqueId(), Long.valueOf(now));
        return true;
    }

    public void playSound(Player player, Sound sound) {
        playSound(player, sound, this.volume, this.pitch);
    }

    public void playSound(Player player, Sound sound, float volume, float pitch) {
        if (!canPlay(player)) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    // ============================================================
    //  KILL SOUND — فقط وقتی بازیکن کسی رو میکشه
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(PlayerDeathEvent event) {
        if (!this.soundsEnabled) return;
        if (!this.killSoundEnabled) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        killer.playSound(killer.getLocation(), Sound.LEVEL_UP, this.volume, 1.2F);
    }

    // ============================================================
    //  DEATH SOUND — وقتی بازیکن میمیره
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!this.soundsEnabled) return;
        if (!this.deathSoundEnabled) return;

        Player victim = event.getEntity();
        if (victim == null) return;

        victim.playSound(victim.getLocation(), Sound.ENDERDRAGON_HIT, 0.6F, 0.8F);
    }

    // ============================================================
    //  JOIN SOUND
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!this.soundsEnabled) return;
        if (!this.joinSoundEnabled) return;

        Player player = event.getPlayer();
        playSound(player, Sound.NOTE_PLING, this.volume, 1.5F);
    }

    // ============================================================
    //  QUIT SOUND
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!this.soundsEnabled) return;
        if (!this.joinSoundEnabled) return;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(event.getPlayer())) continue;
            online.playSound(online.getLocation(), Sound.NOTE_BASS, 0.5F, 0.8F);
        }
    }

    // ============================================================
    //  CHAT SOUND — اختیاری
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!this.soundsEnabled) return;
        if (!this.chatSoundEnabled) return;

        final Player player = event.getPlayer();
        Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.ORB_PICKUP, 0.3F, 1.5F);
                }
            }
        }, 1L);
    }

    public void playKillSound(Player player) {
        if (!this.killSoundEnabled) return;
        playSound(player, Sound.LEVEL_UP, this.volume, 1.2F);
    }

    public void playDeathSound(Player player) {
        if (!this.deathSoundEnabled) return;
        playSound(player, Sound.ENDERDRAGON_HIT, 0.6F, 0.8F);
    }

    public boolean isSoundsEnabled() {
        return this.soundsEnabled;
    }

    public void setSoundsEnabled(boolean enabled) {
        this.soundsEnabled = enabled;
        FileConfiguration config = this.plugin.getConfig();
        config.set("sounds.enabled", Boolean.valueOf(enabled));
        this.plugin.saveConfig();
    }
}
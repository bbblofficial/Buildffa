package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
    private boolean hitSoundEnabled;
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
        this.hitSoundEnabled = config.getBoolean("sounds.hit", true);
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
    //  KILL SOUND — when player kills someone
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(PlayerDeathEvent event) {
        if (!this.soundsEnabled) return;
        if (!this.killSoundEnabled) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        playSound(killer, Sound.LEVEL_UP, this.volume, 1.2F);
    }

    // ============================================================
    //  DEATH SOUND — when player dies
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!this.soundsEnabled) return;
        if (!this.deathSoundEnabled) return;

        Player victim = event.getEntity();
        if (victim == null) return;

        playSound(victim, Sound.ENDERDRAGON_HIT, 0.6F, 0.8F);
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
    //  QUIT SOUND — played to remaining players
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
    //  HIT SOUND — when a player takes a hit
    // ============================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!this.soundsEnabled) return;
        if (!this.hitSoundEnabled) return;

        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        // Sound for attacker (soft click)
        attacker.playSound(attacker.getLocation(), Sound.CLICK, 0.4F, 1.8F);

        // Sound for victim (hurt)
        victim.playSound(victim.getLocation(), Sound.HURT_FLESH, 0.5F, 1.0F);
    }

    // ============================================================
    //  CHAT SOUND — optional, off by default
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

    // ============================================================
    //  PUBLIC METHODS for other classes
    // ============================================================
    public void playKillSound(Player player) {
        if (!this.killSoundEnabled) return;
        playSound(player, Sound.LEVEL_UP, this.volume, 1.2F);
    }

    public void playDeathSound(Player player) {
        if (!this.deathSoundEnabled) return;
        playSound(player, Sound.ENDERDRAGON_HIT, 0.6F, 0.8F);
    }

    public void playHitSound(Player player) {
        if (!this.hitSoundEnabled) return;
        playSound(player, Sound.CLICK, 0.4F, 1.8F);
    }

    public void playLevelUp(Player player) {
        playSound(player, Sound.LEVEL_UP, this.volume, 1.0F);
    }

    public void playAnvilBreak(Player player) {
        playSound(player, Sound.ANVIL_BREAK, 0.5F, 1.0F);
    }

    public void playAnvilLand(Player player) {
        playSound(player, Sound.ANVIL_LAND, 0.5F, 1.0F);
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
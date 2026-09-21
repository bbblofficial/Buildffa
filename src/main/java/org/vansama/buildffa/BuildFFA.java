package org.vansama.buildffa;

import java.io.File;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class BuildFFA extends JavaPlugin implements Listener {

    private Blocks blocks;
    private KitEditor kitEditor;
    private Equip equip;
    private KillListener killListener;
    private ScoreboardManager scoreboardManager;
    private DatabaseManager databaseManager;
    private Voice voice;
    private Connection connection;

    // ==================== COUNTDOWN ====================
    private static int secondsUntilRefresh = 600;
    private static int refreshIntervalSeconds = 600;

    public static int getSecondsUntilRefresh() {
        return secondsUntilRefresh;
    }

    public static int getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public static void resetCountdown() {
        secondsUntilRefresh = refreshIntervalSeconds;
    }
    // ===================================================

    @Override
    public void onEnable() {

        createFolders();
        createConfigIfMissing();
        saveDefaultConfig();
        reloadConfig();
        saveResource("scoreboard.yml", false);

        this.databaseManager = new DatabaseManager(this);
        this.blocks = new Blocks(this);
        this.kitEditor = new KitEditor(this);
        this.equip = new Equip(this);
        this.killListener = new KillListener(this);
        this.scoreboardManager = new ScoreboardManager(this, this.killListener, this.databaseManager);
        this.voice = new Voice(this);
        this.connection = new Connection(this);

        getServer().getPluginManager().registerEvents(this.blocks, (Plugin) this);
        getServer().getPluginManager().registerEvents(this.equip, (Plugin) this);
        getServer().getPluginManager().registerEvents(new High(this), (Plugin) this);
        getServer().getPluginManager().registerEvents(new Void(this), (Plugin) this);
        getServer().getPluginManager().registerEvents(new Welcome(this), (Plugin) this);
        getServer().getPluginManager().registerEvents(new Kill(this, this.killListener, this.databaseManager), (Plugin) this);
        getServer().getPluginManager().registerEvents(new Items(this, this.kitEditor), (Plugin) this);
        getServer().getPluginManager().registerEvents(new Fall(this), (Plugin) this);
        getServer().getPluginManager().registerEvents(this.killListener, (Plugin) this);
        getServer().getPluginManager().registerEvents(this.kitEditor, (Plugin) this);
        getServer().getPluginManager().registerEvents(new Infinite(this), (Plugin) this);
        getServer().getPluginManager().registerEvents(new KitRestore(this, this.equip, this.kitEditor), (Plugin) this);
        getServer().getPluginManager().registerEvents(new SpawnManager(this), (Plugin) this);
        getServer().getPluginManager().registerEvents(new YPvP(this), (Plugin) this);
        getServer().getPluginManager().registerEvents(this.scoreboardManager, (Plugin) this);
        getServer().getPluginManager().registerEvents(this.voice, (Plugin) this);
        getServer().getPluginManager().registerEvents(this.connection, (Plugin) this);
        getServer().getPluginManager().registerEvents(this, (Plugin) this);

        getCommand("buildffa").setExecutor(new BuildFFACommand(this, this.kitEditor, this.scoreboardManager, this.databaseManager));

        // ==================== PlaceholderAPI ====================
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new BuildFFAExpansion(this, this.databaseManager).register();
                getLogger().info("PlaceholderAPI expansion registered!");
            } catch (Throwable t) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        } else {
            getLogger().info("PlaceholderAPI not found - placeholders disabled.");
        }
        // ========================================================

        // ==================== Live countdown ticker ====================
        new BukkitRunnable() {
            @Override
            public void run() {
                if (secondsUntilRefresh > 0) {
                    secondsUntilRefresh--;
                } else {
                    secondsUntilRefresh = refreshIntervalSeconds;
                }
            }
        }.runTaskTimer(this, 20L, 20L);
        // ==============================================================

        getLogger().info("=================================================");
        getLogger().info("  BuildFFA v4.0 - Enabled");
        getLogger().info("  Plugin made by PixelValley");
        getLogger().info("  Author: muvixo");
        getLogger().info("  Database folder: " + this.databaseManager.getDbFolder().getPath());
        getLogger().info("=================================================");

        Bukkit.getScheduler().scheduleSyncRepeatingTask((Plugin) this, new Runnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (entity instanceof Item) {
                            ((Item) entity).remove();
                        }
                    }
                }
            }
        }, 0L, 60L);

        Bukkit.getScheduler().scheduleSyncRepeatingTask((Plugin) this, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                    if (kitEditor.isEditing(player)) continue;
                    if (isEmpty(player)) {
                        equip.giveDiamondArmor(player);
                    }
                }
            }
        }, 20L, 40L);
    }

    private void createFolders() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
            getLogger().info("Created main plugin folder");
        }

        File dbFolder = new File(getDataFolder(), "db");
        if (!dbFolder.exists()) {
            boolean created = dbFolder.mkdirs();
            if (created) {
                getLogger().info("Created db folder at: " + dbFolder.getPath());
            }
        }

        File kitsFolder = new File(getDataFolder(), "kits");
        if (!kitsFolder.exists()) {
            boolean created = kitsFolder.mkdirs();
            if (created) {
                getLogger().info("Created kits folder at: " + kitsFolder.getPath());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.databaseManager.loadPlayer(event.getPlayer().getUniqueId());
        PlayerData data = this.databaseManager.getPlayer(event.getPlayer());
        if (data != null) {
            data.setName(event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.databaseManager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    private boolean isEmpty(Player player) {
        if (player.getInventory().getHelmet() != null) return false;
        if (player.getInventory().getChestplate() != null) return false;
        if (player.getInventory().getLeggings() != null) return false;
        if (player.getInventory().getBoots() != null) return false;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates config.yml if missing, and merges any new keys WITHOUT
     * overwriting existing user values. Safe to update without losing data.
     */
    private void createConfigIfMissing() {
        File configFile = new File(getDataFolder(), "config.yml");
        boolean isNew = !configFile.exists();

        if (isNew) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create config.yml: " + e.getMessage());
                return;
            }
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);

        setIfMissing(cfg, "kill-height", Double.valueOf(0.0D));
        setIfMissing(cfg, "high-limit", Double.valueOf(100.0D));

        setIfMissing(cfg, "void.teleport-instead-of-kill", Boolean.valueOf(true));
        setIfMissing(cfg, "void.teleport-delay", Long.valueOf(0L));
        setIfMissing(cfg, "void.teleport-message", "&cYou fell into the void!.");
        setIfMissing(cfg, "void.death-message", "&c%player% &7fell into the void");
        setIfMissing(cfg, "void.killed-by-message", "&c%player% &7was knocked into the void by &c%killer%");

        setIfMissing(cfg, "ypvp.enabled", Boolean.valueOf(false));
        setIfMissing(cfg, "ypvp.y-level", Double.valueOf(150.0D));
        setIfMissing(cfg, "ypvp.block-projectiles", Boolean.valueOf(true));

        setIfMissing(cfg, "database.autosave", Boolean.valueOf(true));
        setIfMissing(cfg, "database.autosave-interval", Long.valueOf(300L));

        setIfMissing(cfg, "connection-check.enabled", Boolean.valueOf(true));
        setIfMissing(cfg, "connection-check.ping-threshold", Integer.valueOf(150));
        setIfMissing(cfg, "connection-check.check-interval", Integer.valueOf(20));
        setIfMissing(cfg, "connection-check.grace-seconds", Integer.valueOf(30));
        setIfMissing(cfg, "connection-check.warn-cooldown", Integer.valueOf(5));
        setIfMissing(cfg, "connection-check.kick-message", "&cUnstable connection\n&fYour ping is too high: &e%ping%ms&7/&e%max%ms");
        setIfMissing(cfg, "connection-check.broadcast-message", "&c%player% &7was kicked for &eUnstable Connection &7(&c%ping%ms&7)");
        setIfMissing(cfg, "connection-check.bypass-permission", "buildffa.connection.bypass");

        setIfMissing(cfg, "kill", "&a%killer% &7killed &c%loser%");
        setIfMissing(cfg, "Title-Suffix", " &7Kill");
        setIfMissing(cfg, "SubTitle-kill", "&e+1 Kill");
        setIfMissing(cfg, "join-message", "&e%player% &7joined the game &8(&e%online%&7/&e100&8)");
        setIfMissing(cfg, "quit-message", "&e%player% &7left the game &8(&e%online%&7/&e100&8)");

        setIfMissing(cfg, "infinite.food", Boolean.valueOf(true));
        setIfMissing(cfg, "infinite.blocks", Boolean.valueOf(true));

        setIfMissing(cfg, "permissions.ypvp", "buildffa.ypvp");
        setIfMissing(cfg, "permissions.ypvp-bypass", "buildffa.ypvp.bypass");
        setIfMissing(cfg, "permissions.kiteditor", "buildffa.kiteditor");
        setIfMissing(cfg, "permissions.setvoid", "buildffa.setvoid");
        setIfMissing(cfg, "permissions.sethighlimit", "buildffa.sethighlimit");
        setIfMissing(cfg, "permissions.setspawn", "buildffa.setspawn");
        setIfMissing(cfg, "permissions.buildmode", "buildffa.buildmode");
        setIfMissing(cfg, "permissions.reload", "buildffa.reload");
        setIfMissing(cfg, "permissions.scoreboard-toggle", "buildffa.scoreboard.toggle");
        setIfMissing(cfg, "permissions.highlimit-bypass", "buildffa.highlimit.bypass");
        setIfMissing(cfg, "permissions.resetstats", "buildffa.resetstats");
        setIfMissing(cfg, "permissions.connectioncheck", "buildffa.connection");

        setIfMissing(cfg, "messages.no-permission", "&cYou do not have permission to do this.");

        setIfMissing(cfg, "kill-screen.enable-title", Boolean.valueOf(true));
        setIfMissing(cfg, "kill-screen.title", "&e+%killcount% &7Kill");
        setIfMissing(cfg, "kill-screen.subtitle", "&e+1 Kill");
        setIfMissing(cfg, "kill-screen.enable-chat-fallback", Boolean.valueOf(false));
        setIfMissing(cfg, "kill-screen.chat-message", "&a+1 Kill!");

        setIfMissing(cfg, "sounds.enabled", Boolean.valueOf(true));
        setIfMissing(cfg, "sounds.kill", Boolean.valueOf(true));
        setIfMissing(cfg, "sounds.death", Boolean.valueOf(true));
        setIfMissing(cfg, "sounds.join", Boolean.valueOf(true));
        setIfMissing(cfg, "sounds.chat", Boolean.valueOf(false));
        setIfMissing(cfg, "sounds.volume", Double.valueOf(1.0D));
        setIfMissing(cfg, "sounds.pitch", Double.valueOf(1.0D));

        setIfMissing(cfg, "spawn.world", "world");
        setIfMissing(cfg, "spawn.x", Double.valueOf(0.5D));
        setIfMissing(cfg, "spawn.y", Double.valueOf(100.0D));
        setIfMissing(cfg, "spawn.z", Double.valueOf(0.5D));
        setIfMissing(cfg, "spawn.yaw", Float.valueOf(0.0F));
        setIfMissing(cfg, "spawn.pitch", Float.valueOf(0.0F));

        setIfMissing(cfg, "leaderboard-refresh.interval-seconds", Integer.valueOf(600));

        try {
            cfg.save(configFile);
            if (isNew) {
                getLogger().info("Created default config.yml");
            } else {
                getLogger().info("Config.yml merged (existing values preserved).");
            }
        } catch (IOException e) {
            getLogger().warning("Could not save config.yml: " + e.getMessage());
        }

        // Reload interval from config so countdown matches
        refreshIntervalSeconds = cfg.getInt("leaderboard-refresh.interval-seconds", 600);
        secondsUntilRefresh = refreshIntervalSeconds;
    }

    private void setIfMissing(FileConfiguration cfg, String path, Object value) {
        if (!cfg.contains(path)) {
            cfg.set(path, value);
        }
    }

    @Override
    public void onDisable() {
        if (this.databaseManager != null) {
            this.databaseManager.saveAll();
        }
        if (this.blocks != null) {
            this.blocks.onDisable();
        }
        if (this.scoreboardManager != null) {
            this.scoreboardManager.shutdown();
        }
        getLogger().info("BuildFFA disabled.");
    }

    public KitEditor getKitEditor() {
        return this.kitEditor;
    }

    public Equip getEquip() {
        return this.equip;
    }

    public KillListener getKillListener() {
        return this.killListener;
    }

    public ScoreboardManager getScoreboardManager() {
        return this.scoreboardManager;
    }

    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }

    public Voice getVoice() {
        return this.voice;
    }

    public Connection getConnection() {
        return this.connection;
    }
}
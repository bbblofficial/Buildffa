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

public final class BuildFFA extends JavaPlugin implements Listener {

    private Blocks blocks;
    private KitEditor kitEditor;
    private Equip equip;
    private KillListener killListener;
    private ScoreboardManager scoreboardManager;
    private DatabaseManager database;
    private Voice voice;

    @Override
    public void onEnable() {

        // ============================================================
        //  STEP 1: Create all folders FIRST (before anything else)
        // ============================================================
        createFolders();

        // ============================================================
        //  STEP 2: Create config files
        // ============================================================
        createConfigIfMissing();
        saveDefaultConfig();
        reloadConfig();
        saveResource("scoreboard.yml", false);

        // ============================================================
        //  STEP 3: Initialize managers
        // ============================================================
        this.database = new DatabaseManager(this);
        this.blocks = new Blocks(this);
        this.kitEditor = new KitEditor(this);
        this.equip = new Equip(this);
        this.killListener = new KillListener(this);
        this.scoreboardManager = new ScoreboardManager(this, this.killListener, this.database);
        this.voice = new Voice(this);

        // ============================================================
        //  STEP 4: Register listeners
        // ============================================================
        getServer().getPluginManager().registerEvents(this.blocks, (Plugin) this);
        getServer().getPluginManager().registerEvents(this.equip, (Plugin) this);
        getServer().getPluginManager().registerEvents(new High(this), (Plugin) this);
        getServer().getPluginManager().registerEvents(new Void(this), (Plugin) this);
        getServer().getPluginManager().registerEvents(new Welcome(this), (Plugin) this);
        getServer().getPluginManager().registerEvents(new Kill(this, this.killListener, this.database), (Plugin) this);
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
        getServer().getPluginManager().registerEvents(this, (Plugin) this);

        // ============================================================
        //  STEP 5: Register command
        // ============================================================
        getCommand("buildffa").setExecutor(new BuildFFACommand(this, this.kitEditor, this.scoreboardManager, this.database));

        // ============================================================
        //  STEP 6: Startup logs
        // ============================================================
        getLogger().info("=================================================");
        getLogger().info("  BuildFFA v4.0 - Enabled");
        getLogger().info("  Plugin made by PixelValley");
        getLogger().info("  Author: muvixo");
        getLogger().info("  Database folder: " + this.database.getDbFolder().getPath());
        getLogger().info("=================================================");

        // ============================================================
        //  STEP 7: Scheduled tasks
        // ============================================================
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

    // ============================================================
    //  Creates all folders in the plugin's data folder
    //  plugins/BuildFFA/db/
    //  plugins/BuildFFA/kits/
    // ============================================================
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
        this.database.loadPlayer(event.getPlayer().getUniqueId());
        PlayerData data = this.database.getPlayer(event.getPlayer());
        if (data != null) {
            data.setName(event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.database.unloadPlayer(event.getPlayer().getUniqueId());
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

    private void createConfigIfMissing() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);

                cfg.set("kill-height", Double.valueOf(0.0D));
                cfg.set("high-limit", Double.valueOf(100.0D));

                cfg.set("ypvp.enabled", Boolean.valueOf(false));
                cfg.set("ypvp.y-level", Double.valueOf(150.0D));
                cfg.set("ypvp.block-projectiles", Boolean.valueOf(true));

                cfg.set("database.autosave", Boolean.valueOf(true));
                cfg.set("database.autosave-interval", Long.valueOf(300L));

                cfg.set("kill", "&e%killer% &7killed &e%loser% &7(&e%killcount% &7kills)");
                cfg.set("Title-Suffix", " &7Kill");
                cfg.set("SubTitle-kill", "&e+1 Kill");
                cfg.set("join-message", "&e%player% &7joined the game &8(&e%online%&7/&e100&8)");
                cfg.set("quit-message", "&e%player% &7left the game &8(&e%online%&7/&e100&8)");
                cfg.set("void-message", "&c%player% &7fell into the void");

                cfg.set("infinite.food", Boolean.valueOf(true));
                cfg.set("infinite.blocks", Boolean.valueOf(true));

                cfg.set("permissions.ypvp", "buildffa.ypvp");
                cfg.set("permissions.ypvp-bypass", "buildffa.ypvp.bypass");
                cfg.set("permissions.kiteditor", "buildffa.kiteditor");
                cfg.set("permissions.setvoid", "buildffa.setvoid");
                cfg.set("permissions.sethighlimit", "buildffa.sethighlimit");
                cfg.set("permissions.setspawn", "buildffa.setspawn");
                cfg.set("permissions.buildmode", "buildffa.buildmode");
                cfg.set("permissions.reload", "buildffa.reload");
                cfg.set("permissions.scoreboard-toggle", "buildffa.scoreboard.toggle");
                cfg.set("permissions.highlimit-bypass", "buildffa.highlimit.bypass");
                cfg.set("permissions.resetstats", "buildffa.resetstats");

                cfg.set("messages.no-permission", "&cYou do not have permission to do this.");

                cfg.set("kill-screen.enable-title", Boolean.valueOf(true));
                cfg.set("kill-screen.title", "&e+%killcount% &7Kill");
                cfg.set("kill-screen.subtitle", "&e+1 Kill");
                cfg.set("kill-screen.enable-chat-fallback", Boolean.valueOf(false));
                cfg.set("kill-screen.chat-message", "&a+1 Kill!");

                cfg.set("sounds.enabled", Boolean.valueOf(true));
                cfg.set("sounds.kill", Boolean.valueOf(true));
                cfg.set("sounds.death", Boolean.valueOf(true));
                cfg.set("sounds.join", Boolean.valueOf(true));
                cfg.set("sounds.hit", Boolean.valueOf(true));
                cfg.set("sounds.chat", Boolean.valueOf(false));
                cfg.set("sounds.volume", Double.valueOf(1.0D));
                cfg.set("sounds.pitch", Double.valueOf(1.0D));

                cfg.set("spawn.world", "world");
                cfg.set("spawn.x", Double.valueOf(0.5D));
                cfg.set("spawn.y", Double.valueOf(100.0D));
                cfg.set("spawn.z", Double.valueOf(0.5D));
                cfg.set("spawn.yaw", Float.valueOf(0.0F));
                cfg.set("spawn.pitch", Float.valueOf(0.0F));

                cfg.save(configFile);
                getLogger().info("Created default config.yml");
            } catch (IOException e) {
                getLogger().warning("Could not create config.yml: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (this.database != null) {
            this.database.saveAll();
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

    public DatabaseManager getDatabase() {
        return this.database;
    }

    public Voice getVoice() {
        return this.voice;
    }
}
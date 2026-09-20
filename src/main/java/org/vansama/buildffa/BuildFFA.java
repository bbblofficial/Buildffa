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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class BuildFFA extends JavaPlugin {
  
  private Blocks blocks;
  private KitEditor kitEditor;
  private Equip equip;
  private KillListener killListener;
  private ScoreboardManager scoreboardManager;
  
  @Override
  public void onEnable() {
    createConfigIfMissing();
    saveDefaultConfig();
    reloadConfig();
    
    // Save default scoreboard.yml if missing
    saveResource("scoreboard.yml", false);
    
    this.blocks = new Blocks(this);
    this.kitEditor = new KitEditor(this);
    this.equip = new Equip(this);
    this.killListener = new KillListener(this);
    this.scoreboardManager = new ScoreboardManager(this, this.killListener);
    
    // Register all listeners
    getServer().getPluginManager().registerEvents(this.blocks, (Plugin) this);
    getServer().getPluginManager().registerEvents(this.equip, (Plugin) this);
    getServer().getPluginManager().registerEvents(new High(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(new Void(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(new Welcome(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(new Kill(this, this.killListener), (Plugin) this);
    getServer().getPluginManager().registerEvents(new Items(this, this.kitEditor), (Plugin) this);
    getServer().getPluginManager().registerEvents(new Fall(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(this.killListener, (Plugin) this);
    getServer().getPluginManager().registerEvents(this.kitEditor, (Plugin) this);
    getServer().getPluginManager().registerEvents(new Infinite(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(new KitRestore(this, this.equip, this.kitEditor), (Plugin) this);
    getServer().getPluginManager().registerEvents(new SpawnManager(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(new YPvP(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(this.scoreboardManager, (Plugin) this);
    
    // Register command executor
    getCommand("buildffa").setExecutor(new BuildFFACommand(this, this.kitEditor, this.scoreboardManager));
    
    // Log startup
    getLogger().info("Plugin made by PixelValley");
    getLogger().info("You are running on 4.0 (1.8.8 Compatible)");
    getLogger().info("BuildFFA author: muvixo");
    getLogger().info("Created by Muvixo");
    
    // ============================================================
    //  Task 1: Clean up dropped items every 3 seconds
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
    
    // ============================================================
    //  Task 2: Monitor inventory every 2 seconds — if empty, re-give kit
    // ============================================================
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
    if (!getDataFolder().exists()) {
      getDataFolder().mkdirs();
    }
    
    File configFile = new File(getDataFolder(), "config.yml");
    if (!configFile.exists()) {
      try {
        configFile.createNewFile();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        cfg.set("kill-height", Double.valueOf(0.0D));
        cfg.set("high-limit", Double.valueOf(100.0D));
        cfg.set("kill", "&e%killer% &7killed &e%loser% &7(&e%killcount% &7kills)");
        cfg.set("Title-Suffix", " &7Kill");
        cfg.set("SubTitle-kill", "&e+1 Kill");
        cfg.set("join-message", "&e%player% &7joined the game &8(&e%online%&7/&e100&8)");
        cfg.set("quit-message", "&e%player% &7left the game &8(&e%online%&7/&e100&8)");
        cfg.set("infinite.food", Boolean.valueOf(true));
        cfg.set("infinite.blocks", Boolean.valueOf(true));
        cfg.set("ypvp.enabled", Boolean.valueOf(false));
        cfg.set("ypvp.y-level", Double.valueOf(150.0D));
        cfg.save(configFile);
        getLogger().info("Created default config.yml");
      } catch (IOException e) {
        getLogger().warning("Could not create config.yml: " + e.getMessage());
      }
    }
    
    File kitsFolder = new File(getDataFolder(), "kits");
    if (!kitsFolder.exists()) {
      kitsFolder.mkdirs();
      getLogger().info("Created kits folder");
    }
  }
  
  @Override
  public void onDisable() {
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
}
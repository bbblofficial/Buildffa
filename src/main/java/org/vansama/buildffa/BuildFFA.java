package org.vansama.buildffa;

import java.io.File;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class BuildFFA extends JavaPlugin {
  
  private Blocks blocks;
  private KitEditor kitEditor;
  
  @Override
  public void onEnable() {
    // Auto-create config and folders
    createConfigIfMissing();
    saveDefaultConfig();
    reloadConfig();
    
    this.blocks = new Blocks(this);
    this.kitEditor = new KitEditor(this);
    
    getServer().getPluginManager().registerEvents(this.blocks, (Plugin) this);
    getServer().getPluginManager().registerEvents(new Equip(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(new High(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(new Void(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(new Welcome(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(new Kill(this, new KillListener(this)), (Plugin) this);
    getServer().getPluginManager().registerEvents(new Items(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(new Fall(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(new KillListener(this), (Plugin) this);
    getServer().getPluginManager().registerEvents(this.kitEditor, (Plugin) this);
    getServer().getPluginManager().registerEvents(new Infinite(this), (Plugin) this);
    
    getCommand("buildffa").setExecutor(new BuildFFACommand(this, this.kitEditor));
    
    getLogger().info("Plugin made by PixelValley");
    getLogger().info("You are running on 3.3 (1.8.8 Compatible)");
    getLogger().info("BuildFFA author: VanSaMa");
    getLogger().info("Created by Muvixo");
    
    // Clean dropped items every 3 seconds
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
  }
  
  private void createConfigIfMissing() {
    // Ensure plugin data folder exists
    if (!getDataFolder().exists()) {
      getDataFolder().mkdirs();
    }
    
    // Create config.yml if missing
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
        cfg.save(configFile);
        getLogger().info("Created default config.yml");
      } catch (IOException e) {
        getLogger().warning("Could not create config.yml: " + e.getMessage());
      }
    }
    
    // Create kits folder if missing
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
  }
  
  public KitEditor getKitEditor() {
    return this.kitEditor;
  }
}

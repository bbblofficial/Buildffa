package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class BuildFFA extends JavaPlugin {
  
  private Blocks blocks;
  private KitEditor kitEditor;
  
  @Override
  public void onEnable() {
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
    
    getCommand("buildffa").setExecutor(new BuildFFACommand(this, this.kitEditor));
    
    getLogger().info("Plugin made by PixelValley");
    getLogger().info("You are running on 3.1");
    getLogger().info("BuildFFA author: VanSaMa");
    getLogger().info("Created by Muvixo");
    
    Bukkit.getScheduler().scheduleSyncRepeatingTask((Plugin) this, () -> {
      for (World world : Bukkit.getWorlds()) {
        for (Entity entity : world.getEntities()) {
          if (entity instanceof Item) {
            ((Item) entity).remove();
          }
        }
      }
    }, 0L, 60L);
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

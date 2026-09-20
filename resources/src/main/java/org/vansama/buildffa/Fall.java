package org.vansama.buildffa;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Fall implements Listener {
  private JavaPlugin plugin;
  
  public Fall(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  @EventHandler
  public void onPlayerFallDamage(EntityDamageEvent event) {
    if (event.getEntity() instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
      Player player = (Player) event.getEntity();
      event.setDamage(0.0D);
    }
  }
  
  @EventHandler
  public void onPlayerDamageByEntity(EntityDamageByEntityEvent event) {
    if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
      if (event.getEntity() instanceof Player) {
        Player player = (Player) event.getEntity();
        event.setDamage(0.0D);
      }
    }
  }
}

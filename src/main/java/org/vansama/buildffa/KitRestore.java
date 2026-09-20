package org.vansama.buildffa;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class KitRestore implements Listener {
  
  private final JavaPlugin plugin;
  private final Equip equip;
  private final KitEditor kitEditor;
  
  public KitRestore(JavaPlugin plugin, Equip equip, KitEditor kitEditor) {
    this.plugin = plugin;
    this.equip = equip;
    this.kitEditor = kitEditor;
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    final Player player = event.getPlayer();
    Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (!kitEditor.isEditing(player)) {
          equip.giveDiamondArmor(player);
        }
      }
    }, 10L);
  }
  
  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    event.getDrops().clear();
    final Player player = event.getEntity();
    Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (!kitEditor.isEditing(player)) {
          equip.giveDiamondArmor(player);
        }
      }
    }, 5L);
  }
  
  @EventHandler
  public void onCommand(PlayerCommandPreprocessEvent event) {
    String message = event.getMessage().toLowerCase();
    if (message.startsWith("/clear") || message.contains(" clear ")) {
      final Player player = event.getPlayer();
      
      Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
        @Override
        public void run() {
          if (!kitEditor.isEditing(player) && player.isOnline() && player.getGameMode() != GameMode.CREATIVE) {
            if (isEmpty(player)) {
              equip.giveDiamondArmor(player);
              player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                  "&aYour kit has been restored after /clear."));
            }
          }
        }
      }, 5L);
    }
  }
  
  private boolean isEmpty(Player player) {
    if (player.getInventory().getHelmet() != null) return false;
    if (player.getInventory().getChestplate() != null) return false;
    if (player.getInventory().getLeggings() != null) return false;
    if (player.getInventory().getBoots() != null) return false;
    for (ItemStack item : player.getInventory().getContents()) {
      if (item != null && item.getType() != Material.AIR) {
        return false;
      }
    }
    return true;
  }
}
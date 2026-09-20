package org.vansama.buildffa;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Infinite implements Listener {
  
  private final JavaPlugin plugin;
  
  public Infinite(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  // ============================================================
  // Infinite hunger bar
  // ============================================================
  @EventHandler
  public void onFoodLevelChange(FoodLevelChangeEvent event) {
    if (!(event.getEntity() instanceof Player)) return;
    Player player = (Player) event.getEntity();
    if (event.getFoodLevel() < 20) {
      event.setCancelled(true);
      player.setFoodLevel(20);
      player.setSaturation(20.0F);
      player.setExhaustion(0.0F);
    }
  }
  
  // ============================================================
  // Golden apple heals to full — but is CONSUMED normally
  // (count decreases by 1 each time; no restore)
  // ============================================================
  @EventHandler
  public void onItemConsume(PlayerItemConsumeEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getItem();
    Material type = item.getType();
    
    if (type == Material.GOLDEN_APPLE
        || type == Material.GOLDEN_CARROT
        || type == Material.COOKED_BEEF
        || type == Material.BREAD) {
      // Full heal
      player.setHealth(player.getMaxHealth());
      player.setFoodLevel(20);
      player.setSaturation(20.0F);
      player.setExhaustion(0.0F);
    }
    // NOTE: Do NOT cancel and do NOT restore — item is consumed normally
  }
  
  // ============================================================
  // Infinite blocks — placed blocks are refilled
  // ============================================================
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent event) {
    final Player player = event.getPlayer();
    
    if (player.getGameMode() == GameMode.CREATIVE) return;
    if (!event.canBuild()) return;
    
    final ItemStack itemInHand = player.getItemInHand();
    if (itemInHand == null || itemInHand.getType() == Material.AIR) return;
    
    final Material type = itemInHand.getType();
    final short data = itemInHand.getDurability();
    final int amountBeforePlace = itemInHand.getAmount();
    
    this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        ItemStack current = player.getItemInHand();
        if (current != null && current.getType() == type && current.getDurability() == data) {
          current.setAmount(amountBeforePlace);
          player.updateInventory();
          return;
        }
        
        for (int i = 0; i < player.getInventory().getSize(); i++) {
          ItemStack slot = player.getInventory().getItem(i);
          if (slot != null && slot.getType() == type && slot.getDurability() == data) {
            if (slot.getAmount() == amountBeforePlace - 1) {
              slot.setAmount(amountBeforePlace);
              player.updateInventory();
              break;
            }
          }
        }
      }
    }, 1L);
  }
}
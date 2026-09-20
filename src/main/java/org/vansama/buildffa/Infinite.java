package org.vansama.buildffa;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Infinite implements Listener {
  
  private final JavaPlugin plugin;
  
  public Infinite(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  @EventHandler
  public void onFoodLevelChange(FoodLevelChangeEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getEntity();
    if (event.getFoodLevel() < 20) {
      event.setCancelled(true);
      player.setFoodLevel(20);
      player.setSaturation(20.0F);
      player.setExhaustion(0.0F);
    }
  }
  
  @EventHandler
  public void onItemConsume(PlayerItemConsumeEvent event) {
    final Player player = event.getPlayer();
    ItemStack item = event.getItem();
    
    if (item.getType() == Material.GOLDEN_APPLE
        || item.getType() == Material.GOLDEN_CARROT
        || item.getType() == Material.COOKED_BEEF
        || item.getType() == Material.BREAD) {
      
      final ItemStack consumedItem = item.clone();
      consumedItem.setAmount(1);
      
      this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
        @Override
        public void run() {
          player.getInventory().addItem(new ItemStack[] { consumedItem });
        }
      }, 1L);
    }
    
    player.setFoodLevel(20);
    player.setSaturation(20.0F);
  }
  
  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    final Player player = event.getPlayer();
    
    if (player.getGameMode() == GameMode.CREATIVE) {
      return;
    }
    
    final ItemStack itemInHand = player.getItemInHand();
    
    if (itemInHand == null || itemInHand.getType() == Material.AIR) {
      return;
    }
    
    final Material type = itemInHand.getType();
    final short data = itemInHand.getDurability();
    
    this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        PlayerInventory inv = player.getInventory();
        ItemStack current = player.getItemInHand();
        
        if (current != null && current.getType() == type) {
          // Refill to original stack size (never exceed max)
          int amount = current.getAmount();
          int max = current.getMaxStackSize();
          if (amount < max) {
            // Only add if not already at max — prevents "-128" overflow
            if (amount + 1 <= max) {
              current.setAmount(amount + 1);
            }
          }
        } else {
          for (int i = 0; i < inv.getSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot != null && slot.getType() == type && slot.getDurability() == data) {
              int amount = slot.getAmount();
              int max = slot.getMaxStackSize();
              if (amount < max && amount + 1 <= max) {
                slot.setAmount(amount + 1);
              }
              break;
            }
          }
        }
      }
    }, 1L);
  }
}
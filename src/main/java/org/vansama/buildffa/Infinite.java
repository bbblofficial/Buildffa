package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Infinite implements Listener {
  
  private final JavaPlugin plugin;
  // Track original item amounts so we restore the SAME amount, not +1
  private final Map<UUID, Integer> prePlaceAmount = new HashMap<UUID, Integer>();
  
  public Infinite(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
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
  
  /**
   * When a block is placed:
   * 1. Record the item amount BEFORE the placement consumes one.
   * 2. After Bukkit processes the placement (1 tick later), set the slot amount back
   *    to the ORIGINAL amount. Never add +1 repeatedly.
   *
   * Result: the stack size stays the same. Placing a block does not decrease the count.
   * No duplication, no overflow.
   */
  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    final Player player = event.getPlayer();
    
    if (player.getGameMode() == GameMode.CREATIVE) return;
    if (!event.canBuild()) return;
    
    final ItemStack itemInHand = player.getItemInHand();
    if (itemInHand == null || itemInHand.getType() == Material.AIR) return;
    
    final Material type = itemInHand.getType();
    final short data = itemInHand.getDurability();
    final int amountBeforePlace = itemInHand.getAmount(); // e.g. 64
    
    this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        // Find the slot that had this item in hand and set it back to original amount
        // The block was just placed from THIS slot, so amount is now (amountBeforePlace - 1)
        // We want it to be amountBeforePlace again.
        ItemStack current = player.getItemInHand();
        if (current != null && current.getType() == type && current.getDurability() == data) {
          current.setAmount(amountBeforePlace);
          return;
        }
        
        // If the slot changed (auto-sort plugin etc), search the whole inventory for the same item
        // and restore the amount only if it's one less than original
        for (int i = 0; i < player.getInventory().getSize(); i++) {
          ItemStack slot = player.getInventory().getItem(i);
          if (slot != null && slot.getType() == type && slot.getDurability() == data) {
            if (slot.getAmount() == amountBeforePlace - 1) {
              slot.setAmount(amountBeforePlace);
              break;
            }
          }
        }
      }
    }, 1L);
  }
}
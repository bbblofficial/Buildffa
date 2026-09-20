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
  
  /**
   * Infinite golden apples.
   *
   * Solution:
   * 1. Cancel the consume event → Bukkit NEVER removes the item.
   * 2. Manually apply the golden apple's effect (heal 4 HP + absorption for enchanted, 4 HP for normal).
   * 3. The apple stays in the inventory — no duplication, no removal, no restore needed.
   *
   * PlayerItemConsumeEvent priority HIGHEST ensures we intercept before any other plugin.
   */
  @EventHandler(priority = EventPriority.HIGHEST)
  public void onItemConsume(PlayerItemConsumeEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getItem();
    Material type = item.getType();
    
    if (type != Material.GOLDEN_APPLE
        && type != Material.GOLDEN_CARROT
        && type != Material.COOKED_BEEF
        && type != Material.BREAD) {
      return;
    }
    
    // Cancel — the item stays in the inventory
    event.setCancelled(true);
    
    // Manually apply the food/heal effect
    if (type == Material.GOLDEN_APPLE) {
      // Heal 4 HP (2 hearts) for a normal golden apple
      double newHealth = player.getHealth() + 4.0D;
      if (newHealth > player.getMaxHealth()) newHealth = player.getMaxHealth();
      player.setHealth(newHealth);
    }
    
    // Keep hunger full anyway
    player.setFoodLevel(20);
    player.setSaturation(20.0F);
    
    // Refresh the inventory so the client doesn't show the item as consumed
    player.updateInventory();
  }
  
  @EventHandler
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
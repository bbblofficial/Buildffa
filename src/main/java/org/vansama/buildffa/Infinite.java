package org.vansama.buildffa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
  private final Map<UUID, Integer> preEatAmount = new HashMap<UUID, Integer>();
  
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
   * Infinite golden apples + full heal.
   *
   * When the player eats a golden apple:
   * 1. Fully heal to max HP
   * 2. Full hunger + saturation
   * 3. Restore the apple after 2 ticks (so count stays the same)
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onItemConsume(final PlayerItemConsumeEvent event) {
    final Player player = event.getPlayer();
    final ItemStack item = event.getItem();
    final Material type = item.getType();
    
    if (type != Material.GOLDEN_APPLE
        && type != Material.GOLDEN_CARROT
        && type != Material.COOKED_BEEF
        && type != Material.BREAD) {
      return;
    }
    
    final int amountBeforeEat = item.getAmount();
    final short dataBeforeEat = item.getDurability();
    
    // === FULL HEAL ===
    player.setHealth(player.getMaxHealth());
    player.setFoodLevel(20);
    player.setSaturation(20.0F);
    
    // === Restore the apple after consume animation (2 ticks) ===
    this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (!player.isOnline()) return;
        
        boolean restored = false;
        
        // Try hand slot
        ItemStack hand = player.getItemInHand();
        if (hand != null && hand.getType() == type && hand.getDurability() == dataBeforeEat) {
          if (hand.getAmount() < amountBeforeEat) {
            hand.setAmount(amountBeforeEat);
            restored = true;
          }
        }
        
        // Search whole inventory
        if (!restored) {
          for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot != null && slot.getType() == type && slot.getDurability() == dataBeforeEat) {
              if (slot.getAmount() < amountBeforeEat) {
                slot.setAmount(amountBeforeEat);
                restored = true;
                break;
              }
            }
          }
        }
        
        // Add one back if not found
        if (!restored) {
          player.getInventory().addItem(new ItemStack[] { new ItemStack(type, 1, dataBeforeEat) });
        }
        
        player.updateInventory();
      }
    }, 2L);
  }
  
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
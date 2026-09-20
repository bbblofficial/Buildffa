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
  
  // Track the item amount BEFORE eating, to restore the exact same amount after
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
   * Infinite golden apples.
   *
   * 1. Record how many apples the player had BEFORE eating.
   * 2. Let the event fire normally (animation plays, effect applies).
   * 3. After 2 ticks, restore the amount to what it was.
   *
   * This avoids both duplication AND loss of the animation.
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
    
    // Restore the item after the consume animation finishes (2 ticks later)
    this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (!player.isOnline()) return;
        
        // Look for a slot with fewer items than expected, then top it up
        // Because the player may have moved things around
        boolean restored = false;
        
        // Try the hand slot first
        ItemStack hand = player.getItemInHand();
        if (hand != null && hand.getType() == type && hand.getDurability() == dataBeforeEat) {
          if (hand.getAmount() < amountBeforeEat) {
            hand.setAmount(amountBeforeEat);
            restored = true;
          }
        }
        
        if (!restored) {
          // Search whole inventory
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
        
        if (!restored) {
          // Slot had exactly this many, or the item was fully consumed — add one back
          player.getInventory().addItem(new ItemStack[] { new ItemStack(type, 1, dataBeforeEat) });
        }
        
        player.updateInventory();
      }
    }, 2L);
    
    player.setFoodLevel(20);
    player.setSaturation(20.0F);
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
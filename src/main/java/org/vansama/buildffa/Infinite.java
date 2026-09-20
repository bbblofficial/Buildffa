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
  
  /**
   * Infinite golden apples / food.
   *
   * How it works:
   * 1. Player eats 1 apple (slot drops from N to N-1).
   * 2. After 1 tick, Bukkit has finished the consume.
   * 3. We add exactly 1 apple back — the one they just ate.
   * 4. Because we wait a tick, we don't double-add (which was the bug).
   */
  @EventHandler
  public void onItemConsume(final PlayerItemConsumeEvent event) {
    final Player player = event.getPlayer();
    ItemStack item = event.getItem();
    
    Material type = item.getType();
    if (type != Material.GOLDEN_APPLE
        && type != Material.GOLDEN_CARROT
        && type != Material.COOKED_BEEF
        && type != Material.BREAD) {
      return;
    }
    
    // Wait 1 tick for Bukkit to finish consuming, then restore exactly 1 item
    this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (!player.isOnline()) return;
        player.getInventory().addItem(new ItemStack[] { new ItemStack(event.getItem().getType(), 1) });
        player.updateInventory();
      }
    }, 1L);
    
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
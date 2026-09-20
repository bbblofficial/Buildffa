package org.vansama.buildffa;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Equip implements Listener {
  private JavaPlugin plugin;
  
  public Equip(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    giveDiamondArmor(player);
  }
  
  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    final Player player = event.getPlayer();
    this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        giveDiamondArmor(player);
      }
    }, 5L);
  }
  
  public void giveDiamondArmor(Player player) {
    if (plugin.getConfig().getConfigurationSection("kits." + player.getUniqueId().toString()) != null) {
      applyCustomKit(player);
      return;
    }
    
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);
    
    // === ARMOR ===
    player.getInventory().setHelmet(unbreakable(new ItemStack(Material.IRON_HELMET)));
    player.getInventory().setChestplate(unbreakable(new ItemStack(Material.IRON_CHESTPLATE)));
    player.getInventory().setLeggings(unbreakable(new ItemStack(Material.DIAMOND_LEGGINGS)));
    player.getInventory().setBoots(unbreakable(new ItemStack(Material.DIAMOND_BOOTS)));
    
    // === SLOT 0: Stone Sword (Sharpness II, Unbreakable) ===
    ItemStack sword = new ItemStack(Material.STONE_SWORD);
    sword.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 2);
    player.getInventory().setItem(0, unbreakable(sword));
    
    // === SLOT 1: Cyan Wool x 64 ===
    ItemStack cyanWool = new ItemStack(Material.WOOL, 64, (short) 9);
    player.getInventory().setItem(1, cyanWool);
    
    // === SLOT 2: Shears (Unbreakable) ===
    player.getInventory().setItem(2, unbreakable(new ItemStack(Material.SHEARS)));
    
    // === SLOT 3: (empty) ===
    // intentionally empty
    
    // === SLOT 4: Iron Pickaxe (Efficiency II, Unbreakable) ===
    ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
    pickaxe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 2);
    player.getInventory().setItem(4, unbreakable(pickaxe));
    
    // === SLOT 5: Iron Axe (Efficiency I, Unbreakable) ===
    ItemStack axe = new ItemStack(Material.IRON_AXE);
    axe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1);
    player.getInventory().setItem(5, unbreakable(axe));
  }
  
  public static ItemStack unbreakable(ItemStack item) {
    if (item == null) return null;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.spigot().setUnbreakable(true);
    item.setItemMeta(meta);
    return item;
  }
  
  private void applyCustomKit(Player player) {
    org.bukkit.configuration.ConfigurationSection section =
        plugin.getConfig().getConfigurationSection("kits." + player.getUniqueId().toString());
    if (section == null) return;
    
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);
    
    if (section.contains("helmet")) {
      player.getInventory().setHelmet(section.getItemStack("helmet"));
    }
    if (section.contains("chestplate")) {
      player.getInventory().setChestplate(section.getItemStack("chestplate"));
    }
    if (section.contains("leggings")) {
      player.getInventory().setLeggings(section.getItemStack("leggings"));
    }
    if (section.contains("boots")) {
      player.getInventory().setBoots(section.getItemStack("boots"));
    }
    if (section.contains("contents")) {
      java.util.List<?> list = section.getList("contents");
      if (list != null) {
        for (int i = 0; i < list.size(); i++) {
          Object obj = list.get(i);
          if (obj instanceof ItemStack) {
            player.getInventory().setItem(i, (ItemStack) obj);
          }
        }
      }
    }
  }
}
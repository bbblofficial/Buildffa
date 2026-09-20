package org.vansama.buildffa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class KitEditor implements Listener {
  
  private final JavaPlugin plugin;
  private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
  private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
  private final Map<UUID, Boolean> editingKit = new HashMap<>();
  
  public KitEditor(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  public void openKitEditor(Player player) {
    if (this.editingKit.containsKey(player.getUniqueId()) && this.editingKit.get(player.getUniqueId()).booleanValue()) {
      player.sendMessage(colorize("&cYou are already editing your kit."));
      return;
    }
    
    this.savedInventories.put(player.getUniqueId(), player.getInventory().getContents().clone());
    this.savedArmor.put(player.getUniqueId(), player.getInventory().getArmorContents().clone());
    this.editingKit.put(player.getUniqueId(), Boolean.valueOf(true));
    
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);
    
    FileConfiguration config = this.plugin.getConfig();
    ConfigurationSection kitSection = config.getConfigurationSection("kits." + player.getUniqueId().toString());
    
    if (kitSection != null) {
      loadKitFromConfig(player, kitSection);
    } else {
      loadDefaultKit(player);
    }
    
    player.sendMessage(colorize("&8&m----------------------------------"));
    player.sendMessage(colorize("&6&lKit Editor"));
    player.sendMessage(colorize("&7Edit your kit in your inventory."));
    player.sendMessage(colorize("&7Type &e/buildffa kiteditor save &7to save."));
    player.sendMessage(colorize("&7Type &e/buildffa kiteditor cancel &7to cancel."));
    player.sendMessage(colorize("&8&m----------------------------------"));
  }
  
  private void loadDefaultKit(Player player) {
    player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
    player.getInventory().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
    player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
    player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
    
    ItemStack ironSword = new ItemStack(Material.IRON_SWORD);
    ironSword.addEnchantment(Enchantment.KNOCKBACK, 2);
    player.getInventory().addItem(new ItemStack[] { ironSword });
    
    ItemStack dig = new ItemStack(Material.IRON_PICKAXE);
    dig.addEnchantment(Enchantment.DIG_SPEED, 3);
    player.getInventory().addItem(new ItemStack[] { dig });
    
    ItemStack bow = new ItemStack(Material.BOW);
    bow.addEnchantment(Enchantment.ARROW_KNOCKBACK, 1);
    bow.addEnchantment(Enchantment.ARROW_DAMAGE, 2);
    player.getInventory().addItem(new ItemStack[] { bow });
    
    player.getInventory().addItem(new ItemStack[] { new ItemStack(Material.ARROW, 12) });
    player.getInventory().addItem(new ItemStack[] { new ItemStack(Material.GOLDEN_APPLE, 3) });
    player.getInventory().addItem(new ItemStack[] { new ItemStack(Material.SANDSTONE, 128) });
    player.getInventory().addItem(new ItemStack[] { new ItemStack(Material.ENDER_PEARL) });
  }
  
  private void loadKitFromConfig(Player player, ConfigurationSection section) {
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
      List<?> list = section.getList("contents");
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
  
  public void saveKit(Player player) {
    if (!isEditing(player)) {
      player.sendMessage(colorize("&cYou are not editing your kit."));
      return;
    }
    
    FileConfiguration config = this.plugin.getConfig();
    String path = "kits." + player.getUniqueId().toString();
    
    config.set(path + ".helmet", player.getInventory().getHelmet());
    config.set(path + ".chestplate", player.getInventory().getChestplate());
    config.set(path + ".leggings", player.getInventory().getLeggings());
    config.set(path + ".boots", player.getInventory().getBoots());
    config.set(path + ".contents", new ArrayList<ItemStack>(Arrays.asList(player.getInventory().getContents())));
    
    this.plugin.saveConfig();
    
    this.editingKit.remove(player.getUniqueId());
    
    player.sendMessage(colorize("&aYour kit has been saved!"));
    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
    
    restoreInventory(player);
  }
  
  public void cancelKit(Player player) {
    if (!isEditing(player)) {
      player.sendMessage(colorize("&cYou are not editing your kit."));
      return;
    }
    
    this.editingKit.remove(player.getUniqueId());
    player.sendMessage(colorize("&cKit editing cancelled."));
    restoreInventory(player);
  }
  
  public void resetKit(Player player) {
    FileConfiguration config = this.plugin.getConfig();
    config.set("kits." + player.getUniqueId().toString(), null);
    this.plugin.saveConfig();
    
    if (isEditing(player)) {
      player.getInventory().clear();
      player.getInventory().setArmorContents(null);
      loadDefaultKit(player);
    } else {
      Equip equip = new Equip(this.plugin);
      equip.giveDiamondArmor(player);
    }
    
    player.sendMessage(colorize("&aYour kit has been reset to the default kit."));
  }
  
  private void restoreInventory(Player player) {
    ItemStack[] saved = this.savedInventories.remove(player.getUniqueId());
    ItemStack[] savedArmorContents = this.savedArmor.remove(player.getUniqueId());
    
    player.getInventory().clear();
    
    if (saved != null) {
      player.getInventory().setContents(saved);
    }
    if (savedArmorContents != null) {
      player.getInventory().setArmorContents(savedArmorContents);
    }
  }
  
  public boolean isEditing(Player player) {
    return this.editingKit.containsKey(player.getUniqueId()) && this.editingKit.get(player.getUniqueId()).booleanValue();
  }
  
  public void applyKit(Player player) {
    FileConfiguration config = this.plugin.getConfig();
    ConfigurationSection kitSection = config.getConfigurationSection("kits." + player.getUniqueId().toString());
    
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);
    
    if (kitSection != null) {
      loadKitFromConfig(player, kitSection);
    } else {
      loadDefaultKit(player);
    }
  }
  
  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getWhoClicked();
    if (isEditing(player)) {
      return;
    }
  }
  
  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player)) {
      return;
    }
  }
  
  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}

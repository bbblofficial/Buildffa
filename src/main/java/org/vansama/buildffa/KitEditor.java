package org.vansama.buildffa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class KitEditor implements Listener {
  
  private final JavaPlugin plugin;
  private final Map<UUID, ItemStack[]> savedInventories = new HashMap<UUID, ItemStack[]>();
  private final Map<UUID, ItemStack[]> savedArmor = new HashMap<UUID, ItemStack[]>();
  private final Map<UUID, Boolean> editingKit = new HashMap<UUID, Boolean>();
  
  public KitEditor(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  public void openKitEditorGUI(final Player player) {
    this.savedInventories.put(player.getUniqueId(), player.getInventory().getContents().clone());
    this.savedArmor.put(player.getUniqueId(), player.getInventory().getArmorContents().clone());
    
    FileConfiguration config = this.plugin.getConfig();
    ConfigurationSection kitSection = config.getConfigurationSection("kits." + player.getUniqueId().toString());
    
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);
    
    if (kitSection != null) {
      loadKitFromConfig(player, kitSection);
    } else {
      loadDefaultKit(player);
    }
    
    this.editingKit.put(player.getUniqueId(), Boolean.valueOf(true));
    
    Inventory gui = Bukkit.createInventory(null, 27, colorize("&6&lKit Editor"));
    
    ItemStack saveBtn = createButton(Material.EMERALD_BLOCK, colorize("&a&lSave Kit"), Arrays.asList(
        colorize("&7Click to save your current kit")
    ));
    ItemStack cancelBtn = createButton(Material.REDSTONE_BLOCK, colorize("&c&lCancel"), Arrays.asList(
        colorize("&7Click to cancel editing"),
        colorize("&7Your kit will NOT be saved")
    ));
    ItemStack resetBtn = createButton(Material.BARRIER, colorize("&e&lReset Kit"), Arrays.asList(
        colorize("&7Click to reset to default kit")
    ));
    ItemStack infoBtn = createButton(Material.BOOK, colorize("&b&lInfo"), Arrays.asList(
        colorize("&7Edit your items in your inventory"),
        colorize("&7then click Save to keep them"),
        colorize("&7or Cancel to discard changes")
    ));
    
    gui.setItem(11, saveBtn);
    gui.setItem(13, infoBtn);
    gui.setItem(15, cancelBtn);
    gui.setItem(22, resetBtn);
    
    Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        player.openInventory(gui);
        player.sendMessage(colorize("&8&m----------------------------------"));
        player.sendMessage(colorize("&6&lKit Editor &7opened"));
        player.sendMessage(colorize("&7Edit your inventory, then click &aSave &7or &cCancel"));
        player.sendMessage(colorize("&8&m----------------------------------"));
      }
    }, 2L);
  }
  
  private ItemStack createButton(Material mat, String name, List<String> lore) {
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(name);
    meta.setLore(lore);
    item.setItemMeta(meta);
    return item;
  }
  
  private void loadDefaultKit(Player player) {
    // === ARMOR ===
    player.getInventory().setHelmet(unbreakable(new ItemStack(Material.IRON_HELMET)));
    player.getInventory().setChestplate(unbreakable(new ItemStack(Material.IRON_CHESTPLATE)));
    player.getInventory().setLeggings(unbreakable(new ItemStack(Material.DIAMOND_LEGGINGS)));
    player.getInventory().setBoots(unbreakable(new ItemStack(Material.DIAMOND_BOOTS)));
    
    // === SLOT 0: Stone Sword ===
    player.getInventory().setItem(0, unbreakable(new ItemStack(Material.STONE_SWORD)));
    
    // === SLOT 1: Cyan Wool x 128 ===
    ItemStack cyanWool = new ItemStack(Material.WOOL, 128, (short) 9);
    player.getInventory().setItem(1, cyanWool);
    
    // === SLOT 2: Bow (Punch 1, Power 2) ===
    ItemStack bow = new ItemStack(Material.BOW);
    bow.addEnchantment(Enchantment.ARROW_KNOCKBACK, 1);
    bow.addEnchantment(Enchantment.ARROW_DAMAGE, 2);
    player.getInventory().setItem(2, unbreakable(bow));
    
    // === SLOT 3: Iron Pickaxe (Efficiency 2) ===
    ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
    pickaxe.addEnchantment(Enchantment.DIG_SPEED, 2);
    player.getInventory().setItem(3, unbreakable(pickaxe));
    
    // === SLOT 4: Iron Axe (Efficiency 1) ===
    ItemStack axe = new ItemStack(Material.IRON_AXE);
    axe.addEnchantment(Enchantment.DIG_SPEED, 1);
    player.getInventory().setItem(4, unbreakable(axe));
    
    // === Extra items ===
    player.getInventory().addItem(new ItemStack[] { new ItemStack(Material.ARROW, 12) });
    player.getInventory().addItem(new ItemStack[] { new ItemStack(Material.GOLDEN_APPLE, 3) });
    player.getInventory().addItem(new ItemStack[] { new ItemStack(Material.ENDER_PEARL) });
  }
  
  public static ItemStack unbreakable(ItemStack item) {
    if (item == null) return null;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.spigot().setUnbreakable(true);
    item.setItemMeta(meta);
    return item;
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
    FileConfiguration config = this.plugin.getConfig();
    String path = "kits." + player.getUniqueId().toString();
    
    config.set(path + ".helmet", player.getInventory().getHelmet());
    config.set(path + ".chestplate", player.getInventory().getChestplate());
    config.set(path + ".leggings", player.getInventory().getLeggings());
    config.set(path + ".boots", player.getInventory().getBoots());
    config.set(path + ".contents", new ArrayList<ItemStack>(Arrays.asList(player.getInventory().getContents())));
    
    this.plugin.saveConfig();
    
    this.editingKit.remove(player.getUniqueId());
    this.savedInventories.remove(player.getUniqueId());
    this.savedArmor.remove(player.getUniqueId());
    
    player.sendMessage(colorize("&aYour kit has been saved!"));
    player.playSound(player.getLocation(), Sound.LEVEL_UP, 1.0F, 1.0F);
    player.closeInventory();
  }
  
  public void cancelKit(Player player) {
    this.editingKit.remove(player.getUniqueId());
    
    ItemStack[] saved = this.savedInventories.remove(player.getUniqueId());
    ItemStack[] savedArmorContents = this.savedArmor.remove(player.getUniqueId());
    
    player.getInventory().clear();
    if (saved != null) {
      player.getInventory().setContents(saved);
    }
    if (savedArmorContents != null) {
      player.getInventory().setArmorContents(savedArmorContents);
    }
    
    player.sendMessage(colorize("&cKit editing cancelled."));
    player.closeInventory();
  }
  
  public void resetKit(Player player) {
    FileConfiguration config = this.plugin.getConfig();
    config.set("kits." + player.getUniqueId().toString(), null);
    this.plugin.saveConfig();
    
    this.savedInventories.remove(player.getUniqueId());
    this.savedArmor.remove(player.getUniqueId());
    
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
    
    if (event.getInventory() == null || event.getInventory().getTitle() == null) {
      return;
    }
    
    if (!event.getInventory().getTitle().equals(colorize("&6&lKit Editor"))) {
      return;
    }
    
    event.setCancelled(true);
    
    ItemStack clicked = event.getCurrentItem();
    if (clicked == null) {
      return;
    }
    
    if (clicked.getType() == Material.EMERALD_BLOCK) {
      saveKit(player);
    } else if (clicked.getType() == Material.REDSTONE_BLOCK) {
      cancelKit(player);
    } else if (clicked.getType() == Material.BARRIER) {
      resetKit(player);
    }
  }
  
  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    // Nothing
  }
  
  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}
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
  private final Map<UUID, Inventory> openEditors = new HashMap<UUID, Inventory>();
  private final Map<UUID, Boolean> editingKit = new HashMap<UUID, Boolean>();
  
  // 27-slot fake inventory + button slots at the bottom row
  private static final int SIZE = 36;
  // Kit contents are stored in slots 0-26
  // Buttons are in slots 27-35
  
  public KitEditor(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  public void openKitEditorGUI(final Player player) {
    // Build the fake inventory
    Inventory inv = Bukkit.createInventory(null, SIZE, colorize("&6&lKit Editor"));
    
    // Load current kit into the first 27 slots
    FileConfiguration config = this.plugin.getConfig();
    ConfigurationSection kitSection = config.getConfigurationSection("kits." + player.getUniqueId().toString());
    
    ItemStack[] kitContents;
    ItemStack helmet, chestplate, leggings, boots;
    
    if (kitSection != null) {
      kitContents = loadKitContentsFromConfig(kitSection);
      helmet = kitSection.getItemStack("helmet");
      chestplate = kitSection.getItemStack("chestplate");
      leggings = kitSection.getItemStack("leggings");
      boots = kitSection.getItemStack("boots");
    } else {
      kitContents = getDefaultKitContents();
      helmet = unbreakable(new ItemStack(Material.IRON_HELMET));
      chestplate = unbreakable(new ItemStack(Material.IRON_CHESTPLATE));
      leggings = unbreakable(new ItemStack(Material.DIAMOND_LEGGINGS));
      boots = unbreakable(new ItemStack(Material.DIAMOND_BOOTS));
    }
    
    for (int i = 0; i < 27 && i < kitContents.length; i++) {
      if (kitContents[i] != null) {
        inv.setItem(i, kitContents[i]);
      }
    }
    
    // Armor preview (display only, in slots 27-30)
    inv.setItem(27, helmet);
    inv.setItem(28, chestplate);
    inv.setItem(29, leggings);
    inv.setItem(30, boots);
    
    // Save / Cancel / Reset buttons
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
        colorize("&7Move items around in this GUI"),
        colorize("&7The top row is your hotbar"),
        colorize("&7Slot 27-30 are armor"),
        colorize("&7Click Save when done")
    ));
    
    inv.setItem(32, saveBtn);
    inv.setItem(33, cancelBtn);
    inv.setItem(34, resetBtn);
    inv.setItem(35, infoBtn);
    
    // Store and mark editing
    this.openEditors.put(player.getUniqueId(), inv);
    this.editingKit.put(player.getUniqueId(), Boolean.valueOf(true));
    
    // Open the fake inventory
    player.openInventory(inv);
    player.sendMessage(colorize("&8&m----------------------------------"));
    player.sendMessage(colorize("&6&lKit Editor &7opened"));
    player.sendMessage(colorize("&7Move items freely, then click &aSave &7or &cCancel"));
    player.sendMessage(colorize("&7Top row is your hotbar. Slot 27-30 is armor."));
    player.sendMessage(colorize("&8&m----------------------------------"));
  }
  
  private ItemStack[] loadKitContentsFromConfig(ConfigurationSection section) {
    ItemStack[] contents = new ItemStack[36];
    if (section.contains("contents")) {
      List<?> list = section.getList("contents");
      if (list != null) {
        for (int i = 0; i < list.size() && i < 36; i++) {
          Object obj = list.get(i);
          if (obj instanceof ItemStack) {
            contents[i] = (ItemStack) obj;
          }
        }
      }
    }
    return contents;
  }
  
  private ItemStack[] getDefaultKitContents() {
    ItemStack[] contents = new ItemStack[36];
    
    // Slot 0: Stone Sword
    contents[0] = unbreakable(new ItemStack(Material.STONE_SWORD));
    
    // Slot 1: Cyan Wool x 64
    contents[1] = new ItemStack(Material.WOOL, 64, (short) 9);
    
    // Slot 2: Bow
    ItemStack bow = new ItemStack(Material.BOW);
    bow.addEnchantment(Enchantment.ARROW_KNOCKBACK, 1);
    bow.addEnchantment(Enchantment.ARROW_DAMAGE, 2);
    contents[2] = unbreakable(bow);
    
    // Slot 3: Iron Pickaxe (Efficiency 2)
    ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
    pickaxe.addEnchantment(Enchantment.DIG_SPEED, 2);
    contents[3] = unbreakable(pickaxe);
    
    // Slot 4: Iron Axe (Efficiency 1)
    ItemStack axe = new ItemStack(Material.IRON_AXE);
    axe.addEnchantment(Enchantment.DIG_SPEED, 1);
    contents[4] = unbreakable(axe);
    
    // Slot 5: Arrows
    contents[5] = new ItemStack(Material.ARROW, 12);
    
    // Slot 6: Golden Apples
    contents[6] = new ItemStack(Material.GOLDEN_APPLE, 3);
    
    // Slot 7: Ender Pearl
    contents[7] = new ItemStack(Material.ENDER_PEARL);
    
    return contents;
  }
  
  private ItemStack createButton(Material mat, String name, List<String> lore) {
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(name);
    meta.setLore(lore);
    item.setItemMeta(meta);
    return item;
  }
  
  public static ItemStack unbreakable(ItemStack item) {
    if (item == null) return null;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.spigot().setUnbreakable(true);
    item.setItemMeta(meta);
    return item;
  }
  
  public void saveKit(Player player) {
    Inventory inv = this.openEditors.get(player.getUniqueId());
    if (inv == null) {
      return;
    }
    
    FileConfiguration config = this.plugin.getConfig();
    String path = "kits." + player.getUniqueId().toString();
    
    // Armor from display slots 27-30
    config.set(path + ".helmet", inv.getItem(27));
    config.set(path + ".chestplate", inv.getItem(28));
    config.set(path + ".leggings", inv.getItem(29));
    config.set(path + ".boots", inv.getItem(30));
    
    // Contents from slots 0-26 (the kit itself)
    List<ItemStack> contents = new ArrayList<ItemStack>();
    for (int i = 0; i < 27; i++) {
      contents.add(inv.getItem(i));
    }
    config.set(path + ".contents", contents);
    
    this.plugin.saveConfig();
    
    this.editingKit.remove(player.getUniqueId());
    this.openEditors.remove(player.getUniqueId());
    
    player.closeInventory();
    player.sendMessage(colorize("&aYour kit has been saved!"));
    player.playSound(player.getLocation(), Sound.LEVEL_UP, 1.0F, 1.0F);
    
    // Apply the new kit immediately
    Equip equip = new Equip(this.plugin);
    equip.giveDiamondArmor(player);
  }
  
  public void cancelKit(Player player) {
    this.editingKit.remove(player.getUniqueId());
    this.openEditors.remove(player.getUniqueId());
    player.closeInventory();
    player.sendMessage(colorize("&cKit editing cancelled."));
  }
  
  public void resetKit(Player player) {
    FileConfiguration config = this.plugin.getConfig();
    config.set("kits." + player.getUniqueId().toString(), null);
    this.plugin.saveConfig();
    
    this.openEditors.remove(player.getUniqueId());
    this.editingKit.remove(player.getUniqueId());
    
    player.closeInventory();
    
    if (player.isOnline()) {
      Equip equip = new Equip(this.plugin);
      equip.giveDiamondArmor(player);
    }
    
    player.sendMessage(colorize("&aYour kit has been reset to the default kit."));
  }
  
  public boolean isEditing(Player player) {
    return this.editingKit.containsKey(player.getUniqueId()) && this.editingKit.get(player.getUniqueId()).booleanValue();
  }
  
  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getWhoClicked();
    
    if (!isEditing(player)) {
      return;
    }
    
    Inventory currentInv = this.openEditors.get(player.getUniqueId());
    if (currentInv == null) {
      return;
    }
    
    // Only handle clicks in our fake editor inventory
    if (event.getInventory() != currentInv) {
      // If they shift-click from their own inventory, cancel that to prevent desync
      if (event.isShiftClick()) {
        event.setCancelled(true);
      }
      return;
    }
    
    int slot = event.getRawSlot();
    
    // Buttons
    if (slot == 32) {
      event.setCancelled(true);
      saveKit(player);
      return;
    }
    if (slot == 33) {
      event.setCancelled(true);
      cancelKit(player);
      return;
    }
    if (slot == 34) {
      event.setCancelled(true);
      resetKit(player);
      return;
    }
    if (slot == 35) {
      event.setCancelled(true);
      return;
    }
    
    // Armor display slots (27-30) — allow editing
    // Kit slots (0-26) — allow editing
    // Bottom row slots (31) — allow (empty spacer)
    // Anything else — let Bukkit handle it
    // Allow all moves within the fake GUI
  }
  
  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getPlayer();
    
    // If they close without saving, cancel
    if (isEditing(player)) {
      this.editingKit.remove(player.getUniqueId());
      this.openEditors.remove(player.getUniqueId());
      player.sendMessage(colorize("&7Kit editing closed (not saved)."));
    }
  }
  
  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}
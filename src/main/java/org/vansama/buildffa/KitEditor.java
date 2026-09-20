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
  private final Map<UUID, Boolean> saving = new HashMap<UUID, Boolean>();
  
  private static final int SIZE = 36;
  
  private static final int SLOT_SAVE = 32;
  private static final int SLOT_CANCEL = 33;
  private static final int SLOT_RESET = 34;
  private static final int SLOT_INFO = 35;
  private static final int SLOT_SPACER = 31;
  
  public KitEditor(JavaPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }
  
  public void openKitEditorGUI(final Player player) {
    Inventory inv = Bukkit.createInventory(null, SIZE, colorize("&6&lKit Editor"));
    
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
    
    inv.setItem(27, helmet);
    inv.setItem(28, chestplate);
    inv.setItem(29, leggings);
    inv.setItem(30, boots);
    
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
        colorize("&7Top 27 slots = kit contents"),
        colorize("&7Slots 27-30 = armor"),
        colorize("&7Click Save when done")
    ));
    
    inv.setItem(SLOT_SAVE, saveBtn);
    inv.setItem(SLOT_CANCEL, cancelBtn);
    inv.setItem(SLOT_RESET, resetBtn);
    inv.setItem(SLOT_INFO, infoBtn);
    
    this.openEditors.put(player.getUniqueId(), inv);
    this.editingKit.put(player.getUniqueId(), Boolean.valueOf(true));
    this.saving.put(player.getUniqueId(), Boolean.valueOf(false));
    
    player.openInventory(inv);
    player.sendMessage(colorize("&8&m----------------------------------"));
    player.sendMessage(colorize("&6&lKit Editor &7opened"));
    player.sendMessage(colorize("&7Move items freely, then click &aSave &7or &cCancel"));
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
    
    // Slot 0: Diamond Sword (Sharpness II, Unbreakable)
    ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
    sword.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 2);
    contents[0] = unbreakable(sword);
    
    // Slot 1: Cyan Wool x 64
    contents[1] = new ItemStack(Material.WOOL, 64, (short) 9);
    
    // Slot 2: Iron Axe (Efficiency I, Unbreakable)
    ItemStack axe = new ItemStack(Material.IRON_AXE);
    axe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1);
    contents[2] = unbreakable(axe);
    
    // Slot 3: Iron Pickaxe (Efficiency I, Unbreakable)
    ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
    pickaxe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1);
    contents[3] = unbreakable(pickaxe);
    
    // Slot 4-26: empty
    // No bow, no arrows, no ender pearl, no golden apples
    
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
      player.sendMessage(colorize("&cError: editor inventory was lost."));
      return;
    }
    
    this.saving.put(player.getUniqueId(), Boolean.valueOf(true));
    
    FileConfiguration config = this.plugin.getConfig();
    String path = "kits." + player.getUniqueId().toString();
    
    config.set(path + ".helmet", inv.getItem(27));
    config.set(path + ".chestplate", inv.getItem(28));
    config.set(path + ".leggings", inv.getItem(29));
    config.set(path + ".boots", inv.getItem(30));
    
    List<ItemStack> contents = new ArrayList<ItemStack>();
    for (int i = 0; i < 27; i++) {
      contents.add(inv.getItem(i));
    }
    config.set(path + ".contents", contents);
    
    this.plugin.saveConfig();
    
    this.editingKit.remove(player.getUniqueId());
    this.openEditors.remove(player.getUniqueId());
    this.saving.remove(player.getUniqueId());
    
    player.closeInventory();
    player.sendMessage(colorize("&aYour kit has been saved!"));
    player.playSound(player.getLocation(), Sound.LEVEL_UP, 1.0F, 1.0F);
    
    Equip equip = new Equip(this.plugin);
    equip.giveDiamondArmor(player);
  }
  
  public void cancelKit(Player player) {
    this.saving.put(player.getUniqueId(), Boolean.valueOf(true));
    this.editingKit.remove(player.getUniqueId());
    this.openEditors.remove(player.getUniqueId());
    this.saving.remove(player.getUniqueId());
    player.closeInventory();
    player.sendMessage(colorize("&cKit editing cancelled."));
  }
  
  public void resetKit(Player player) {
    this.saving.put(player.getUniqueId(), Boolean.valueOf(true));
    
    FileConfiguration config = this.plugin.getConfig();
    config.set("kits." + player.getUniqueId().toString(), null);
    this.plugin.saveConfig();
    
    this.editingKit.remove(player.getUniqueId());
    this.openEditors.remove(player.getUniqueId());
    this.saving.remove(player.getUniqueId());
    
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
    
    Inventory editor = this.openEditors.get(player.getUniqueId());
    if (editor == null) return;
    
    if (event.getInventory() != editor) {
      event.setCancelled(true);
      return;
    }
    
    int slot = event.getRawSlot();
    
    if (slot == SLOT_SAVE) {
      event.setCancelled(true);
      saveKit(player);
      return;
    }
    if (slot == SLOT_CANCEL) {
      event.setCancelled(true);
      cancelKit(player);
      return;
    }
    if (slot == SLOT_RESET) {
      event.setCancelled(true);
      resetKit(player);
      return;
    }
    if (slot == SLOT_INFO) {
      event.setCancelled(true);
      return;
    }
    if (slot == SLOT_SPACER) {
      event.setCancelled(true);
      return;
    }
    
    if (event.isShiftClick()) {
      event.setCancelled(true);
      return;
    }
  }
  
  @EventHandler
  public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getWhoClicked();
    if (!isEditing(player)) return;
    
    Inventory editor = this.openEditors.get(player.getUniqueId());
    if (editor == null) return;
    
    for (Integer rawSlot : event.getRawSlots()) {
      int s = rawSlot.intValue();
      if (s >= SLOT_SPACER) {
        event.setCancelled(true);
        return;
      }
      if (s >= SIZE) {
        event.setCancelled(true);
        return;
      }
    }
  }
  
  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player)) return;
    Player player = (Player) event.getPlayer();
    
    if (this.saving.containsKey(player.getUniqueId()) && this.saving.get(player.getUniqueId()).booleanValue()) {
      return;
    }
    
    if (isEditing(player)) {
      this.editingKit.remove(player.getUniqueId());
      this.openEditors.remove(player.getUniqueId());
      player.sendMessage(colorize("&7Kit editor closed (not saved)."));
    }
  }
  
  private String colorize(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}
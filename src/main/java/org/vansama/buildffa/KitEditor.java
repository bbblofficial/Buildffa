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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class KitEditor implements Listener {

  private final JavaPlugin plugin;
  private final KitDatabase kitDatabase;
  private final DatabaseManager database;

  private final Map<UUID, Inventory> openEditors = new HashMap<UUID, Inventory>();
  private final Map<UUID, Boolean> editingKit = new HashMap<UUID, Boolean>();
  private final Map<UUID, Boolean> saving = new HashMap<UUID, Boolean>();

  private static final int SIZE = 36;

  private static final int SLOT_SAVE = 32;
  private static final int SLOT_CANCEL = 33;
  private static final int SLOT_RESET = 34;
  private static final int SLOT_INFO = 35;
  private static final int SLOT_SPACER = 31;

  public KitEditor(JavaPlugin plugin, DatabaseManager database) {
    this.plugin = plugin;
    this.database = database;
    this.kitDatabase = new KitDatabase(plugin, database);
    Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }

  public KitDatabase getKitDatabase() {
    return this.kitDatabase;
  }

  public DatabaseManager getDatabase() {
    return this.database;
  }

  public void openKitEditorGUI(final Player player) {
    Inventory inv = Bukkit.createInventory(null, SIZE, colorize("&6&lKit Editor"));

    UUID uuid = player.getUniqueId();

    ItemStack[] kitContents;
    ItemStack helmet, chestplate, leggings, boots;

    if (kitDatabase.hasKit(uuid)) {
      List<ItemStack> contentsList = kitDatabase.getContents(uuid);
      kitContents = new ItemStack[36];
      if (contentsList != null) {
        for (int i = 0; i < contentsList.size() && i < 36; i++) {
          ItemStack item = contentsList.get(i);
          if (item != null) {
            kitContents[i] = item;
          }
        }
      }
      helmet = kitDatabase.getHelmet(uuid);
      chestplate = kitDatabase.getChestplate(uuid);
      leggings = kitDatabase.getLeggings(uuid);
      boots = kitDatabase.getBoots(uuid);
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
        colorize("&7Armor is locked (cannot be edited)"),
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

  private ItemStack[] getDefaultKitContents() {
    ItemStack[] contents = new ItemStack[36];

    ItemStack sword = new ItemStack(Material.STONE_SWORD);
    sword.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 2);
    contents[0] = unbreakable(sword);

    contents[1] = new ItemStack(Material.WOOL, 64, (short) 9);
    contents[2] = unbreakable(new ItemStack(Material.SHEARS));

    ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
    pickaxe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 2);
    contents[3] = unbreakable(pickaxe);

    ItemStack axe = new ItemStack(Material.IRON_AXE);
    axe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1);
    contents[4] = unbreakable(axe);

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

    UUID uuid = player.getUniqueId();

    List<ItemStack> contents = new ArrayList<ItemStack>();
    for (int i = 0; i < 27; i++) {
      contents.add(inv.getItem(i));
    }

    kitDatabase.saveKit(
        uuid,
        inv.getItem(27),
        inv.getItem(28),
        inv.getItem(29),
        inv.getItem(30),
        contents
    );

    this.editingKit.remove(player.getUniqueId());
    this.openEditors.remove(player.getUniqueId());
    this.saving.remove(player.getUniqueId());

    player.closeInventory();
    player.sendMessage(colorize("&aYour kit has been saved!"));
    player.playSound(player.getLocation(), Sound.LEVEL_UP, 1.0F, 1.0F);

    // restore kit immediately
    try {
      BuildFFA bffa = (BuildFFA) this.plugin;
      if (bffa.getEquip() != null) {
        bffa.getEquip().giveDiamondArmor(player);
      }
    } catch (Throwable ignored) {}
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

    kitDatabase.deleteKit(player.getUniqueId());

    this.editingKit.remove(player.getUniqueId());
    this.openEditors.remove(player.getUniqueId());
    this.saving.remove(player.getUniqueId());

    player.closeInventory();

    if (player.isOnline()) {
      try {
        BuildFFA bffa = (BuildFFA) this.plugin;
        if (bffa.getEquip() != null) {
          bffa.getEquip().giveDiamondArmor(player);
        }
      } catch (Throwable ignored) {}
    }

    player.sendMessage(colorize("&aYour kit has been reset to the default kit."));
  }

  public boolean isEditing(Player player) {
    return this.editingKit.containsKey(player.getUniqueId())
        && this.editingKit.get(player.getUniqueId()).booleanValue();
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) return;
    Player player = (Player) event.getWhoClicked();

    if (!isEditing(player)) return;

    Inventory editor = this.openEditors.get(player.getUniqueId());
    if (editor == null) return;

    int slot = event.getRawSlot();

    if (slot >= SIZE) return;

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

    // ==== ARMOR SLOTS LOCKED ====
    if (slot >= 27 && slot <= 30) {
      event.setCancelled(true);
      player.sendMessage(colorize("&cYou cannot change the armor in the Kit Editor."));
      return;
    }
  }

  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) return;
    Player player = (Player) event.getWhoClicked();
    if (!isEditing(player)) return;

    Inventory editor = this.openEditors.get(player.getUniqueId());
    if (editor == null) return;

    for (Integer rawSlot : event.getRawSlots()) {
      int s = rawSlot.intValue();

      if (s == SLOT_SAVE || s == SLOT_CANCEL || s == SLOT_RESET
          || s == SLOT_INFO || s == SLOT_SPACER) {
        event.setCancelled(true);
        return;
      }

      if (s >= 27 && s <= 30) {
        event.setCancelled(true);
        player.sendMessage(colorize("&cYou cannot change the armor in the Kit Editor."));
        return;
      }
    }
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player)) return;
    Player player = (Player) event.getPlayer();

    if (this.saving.containsKey(player.getUniqueId())
        && this.saving.get(player.getUniqueId()).booleanValue()) {
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
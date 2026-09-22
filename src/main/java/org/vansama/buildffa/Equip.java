package org.vansama.buildffa;

import java.util.List;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Equip implements Listener {
  private JavaPlugin plugin;

  public Equip(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, (Plugin) plugin);
  }

  // ============================================================
  //  JOIN — equip kit 5 ticks after join
  // ============================================================
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    final Player player = event.getPlayer();
    this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (player.isOnline()) {
          giveDiamondArmor(player);
        }
      }
    }, 5L);
  }

  // ============================================================
  //  RESPAWN — equip kit 5 ticks after respawn
  // ============================================================
  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    final Player player = event.getPlayer();
    this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
      @Override
      public void run() {
        if (player.isOnline()) {
          giveDiamondArmor(player);
        }
      }
    }, 5L);
  }

  // ============================================================
  //  GIVE KIT — tries custom kit from SQLite first, else default
  // ============================================================
  public void giveDiamondArmor(Player player) {
    if (player == null || !player.isOnline()) return;

    // 1) Player's personal kit (from SQLite)
    if (tryApplyCustomKit(player)) {
      return;
    }

    // 2) Server default kit (from kit-setting.json)
    if (tryApplyServerDefaultKit(player)) {
      return;
    }

    // 3) Hardcoded fallback (leather red armor + stone sword)
    applyHardcodedDefault(player);
  }

  // ============================================================
  //  HARDCODED DEFAULT — Leather RED armor
  // ============================================================
  private void applyHardcodedDefault(Player player) {
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);

    player.getInventory().setHelmet(redLeather(Material.LEATHER_HELMET));
    player.getInventory().setChestplate(redLeather(Material.LEATHER_CHESTPLATE));
    player.getInventory().setLeggings(redLeather(Material.LEATHER_LEGGINGS));
    player.getInventory().setBoots(redLeather(Material.LEATHER_BOOTS));

    ItemStack sword = new ItemStack(Material.STONE_SWORD);
    sword.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 2);
    player.getInventory().setItem(0, unbreakable(sword));

    ItemStack cyanWool = new ItemStack(Material.WOOL, 64, (short) 9);
    player.getInventory().setItem(1, cyanWool);

    player.getInventory().setItem(2, unbreakable(new ItemStack(Material.SHEARS)));

    ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
    pickaxe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 2);
    player.getInventory().setItem(3, unbreakable(pickaxe));

    ItemStack axe = new ItemStack(Material.IRON_AXE);
    axe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1);
    player.getInventory().setItem(4, unbreakable(axe));

    player.updateInventory();
  }

  // ============================================================
  //  LOAD SERVER DEFAULT KIT FROM kit-setting.json
  // ============================================================
  private boolean tryApplyServerDefaultKit(Player player) {
    try {
      BuildFFA bffa = (BuildFFA) this.plugin;
      KitSettingsManager settings = bffa.getKitSettings();
      if (settings == null) return false;

      List<ItemStack> contents = settings.getDefaultKitContents();
      if (contents == null || contents.isEmpty()) return false;

      player.getInventory().clear();
      player.getInventory().setArmorContents(null);

      // First 4 = armor (helmet, chest, legs, boots)
      if (contents.size() >= 1 && contents.get(0) != null) player.getInventory().setHelmet(contents.get(0));
      if (contents.size() >= 2 && contents.get(1) != null) player.getInventory().setChestplate(contents.get(1));
      if (contents.size() >= 3 && contents.get(2) != null) player.getInventory().setLeggings(contents.get(2));
      if (contents.size() >= 4 && contents.get(3) != null) player.getInventory().setBoots(contents.get(3));

      // Rest = inventory items (slots 0-35)
      for (int i = 4; i < contents.size() && i < 40; i++) {
        ItemStack item = contents.get(i);
        if (item != null && item.getType() != Material.AIR) {
          player.getInventory().setItem(i - 4, item);
        }
      }

      player.updateInventory();
      return true;

    } catch (Throwable t) {
      this.plugin.getLogger().warning("Server default kit load failed: " + t.getMessage());
      return false;
    }
  }

  // ============================================================
  //  LOAD CUSTOM KIT FROM SQLite (per-player)
  // ============================================================
  private boolean tryApplyCustomKit(Player player) {
    try {
      BuildFFA bffa = (BuildFFA) this.plugin;
      DatabaseManager db = bffa.getDatabaseManager();
      if (db == null) return false;

      UUID uuid = player.getUniqueId();
      if (!db.hasKit(uuid)) return false;

      player.getInventory().clear();
      player.getInventory().setArmorContents(null);

      ItemStack helmet = db.getKitHelmet(uuid);
      ItemStack chestplate = db.getKitChestplate(uuid);
      ItemStack leggings = db.getKitLeggings(uuid);
      ItemStack boots = db.getKitBoots(uuid);

      if (helmet != null) player.getInventory().setHelmet(helmet);
      if (chestplate != null) player.getInventory().setChestplate(chestplate);
      if (leggings != null) player.getInventory().setLeggings(leggings);
      if (boots != null) player.getInventory().setBoots(boots);

      List<ItemStack> contents = db.getKitContents(uuid);
      if (contents != null) {
        for (int i = 0; i < contents.size() && i < 36; i++) {
          ItemStack item = contents.get(i);
          if (item != null && item.getType() != Material.AIR) {
            player.getInventory().setItem(i, item);
          }
        }
      }

      player.updateInventory();
      return true;

    } catch (Throwable t) {
      this.plugin.getLogger().warning("Custom kit load failed for " + player.getName() + ": " + t.getMessage());
      return false;
    }
  }

  // ============================================================
  //  HELPERS
  // ============================================================
  public static ItemStack unbreakable(ItemStack item) {
    if (item == null) return null;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.spigot().setUnbreakable(true);
    item.setItemMeta(meta);
    return item;
  }

  /** Creates a RED leather armor piece. */
  public static ItemStack redLeather(Material type) {
    ItemStack item = new ItemStack(type);
    ItemMeta meta = item.getItemMeta();
    if (meta instanceof LeatherArmorMeta) {
      ((LeatherArmorMeta) meta).setColor(Color.RED);
    }
    meta.spigot().setUnbreakable(true);
    item.setItemMeta(meta);
    return item;
  }
}
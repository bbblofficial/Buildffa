package org.vansama.buildffa;

import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
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
    // Try to load custom kit from kits/<uuid>.yml
    if (tryApplyCustomKit(player)) {
      return;
    }

    // Default kit
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);

    player.getInventory().setHelmet(unbreakable(new ItemStack(Material.IRON_HELMET)));
    player.getInventory().setChestplate(unbreakable(new ItemStack(Material.IRON_CHESTPLATE)));
    player.getInventory().setLeggings(unbreakable(new ItemStack(Material.DIAMOND_LEGGINGS)));
    player.getInventory().setBoots(unbreakable(new ItemStack(Material.DIAMOND_BOOTS)));

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
  }

  /**
   * Loads the custom kit from plugins/BuildFFA/kits/<uuid>.yml
   * Returns true if a custom kit existed and was applied.
   */
  private boolean tryApplyCustomKit(Player player) {
    try {
      BuildFFA bffa = (BuildFFA) this.plugin;
      KitEditor kitEditor = bffa.getKitEditor();
      if (kitEditor == null) return false;

      KitDatabase kitDb = kitEditor.getKitDatabase();
      if (kitDb == null) return false;

      UUID uuid = player.getUniqueId();
      if (!kitDb.hasKit(uuid)) return false;

      player.getInventory().clear();
      player.getInventory().setArmorContents(null);

      ItemStack helmet = kitDb.getHelmet(uuid);
      ItemStack chestplate = kitDb.getChestplate(uuid);
      ItemStack leggings = kitDb.getLeggings(uuid);
      ItemStack boots = kitDb.getBoots(uuid);

      if (helmet != null) player.getInventory().setHelmet(helmet);
      if (chestplate != null) player.getInventory().setChestplate(chestplate);
      if (leggings != null) player.getInventory().setLeggings(leggings);
      if (boots != null) player.getInventory().setBoots(boots);

      List<?> contents = kitDb.getContents(uuid);
      if (contents != null) {
        for (int i = 0; i < contents.size() && i < 36; i++) {
          Object obj = contents.get(i);
          if (obj instanceof ItemStack) {
            player.getInventory().setItem(i, (ItemStack) obj);
          }
        }
      }
      return true;

    } catch (Throwable t) {
      return false;
    }
  }

  public static ItemStack unbreakable(ItemStack item) {
    if (item == null) return null;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.spigot().setUnbreakable(true);
    item.setItemMeta(meta);
    return item;
  }
}
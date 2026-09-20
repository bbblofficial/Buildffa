package org.vansama.buildffa;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
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
    Player player = event.getPlayer();
    giveDiamondArmor(player);
  }
  
  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    giveDiamondArmor(player);
  }
  
  public void giveDiamondArmor(Player player) {
    player.getInventory().clear();
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
}

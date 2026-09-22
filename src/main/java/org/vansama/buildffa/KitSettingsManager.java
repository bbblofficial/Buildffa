package org.vansama.buildffa;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class KitSettingsManager {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    private List<ItemStack> cachedDefaultKit = null;

    public KitSettingsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kit-setting.yml");
        load();
    }

    // ============================================================
    //  LOAD
    // ============================================================
    public void load() {
        if (!this.file.exists()) {
            createDefaultFile();
        }
        this.config = YamlConfiguration.loadConfiguration(this.file);
        this.cachedDefaultKit = parseKitContents();
        plugin.getLogger().info("Kit settings loaded (" + 
                (this.cachedDefaultKit != null ? this.cachedDefaultKit.size() : 0) + " entries)");
    }

    public void reload() {
        load();
    }

    // ============================================================
    //  CREATE DEFAULT FILE if missing
    // ============================================================
    private void createDefaultFile() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            this.file.createNewFile();

            FileConfiguration cfg = new YamlConfiguration();

            // ===== ARMOR =====
            cfg.set("armor.helmet.type", "LEATHER_HELMET");
            cfg.set("armor.helmet.color", "RED");
            cfg.set("armor.helmet.unbreakable", true);
            cfg.set("armor.helmet.name", "");
            cfg.set("armor.helmet.lore", new ArrayList<String>());
            cfg.set("armor.helmet.enchantments", new ArrayList<String>());

            cfg.set("armor.chestplate.type", "LEATHER_CHESTPLATE");
            cfg.set("armor.chestplate.color", "RED");
            cfg.set("armor.chestplate.unbreakable", true);
            cfg.set("armor.chestplate.name", "");
            cfg.set("armor.chestplate.lore", new ArrayList<String>());
            cfg.set("armor.chestplate.enchantments", new ArrayList<String>());

            cfg.set("armor.leggings.type", "LEATHER_LEGGINGS");
            cfg.set("armor.leggings.color", "RED");
            cfg.set("armor.leggings.unbreakable", true);
            cfg.set("armor.leggings.name", "");
            cfg.set("armor.leggings.lore", new ArrayList<String>());
            cfg.set("armor.leggings.enchantments", new ArrayList<String>());

            cfg.set("armor.boots.type", "LEATHER_BOOTS");
            cfg.set("armor.boots.color", "RED");
            cfg.set("armor.boots.unbreakable", true);
            cfg.set("armor.boots.name", "");
            cfg.set("armor.boots.lore", new ArrayList<String>());
            cfg.set("armor.boots.enchantments", new ArrayList<String>());

            // ===== ITEMS =====
            cfg.set("items.sword.slot", 0);
            cfg.set("items.sword.type", "STONE_SWORD");
            cfg.set("items.sword.amount", 1);
            cfg.set("items.sword.unbreakable", true);
            cfg.set("items.sword.name", "");
            cfg.set("items.sword.lore", new ArrayList<String>());
            cfg.set("items.sword.enchantments", Arrays.asList("DAMAGE_ALL:2"));

            cfg.set("items.wool.slot", 1);
            cfg.set("items.wool.type", "WOOL");
            cfg.set("items.wool.data", 9);
            cfg.set("items.wool.amount", 64);
            cfg.set("items.wool.unbreakable", false);
            cfg.set("items.wool.name", "");
            cfg.set("items.wool.lore", new ArrayList<String>());
            cfg.set("items.wool.enchantments", new ArrayList<String>());

            cfg.set("items.shears.slot", 2);
            cfg.set("items.shears.type", "SHEARS");
            cfg.set("items.shears.amount", 1);
            cfg.set("items.shears.unbreakable", true);
            cfg.set("items.shears.name", "");
            cfg.set("items.shears.lore", new ArrayList<String>());
            cfg.set("items.shears.enchantments", new ArrayList<String>());

            cfg.set("items.pickaxe.slot", 3);
            cfg.set("items.pickaxe.type", "IRON_PICKAXE");
            cfg.set("items.pickaxe.amount", 1);
            cfg.set("items.pickaxe.unbreakable", true);
            cfg.set("items.pickaxe.name", "");
            cfg.set("items.pickaxe.lore", new ArrayList<String>());
            cfg.set("items.pickaxe.enchantments", Arrays.asList("DIG_SPEED:2"));

            cfg.set("items.axe.slot", 4);
            cfg.set("items.axe.type", "IRON_AXE");
            cfg.set("items.axe.amount", 1);
            cfg.set("items.axe.unbreakable", true);
            cfg.set("items.axe.name", "");
            cfg.set("items.axe.lore", new ArrayList<String>());
            cfg.set("items.axe.enchantments", Arrays.asList("DIG_SPEED:1"));

            cfg.save(this.file);
            plugin.getLogger().info("Created default kit-setting.yml");

        } catch (IOException e) {
            plugin.getLogger().warning("Could not create kit-setting.yml: " + e.getMessage());
        }
    }

    // ============================================================
    //  PARSE - config -> List<ItemStack>
    //  Order: [helmet, chestplate, leggings, boots, item0, item1, ...]
    // ============================================================
    private List<ItemStack> parseKitContents() {
        List<ItemStack> out = new ArrayList<ItemStack>();

        // ===== ARMOR =====
        out.add(parseSingleItem(config.getConfigurationSection("armor.helmet")));
        out.add(parseSingleItem(config.getConfigurationSection("armor.chestplate")));
        out.add(parseSingleItem(config.getConfigurationSection("armor.leggings")));
        out.add(parseSingleItem(config.getConfigurationSection("armor.boots")));

        // ===== ITEMS =====
        ItemStack[] slots = new ItemStack[36];
        ConfigurationSection itemsSec = config.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ConfigurationSection s = itemsSec.getConfigurationSection(key);
                if (s == null) continue;
                int slot = s.getInt("slot", -1);
                if (slot < 0 || slot > 35) continue;
                ItemStack item = parseSingleItem(s);
                if (item != null) {
                    slots[slot] = item;
                }
            }
        }

        for (int i = 0; i < 36; i++) {
            out.add(slots[i]);
        }

        return out;
    }

    // ============================================================
    //  PARSE a single item
    // ============================================================
    @SuppressWarnings("deprecation")
    private ItemStack parseSingleItem(ConfigurationSection s) {
        if (s == null) return null;

        String typeName = s.getString("type", "AIR");
        Material material;
        try {
            material = Material.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material in kit-setting.yml: " + typeName);
            return null;
        }

        int amount = s.getInt("amount", 1);
        if (amount < 1) amount = 1;
        if (amount > 64) amount = 64;

        short data = (short) s.getInt("data", 0);

        ItemStack item = new ItemStack(material, amount, data);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Unbreakable
        if (s.getBoolean("unbreakable", false)) {
            meta.spigot().setUnbreakable(true);
        }

        // Name
        String name = s.getString("name", "");
        if (name != null && !name.isEmpty()) {
            meta.setDisplayName(colorize(name));
        }

        // Lore
        List<String> lore = s.getStringList("lore");
        if (lore != null && !lore.isEmpty()) {
            List<String> colored = new ArrayList<String>();
            for (String line : lore) {
                colored.add(colorize(line));
            }
            meta.setLore(colored);
        }

        // Enchantments
        List<String> enchants = s.getStringList("enchantments");
        if (enchants != null) {
            for (String raw : enchants) {
                if (raw == null || raw.isEmpty()) continue;
                String[] parts = raw.split(":");
                if (parts.length < 1) continue;
                String enchName = parts[0].toUpperCase();
                int level = 1;
                if (parts.length >= 2) {
                    try {
                        level = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {}
                }
                Enchantment ench = Enchantment.getByName(enchName);
                if (ench != null) {
                    meta.addEnchant(ench, level, true);
                } else {
                    plugin.getLogger().warning("Unknown enchantment: " + enchName);
                }
            }
        }

        // Leather color
        if (meta instanceof LeatherArmorMeta) {
            String colorStr = s.getString("color", "");
            if (colorStr != null && !colorStr.isEmpty()) {
                Color c = parseColor(colorStr);
                if (c != null) {
                    ((LeatherArmorMeta) meta).setColor(c);
                }
            }
        }

        // Potion effects
        if (meta instanceof PotionMeta) {
            List<String> effects = s.getStringList("effects");
            if (effects != null) {
                for (String raw : effects) {
                    if (raw == null || raw.isEmpty()) continue;
                    String[] parts = raw.split(":");
                    if (parts.length < 3) continue;
                    PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
                    if (type == null) continue;
                    try {
                        int duration = Integer.parseInt(parts[1]);
                        int amp = Integer.parseInt(parts[2]);
                        ((PotionMeta) meta).addCustomEffect(
                                new PotionEffect(type, duration, amp), true);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    // ============================================================
    //  COLOR parser
    // ============================================================
    private Color parseColor(String input) {
        if (input == null) return null;
        input = input.trim();

        if (input.equalsIgnoreCase("RED")) return Color.RED;
        if (input.equalsIgnoreCase("BLUE")) return Color.BLUE;
        if (input.equalsIgnoreCase("GREEN")) return Color.GREEN;
        if (input.equalsIgnoreCase("YELLOW")) return Color.YELLOW;
        if (input.equalsIgnoreCase("AQUA")) return Color.AQUA;
        if (input.equalsIgnoreCase("BLACK")) return Color.BLACK;
        if (input.equalsIgnoreCase("WHITE")) return Color.WHITE;
        if (input.equalsIgnoreCase("PURPLE")) return Color.PURPLE;
        if (input.equalsIgnoreCase("ORANGE")) return Color.ORANGE;
        if (input.equalsIgnoreCase("FUCHSIA") || input.equalsIgnoreCase("MAGENTA")) return Color.FUCHSIA;
        if (input.equalsIgnoreCase("GRAY") || input.equalsIgnoreCase("GREY")) return Color.GRAY;
        if (input.equalsIgnoreCase("SILVER")) return Color.SILVER;
        if (input.equalsIgnoreCase("LIME")) return Color.LIME;
        if (input.equalsIgnoreCase("MAROON")) return Color.MAROON;
        if (input.equalsIgnoreCase("NAVY")) return Color.NAVY;
        if (input.equalsIgnoreCase("OLIVE")) return Color.OLIVE;
        if (input.equalsIgnoreCase("TEAL")) return Color.TEAL;

        if (input.contains(",")) {
            String[] parts = input.split(",");
            if (parts.length == 3) {
                try {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    return Color.fromRGB(r, g, b);
                } catch (NumberFormatException ignored) {}
            }
        }

        if (input.startsWith("#") && input.length() == 7) {
            try {
                int rgb = Integer.parseInt(input.substring(1), 16);
                return Color.fromRGB(rgb);
            } catch (NumberFormatException ignored) {}
        }

        return null;
    }

    // ============================================================
    //  GETTERS / SAVE
    // ============================================================
    public List<ItemStack> getDefaultKitContents() {
        return this.cachedDefaultKit;
    }

    public FileConfiguration getConfig() {
        return this.config;
    }

    public File getFile() {
        return this.file;
    }

    public void save() {
        try {
            this.config.save(this.file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save kit-setting.yml: " + e.getMessage());
        }
    }

    private String colorize(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
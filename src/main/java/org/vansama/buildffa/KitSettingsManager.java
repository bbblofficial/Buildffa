package org.vansama.buildffa;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
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

        // ✅ Force-correct armor types on every load
        //    (fixes old files that had wrong armor / missing enchants)
        forceArmorDefaults();

        this.cachedDefaultKit = parseKitContents();
        plugin.getLogger().info("Kit settings loaded (" +
                (this.cachedDefaultKit != null ? this.cachedDefaultKit.size() : 0) + " entries)");
    }

    public void reload() {
        load();
    }

    // ============================================================
    //  FORCE ARMOR DEFAULTS
    //  Ensures every server has:
    //    Helmet + Chestplate = LEATHER_* (RED)
    //    Leggings + Boots    = DIAMOND_*
    //    ALL with Protection II + Unbreakable
    // ============================================================
    private void forceArmorDefaults() {
        boolean changed = false;

        // ---- Helmet ----
        changed |= setIfWrong("armor.helmet.type", "LEATHER_HELMET");
        changed |= setIfWrong("armor.helmet.color", "RED");
        changed |= setIfWrong("armor.helmet.unbreakable", true);
        changed |= setEnchantsIfWrong("armor.helmet.enchantments",
                Arrays.asList("PROTECTION_ENVIRONMENTAL:2"));

        // ---- Chestplate ----
        changed |= setIfWrong("armor.chestplate.type", "LEATHER_CHESTPLATE");
        changed |= setIfWrong("armor.chestplate.color", "RED");
        changed |= setIfWrong("armor.chestplate.unbreakable", true);
        changed |= setEnchantsIfWrong("armor.chestplate.enchantments",
                Arrays.asList("PROTECTION_ENVIRONMENTAL:2"));

        // ---- Leggings ----
        changed |= setIfWrong("armor.leggings.type", "DIAMOND_LEGGINGS");
        changed |= setIfWrong("armor.leggings.unbreakable", true);
        changed |= setEnchantsIfWrong("armor.leggings.enchantments",
                Arrays.asList("PROTECTION_ENVIRONMENTAL:2"));
        // Remove stray color key for diamond armor
        if (config.contains("armor.leggings.color")) {
            config.set("armor.leggings.color", null);
            changed = true;
        }

        // ---- Boots ----
        changed |= setIfWrong("armor.boots.type", "DIAMOND_BOOTS");
        changed |= setIfWrong("armor.boots.unbreakable", true);
        changed |= setEnchantsIfWrong("armor.boots.enchantments",
                Arrays.asList("PROTECTION_ENVIRONMENTAL:2"));
        if (config.contains("armor.boots.color")) {
            config.set("armor.boots.color", null);
            changed = true;
        }

        if (changed) {
            save();
            plugin.getLogger().info("kit-setting.yml armor defaults auto-corrected.");
        }
    }

    private boolean setIfWrong(String path, Object expected) {
        Object current = config.get(path);
        if (current == null || !current.equals(expected)) {
            config.set(path, expected);
            return true;
        }
        return false;
    }

    private boolean setEnchantsIfWrong(String path, List<String> expected) {
        List<String> current = config.getStringList(path);
        if (current == null || !current.equals(expected)) {
            config.set(path, expected);
            return true;
        }
        return false;
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

            // ===== ARMOR (Leather RED + Diamond, all Prot II) =====
            cfg.set("armor.helmet.type", "LEATHER_HELMET");
            cfg.set("armor.helmet.color", "RED");
            cfg.set("armor.helmet.unbreakable", true);
            cfg.set("armor.helmet.enchantments", Arrays.asList("PROTECTION_ENVIRONMENTAL:2"));

            cfg.set("armor.chestplate.type", "LEATHER_CHESTPLATE");
            cfg.set("armor.chestplate.color", "RED");
            cfg.set("armor.chestplate.unbreakable", true);
            cfg.set("armor.chestplate.enchantments", Arrays.asList("PROTECTION_ENVIRONMENTAL:2"));

            cfg.set("armor.leggings.type", "DIAMOND_LEGGINGS");
            cfg.set("armor.leggings.unbreakable", true);
            cfg.set("armor.leggings.enchantments", Arrays.asList("PROTECTION_ENVIRONMENTAL:2"));

            cfg.set("armor.boots.type", "DIAMOND_BOOTS");
            cfg.set("armor.boots.unbreakable", true);
            cfg.set("armor.boots.enchantments", Arrays.asList("PROTECTION_ENVIRONMENTAL:2"));

            // ===== ITEMS =====
            // Stone Sword — Sharp II, 7.5 dmg
            cfg.set("items.sword.slot", 0);
            cfg.set("items.sword.type", "STONE_SWORD");
            cfg.set("items.sword.amount", 1);
            cfg.set("items.sword.unbreakable", true);
            cfg.set("items.sword.attack-damage", 7.5);
            cfg.set("items.sword.enchantments", Arrays.asList("DAMAGE_ALL:2"));

            // Wool (cyan)
            cfg.set("items.wool.slot", 1);
            cfg.set("items.wool.type", "WOOL");
            cfg.set("items.wool.data", 9);
            cfg.set("items.wool.amount", 64);
            cfg.set("items.wool.unbreakable", false);

            // Shears
            cfg.set("items.shears.slot", 2);
            cfg.set("items.shears.type", "SHEARS");
            cfg.set("items.shears.amount", 1);
            cfg.set("items.shears.unbreakable", true);

            // Wooden Axe — Eff I, 3 dmg
            cfg.set("items.axe.slot", 3);
            cfg.set("items.axe.type", "WOODEN_AXE");
            cfg.set("items.axe.amount", 1);
            cfg.set("items.axe.unbreakable", false);
            cfg.set("items.axe.attack-damage", 3.0);
            cfg.set("items.axe.enchantments", Arrays.asList("DIG_SPEED:1"));

            // Wooden Pickaxe — Eff II, 2 dmg
            cfg.set("items.pickaxe.slot", 4);
            cfg.set("items.pickaxe.type", "WOODEN_PICKAXE");
            cfg.set("items.pickaxe.amount", 1);
            cfg.set("items.pickaxe.unbreakable", false);
            cfg.set("items.pickaxe.attack-damage", 2.0);
            cfg.set("items.pickaxe.enchantments", Arrays.asList("DIG_SPEED:2"));

            // Jump V Potion
            cfg.set("items.jump-potion.slot", 5);
            cfg.set("items.jump-potion.type", "POTION");
            cfg.set("items.jump-potion.amount", 1);
            cfg.set("items.jump-potion.unbreakable", false);
            cfg.set("items.jump-potion.name", "&aPotion of Leaping V");
            cfg.set("items.jump-potion.lore", Arrays.asList("&7Drink to jump higher!"));
            cfg.set("items.jump-potion.effects", Arrays.asList("JUMP:180:4"));

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
                    if (type == null) {
                        plugin.getLogger().warning("Unknown potion effect: " + parts[0]);
                        continue;
                    }
                    try {
                        int duration = Integer.parseInt(parts[1]) * 20; // seconds -> ticks
                        int amp = Integer.parseInt(parts[2]);
                        ((PotionMeta) meta).addCustomEffect(
                                new PotionEffect(type, duration, amp), true);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        item.setItemMeta(meta);

        // ✅ Custom attack damage (NMS attribute)
        double attackDamage = s.getDouble("attack-damage", -1.0);
        if (attackDamage > 0) {
            applyAttackDamage(item, attackDamage);
        }

        return item;
    }

    // ============================================================
    //  APPLY CUSTOM ATTACK DAMAGE (1.8.8 NMS)
    //
    //  In 1.8.8, attack damage is set via the
    //  generic.attackDamage AttributeModifier on the ItemStack.
    //  We use NMS because Bukkit 1.8 has no Attribute API for items.
    // ============================================================
    private void applyAttackDamage(ItemStack item, double damage) {
        try {
            // Get the NMS ItemStack from the CraftItemStack
            Class<?> craftItemStackClass = Class.forName(
                    "org.bukkit.craftbukkit." + getNmsVersion() + ".inventory.CraftItemStack");
            Object nmsStack = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class)
                    .invoke(null, item);

            // Get the NMS Item class
            Object nmsItem = nmsStack.getClass().getMethod("getItem").invoke(nmsStack);

            // Build the AttributeModifier
            // In 1.8: new AttributeModifier(UUID, String, double, int)
            Class<?> attrModClass = Class.forName(
                    "net.minecraft.server." + getNmsVersion() + ".AttributeModifier");
            Class<?> attrClass = Class.forName(
                    "net.minecraft.server." + getNmsVersion() + ".AttributeInstance");

            // Attribute name: "generic.attackDamage"
            String attrName = "generic.attackDamage";

            // Find the AttributeBase for attackDamage via reflection
            // In 1.8, we use net.minecraft.server.X.Item.ATTACK_DAMAGE (a field)
            Class<?> itemClass = Class.forName(
                    "net.minecraft.server." + getNmsVersion() + ".Item");

            // Get the map of default modifiers to clear old ones
            // Simpler approach: set the base value directly
            // NMS ItemStack has no direct setter; we rebuild via AttributeModifier

            // Create unique UUID for this modifier
            java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(
                    ("buildffa.attack." + damage).getBytes());

            // AttributeModifier constructor: (UUID, String, double, int)
            java.lang.reflect.Constructor<?> ctor = attrModClass.getConstructor(
                    java.util.UUID.class, String.class, double.class, int.class);
            Object modifier = ctor.newInstance(uuid, "buildffa_damage", damage - 1.0, 0);

            // Get the NMS Item's attribute modifier map
            // In 1.8, Item class has a Map<String, AttributeModifier> field
            // We need to add to it via the "a" (default) map
            java.lang.reflect.Field modifiersField = null;
            for (java.lang.reflect.Field f : nmsItem.getClass().getSuperclass().getDeclaredFields()) {
                if (f.getType().getName().contains("Map")) {
                    modifiersField = f;
                    break;
                }
            }
            if (modifiersField == null) {
                // Fallback: try known field name "a"
                try {
                    modifiersField = nmsItem.getClass().getSuperclass()
                            .getDeclaredField("a");
                } catch (NoSuchFieldException ignored) {
                    plugin.getLogger().warning("Could not find attribute modifier field");
                    return;
                }
            }
            modifiersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map =
                    (java.util.Map<String, Object>) modifiersField.get(null);

            if (map != null) {
                // Store under a unique key so multiple items don't clash
                map.put("buildffa.attack." + System.identityHashCode(item), modifier);
            }

            // Copy the NMS stack back to the Bukkit ItemStack
            Object bukkitCopy = craftItemStackClass.getMethod("asBukkitCopy", nmsStack.getClass())
                    .invoke(null, nmsStack);
            if (bukkitCopy instanceof ItemStack) {
                ItemStack result = (ItemStack) bukkitCopy;
                item.setItemMeta(result.getItemMeta());
                item.setDurability(result.getDurability());
            }

        } catch (Throwable t) {
            // Fallback: use lore hint (not ideal, but safe)
            plugin.getLogger().warning("Could not set attack-damage for "
                    + item.getType() + ": " + t.getMessage());
        }
    }

    private String getNmsVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
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
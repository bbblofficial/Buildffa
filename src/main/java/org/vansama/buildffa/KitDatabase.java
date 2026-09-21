package org.vansama.buildffa;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class KitDatabase {

    private final JavaPlugin plugin;
    private final File kitsFolder;

    public KitDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.kitsFolder = new File(plugin.getDataFolder(), "kits");
        if (!this.kitsFolder.exists()) {
            this.kitsFolder.mkdirs();
        }
    }

    private File getKitFile(UUID uuid) {
        return new File(this.kitsFolder, uuid.toString() + ".yml");
    }

    public boolean hasKit(UUID uuid) {
        return getKitFile(uuid).exists();
    }

    public FileConfiguration loadKit(UUID uuid) {
        File file = getKitFile(uuid);
        if (!file.exists()) return null;
        return YamlConfiguration.loadConfiguration(file);
    }

    public void saveKit(UUID uuid,
                        ItemStack helmet,
                        ItemStack chestplate,
                        ItemStack leggings,
                        ItemStack boots,
                        List<ItemStack> contents) {

        File file = getKitFile(uuid);
        FileConfiguration cfg = new YamlConfiguration();

        cfg.set("helmet", helmet);
        cfg.set("chestplate", chestplate);
        cfg.set("leggings", leggings);
        cfg.set("boots", boots);
        cfg.set("contents", contents);

        try {
            cfg.save(file);
        } catch (IOException e) {
            this.plugin.getLogger().warning("Could not save kit for " + uuid + ": " + e.getMessage());
        }
    }

    public void deleteKit(UUID uuid) {
        File file = getKitFile(uuid);
        if (file.exists()) {
            file.delete();
        }
    }

    public ItemStack getHelmet(UUID uuid) {
        FileConfiguration cfg = loadKit(uuid);
        return cfg == null ? null : cfg.getItemStack("helmet");
    }

    public ItemStack getChestplate(UUID uuid) {
        FileConfiguration cfg = loadKit(uuid);
        return cfg == null ? null : cfg.getItemStack("chestplate");
    }

    public ItemStack getLeggings(UUID uuid) {
        FileConfiguration cfg = loadKit(uuid);
        return cfg == null ? null : cfg.getItemStack("leggings");
    }

    public ItemStack getBoots(UUID uuid) {
        FileConfiguration cfg = loadKit(uuid);
        return cfg == null ? null : cfg.getItemStack("boots");
    }

    public List<?> getContents(UUID uuid) {
        FileConfiguration cfg = loadKit(uuid);
        return cfg == null ? null : cfg.getList("contents");
    }

    public File getKitsFolder() {
        return this.kitsFolder;
    }
}
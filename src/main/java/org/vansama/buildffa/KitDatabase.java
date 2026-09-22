package org.vansama.buildffa;

import java.io.File;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Wrapper قدیمی که الان از DatabaseManager (SQLite) استفاده میکنه.
 * برای سازگاری با کدهای قدیمی که از KitDatabase استفاده میکنن.
 */
public class KitDatabase {

    private final JavaPlugin plugin;
    private final DatabaseManager database;

    public KitDatabase(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public boolean hasKit(UUID uuid) {
        return database.hasKit(uuid);
    }

    public void saveKit(UUID uuid,
                        ItemStack helmet,
                        ItemStack chestplate,
                        ItemStack leggings,
                        ItemStack boots,
                        List<ItemStack> contents) {
        database.saveKit(uuid, helmet, chestplate, leggings, boots, contents);
    }

    public void deleteKit(UUID uuid) {
        database.deleteKit(uuid);
    }

    public ItemStack getHelmet(UUID uuid)     { return database.getKitHelmet(uuid); }
    public ItemStack getChestplate(UUID uuid) { return database.getKitChestplate(uuid); }
    public ItemStack getLeggings(UUID uuid)   { return database.getKitLeggings(uuid); }
    public ItemStack getBoots(UUID uuid)      { return database.getKitBoots(uuid); }

    public List<ItemStack> getContents(UUID uuid) {
        return database.getKitContents(uuid);
    }

    // برای سازگاری با کد قدیمی که getKitsFolder میخواست
    public File getKitsFolder() {
        return this.plugin.getDataFolder();
    }
}
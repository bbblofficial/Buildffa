package org.vansama.buildffa;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final File dbFile;
    private final File backupFolder;

    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<UUID, PlayerData>();
    private final Map<UUID, Long> lastSaveTime = new ConcurrentHashMap<UUID, Long>();

    private Connection connection;
    private final Object dbLock = new Object();

    private long autosaveInterval;
    private boolean autosaveEnabled;
    private int autosaveTaskId = -1;

    private static final long SAVE_DEBOUNCE_MS = 300L;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;

        // پوشه اصلی
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        this.dbFile = new File(plugin.getDataFolder(), "buildffa.db");
        this.backupFolder = new File(plugin.getDataFolder(), "db_backup");
        if (!this.backupFolder.exists()) {
            this.backupFolder.mkdirs();
        }

        // لود درایور SQLite
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            // تلاش برای درایور shaded
            try {
                Class.forName("org.vansama.buildffa.libs.sqlite.JDBC");
            } catch (ClassNotFoundException e2) {
                plugin.getLogger().severe("SQLite JDBC driver not found! Plugin cannot work.");
                throw new RuntimeException("SQLite driver missing", e2);
            }
        }

        // اتصال به دیتابیس
        try {
            openConnection();
            createTables();
            migrateLegacyFiles();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }

        // خوندن کانفیگ
        try {
            FileConfiguration config = plugin.getConfig();
            this.autosaveEnabled = config.getBoolean("database.autosave", true);
            this.autosaveInterval = config.getLong("database.autosave-interval", 300L);
            if (this.autosaveInterval < 30L) this.autosaveInterval = 30L;
        } catch (Throwable t) {
            this.autosaveEnabled = true;
            this.autosaveInterval = 300L;
        }

        if (this.autosaveEnabled) {
            startAutosave();
            plugin.getLogger().info("Database autosave every " + this.autosaveInterval + "s");
        }

        plugin.getLogger().info("SQLite DB ready: " + this.dbFile.getPath());
    }

    // ============================================================
    //  CONNECTION
    // ============================================================
    private void openConnection() throws SQLException {
        synchronized (dbLock) {
            if (connection != null && !connection.isClosed()) return;
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            connection.setAutoCommit(true);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            openConnection();
        }
        return connection;
    }

    // ============================================================
    //  TABLES
    // ============================================================
    private void createTables() throws SQLException {
        synchronized (dbLock) {
            Connection c = getConnection();
            Statement st = c.createStatement();

            // جدول استت پلیر
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS players (" +
                "  uuid TEXT PRIMARY KEY," +
                "  name TEXT," +
                "  kills INTEGER NOT NULL DEFAULT 0," +
                "  deaths INTEGER NOT NULL DEFAULT 0," +
                "  killstreak INTEGER NOT NULL DEFAULT 0," +
                "  best_killstreak INTEGER NOT NULL DEFAULT 0," +
                "  last_seen INTEGER NOT NULL DEFAULT 0" +
                ")"
            );

            // جدول کیتها (base64 serialized ItemStack)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS kits (" +
                "  uuid TEXT PRIMARY KEY," +
                "  helmet TEXT," +
                "  chestplate TEXT," +
                "  leggings TEXT," +
                "  boots TEXT," +
                "  contents TEXT," +
                "  updated_at INTEGER NOT NULL DEFAULT 0" +
                ")"
            );

            // ایندکسها برای سرعت leaderboard
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_kills ON players(kills DESC)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_deaths ON players(deaths DESC)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_best_ks ON players(best_killstreak DESC)");

            st.close();
        }
    }

    // ============================================================
    //  MIGRATION از فایلهای قدیمی .json / .yml / kits/*.yml
    // ============================================================
    private void migrateLegacyFiles() {
        try {
            File oldDbFolder = new File(plugin.getDataFolder(), "db");
            File oldKitsFolder = new File(plugin.getDataFolder(), "kits");

            int migratedPlayers = 0;
            int migratedKits = 0;

            // --- پلیرها ---
            if (oldDbFolder.exists() && oldDbFolder.isDirectory()) {
                File[] files = oldDbFolder.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String n = f.getName();
                        if (!n.endsWith(".json") && !n.endsWith(".yml")) continue;

                        String uuidStr = n.replace(".json", "").replace(".yml", "");
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(uuidStr);
                        } catch (IllegalArgumentException e) {
                            continue;
                        }

                        // اگه توی دیتابیس هست، رد شو
                        if (playerExists(uuid)) continue;

                        try {
                            org.bukkit.configuration.file.FileConfiguration cfg =
                                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);

                            PlayerData data = new PlayerData(
                                uuid,
                                cfg.getString("name", "unknown"),
                                cfg.getInt("kills", 0),
                                cfg.getInt("deaths", 0),
                                cfg.getInt("killstreak", 0),
                                cfg.getInt("best-killstreak", 0),
                                cfg.getLong("last-seen", System.currentTimeMillis())
                            );

                            savePlayerImmediate(data);
                            migratedPlayers++;
                        } catch (Throwable t) {
                            plugin.getLogger().warning("Migration failed for " + n + ": " + t.getMessage());
                        }
                    }
                }

                // rename پوشه قدیمی بعد از مهاجرت
                if (migratedPlayers > 0) {
                    File renamed = new File(plugin.getDataFolder(),
                            "db_legacy_" + System.currentTimeMillis());
                    oldDbFolder.renameTo(renamed);
                }
            }

            // --- کیتها ---
            if (oldKitsFolder.exists() && oldKitsFolder.isDirectory()) {
                File[] files = oldKitsFolder.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String n = f.getName();
                        if (!n.endsWith(".yml")) continue;

                        String uuidStr = n.replace(".yml", "");
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(uuidStr);
                        } catch (IllegalArgumentException e) {
                            continue;
                        }

                        if (hasKit(uuid)) continue;

                        try {
                            org.bukkit.configuration.file.FileConfiguration cfg =
                                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);

                            ItemStack helmet = cfg.getItemStack("helmet");
                            ItemStack chestplate = cfg.getItemStack("chestplate");
                            ItemStack leggings = cfg.getItemStack("leggings");
                            ItemStack boots = cfg.getItemStack("boots");
                            List<?> contentsRaw = cfg.getList("contents");

                            List<ItemStack> contents = new ArrayList<ItemStack>();
                            if (contentsRaw != null) {
                                for (Object o : contentsRaw) {
                                    if (o instanceof ItemStack) contents.add((ItemStack) o);
                                    else contents.add(null);
                                }
                            }

                            saveKit(uuid, helmet, chestplate, leggings, boots, contents);
                            migratedKits++;
                        } catch (Throwable t) {
                            plugin.getLogger().warning("Kit migration failed for " + n + ": " + t.getMessage());
                        }
                    }
                }

                if (migratedKits > 0) {
                    File renamed = new File(plugin.getDataFolder(),
                            "kits_legacy_" + System.currentTimeMillis());
                    oldKitsFolder.renameTo(renamed);
                }
            }

            if (migratedPlayers > 0 || migratedKits > 0) {
                plugin.getLogger().info("Migrated " + migratedPlayers + " players, " +
                        migratedKits + " kits into SQLite.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Legacy migration error: " + t.getMessage());
        }
    }

    private boolean playerExists(UUID uuid) {
        try {
            synchronized (dbLock) {
                PreparedStatement ps = getConnection().prepareStatement(
                        "SELECT 1 FROM players WHERE uuid = ? LIMIT 1");
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                boolean exists = rs.next();
                rs.close();
                ps.close();
                return exists;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean hasKit(UUID uuid) {
        try {
            synchronized (dbLock) {
                PreparedStatement ps = getConnection().prepareStatement(
                        "SELECT 1 FROM kits WHERE uuid = ? LIMIT 1");
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                boolean exists = rs.next();
                rs.close();
                ps.close();
                return exists;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    // ============================================================
    //  PLAYER
    // ============================================================
    public PlayerData getPlayer(UUID uuid) {
        PlayerData data = this.cache.get(uuid);
        if (data != null) return data;
        return loadPlayer(uuid);
    }

    public PlayerData getPlayer(Player player) {
        PlayerData data = getPlayer(player.getUniqueId());
        if (data != null) data.setName(player.getName());
        return data;
    }

    public PlayerData loadPlayer(UUID uuid) {
        PlayerData cached = this.cache.get(uuid);
        if (cached != null) return cached;

        try {
            synchronized (dbLock) {
                PreparedStatement ps = getConnection().prepareStatement(
                        "SELECT name, kills, deaths, killstreak, best_killstreak, last_seen " +
                        "FROM players WHERE uuid = ?");
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                PlayerData data;
                if (rs.next()) {
                    data = new PlayerData(
                            uuid,
                            rs.getString("name"),
                            rs.getInt("kills"),
                            rs.getInt("deaths"),
                            rs.getInt("killstreak"),
                            rs.getInt("best_killstreak"),
                            rs.getLong("last_seen")
                    );
                } else {
                    data = new PlayerData(uuid, "unknown");
                }

                rs.close();
                ps.close();
                this.cache.put(uuid, data);
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("loadPlayer failed for " + uuid + ": " + e.getMessage());
            PlayerData data = new PlayerData(uuid, "unknown");
            this.cache.put(uuid, data);
            return data;
        }
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = this.cache.get(uuid);
        if (data == null) return;
        savePlayer(data);
    }

    public void savePlayer(PlayerData data) {
        if (data == null) return;

        // debounce
        long now = System.currentTimeMillis();
        Long last = this.lastSaveTime.get(data.getUuid());
        if (last != null && now - last < SAVE_DEBOUNCE_MS) {
            return;
        }
        this.lastSaveTime.put(data.getUuid(), now);

        savePlayerImmediate(data);
    }

    /** بدون debounce — برای مهاجرت و shutdown */
    public void savePlayerImmediate(PlayerData data) {
        if (data == null) return;

        try {
            synchronized (dbLock) {
                PreparedStatement ps = getConnection().prepareStatement(
                    "INSERT INTO players (uuid, name, kills, deaths, killstreak, best_killstreak, last_seen) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET " +
                    "  name = excluded.name," +
                    "  kills = excluded.kills," +
                    "  deaths = excluded.deaths," +
                    "  killstreak = excluded.killstreak," +
                    "  best_killstreak = excluded.best_killstreak," +
                    "  last_seen = excluded.last_seen"
                );
                ps.setString(1, data.getUuid().toString());
                ps.setString(2, data.getName());
                ps.setInt(3, data.getKills());
                ps.setInt(4, data.getDeaths());
                ps.setInt(5, data.getKillstreak());
                ps.setInt(6, data.getBestKillstreak());
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
                ps.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("savePlayer failed for " + data.getUuid() + ": " + e.getMessage());
        }
    }

    public void unloadPlayer(UUID uuid) {
        PlayerData data = this.cache.get(uuid);
        if (data != null) {
            this.lastSaveTime.remove(uuid);
            savePlayerImmediate(data);
            this.cache.remove(uuid);
        }
    }

    public void deletePlayer(UUID uuid) {
        this.cache.remove(uuid);
        this.lastSaveTime.remove(uuid);
        try {
            synchronized (dbLock) {
                PreparedStatement ps1 = getConnection().prepareStatement(
                        "DELETE FROM players WHERE uuid = ?");
                ps1.setString(1, uuid.toString());
                ps1.executeUpdate();
                ps1.close();

                PreparedStatement ps2 = getConnection().prepareStatement(
                        "DELETE FROM kits WHERE uuid = ?");
                ps2.setString(1, uuid.toString());
                ps2.executeUpdate();
                ps2.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("deletePlayer failed: " + e.getMessage());
        }
    }

    // ============================================================
    //  KITS  (base64 serialized)
    // ============================================================
    public boolean hasKit(UUID uuid) {
        try {
            synchronized (dbLock) {
                PreparedStatement ps = getConnection().prepareStatement(
                        "SELECT 1 FROM kits WHERE uuid = ? LIMIT 1");
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                boolean exists = rs.next();
                rs.close();
                ps.close();
                return exists;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public void saveKit(UUID uuid,
                        ItemStack helmet, ItemStack chestplate,
                        ItemStack leggings, ItemStack boots,
                        List<ItemStack> contents) {
        try {
            String sHelmet = serializeItem(helmet);
            String sChest  = serializeItem(chestplate);
            String sLegs   = serializeItem(leggings);
            String sBoots  = serializeItem(boots);
            String sContents = serializeItemList(contents);

            synchronized (dbLock) {
                PreparedStatement ps = getConnection().prepareStatement(
                    "INSERT INTO kits (uuid, helmet, chestplate, leggings, boots, contents, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET " +
                    "  helmet = excluded.helmet," +
                    "  chestplate = excluded.chestplate," +
                    "  leggings = excluded.leggings," +
                    "  boots = excluded.boots," +
                    "  contents = excluded.contents," +
                    "  updated_at = excluded.updated_at"
                );
                ps.setString(1, uuid.toString());
                ps.setString(2, sHelmet);
                ps.setString(3, sChest);
                ps.setString(4, sLegs);
                ps.setString(5, sBoots);
                ps.setString(6, sContents);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
                ps.close();
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("saveKit failed for " + uuid + ": " + t.getMessage());
        }
    }

    public void deleteKit(UUID uuid) {
        try {
            synchronized (dbLock) {
                PreparedStatement ps = getConnection().prepareStatement(
                        "DELETE FROM kits WHERE uuid = ?");
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
                ps.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("deleteKit failed: " + e.getMessage());
        }
    }

    public ItemStack getKitHelmet(UUID uuid)     { return deserializeItem(getKitField(uuid, "helmet")); }
    public ItemStack getKitChestplate(UUID uuid) { return deserializeItem(getKitField(uuid, "chestplate")); }
    public ItemStack getKitLeggings(UUID uuid)   { return deserializeItem(getKitField(uuid, "leggings")); }
    public ItemStack getKitBoots(UUID uuid)      { return deserializeItem(getKitField(uuid, "boots")); }

    public List<ItemStack> getKitContents(UUID uuid) {
        return deserializeItemList(getKitField(uuid, "contents"));
    }

    private String getKitField(UUID uuid, String column) {
        try {
            synchronized (dbLock) {
                PreparedStatement ps = getConnection().prepareStatement(
                        "SELECT " + column + " FROM kits WHERE uuid = ?");
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                String out = null;
                if (rs.next()) out = rs.getString(column);
                rs.close();
                ps.close();
                return out;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    // ============================================================
    //  SERIALIZATION
    // ============================================================
    private String serializeItem(ItemStack item) {
        if (item == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos);
            out.writeObject(item);
            out.close();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    private ItemStack deserializeItem(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            BukkitObjectInputStream in = new BukkitObjectInputStream(bais);
            ItemStack item = (ItemStack) in.readObject();
            in.close();
            return item;
        } catch (Throwable e) {
            return null;
        }
    }

    private String serializeItemList(List<ItemStack> list) {
        if (list == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos);
            out.writeInt(list.size());
            for (ItemStack item : list) {
                out.writeObject(item);
            }
            out.close();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    private List<ItemStack> deserializeItemList(String data) {
        List<ItemStack> list = new ArrayList<ItemStack>();
        if (data == null || data.isEmpty()) return list;
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            BukkitObjectInputStream in = new BukkitObjectInputStream(bais);
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                try {
                    Object o = in.readObject();
                    list.add(o instanceof ItemStack ? (ItemStack) o : null);
                } catch (Throwable t) {
                    list.add(null);
                }
            }
            in.close();
        } catch (Throwable ignored) {}
        return list;
    }

    // ============================================================
    //  LEADERBOARDS
    // ============================================================
    public List<PlayerData> getTopKills(int limit)       { return queryTop("kills DESC", limit); }
    public List<PlayerData> getTopDeaths(int limit)      { return queryTop("deaths DESC", limit); }
    public List<PlayerData> getTopKillstreak(int limit)  { return queryTop("best_killstreak DESC", limit); }

    public List<PlayerData> getTopKDR(int limit) {
        // KDR = kills / deaths (با deaths=0 میشه kills)
        List<PlayerData> list = new ArrayList<PlayerData>();
        try {
            synchronized (dbLock) {
                PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT uuid, name, kills, deaths, killstreak, best_killstreak, last_seen " +
                    "FROM players " +
                    "ORDER BY (CAST(kills AS REAL) / CASE WHEN deaths = 0 THEN 1 ELSE deaths END) DESC, kills DESC " +
                    "LIMIT ?"
                );
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(fromResultSet(rs));
                }
                rs.close();
                ps.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getTopKDR failed: " + e.getMessage());
        }
        return list;
    }

    private List<PlayerData> queryTop(String orderBy, int limit) {
        List<PlayerData> list = new ArrayList<PlayerData>();
        try {
            synchronized (dbLock) {
                PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT uuid, name, kills, deaths, killstreak, best_killstreak, last_seen " +
                    "FROM players ORDER BY " + orderBy + " LIMIT ?"
                );
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(fromResultSet(rs));
                }
                rs.close();
                ps.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("queryTop failed: " + e.getMessage());
        }
        return list;
    }

    private PlayerData fromResultSet(ResultSet rs) throws SQLException {
        UUID uuid;
        try {
            uuid = UUID.fromString(rs.getString("uuid"));
        } catch (IllegalArgumentException e) {
            uuid = UUID.randomUUID();
        }
        return new PlayerData(
                uuid,
                rs.getString("name"),
                rs.getInt("kills"),
                rs.getInt("deaths"),
                rs.getInt("killstreak"),
                rs.getInt("best_killstreak"),
                rs.getLong("last_seen")
        );
    }

    public int getPlayerRank(UUID uuid, String type) {
        // rank بر اساس نوع
        String orderBy;
        if ("deaths".equals(type)) orderBy = "deaths DESC";
        else if ("killstreak".equals(type) || "streak".equals(type)) orderBy = "best_killstreak DESC";
        else if ("kdr".equals(type)) {
            orderBy = "(CAST(kills AS REAL) / CASE WHEN deaths = 0 THEN 1 ELSE deaths END) DESC";
        } else orderBy = "kills DESC";

        try {
            synchronized (dbLock) {
                PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT uuid FROM players ORDER BY " + orderBy
                );
                ResultSet rs = ps.executeQuery();
                int rank = 1;
                String target = uuid.toString();
                while (rs.next()) {
                    if (target.equals(rs.getString("uuid"))) {
                        rs.close();
                        ps.close();
                        return rank;
                    }
                    rank++;
                }
                rs.close();
                ps.close();
            }
        } catch (SQLException ignored) {}
        return -1;
    }

    // ============================================================
    //  AUTOSAVE / BACKUP / SHUTDOWN
    // ============================================================
    private void startAutosave() {
        long ticks = this.autosaveInterval * 20L;
        this.autosaveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    saveAll();
                } catch (Throwable t) {
                    plugin.getLogger().warning("Autosave failed: " + t.getMessage());
                }
            }
        }, ticks, ticks);
    }

    public void saveAll() {
        int count = 0;
        for (PlayerData data : new ArrayList<PlayerData>(this.cache.values())) {
            this.lastSaveTime.remove(data.getUuid());
            savePlayerImmediate(data);
            count++;
        }
        if (count > 0) {
            plugin.getLogger().info("Saved " + count + " player records to SQLite");
        }

        // backup دوره‌ای
        makeBackup();
    }

    public void makeBackup() {
        try {
            if (!dbFile.exists()) return;

            // فقط هر 24 ساعت یه بار backup کامل
            File[] backups = backupFolder.listFiles();
            long now = System.currentTimeMillis();
            if (backups != null) {
                for (File b : backups) {
                    if (now - b.lastModified() < 24L * 60L * 60L * 1000L) {
                        return; // backup اخیر وجود داره
                    }
                }
            }

            File backup = new File(backupFolder,
                    "buildffa_" + System.currentTimeMillis() + ".db");
            Files.copy(dbFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // فقط 5 backup آخر رو نگهدار
            File[] all = backupFolder.listFiles();
            if (all != null && all.length > 5) {
                java.util.Arrays.sort(all, new java.util.Comparator<File>() {
                    public int compare(File a, File b) {
                        return Long.compare(a.lastModified(), b.lastModified());
                    }
                });
                for (int i = 0; i < all.length - 5; i++) {
                    all[i].delete();
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Backup failed: " + t.getMessage());
        }
    }

    public void shutdown() {
        if (this.autosaveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(this.autosaveTaskId);
            this.autosaveTaskId = -1;
        }

        // ذخیره نهایی همه
        for (PlayerData data : new ArrayList<PlayerData>(this.cache.values())) {
            this.lastSaveTime.remove(data.getUuid());
            savePlayerImmediate(data);
        }

        makeBackup();

        // بستن اتصال
        synchronized (dbLock) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException ignored) {}
            connection = null;
        }
    }

    public File getDbFolder() {
        return this.dbFile.getParentFile();
    }

    public File getDbFile() {
        return this.dbFile;
    }

    public File getBackupFolder() {
        return this.backupFolder;
    }

    public int getCacheSize() {
        return this.cache.size();
    }
}
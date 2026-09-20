package org.vansama.buildffa;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final File dbFolder;
    private final Map<UUID, PlayerData> cache = new HashMap<UUID, PlayerData>();
    private final Map<UUID, Long> lastSaveTime = new HashMap<UUID, Long>();

    private long autosaveInterval;
    private boolean autosaveEnabled;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;

        this.dbFolder = new File(plugin.getDataFolder(), "db");
        if (!this.dbFolder.exists()) {
            this.dbFolder.mkdirs();
            plugin.getLogger().info("Created db folder");
        }

        FileConfiguration config = plugin.getConfig();
        this.autosaveEnabled = config.getBoolean("database.autosave", true);
        this.autosaveInterval = config.getLong("database.autosave-interval", 300L);

        if (this.autosaveEnabled) {
            startAutosave();
        }
    }

    private void startAutosave() {
        long ticks = this.autosaveInterval * 20L;
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, new Runnable() {
            @Override
            public void run() {
                saveAll();
            }
        }, ticks, ticks);
    }

    private File getPlayerFile(UUID uuid) {
        return new File(this.dbFolder, uuid.toString() + ".json");
    }

    public PlayerData getPlayer(UUID uuid) {
        PlayerData data = this.cache.get(uuid);
        if (data != null) return data;
        return loadPlayer(uuid);
    }

    public PlayerData getPlayer(Player player) {
        PlayerData data = getPlayer(player.getUniqueId());
        if (data != null) {
            data.setName(player.getName());
        }
        return data;
    }

    public PlayerData loadPlayer(UUID uuid) {
        File file = getPlayerFile(uuid);
        if (!file.exists()) {
            PlayerData data = new PlayerData(uuid, "unknown");
            this.cache.put(uuid, data);
            return data;
        }

        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String name = cfg.getString("name", "unknown");
            int kills = cfg.getInt("kills", 0);
            int deaths = cfg.getInt("deaths", 0);
            int killstreak = cfg.getInt("killstreak", 0);
            int bestKillstreak = cfg.getInt("best-killstreak", 0);
            long lastSeen = cfg.getLong("last-seen", System.currentTimeMillis());

            PlayerData data = new PlayerData(uuid, name, kills, deaths,
                    killstreak, bestKillstreak, lastSeen);
            this.cache.put(uuid, data);
            return data;
        } catch (Exception e) {
            this.plugin.getLogger().warning("Could not load data for " + uuid + ": " + e.getMessage());
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

        File file = getPlayerFile(data.getUuid());

        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

            cfg.set("uuid", data.getUuid().toString());
            cfg.set("name", data.getName());
            cfg.set("kills", Integer.valueOf(data.getKills()));
            cfg.set("deaths", Integer.valueOf(data.getDeaths()));
            cfg.set("killstreak", Integer.valueOf(data.getKillstreak()));
            cfg.set("best-killstreak", Integer.valueOf(data.getBestKillstreak()));
            cfg.set("kdr", Double.valueOf(data.getKDR()));
            cfg.set("last-seen", Long.valueOf(System.currentTimeMillis()));

            cfg.save(file);
            this.lastSaveTime.put(data.getUuid(), Long.valueOf(System.currentTimeMillis()));
        } catch (IOException e) {
            this.plugin.getLogger().warning("Could not save data for " + data.getUuid() + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        int count = 0;
        for (PlayerData data : new ArrayList<PlayerData>(this.cache.values())) {
            savePlayer(data);
            count++;
        }
        if (count > 0) {
            this.plugin.getLogger().info("Saved " + count + " player data files");
        }
    }

    public void unloadPlayer(UUID uuid) {
        PlayerData data = this.cache.get(uuid);
        if (data != null) {
            savePlayer(data);
            this.cache.remove(uuid);
            this.lastSaveTime.remove(uuid);
        }
    }

    public void deletePlayer(UUID uuid) {
        this.cache.remove(uuid);
        File file = getPlayerFile(uuid);
        if (file.exists()) {
            file.delete();
        }
    }

    public List<PlayerData> getTopKills(int limit) {
        List<PlayerData> all = loadAllPlayers();
        Collections.sort(all, new Comparator<PlayerData>() {
            @Override
            public int compare(PlayerData a, PlayerData b) {
                return Integer.compare(b.getKills(), a.getKills());
            }
        });
        if (all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    public List<PlayerData> getTopDeaths(int limit) {
        List<PlayerData> all = loadAllPlayers();
        Collections.sort(all, new Comparator<PlayerData>() {
            @Override
            public int compare(PlayerData a, PlayerData b) {
                return Integer.compare(b.getDeaths(), a.getDeaths());
            }
        });
        if (all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    public List<PlayerData> getTopKDR(int limit) {
        List<PlayerData> all = loadAllPlayers();
        Collections.sort(all, new Comparator<PlayerData>() {
            @Override
            public int compare(PlayerData a, PlayerData b) {
                return Double.compare(b.getKDR(), a.getKDR());
            }
        });
        if (all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    public List<PlayerData> getTopKillstreak(int limit) {
        List<PlayerData> all = loadAllPlayers();
        Collections.sort(all, new Comparator<PlayerData>() {
            @Override
            public int compare(PlayerData a, PlayerData b) {
                return Integer.compare(b.getBestKillstreak(), a.getBestKillstreak());
            }
        });
        if (all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    private List<PlayerData> loadAllPlayers() {
        List<PlayerData> list = new ArrayList<PlayerData>();

        for (PlayerData cached : this.cache.values()) {
            list.add(cached);
        }

        File[] files = this.dbFolder.listFiles();
        if (files == null) return list;

        for (File file : files) {
            if (!file.getName().endsWith(".json")) continue;

            String fileName = file.getName().replace(".json", "");
            UUID uuid;
            try {
                uuid = UUID.fromString(fileName);
            } catch (IllegalArgumentException e) {
                continue;
            }

            if (this.cache.containsKey(uuid)) continue;

            PlayerData data = loadPlayer(uuid);
            if (data != null) {
                list.add(data);
            }
        }

        return list;
    }

    public int getCacheSize() {
        return this.cache.size();
    }

    public File getDbFolder() {
        return this.dbFolder;
    }
}
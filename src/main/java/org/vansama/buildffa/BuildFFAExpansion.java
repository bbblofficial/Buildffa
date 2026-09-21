package org.vansama.buildffa;

import java.util.List;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class BuildFFAExpansion extends PlaceholderExpansion {

    private final BuildFFA plugin;
    private final DatabaseManager database;

    public BuildFFAExpansion(BuildFFA plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public String getIdentifier() {
        return "buildffa";
    }

    @Override
    public String getAuthor() {
        return "VanSaMa";
    }

    @Override
    public String getVersion() {
        return "4.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (identifier == null) return "";

        String id = identifier.toLowerCase();

        // ---------- Player's own stats ----------
        if (player != null && player.isOnline()) {
            Player online = player.getPlayer();
            PlayerData data = database.getPlayer(player.getUniqueId());
            if (data == null) data = database.loadPlayer(player.getUniqueId());

            if (id.equals("kills")) return String.valueOf(data.getKills());
            if (id.equals("deaths")) return String.valueOf(data.getDeaths());
            if (id.equals("kdr")) return String.format("%.2f", data.getKDR());
            if (id.equals("killstreak") || id.equals("streak")) return String.valueOf(data.getKillstreak());
            if (id.equals("best_killstreak") || id.equals("beststreak")) return String.valueOf(data.getBestKillstreak());
        }

        // ---------- Top placeholders: %buildffa_top_name_kills_1% ----------
        if (id.startsWith("top_") && !id.startsWith("top_rank_")) {
            String[] parts = id.split("_");
            if (parts.length < 4) return "";

            String mode = parts[1];   // "name" or "value"
            String type = parts[2];   // "kills", "deaths", "kdr", "killstreak"
            int rank;
            try {
                rank = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                return "";
            }

            if (rank < 1 || rank > 50) return "";

            if (type.equals("streak")) type = "killstreak";

            List<PlayerData> top;
            if (type.equals("deaths")) {
                top = database.getTopDeaths(rank);
            } else if (type.equals("kdr")) {
                top = database.getTopKDR(rank);
            } else if (type.equals("killstreak")) {
                top = database.getTopKillstreak(rank);
            } else {
                top = database.getTopKills(rank);
            }

            if (top.size() < rank) {
                if (mode.equals("name")) return "---";
                return "0";
            }

            PlayerData entry = top.get(rank - 1);

            if (mode.equals("name")) {
                return entry.getName();
            } else {
                if (type.equals("kdr")) return String.format("%.2f", entry.getKDR());
                if (type.equals("deaths")) return String.valueOf(entry.getDeaths());
                if (type.equals("killstreak")) return String.valueOf(entry.getBestKillstreak());
                return String.valueOf(entry.getKills());
            }
        }

        // ---------- Player's own rank: %buildffa_top_rank_kills% ----------
        if (id.startsWith("top_rank_")) {
            if (player == null) return "?";

            String type = id.substring("top_rank_".length());
            if (type.equals("streak")) type = "killstreak";

            List<PlayerData> all;
            if (type.equals("deaths")) {
                all = database.getTopDeaths(1000);
            } else if (type.equals("kdr")) {
                all = database.getTopKDR(1000);
            } else if (type.equals("killstreak")) {
                all = database.getTopKillstreak(1000);
            } else {
                all = database.getTopKills(1000);
            }

            int rank = 1;
            for (PlayerData d : all) {
                if (d.getUuid().equals(player.getUniqueId())) {
                    return String.valueOf(rank);
                }
                rank++;
            }
            return "?";
        }

        return null;
    }
}
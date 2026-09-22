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

    // ============================================================
    //  Main entry with try/catch
    // ============================================================
    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        try {
            return handleRequest(player, identifier);
        } catch (Throwable t) {
            this.plugin.getLogger().warning(
                "Placeholder error for '" + identifier + "': " + t.getMessage());
            return "ERR";
        }
    }

    private String handleRequest(OfflinePlayer player, String identifier) {
        if (identifier == null) return "";

        String id = identifier.toLowerCase();

        // ============================================================
        //  Live countdown: %buildffa_next_update%
        // ============================================================
        if (id.equals("next_update") || id.equals("countdown")) {
            int secs = BuildFFA.getSecondsUntilRefresh();
            int minutes = secs / 60;
            int seconds = secs % 60;
            return String.format("%02d:%02d", minutes, seconds);
        }

        // ============================================================
        //  Player stats (fresh from cache)
        // ============================================================
        if (player != null && player.isOnline()) {
            PlayerData data = database.getPlayer(player.getUniqueId());
            if (data == null) data = database.loadPlayer(player.getUniqueId());

            if (data != null) {
                if (id.equals("kills")) return String.valueOf(data.getKills());
                if (id.equals("deaths")) return String.valueOf(data.getDeaths());
                if (id.equals("kdr")) return String.format("%.2f", data.getKDR());
                if (id.equals("killstreak") || id.equals("streak")) return String.valueOf(data.getKillstreak());
                if (id.equals("best_killstreak") || id.equals("beststreak")) return String.valueOf(data.getBestKillstreak());
            }
        }

        // ============================================================
        //  Top placeholders:  top_<mode>_<type>_<rank>
        //  Examples:
        //    top_name_killstreak_1  → name of #1 by best killstreak
        //    top_value_killstreak_1 → best killstreak of #1
        //    top_name_kills_1       → name of #1 by kills
        //    top_value_kills_1      → kills of #1
        //    top_name_kdr_1         → name of #1 by KDR
        //    top_value_kdr_1        → KDR of #1
        //    top_name_deaths_1      → name of #1 by deaths
        //    top_value_deaths_1     → deaths of #1
        // ============================================================
        if (id.startsWith("top_") && !id.startsWith("top_rank_")) {
            String[] parts = id.split("_");
            if (parts.length < 4) return "";

            String mode = parts[1];       // name | value
            String type = parts[2];       // kills | deaths | kdr | killstreak | streak
            int rank;
            try {
                rank = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                return "";
            }

            if (rank < 1 || rank > 50) return "";

            if (type.equals("streak")) type = "killstreak";

            // ✅ cache-aware top fetch
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

            // No data yet
            if (top.size() < rank) {
                if (mode.equals("name")) return "None";
                return "-";
            }

            PlayerData entry = top.get(rank - 1);

            if (mode.equals("name")) {
                String name = entry.getName();
                if (name == null || name.isEmpty()) return "None";
                return name;
            } else {
                if (type.equals("kdr")) return String.format("%.2f", entry.getKDR());
                if (type.equals("deaths")) return String.valueOf(entry.getDeaths());
                if (type.equals("killstreak")) return String.valueOf(entry.getBestKillstreak());
                return String.valueOf(entry.getKills());
            }
        }

        // ============================================================
        //  Own rank:  top_rank_<type>
        //  Examples:
        //    top_rank_kills
        //    top_rank_kdr
        //    top_rank_killstreak
        //    top_rank_deaths
        // ============================================================
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
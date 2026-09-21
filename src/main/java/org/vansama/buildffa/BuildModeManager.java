package org.vansama.buildffa;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

public class BuildModeManager {

    private static final Set<UUID> buildModePlayers = new HashSet<UUID>();

    public static boolean isInBuildMode(Player player) {
        if (player == null) return false;
        return buildModePlayers.contains(player.getUniqueId());
    }

    public static boolean isInBuildMode(UUID uuid) {
        return buildModePlayers.contains(uuid);
    }

    public static void setBuildMode(Player player, boolean enabled) {
        if (player == null) return;
        if (enabled) {
            buildModePlayers.add(player.getUniqueId());
        } else {
            buildModePlayers.remove(player.getUniqueId());
        }
    }

    public static boolean toggleBuildMode(Player player) {
        if (player == null) return false;
        if (buildModePlayers.contains(player.getUniqueId())) {
            buildModePlayers.remove(player.getUniqueId());
            return false;
        } else {
            buildModePlayers.add(player.getUniqueId());
            return true;
        }
    }

    public static void clear(Player player) {
        if (player == null) return;
        buildModePlayers.remove(player.getUniqueId());
    }

    public static void clearAll() {
        buildModePlayers.clear();
    }

    public static Set<UUID> getAll() {
        return buildModePlayers;
    }
}
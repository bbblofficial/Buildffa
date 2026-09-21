package org.vansama.buildffa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CombatManager {

    // Victim -> Attacker (last hit)
    private static final Map<UUID, UUID> lastAttacker = new HashMap<UUID, UUID>();
    // Victim -> timestamp of last hit
    private static final Map<UUID, Long> lastHitTime = new HashMap<UUID, Long>();
    // Who is currently in combat with whom (pair set)
    private static final Map<UUID, UUID> combatPartner = new HashMap<UUID, UUID>();

    private static long combatTimeoutMs = 15000L; // 15s default

    public static void setCombatTimeoutSeconds(int seconds) {
        if (seconds < 1) seconds = 1;
        if (seconds > 300) seconds = 300;
        combatTimeoutMs = seconds * 1000L;
    }

    /**
     * Called when attacker hits victim.
     * Both enter combat mode.
     */
    public static void registerHit(UUID attacker, UUID victim) {
        if (attacker == null || victim == null) return;
        if (attacker.equals(victim)) return;

        long now = System.currentTimeMillis();
        lastAttacker.put(victim, attacker);
        lastHitTime.put(victim, Long.valueOf(now));

        combatPartner.put(attacker, victim);
        combatPartner.put(victim, attacker);
    }

    /**
     * Returns true if both players are in combat with each other and still within the timeout.
     */
    public static boolean areInCombat(UUID a, UUID b) {
        if (a == null || b == null) return false;
        UUID partnerA = combatPartner.get(a);
        UUID partnerB = combatPartner.get(b);
        if (partnerA == null || partnerB == null) return false;
        if (!partnerA.equals(b) || !partnerB.equals(a)) return false;

        // Check timeout
        Long timeA = lastHitTime.get(a);
        Long timeB = lastHitTime.get(b);
        long now = System.currentTimeMillis();
        if (timeA != null && now - timeA.longValue() > combatTimeoutMs) return false;
        if (timeB != null && now - timeB.longValue() > combatTimeoutMs) return false;

        return true;
    }

    /**
     * Returns the partner UUID that this player is currently fighting, or null.
     */
    public static UUID getCombatPartner(UUID player) {
        return combatPartner.get(player);
    }

    /**
     * Called when a player disconnects. Removes them from all combat maps.
     */
    public static void clearPlayer(UUID uuid) {
        if (uuid == null) return;
        UUID partner = combatPartner.get(uuid);
        if (partner != null) {
            combatPartner.remove(partner);
        }
        combatPartner.remove(uuid);
        lastAttacker.remove(uuid);
        lastHitTime.remove(uuid);
    }

    public static void clearAll() {
        lastAttacker.clear();
        lastHitTime.clear();
        combatPartner.clear();
    }

    /**
     * Removes expired combat entries. Should be called periodically.
     */
    public static void cleanupExpired() {
        long now = System.currentTimeMillis();
        Set<UUID> toRemove = new HashSet<UUID>();
        for (Map.Entry<UUID, Long> entry : lastHitTime.entrySet()) {
            if (now - entry.getValue().longValue() > combatTimeoutMs) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID uuid : toRemove) {
            clearPlayer(uuid);
        }
    }
}
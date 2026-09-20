package org.vansama.buildffa;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private String name;
    private int kills;
    private int deaths;
    private int killstreak;
    private int bestKillstreak;
    private long lastSeen;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.kills = 0;
        this.deaths = 0;
        this.killstreak = 0;
        this.bestKillstreak = 0;
        this.lastSeen = System.currentTimeMillis();
    }

    public PlayerData(UUID uuid, String name, int kills, int deaths,
                      int killstreak, int bestKillstreak, long lastSeen) {
        this.uuid = uuid;
        this.name = name;
        this.kills = kills;
        this.deaths = deaths;
        this.killstreak = killstreak;
        this.bestKillstreak = bestKillstreak;
        this.lastSeen = lastSeen;
    }

    public UUID getUuid() { return this.uuid; }

    public String getName() { return this.name; }
    public void setName(String name) { this.name = name; }

    public int getKills() { return this.kills; }
    public void setKills(int kills) { this.kills = kills; }
    public void addKill() { this.kills++; }

    public int getDeaths() { return this.deaths; }
    public void setDeaths(int deaths) { this.deaths = deaths; }
    public void addDeath() { this.deaths++; }

    public int getKillstreak() { return this.killstreak; }
    public void setKillstreak(int killstreak) { this.killstreak = killstreak; }
    public void addKillstreak() {
        this.killstreak++;
        if (this.killstreak > this.bestKillstreak) {
            this.bestKillstreak = this.killstreak;
        }
    }
    public void resetKillstreak() { this.killstreak = 0; }

    public int getBestKillstreak() { return this.bestKillstreak; }
    public void setBestKillstreak(int bestKillstreak) { this.bestKillstreak = bestKillstreak; }

    public long getLastSeen() { return this.lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

    public double getKDR() {
        if (this.deaths == 0) return this.kills;
        return (double) this.kills / (double) this.deaths;
    }
}
package com.example.mysubmod.submodes.submode1.data;

import com.example.mysubmod.submodes.submode1.islands.IslandType;

public class CandySpawnEntry {
    private final int timeSeconds;
    private final int candyCount;
    private final IslandType island;
    private final int spawnPointNumber;

    public CandySpawnEntry(int timeSeconds, int candyCount, IslandType island, int spawnPointNumber) {
        this.timeSeconds = timeSeconds;
        this.candyCount = candyCount;
        this.island = island;
        this.spawnPointNumber = spawnPointNumber;
    }

    public int getTimeSeconds() {
        return timeSeconds;
    }

    public int getCandyCount() {
        return candyCount;
    }

    public IslandType getIsland() {
        return island;
    }

    public int getSpawnPointNumber() {
        return spawnPointNumber;
    }

    public long getTimeMs() {
        return timeSeconds * 1000L;
    }

    @Override
    public String toString() {
        return String.format("CandySpawnEntry{time=%ds, count=%d, island=%s, spawnPoint=%d}",
            timeSeconds, candyCount, island.getDisplayName(), spawnPointNumber);
    }
}
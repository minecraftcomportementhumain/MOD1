package com.example.mysubmod.submodes.submodeParent.data;

import net.minecraft.core.BlockPos;

public class CandySpawnEntry {
    protected int timeSeconds;      // Time in seconds (max 900 = 15 minutes)
    protected int candyCount;       // Number of candies (max 100)
    protected BlockPos position;    // Exact coordinates

    public CandySpawnEntry(int timeSeconds, int candyCount, BlockPos position) {
        this.timeSeconds = timeSeconds;
        this.candyCount = candyCount;
        this.position = position;
    }

    public int getTimeSeconds() {
        return timeSeconds;
    }

    public int getCandyCount() {
        return candyCount;
    }

    public BlockPos getPosition() {
        return position;
    }

    public long getTimeMs() {
        return timeSeconds * 1000L;
    }

    @Override
    public String toString() {
        return String.format("CandySpawnEntry{time=%ds, count=%d, pos=(%d,%d,%d)}",
            timeSeconds, candyCount, position.getX(), position.getY(), position.getZ());
    }
}
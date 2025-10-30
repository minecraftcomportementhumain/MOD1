package com.example.mysubmod.submodes.submode2.data;

import com.example.mysubmod.submodes.submode2.ResourceType;
import net.minecraft.core.BlockPos;

/**
 * Entry pour le spawn de bonbons dans le Sous-mode 2
 * Contient le type de ressource (A ou B) en plus des infos de base
 */
public class CandySpawnEntry {
    private final int timeSeconds;       // Time in seconds (max 900 = 15 minutes)
    private final int candyCount;        // Number of candies (max 100)
    private final BlockPos position;     // Exact coordinates
    private final ResourceType type;     // Type de ressource (A ou B)

    public CandySpawnEntry(int timeSeconds, int candyCount, BlockPos position, ResourceType type) {
        this.timeSeconds = timeSeconds;
        this.candyCount = candyCount;
        this.position = position;
        this.type = type;
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

    public ResourceType getType() {
        return type;
    }

    public long getTimeMs() {
        return timeSeconds * 1000L;
    }

    @Override
    public String toString() {
        return String.format("CandySpawnEntry{time=%ds, count=%d, pos=(%d,%d,%d), type=%s}",
            timeSeconds, candyCount, position.getX(), position.getY(), position.getZ(), type);
    }
}

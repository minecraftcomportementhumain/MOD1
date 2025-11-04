package com.example.mysubmod.submodes.submode2.data;

import com.example.mysubmod.submodes.submode2.ResourceType;
import com.example.mysubmod.submodes.submodeParent.data.CandySpawnEntry;
import net.minecraft.core.BlockPos;

/**
 * Entry pour le spawn de bonbons dans le Sous-mode 2
 * Contient le type de ressource (A ou B) en plus des infos de base
 */
public class SubMode2SpawnEntry extends CandySpawnEntry {
    private final ResourceType type;     // Type de ressource (A ou B)

    public SubMode2SpawnEntry(int timeSeconds, int candyCount, BlockPos position, ResourceType type) {
        super(timeSeconds,candyCount,position);
        this.type = type;
    }

    public ResourceType getType() {
        return type;
    }

    @Override
    public String toString() {
        return String.format("CandySpawnEntry{time=%ds, count=%d, pos=(%d,%d,%d), type=%s}",
            timeSeconds, candyCount, position.getX(), position.getY(), position.getZ(), type);
    }
}

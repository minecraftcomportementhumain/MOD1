package com.example.mysubmod.submodes.submode1;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submode1.islands.IslandType;
import net.minecraft.core.BlockPos;

import java.util.*;

public class SpawnPointManager {
    private static SpawnPointManager instance;
    private final Map<IslandType, List<BlockPos>> spawnPoints = new HashMap<>();
    private static final int MIN_DISTANCE = 40; // Minimum 40 blocks between spawn points

    private SpawnPointManager() {}

    public static SpawnPointManager getInstance() {
        if (instance == null) {
            instance = new SpawnPointManager();
        }
        return instance;
    }

    /**
     * Generate random spawn points for all islands at game start
     */
    public void generateSpawnPoints(BlockPos smallIslandCenter, BlockPos mediumIslandCenter,
                                     BlockPos largeIslandCenter, BlockPos extraLargeIslandCenter) {
        spawnPoints.clear();
        Random random = new Random();

        // Generate spawn points for each island
        spawnPoints.put(IslandType.SMALL, generateIslandSpawnPoints(smallIslandCenter, IslandType.SMALL, random));
        spawnPoints.put(IslandType.MEDIUM, generateIslandSpawnPoints(mediumIslandCenter, IslandType.MEDIUM, random));
        spawnPoints.put(IslandType.LARGE, generateIslandSpawnPoints(largeIslandCenter, IslandType.LARGE, random));
        spawnPoints.put(IslandType.EXTRA_LARGE, generateIslandSpawnPoints(extraLargeIslandCenter, IslandType.EXTRA_LARGE, random));

        MySubMod.LOGGER.info("Generated spawn points for all islands");
    }

    private List<BlockPos> generateIslandSpawnPoints(BlockPos islandCenter, IslandType type, Random random) {
        List<BlockPos> points = new ArrayList<>();
        int spawnCount = type.getSpawnPointCount();
        int radius = type.getRadius();
        int maxAttempts = 1000;

        MySubMod.LOGGER.info("Generating {} spawn points for {} island at {}", spawnCount, type.name(), islandCenter);

        for (int i = 0; i < spawnCount; i++) {
            BlockPos spawnPoint = null;
            int attempts = 0;

            while (spawnPoint == null && attempts < maxAttempts) {
                attempts++;

                // Generate random position within island bounds (square island)
                int x = islandCenter.getX() + random.nextInt(radius * 2) - radius;
                int z = islandCenter.getZ() + random.nextInt(radius * 2) - radius;
                int y = islandCenter.getY() + 1; // Spawn on island surface

                BlockPos candidate = new BlockPos(x, y, z);

                // Check distance from all existing spawn points
                boolean validDistance = true;
                for (BlockPos existing : points) {
                    double distance = Math.sqrt(Math.pow(candidate.getX() - existing.getX(), 2) +
                                               Math.pow(candidate.getZ() - existing.getZ(), 2));
                    if (distance < MIN_DISTANCE) {
                        validDistance = false;
                        break;
                    }
                }

                if (validDistance) {
                    spawnPoint = candidate;
                    points.add(spawnPoint);
                    MySubMod.LOGGER.debug("Generated spawn point {} for {}: {}", i + 1, type.name(), spawnPoint);
                }
            }

            if (spawnPoint == null) {
                MySubMod.LOGGER.warn("Could not generate spawn point {} for {} after {} attempts", i + 1, type.name(), maxAttempts);
            }
        }

        return points;
    }

    /**
     * Get spawn point for a specific island and spawn point number
     */
    public BlockPos getSpawnPoint(IslandType island, int spawnPointNumber) {
        List<BlockPos> points = spawnPoints.get(island);
        if (points == null || points.isEmpty()) {
            MySubMod.LOGGER.error("No spawn points generated for island: {}", island.name());
            return null;
        }

        // Validate spawn point number (1-based index)
        if (spawnPointNumber < 1 || spawnPointNumber > points.size()) {
            MySubMod.LOGGER.error("Invalid spawn point number {} for island {} (max: {})",
                spawnPointNumber, island.name(), points.size());
            return points.get(0); // Fallback to first spawn point
        }

        return points.get(spawnPointNumber - 1); // Convert to 0-based index
    }

    /**
     * Clear all spawn points
     */
    public void clear() {
        spawnPoints.clear();
    }

    /**
     * Get all spawn points for an island (for debugging/logging)
     */
    public List<BlockPos> getIslandSpawnPoints(IslandType island) {
        return new ArrayList<>(spawnPoints.getOrDefault(island, new ArrayList<>()));
    }
}

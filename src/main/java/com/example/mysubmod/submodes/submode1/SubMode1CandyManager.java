package com.example.mysubmod.submodes.submode1;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.items.ModItems;
import com.example.mysubmod.submodes.submode1.data.CandySpawnEntry;
import com.example.mysubmod.submodes.submodeParent.SubModeParentManager;
import com.example.mysubmod.submodes.submodeParent.islands.IslandType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SubMode1CandyManager {
    private static SubMode1CandyManager instance;
    private Timer candySpawnTimer;
    private final Map<ItemEntity, Long> candySpawnTimes = new ConcurrentHashMap<>();
    private final Set<net.minecraft.world.level.ChunkPos> forceLoadedChunks = new HashSet<>();
    private MinecraftServer gameServer; // Keep reference to server

    private SubMode1CandyManager() {}

    public static SubMode1CandyManager getInstance() {
        if (instance == null) {
            instance = new SubMode1CandyManager();
        }
        return instance;
    }

    public void startCandySpawning(MinecraftServer server) {
        MySubMod.LOGGER.info("Starting candy spawning for SubMode1");

        // Clean up any previously loaded chunks before starting
        unforceLoadChunks();

        // Store server reference
        this.gameServer = server;

        // Force load chunks for all islands to keep entities loaded
        forceLoadIslandChunks(server);

        // Get spawn configuration from SubMode1Manager
        List<CandySpawnEntry> spawnConfig = SubMode1Manager.getInstance().getCandySpawnConfig();

        if (spawnConfig == null || spawnConfig.isEmpty()) {
            MySubMod.LOGGER.error("No candy spawn configuration loaded!");
            return;
        }

        MySubMod.LOGGER.info("Loaded {} candy spawn entries from configuration", spawnConfig.size());

        // Schedule candy spawns based on configuration file
        scheduleCandySpawnsFromConfig(server, spawnConfig);
    }

    private void scheduleCandySpawnsFromConfig(MinecraftServer server, List<CandySpawnEntry> spawnConfig) {
        candySpawnTimer = new Timer();

        for (CandySpawnEntry entry : spawnConfig) {
            candySpawnTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    server.execute(() -> spawnCandiesFromEntry(server, entry));
                }
            }, entry.getTimeMs());
        }
    }

    public void stopCandySpawning() {
        if (candySpawnTimer != null) {
            candySpawnTimer.cancel();
            candySpawnTimer = null;
        }

        // Remove all existing candies
        removeAllCandies();

        // Unforce load chunks
        unforceLoadChunks();

        MySubMod.LOGGER.info("Stopped candy spawning for SubMode1");
    }

    private void spawnCandiesFromEntry(MinecraftServer server, CandySpawnEntry entry) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        // Check if we still have alive players
        Set<UUID> alivePlayers = SubMode1Manager.getInstance().getAlivePlayers();
        if (alivePlayers.isEmpty()) return;

        BlockPos spawnCenter = entry.getPosition();
        int candyCount = entry.getCandyCount();

        // Track used positions to avoid duplicate coordinates
        Set<BlockPos> usedPositions = new HashSet<>();
        Random random = new Random();

        // Spawn multiple candies around the specified position
        for (int i = 0; i < candyCount; i++) {
            BlockPos spawnPos = findValidSpawnPositionNearPoint(overworld, spawnCenter, 3, usedPositions); // 3 block radius around spawn point
            if (spawnPos != null) {
                usedPositions.add(spawnPos); // Mark this position as used

                // Add small random offset to prevent exact stacking
                double xOffset = 0.3 + random.nextDouble() * 0.4; // Random between 0.3 and 0.7
                double zOffset = 0.3 + random.nextDouble() * 0.4; // Random between 0.3 and 0.7

                ItemStack candyStack = new ItemStack(ModItems.CANDY.get());
                ItemEntity candyEntity = new ItemEntity(overworld,
                    spawnPos.getX() + xOffset,
                    spawnPos.getY() - 0.9,
                    spawnPos.getZ() + zOffset,
                    candyStack);

                // Prevent pickup for 3 seconds
                candyEntity.setPickUpDelay(60); // 3 seconds (60 ticks)

                // Prevent candy from despawning
                candyEntity.setUnlimitedLifetime();

                overworld.addFreshEntity(candyEntity);
                candySpawnTimes.put(candyEntity, System.currentTimeMillis());

                // Log candy spawn
                if (SubMode1Manager.getInstance().getDataLogger() != null) {
                    SubMode1Manager.getInstance().getDataLogger().logCandySpawn(spawnPos);
                }
            }
        }

        MySubMod.LOGGER.info("Spawned {} candies at time {}s", candyCount, entry.getTimeSeconds());
    }


    private BlockPos findValidSpawnPositionNearPoint(ServerLevel level, BlockPos spawnPoint, int radius, Set<BlockPos> usedPositions) {
        Random random = new Random();

        for (int attempts = 0; attempts < 100; attempts++) {
            int x = spawnPoint.getX() + random.nextInt(radius * 2 + 1) - radius;
            int z = spawnPoint.getZ() + random.nextInt(radius * 2 + 1) - radius;

            // Find ground level
            for (int y = spawnPoint.getY() + 5; y >= spawnPoint.getY() - 5; y--) {
                BlockPos checkPos = new BlockPos(x, y, z);
                BlockPos abovePos = checkPos.above();

                // Check if position is already used
                if (usedPositions.contains(abovePos)) {
                    continue;
                }

                if (!level.getBlockState(checkPos).isAir() &&
                    level.getBlockState(abovePos).isAir() &&
                    level.getBlockState(abovePos.above()).isAir() &&
                    isOnIslandSurface(level, checkPos)) {
                    return abovePos;
                }
            }
        }

        // If no valid position found nearby, return spawn point itself (with small offset to avoid exact overlap)
        return spawnPoint;
    }

    private boolean isOnIslandSurface(ServerLevel level, BlockPos pos) {
        // Check if the block is grass (island surface) and not stone bricks (path)
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
    }


    private void removeAllCandies() {
        for (ItemEntity candy : candySpawnTimes.keySet()) {
            if (candy.isAlive()) {
                candy.discard();
            }
        }
        candySpawnTimes.clear();
    }

    public void removeAllCandiesFromWorld(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        int removedCount = 0;

        // Force load island chunks to ensure we catch all candies
        SubModeParentManager manager = SubMode1Manager.getInstance();
        if (manager.getSmallIslandCenter() != null) {
            forceLoadChunk(overworld, manager.getSmallIslandCenter());
            forceLoadChunk(overworld, manager.getMediumIslandCenter());
            forceLoadChunk(overworld, manager.getLargeIslandCenter());
            forceLoadChunk(overworld, manager.getExtraLargeIslandCenter());
        }

        // Remove ALL candy items from the world
        for (net.minecraft.world.entity.Entity entity : overworld.getAllEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                if (itemEntity.getItem().getItem() == ModItems.CANDY.get()) {
                    itemEntity.discard();
                    removedCount++;
                }
            }
        }

        candySpawnTimes.clear();
        MySubMod.LOGGER.info("Removed {} candy items from the world", removedCount);
    }

    private void forceLoadChunk(ServerLevel level, BlockPos pos) {
        if (pos != null) {
            level.getChunkAt(pos);
        }
    }

    /**
     * Get count of available candies per island by scanning all candy items in the world
     * Counts EVERY candy ItemEntity individually, even if multiple candies share the same position
     */
    public Map<IslandType, Integer> getAvailableCandiesPerIsland(MinecraftServer server) {
        Map<IslandType, Integer> counts = new HashMap<>();
        counts.put(IslandType.SMALL, 0);
        counts.put(IslandType.MEDIUM, 0);
        counts.put(IslandType.LARGE, 0);
        counts.put(IslandType.EXTRA_LARGE, 0);

        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return counts;

        SubModeParentManager manager = SubMode1Manager.getInstance();
        int totalCounted = 0;
        int notOnIsland = 0;

        // Scan ALL candy ItemEntity objects in the world - count EACH entity separately
        for (net.minecraft.world.entity.Entity entity : overworld.getAllEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                if (itemEntity.getItem().getItem() == ModItems.CANDY.get()) {
                    if (itemEntity.isAlive() && !itemEntity.isRemoved()) {
                        // Count the candy based on stack size (in case multiple candies are in one ItemEntity)
                        int candyAmount = itemEntity.getItem().getCount();

                        // Determine island by current position
                        IslandType island = determineIslandFromPosition(itemEntity.position(), manager);
                        if (island != null) {
                            totalCounted += candyAmount;
                            counts.put(island, counts.get(island) + candyAmount);
                        } else {
                            // Candy not on any island
                            notOnIsland += candyAmount;
                            MySubMod.LOGGER.warn("Candy not counted - not on any island at {} (entity ID: {})",
                                itemEntity.position(), entity.getId());
                        }
                    }
                }
            }
        }

        MySubMod.LOGGER.info("Candy count: {} total found ({} not on islands) - Small:{} Medium:{} Large:{} ExtraLarge:{}",
            totalCounted, notOnIsland, counts.get(IslandType.SMALL), counts.get(IslandType.MEDIUM),
            counts.get(IslandType.LARGE), counts.get(IslandType.EXTRA_LARGE));

        return counts;
    }

    private void forceLoadIslandChunks(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        SubModeParentManager manager = SubMode1Manager.getInstance();

        // Force load chunks for each island (load 3x3 chunks around center to cover entire island)
        forceLoadIslandArea(overworld, manager.getSmallIslandCenter(), 2); // Small: 60x60 = ~4 chunks
        forceLoadIslandArea(overworld, manager.getMediumIslandCenter(), 3); // Medium: 90x90 = ~6 chunks
        forceLoadIslandArea(overworld, manager.getLargeIslandCenter(), 4); // Large: 120x120 = ~8 chunks
        forceLoadIslandArea(overworld, manager.getExtraLargeIslandCenter(), 5); // Extra Large: 150x150 = ~10 chunks

        MySubMod.LOGGER.info("Force loaded {} chunks for all islands", forceLoadedChunks.size());
    }

    private void forceLoadIslandArea(ServerLevel level, BlockPos center, int chunkRadius) {
        if (center == null) return;

        net.minecraft.world.level.ChunkPos centerChunk = new net.minecraft.world.level.ChunkPos(center);

        // Load chunkRadius x chunkRadius chunks around the island center
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(
                    centerChunk.x + x,
                    centerChunk.z + z
                );

                // Only force load if not already tracked
                if (!forceLoadedChunks.contains(chunkPos)) {
                    try {
                        level.setChunkForced(chunkPos.x, chunkPos.z, true);
                        forceLoadedChunks.add(chunkPos);
                    } catch (Exception e) {
                        MySubMod.LOGGER.warn("Failed to force load chunk at {}, {}: {}", chunkPos.x, chunkPos.z, e.getMessage());
                    }
                }
            }
        }
    }

    private void unforceLoadChunks() {
        if (gameServer == null || forceLoadedChunks.isEmpty()) return;

        ServerLevel overworld = gameServer.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            forceLoadedChunks.clear();
            gameServer = null;
            return;
        }

        try {
            for (net.minecraft.world.level.ChunkPos chunkPos : forceLoadedChunks) {
                try {
                    overworld.setChunkForced(chunkPos.x, chunkPos.z, false);
                } catch (Exception e) {
                    MySubMod.LOGGER.warn("Failed to unforce chunk at {}, {}: {}", chunkPos.x, chunkPos.z, e.getMessage());
                }
            }
        } finally {
            forceLoadedChunks.clear();
            gameServer = null;
        }
    }

    /**
     * Determine which island a position belongs to by checking if it's within island bounds
     */
    private IslandType determineIslandFromPosition(Vec3 pos, SubModeParentManager manager) {
        BlockPos smallCenter = manager.getSmallIslandCenter();
        BlockPos mediumCenter = manager.getMediumIslandCenter();
        BlockPos largeCenter = manager.getLargeIslandCenter();
        BlockPos extraLargeCenter = manager.getExtraLargeIslandCenter();

        if (smallCenter == null || mediumCenter == null || largeCenter == null || extraLargeCenter == null) {
            return null;
        }

        // Check each island's bounds (square area defined by radius)
        // Small island: 60x60 (radius 30)
        if (isWithinIslandBounds(pos, smallCenter, IslandType.SMALL.getRadius())) {
            return IslandType.SMALL;
        }

        // Medium island: 90x90 (radius 45)
        if (isWithinIslandBounds(pos, mediumCenter, IslandType.MEDIUM.getRadius())) {
            return IslandType.MEDIUM;
        }

        // Large island: 120x120 (radius 60)
        if (isWithinIslandBounds(pos, largeCenter, IslandType.LARGE.getRadius())) {
            return IslandType.LARGE;
        }

        // Extra Large island: 150x150 (radius 75)
        if (isWithinIslandBounds(pos, extraLargeCenter, IslandType.EXTRA_LARGE.getRadius())) {
            return IslandType.EXTRA_LARGE;
        }

        return null; // Not on any island
    }

    /**
     * Check if a position is within the square bounds of an island
     */
    private boolean isWithinIslandBounds(net.minecraft.world.phys.Vec3 pos, BlockPos center, int radius) {
        // Check if position is within the square area (not circle)
        // Islands are square with side length = 2 * radius
        double distX = Math.abs(pos.x - center.getX());
        double distZ = Math.abs(pos.z - center.getZ());

        return distX <= radius && distZ <= radius;
    }

    public void onCandyPickup(ItemEntity candy) {
        candySpawnTimes.remove(candy);
    }
}
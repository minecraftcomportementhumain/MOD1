package com.example.mysubmod.submodes.submode2;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.items.ModItems;
import com.example.mysubmod.submodes.submode2.data.CandySpawnEntry;
import com.example.mysubmod.submodes.submode2.islands.IslandType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SubMode2 candy manager with support for dual resource types (A and B)
 * Uses NBT tags to differentiate candy types
 */
public class SubMode2CandyManager {
    private static SubMode2CandyManager instance;
    private Timer candySpawnTimer;
    private final Map<ItemEntity, Long> candySpawnTimes = new ConcurrentHashMap<>();
    private final Set<net.minecraft.world.level.ChunkPos> forceLoadedChunks = new HashSet<>();
    private MinecraftServer gameServer;

    // NBT tag key for resource type
    private static final String NBT_RESOURCE_TYPE = "SubMode2ResourceType";

    private SubMode2CandyManager() {}

    public static SubMode2CandyManager getInstance() {
        if (instance == null) {
            instance = new SubMode2CandyManager();
        }
        return instance;
    }

    public void startCandySpawning(MinecraftServer server) {
        MySubMod.LOGGER.info("Starting candy spawning for SubMode2");

        // Clean up any previously loaded chunks before starting
        unforceLoadChunks();

        this.gameServer = server;
        forceLoadIslandChunks(server);

        List<CandySpawnEntry> spawnConfig = SubMode2Manager.getInstance().getCandySpawnConfig();

        if (spawnConfig == null || spawnConfig.isEmpty()) {
            MySubMod.LOGGER.error("No candy spawn configuration loaded for SubMode2!");
            return;
        }

        MySubMod.LOGGER.info("Loaded {} candy spawn entries from SubMode2 configuration", spawnConfig.size());
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

        removeAllCandies();
        unforceLoadChunks();

        MySubMod.LOGGER.info("Stopped candy spawning for SubMode2");
    }

    private void spawnCandiesFromEntry(MinecraftServer server, CandySpawnEntry entry) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        Set<UUID> alivePlayers = SubMode2Manager.getInstance().getAlivePlayers();
        if (alivePlayers.isEmpty()) return;

        BlockPos spawnCenter = entry.getPosition();
        int candyCount = entry.getCandyCount();
        ResourceType resourceType = entry.getType();

        Set<BlockPos> usedPositions = new HashSet<>();
        Random random = new Random();

        for (int i = 0; i < candyCount; i++) {
            BlockPos spawnPos = findValidSpawnPositionNearPoint(overworld, spawnCenter, 3, usedPositions);
            if (spawnPos != null) {
                usedPositions.add(spawnPos);

                double xOffset = 0.3 + random.nextDouble() * 0.4;
                double zOffset = 0.3 + random.nextDouble() * 0.4;

                // Use different items based on resource type
                ItemStack candyStack;
                if (resourceType == ResourceType.TYPE_A) {
                    candyStack = new ItemStack(ModItems.CANDY_BLUE.get());
                } else {
                    candyStack = new ItemStack(ModItems.CANDY_RED.get());
                }

                ItemEntity candyEntity = new ItemEntity(overworld,
                    spawnPos.getX() + xOffset,
                    spawnPos.getY() - 0.9,
                    spawnPos.getZ() + zOffset,
                    candyStack);

                candyEntity.setPickUpDelay(60); // 3 seconds
                candyEntity.setUnlimitedLifetime();

                overworld.addFreshEntity(candyEntity);
                candySpawnTimes.put(candyEntity, System.currentTimeMillis());

                // Log candy spawn with type
                if (SubMode2Manager.getInstance().getDataLogger() != null) {
                    SubMode2Manager.getInstance().getDataLogger().logCandySpawn(spawnPos, resourceType);
                }
            }
        }

        MySubMod.LOGGER.info("Spawned {} candies (Type {}) at time {}s", candyCount, resourceType.name(), entry.getTimeSeconds());
    }

    private BlockPos findValidSpawnPositionNearPoint(ServerLevel level, BlockPos spawnPoint, int radius, Set<BlockPos> usedPositions) {
        Random random = new Random();

        for (int attempts = 0; attempts < 100; attempts++) {
            int x = spawnPoint.getX() + random.nextInt(radius * 2 + 1) - radius;
            int z = spawnPoint.getZ() + random.nextInt(radius * 2 + 1) - radius;

            for (int y = spawnPoint.getY() + 5; y >= spawnPoint.getY() - 5; y--) {
                BlockPos checkPos = new BlockPos(x, y, z);
                BlockPos abovePos = checkPos.above();

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

        return spawnPoint;
    }

    private boolean isOnIslandSurface(ServerLevel level, BlockPos pos) {
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

        SubMode2Manager manager = SubMode2Manager.getInstance();
        if (manager.getSmallIslandCenter() != null) {
            forceLoadChunk(overworld, manager.getSmallIslandCenter());
            forceLoadChunk(overworld, manager.getMediumIslandCenter());
            forceLoadChunk(overworld, manager.getLargeIslandCenter());
            forceLoadChunk(overworld, manager.getExtraLargeIslandCenter());
        }

        for (net.minecraft.world.entity.Entity entity : overworld.getAllEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                // Remove SubMode2 candies (blue and red)
                if (itemEntity.getItem().getItem() == ModItems.CANDY_BLUE.get() ||
                    itemEntity.getItem().getItem() == ModItems.CANDY_RED.get()) {
                    itemEntity.discard();
                    removedCount++;
                }
            }
        }

        candySpawnTimes.clear();
        MySubMod.LOGGER.info("Removed {} SubMode2 candy items from the world", removedCount);
    }

    private void forceLoadChunk(ServerLevel level, BlockPos pos) {
        if (pos != null) {
            level.getChunkAt(pos);
        }
    }

    /**
     * Get count of available candies per island AND per resource type
     * Returns nested map: IslandType -> ResourceType -> Count
     */
    public Map<IslandType, Map<ResourceType, Integer>> getAvailableCandiesPerIsland(MinecraftServer server) {
        Map<IslandType, Map<ResourceType, Integer>> counts = new HashMap<>();

        // Initialize structure
        for (IslandType island : IslandType.values()) {
            Map<ResourceType, Integer> typeCounts = new HashMap<>();
            typeCounts.put(ResourceType.TYPE_A, 0);
            typeCounts.put(ResourceType.TYPE_B, 0);
            counts.put(island, typeCounts);
        }

        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return counts;

        SubMode2Manager manager = SubMode2Manager.getInstance();
        int totalCounted = 0;
        int notOnIsland = 0;

        for (net.minecraft.world.entity.Entity entity : overworld.getAllEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                ResourceType resourceType = getResourceTypeFromCandy(itemEntity.getItem());

                // Only count SubMode2 candies (blue and red)
                if (resourceType != null && itemEntity.isAlive() && !itemEntity.isRemoved()) {
                    int candyAmount = itemEntity.getItem().getCount();

                    IslandType island = determineIslandFromPosition(itemEntity.position(), manager);
                    if (island != null) {
                        totalCounted += candyAmount;
                        Map<ResourceType, Integer> typeCounts = counts.get(island);
                        typeCounts.put(resourceType, typeCounts.get(resourceType) + candyAmount);
                    } else {
                        notOnIsland += candyAmount;
                        MySubMod.LOGGER.warn("SubMode2 candy not counted - not on any island at {} (entity ID: {})",
                            itemEntity.position(), entity.getId());
                    }
                }
            }
        }

        MySubMod.LOGGER.info("SubMode2 candy count: {} total found ({} not on islands)", totalCounted, notOnIsland);

        return counts;
    }

    /**
     * Extract ResourceType from candy ItemStack based on item type
     */
    public static ResourceType getResourceTypeFromCandy(ItemStack candyStack) {
        if (candyStack.getItem() == ModItems.CANDY_BLUE.get()) {
            return ResourceType.TYPE_A;
        } else if (candyStack.getItem() == ModItems.CANDY_RED.get()) {
            return ResourceType.TYPE_B;
        }
        return null;
    }

    private void forceLoadIslandChunks(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        SubMode2Manager manager = SubMode2Manager.getInstance();

        forceLoadIslandArea(overworld, manager.getSmallIslandCenter(), 2);
        forceLoadIslandArea(overworld, manager.getMediumIslandCenter(), 3);
        forceLoadIslandArea(overworld, manager.getLargeIslandCenter(), 4);
        forceLoadIslandArea(overworld, manager.getExtraLargeIslandCenter(), 5);

        MySubMod.LOGGER.info("Force loaded {} chunks for all SubMode2 islands", forceLoadedChunks.size());
    }

    private void forceLoadIslandArea(ServerLevel level, BlockPos center, int chunkRadius) {
        if (center == null) return;

        net.minecraft.world.level.ChunkPos centerChunk = new net.minecraft.world.level.ChunkPos(center);

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

    private IslandType determineIslandFromPosition(net.minecraft.world.phys.Vec3 pos, SubMode2Manager manager) {
        BlockPos smallCenter = manager.getSmallIslandCenter();
        BlockPos mediumCenter = manager.getMediumIslandCenter();
        BlockPos largeCenter = manager.getLargeIslandCenter();
        BlockPos extraLargeCenter = manager.getExtraLargeIslandCenter();

        if (smallCenter == null || mediumCenter == null || largeCenter == null || extraLargeCenter == null) {
            return null;
        }

        if (isWithinIslandBounds(pos, smallCenter, IslandType.SMALL.getRadius())) {
            return IslandType.SMALL;
        }

        if (isWithinIslandBounds(pos, mediumCenter, IslandType.MEDIUM.getRadius())) {
            return IslandType.MEDIUM;
        }

        if (isWithinIslandBounds(pos, largeCenter, IslandType.LARGE.getRadius())) {
            return IslandType.LARGE;
        }

        if (isWithinIslandBounds(pos, extraLargeCenter, IslandType.EXTRA_LARGE.getRadius())) {
            return IslandType.EXTRA_LARGE;
        }

        return null;
    }

    private boolean isWithinIslandBounds(net.minecraft.world.phys.Vec3 pos, BlockPos center, int radius) {
        double distX = Math.abs(pos.x - center.getX());
        double distZ = Math.abs(pos.z - center.getZ());

        return distX <= radius && distZ <= radius;
    }

    public void onCandyPickup(ItemEntity candy) {
        candySpawnTimes.remove(candy);
    }
}

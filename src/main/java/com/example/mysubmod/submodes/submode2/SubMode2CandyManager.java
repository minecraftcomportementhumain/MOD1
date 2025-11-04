package com.example.mysubmod.submodes.submode2;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.items.ModItems;
import com.example.mysubmod.submodes.submode2.data.SubMode2SpawnEntry;
import com.example.mysubmod.submodes.submodeParent.CandyManager;
import com.example.mysubmod.submodes.submodeParent.SubModeParentManager;
import com.example.mysubmod.submodes.submodeParent.islands.IslandType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * SubMode2 candy manager with support for dual resource types (A and B)
 * Uses NBT tags to differentiate candy types
 */
public class SubMode2CandyManager extends CandyManager {

    @Override
    public void startCandySpawning(MinecraftServer server) {
        MySubMod.LOGGER.info("Starting candy spawning for SubMode2");

        // Clean up any previously loaded chunks before starting
        unforceLoadChunks();

        this.gameServer = server;
        forceLoadIslandChunks(server);

        List<?> spawnConfig = SubMode2Manager.getInstance().getCandySpawnConfig();

        if (spawnConfig == null || spawnConfig.isEmpty()) {
            MySubMod.LOGGER.error("No candy spawn configuration loaded for SubMode2!");
            return;
        }

        MySubMod.LOGGER.info("Loaded {} candy spawn entries from SubMode2 configuration", spawnConfig.size());
        scheduleResourceSpawnsFromConfig(server, (List<SubMode2SpawnEntry>) spawnConfig);
    }

    protected void scheduleResourceSpawnsFromConfig(MinecraftServer server, List<SubMode2SpawnEntry> spawnConfig) {
        candySpawnTimer = new Timer();

        for (SubMode2SpawnEntry entry : spawnConfig) {
            candySpawnTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    server.execute(() -> spawnCandiesFromEntry(server, entry));
                }
            }, entry.getTimeMs());
        }
    }

    protected void spawnCandiesFromEntry(MinecraftServer server, SubMode2SpawnEntry entry) {
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

    @Override
    public void removeAllCandiesFromWorld(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        int removedCount = 0;

        SubModeParentManager manager = SubMode2Manager.getInstance();
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


    /**
     * Get count of available candies per island AND per resource type
     * Returns nested map: IslandType -> ResourceType -> Count
     */
    public static Map<IslandType, Map<ResourceType, Integer>> getResourcesPerIsland(MinecraftServer server) {
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

        SubModeParentManager manager = SubMode2Manager.getInstance();
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

}

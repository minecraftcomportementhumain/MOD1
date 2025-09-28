package com.example.mysubmod.submodes.submode1;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.items.ModItems;
import com.example.mysubmod.submodes.submode1.islands.IslandType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SubMode1CandyManager {
    private static SubMode1CandyManager instance;
    private Timer candySpawnTimer;
    private Timer candyExpirationTimer;
    private final Map<ItemEntity, Long> candySpawnTimes = new ConcurrentHashMap<>();

    private static final int EXPIRATION_TIME_MS = 120000; // 2 minutes
    private static final int CANDIES_PER_PLAYER = 35;
    private static final int GAME_DURATION_MS = 15 * 60 * 1000; // 15 minutes

    // Distribution proportions
    private static final double LARGE_ISLAND_RATIO = 0.5;   // 1/2
    private static final double MEDIUM_ISLAND_RATIO = 0.3;  // 3/10
    private static final double SMALL_ISLAND_RATIO = 0.2;   // 1/5

    // Spawn tracking
    private int totalCandiesTarget;
    private int largeCandiesTarget;
    private int mediumCandiesTarget;
    private int smallCandiesTarget;
    private int largeCandiesSpawned = 0;
    private int mediumCandiesSpawned = 0;
    private int smallCandiesSpawned = 0;

    private SubMode1CandyManager() {}

    public static SubMode1CandyManager getInstance() {
        if (instance == null) {
            instance = new SubMode1CandyManager();
        }
        return instance;
    }

    public void startCandySpawning(MinecraftServer server) {
        MySubMod.LOGGER.info("Starting candy spawning for SubMode1");

        // Calculate targets based on alive players at start
        Set<UUID> alivePlayers = SubMode1Manager.getInstance().getAlivePlayers();
        totalCandiesTarget = CANDIES_PER_PLAYER * alivePlayers.size();
        largeCandiesTarget = (int) (totalCandiesTarget * LARGE_ISLAND_RATIO);
        mediumCandiesTarget = (int) (totalCandiesTarget * MEDIUM_ISLAND_RATIO);
        smallCandiesTarget = (int) (totalCandiesTarget * SMALL_ISLAND_RATIO);

        MySubMod.LOGGER.info("Candy targets: Total={}, Large={}, Medium={}, Small={}",
            totalCandiesTarget, largeCandiesTarget, mediumCandiesTarget, smallCandiesTarget);

        // Reset spawn counters
        largeCandiesSpawned = 0;
        mediumCandiesSpawned = 0;
        smallCandiesSpawned = 0;

        // Schedule candy spawns distributed over the 15-minute game
        scheduleCandySpawns(server);

        // Start candy expiration timer
        candyExpirationTimer = new Timer();
        candyExpirationTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                server.execute(() -> checkCandyExpiration());
            }
        }, 10000, 10000); // Check every 10 seconds
    }

    private void scheduleCandySpawns(MinecraftServer server) {
        candySpawnTimer = new Timer();
        Random random = new Random();

        // Create a list of all spawn times for each island
        List<Long> largeSpawnTimes = generateSpawnTimes(largeCandiesTarget, random);
        List<Long> mediumSpawnTimes = generateSpawnTimes(mediumCandiesTarget, random);
        List<Long> smallSpawnTimes = generateSpawnTimes(smallCandiesTarget, random);

        // Schedule large island candies
        for (Long spawnTime : largeSpawnTimes) {
            candySpawnTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    server.execute(() -> spawnSingleCandy(server, IslandType.LARGE));
                }
            }, spawnTime);
        }

        // Schedule medium island candies
        for (Long spawnTime : mediumSpawnTimes) {
            candySpawnTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    server.execute(() -> spawnSingleCandy(server, IslandType.MEDIUM));
                }
            }, spawnTime);
        }

        // Schedule small island candies
        for (Long spawnTime : smallSpawnTimes) {
            candySpawnTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    server.execute(() -> spawnSingleCandy(server, IslandType.SMALL));
                }
            }, spawnTime);
        }
    }

    private List<Long> generateSpawnTimes(int candyCount, Random random) {
        List<Long> spawnTimes = new ArrayList<>();

        for (int i = 0; i < candyCount; i++) {
            // Distribute spawns evenly over the 15-minute game duration
            // Add some randomness (±30 seconds) for more natural distribution
            long baseTime = (long) ((double) i / candyCount * GAME_DURATION_MS);
            long randomOffset = random.nextInt(60000) - 30000; // ±30 seconds
            long spawnTime = Math.max(0, Math.min(GAME_DURATION_MS - 1000, baseTime + randomOffset));
            spawnTimes.add(spawnTime);
        }

        return spawnTimes;
    }

    public void stopCandySpawning() {
        if (candySpawnTimer != null) {
            candySpawnTimer.cancel();
            candySpawnTimer = null;
        }

        if (candyExpirationTimer != null) {
            candyExpirationTimer.cancel();
            candyExpirationTimer = null;
        }

        // Remove all existing candies
        removeAllCandies();

        MySubMod.LOGGER.info("Stopped candy spawning for SubMode1");
    }

    private void spawnSingleCandy(MinecraftServer server, IslandType island) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        // Check if we still have alive players
        Set<UUID> alivePlayers = SubMode1Manager.getInstance().getAlivePlayers();
        if (alivePlayers.isEmpty()) return;

        // Check if we've reached our target for this island
        if ((island == IslandType.LARGE && largeCandiesSpawned >= largeCandiesTarget) ||
            (island == IslandType.MEDIUM && mediumCandiesSpawned >= mediumCandiesTarget) ||
            (island == IslandType.SMALL && smallCandiesSpawned >= smallCandiesTarget)) {
            return;
        }

        // Spawn one candy on the specified island
        BlockPos spawnPos = findValidSpawnPosition(overworld, getIslandCenter(island), island.getRadius());
        if (spawnPos != null) {
            ItemStack candyStack = new ItemStack(ModItems.CANDY.get());
            ItemEntity candyEntity = new ItemEntity(overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5, candyStack);

            // Prevent immediate pickup
            candyEntity.setPickUpDelay(20); // 1 second delay

            // Make candy more visible from far distance
            candyEntity.setGlowingTag(true); // Add glowing effect
            candyEntity.setNoGravity(true); // Float in place for better visibility

            overworld.addFreshEntity(candyEntity);
            candySpawnTimes.put(candyEntity, System.currentTimeMillis());

            // Update spawn counters
            switch (island) {
                case LARGE -> largeCandiesSpawned++;
                case MEDIUM -> mediumCandiesSpawned++;
                case SMALL -> smallCandiesSpawned++;
            }

            MySubMod.LOGGER.debug("Spawned candy #{} on {} ({}/{} total)",
                getSpawnedCount(island), island.getDisplayName(),
                largeCandiesSpawned + mediumCandiesSpawned + smallCandiesSpawned, totalCandiesTarget);

            // Log candy spawn
            if (SubMode1Manager.getInstance().getDataLogger() != null) {
                SubMode1Manager.getInstance().getDataLogger().logCandySpawn(spawnPos, island);
            }
        }
    }

    private int getSpawnedCount(IslandType island) {
        return switch (island) {
            case LARGE -> largeCandiesSpawned;
            case MEDIUM -> mediumCandiesSpawned;
            case SMALL -> smallCandiesSpawned;
        };
    }


    private BlockPos getIslandCenter(IslandType island) {
        SubMode1Manager manager = SubMode1Manager.getInstance();
        switch (island) {
            case SMALL: return manager.getSmallIslandCenter();
            case MEDIUM: return manager.getMediumIslandCenter();
            case LARGE: return manager.getLargeIslandCenter();
            default: return null;
        }
    }

    private BlockPos findValidSpawnPosition(ServerLevel level, BlockPos center, int size) {
        Random random = new Random();

        // Use slightly smaller size to avoid spawning too close to barriers (size - 1 margin for safety)
        int accessibleSize = size - 1; // Stay away from barrier edges

        for (int attempts = 0; attempts < 50; attempts++) {
            int x = center.getX() + random.nextInt(accessibleSize * 2) - accessibleSize;
            int z = center.getZ() + random.nextInt(accessibleSize * 2) - accessibleSize;

            // Check if position is within accessible square island bounds (well inside barriers)
            int distanceFromCenterX = Math.abs(x - center.getX());
            int distanceFromCenterZ = Math.abs(z - center.getZ());
            if (distanceFromCenterX > accessibleSize || distanceFromCenterZ > accessibleSize) continue;

            // Find ground level
            for (int y = center.getY() + 5; y >= center.getY() - 2; y--) {
                BlockPos checkPos = new BlockPos(x, y, z);
                BlockPos abovePos = checkPos.above();

                if (!level.getBlockState(checkPos).isAir() &&
                    level.getBlockState(abovePos).isAir() &&
                    level.getBlockState(abovePos.above()).isAir() &&
                    isOnIslandSurface(level, checkPos) &&
                    isAccessiblePosition(center, x, z, size)) {
                    return abovePos;
                }
            }
        }

        return null; // No valid position found
    }

    private boolean isAccessiblePosition(BlockPos center, int x, int z, int originalSize) {
        // Ensure position is well inside the square barrier zone
        int barrierSize = originalSize; // Barriers are at exact size
        int distanceFromCenterX = Math.abs(x - center.getX());
        int distanceFromCenterZ = Math.abs(z - center.getZ());

        // Position must be at least 1 block inside the barrier perimeter
        return (distanceFromCenterX < (barrierSize - 1)) && (distanceFromCenterZ < (barrierSize - 1));
    }

    private boolean isOnIslandSurface(ServerLevel level, BlockPos pos) {
        // Check if the block is grass (island surface) and not stone bricks (path)
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
    }

    private void checkCandyExpiration() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<ItemEntity, Long>> iterator = candySpawnTimes.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<ItemEntity, Long> entry = iterator.next();
            ItemEntity candy = entry.getKey();
            long spawnTime = entry.getValue();

            if (currentTime - spawnTime > EXPIRATION_TIME_MS) {
                // Remove expired candy
                if (candy.isAlive()) {
                    candy.discard();
                    MySubMod.LOGGER.debug("Removed expired candy at {}", candy.position());
                }
                iterator.remove();
            }
        }
    }

    private void removeAllCandies() {
        for (ItemEntity candy : candySpawnTimes.keySet()) {
            if (candy.isAlive()) {
                candy.discard();
            }
        }
        candySpawnTimes.clear();
    }

    public void onCandyPickup(ItemEntity candy) {
        candySpawnTimes.remove(candy);
    }

    public int getActiveCandyCount() {
        return candySpawnTimes.size();
    }
}
package com.example.mysubmod.submodes.submode1;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode1.data.SubMode1DataLogger;
import com.example.mysubmod.submodes.submode1.islands.IslandGenerator;
import com.example.mysubmod.submodes.submode1.islands.IslandType;
import com.example.mysubmod.submodes.submode1.network.IslandSelectionPacket;
import com.example.mysubmod.submodes.submode1.timer.GameTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SubMode1Manager {
    private static SubMode1Manager instance;
    private final Map<UUID, List<ItemStack>> storedInventories = new ConcurrentHashMap<>();
    private final Map<UUID, IslandType> playerIslandSelections = new ConcurrentHashMap<>();
    private final Set<UUID> alivePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spectatorPlayers = ConcurrentHashMap.newKeySet();

    private boolean gameActive = false;
    private boolean selectionPhase = false;
    private boolean gameEnding = false;
    private Timer selectionTimer;
    private GameTimer gameTimer;
    private SubMode1DataLogger dataLogger;

    // Island positions
    private BlockPos smallIslandCenter;
    private BlockPos mediumIslandCenter;
    private BlockPos largeIslandCenter;
    private BlockPos spectatorPlatform;

    private static final int SELECTION_TIME_SECONDS = 30;
    private static final int GAME_TIME_MINUTES = 15;

    private SubMode1Manager() {}

    public static SubMode1Manager getInstance() {
        if (instance == null) {
            instance = new SubMode1Manager();
        }
        return instance;
    }

    public void activate(MinecraftServer server) {
        MySubMod.LOGGER.info("Activating SubMode1");

        // Initialize positions
        initializePositions();

        // Generate islands and spectator platform
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            generateMap(overworld);
            generatePaths(overworld);
            generateInvisibleWalls(overworld);
            generateSpectatorPlatform(overworld);
        }

        // Set permanent daylight
        if (overworld != null) {
            overworld.setDayTime(6000); // Set to noon
        }

        // Initialize data logger
        dataLogger = new SubMode1DataLogger();
        dataLogger.startNewGame();

        // Teleport admins to spectator platform
        teleportAdminsToSpectator(server);

        // Teleport all non-admin players to small island temporarily
        teleportAllPlayersToSmallIsland(server);

        // Start island selection phase (players can choose their final island)
        startIslandSelection(server);
    }

    public void deactivate(MinecraftServer server) {
        MySubMod.LOGGER.info("Deactivating SubMode1");

        try {
            // Stop all timers
            try {
                stopSelectionTimer();
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error stopping selection timer", e);
            }

            try {
                if (gameTimer != null) {
                    gameTimer.stop();
                }
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error stopping game timer", e);
            }

            // Stop data logging
            try {
                if (dataLogger != null) {
                    dataLogger.endGame();
                }
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error ending data logging", e);
            }

            // Note: Mode change to waiting room is handled by the calling code, not here
            // to avoid recursive calls between deactivate() and changeSubMode()

            // Restore inventories
            try {
                restoreAllInventories(server);
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error restoring inventories", e);
            }

            // Clear map (islands + spectator platform) AFTER players are safely teleported
            try {
                clearMap(server.getLevel(ServerLevel.OVERWORLD));
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error clearing map", e);
            }

        } finally {
            // Reset state regardless of errors
            gameActive = false;
            selectionPhase = false;
            gameEnding = false;
            playerIslandSelections.clear();
            alivePlayers.clear();
            spectatorPlayers.clear();

            MySubMod.LOGGER.info("SubMode1 deactivation completed");
        }
    }

    private void initializePositions() {
        // Islands positioned at different locations
        smallIslandCenter = new BlockPos(-100, 100, -100);
        mediumIslandCenter = new BlockPos(0, 100, -100);
        largeIslandCenter = new BlockPos(100, 100, -100);
        spectatorPlatform = new BlockPos(0, 150, 0);
    }

    private void generateMap(ServerLevel level) {
        IslandGenerator generator = new IslandGenerator();
        generator.generateIsland(level, smallIslandCenter, IslandType.SMALL);
        generator.generateIsland(level, mediumIslandCenter, IslandType.MEDIUM);
        generator.generateIsland(level, largeIslandCenter, IslandType.LARGE);
    }

    private void generatePaths(ServerLevel level) {
        MySubMod.LOGGER.info("Generating paths between islands");

        // Path from small island to medium island (-100,100,-100) to (0,100,-100)
        generatePath(level, smallIslandCenter, mediumIslandCenter, 3);

        // Path from medium island to large island (0,100,-100) to (100,100,-100)
        generatePath(level, mediumIslandCenter, largeIslandCenter, 3);
    }

    private void generatePath(ServerLevel level, BlockPos start, BlockPos end, int width) {
        // Calculate path direction and length
        int deltaX = end.getX() - start.getX();
        int deltaZ = end.getZ() - start.getZ();
        int length = Math.max(Math.abs(deltaX), Math.abs(deltaZ));

        // Create straight path
        for (int i = 0; i <= length; i++) {
            double progress = (double) i / length;
            int pathX = (int) (start.getX() + deltaX * progress);
            int pathY = start.getY();
            int pathZ = (int) (start.getZ() + deltaZ * progress);

            // Generate path width
            for (int w = -width/2; w <= width/2; w++) {
                for (int l = -width/2; l <= width/2; l++) {
                    BlockPos pathPos = new BlockPos(pathX + w, pathY - 1, pathZ + l);
                    BlockPos surfacePos = new BlockPos(pathX + w, pathY, pathZ + l);

                    // Place stone base and stone brick surface
                    level.setBlock(pathPos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(surfacePos, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);

                    // Clear space above path
                    for (int h = 1; h <= 3; h++) {
                        level.setBlock(new BlockPos(pathX + w, pathY + h, pathZ + l),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private void generateInvisibleWalls(ServerLevel level) {
        MySubMod.LOGGER.info("Generating invisible walls around island system");

        // Generate barriers around each island
        generateIslandBarriers(level, smallIslandCenter, IslandType.SMALL);
        generateIslandBarriers(level, mediumIslandCenter, IslandType.MEDIUM);
        generateIslandBarriers(level, largeIslandCenter, IslandType.LARGE);

        // Generate barriers along paths (preventing falling into water)
        generatePathBarriers(level);

        // Generate additional barriers next to path openings
        generatePathOpeningBarriers(level);
    }

    private void generateIslandBarriers(ServerLevel level, BlockPos center, IslandType type) {
        int size = type.getRadius(); // Barriers at the exact edge of square islands
        int wallHeight = 20;

        // Generate square barriers inside the island perimeter for maximum tightness
        // North and South walls
        for (int x = center.getX() - size; x <= center.getX() + size; x++) {
            // North wall (negative Z) - inside square island edge
            int northZ = center.getZ() - size;
            if (!isPathConnectionPoint(center, x, northZ)) {
                for (int y = 90; y <= center.getY() + wallHeight; y++) {
                    level.setBlock(new BlockPos(x, y, northZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }

            // South wall (positive Z) - inside square island edge
            int southZ = center.getZ() + size;
            if (!isPathConnectionPoint(center, x, southZ)) {
                for (int y = 90; y <= center.getY() + wallHeight; y++) {
                    level.setBlock(new BlockPos(x, y, southZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }

        // East and West walls
        for (int z = center.getZ() - size; z <= center.getZ() + size; z++) {
            // West wall (negative X) - inside square island edge
            int westX = center.getX() - size;
            if (!isPathConnectionPoint(center, westX, z)) {
                for (int y = 90; y <= center.getY() + wallHeight; y++) {
                    level.setBlock(new BlockPos(westX, y, z),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }

            // East wall (positive X) - inside square island edge
            int eastX = center.getX() + size;
            if (!isPathConnectionPoint(center, eastX, z)) {
                for (int y = 90; y <= center.getY() + wallHeight; y++) {
                    level.setBlock(new BlockPos(eastX, y, z),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }
    }

    private boolean isPathConnectionPoint(BlockPos islandCenter, int x, int z) {
        // Check if this point is where a path connects to the island
        // Islands are at Z=-100, paths go in X direction between islands
        // Paths connect on east/west sides of islands, not north/south

        int pathHalfWidth = 1; // 3 blocks total opening to match path width

        // For medium island (0, 100, -100): paths connect on east and west sides
        if (islandCenter.equals(mediumIslandCenter)) {
            // West side - path to small island at (-100, 100, -100)
            if (x == islandCenter.getX() - IslandType.MEDIUM.getRadius()) { // West barrier edge
                return Math.abs(z - islandCenter.getZ()) <= pathHalfWidth;
            }
            // East side - path to large island at (100, 100, -100)
            if (x == islandCenter.getX() + IslandType.MEDIUM.getRadius()) { // East barrier edge
                return Math.abs(z - islandCenter.getZ()) <= pathHalfWidth;
            }
        }

        // For small island (-100, 100, -100): path connects on east side to medium island
        if (islandCenter.equals(smallIslandCenter)) {
            if (x == islandCenter.getX() + IslandType.SMALL.getRadius()) { // East barrier edge
                return Math.abs(z - islandCenter.getZ()) <= pathHalfWidth;
            }
        }

        // For large island (100, 100, -100): path connects on west side to medium island
        if (islandCenter.equals(largeIslandCenter)) {
            if (x == islandCenter.getX() - IslandType.LARGE.getRadius()) { // West barrier edge
                return Math.abs(z - islandCenter.getZ()) <= pathHalfWidth;
            }
        }

        return false;
    }

    private void generatePathBarriers(ServerLevel level) {
        // Barriers along path from small to medium island
        generatePathSideBarriers(level, smallIslandCenter, mediumIslandCenter, 3);

        // Barriers along path from medium to large island
        generatePathSideBarriers(level, mediumIslandCenter, largeIslandCenter, 3);
    }

    private void generatePathSideBarriers(ServerLevel level, BlockPos start, BlockPos end, int pathWidth) {
        int deltaX = end.getX() - start.getX();
        int deltaZ = end.getZ() - start.getZ();
        int length = Math.max(Math.abs(deltaX), Math.abs(deltaZ));

        for (int i = 0; i <= length; i++) {
            double progress = (double) i / length;
            int pathX = (int) (start.getX() + deltaX * progress);
            int pathY = start.getY();
            int pathZ = (int) (start.getZ() + deltaZ * progress);

            // Skip barriers if path point is on an island
            if (isPointOnAnyIsland(pathX, pathZ)) {
                continue;
            }

            // Place barriers on both sides of the path
            int barrierOffset = pathWidth/2 + 1; // 1 block away from path edge

            // Calculate perpendicular direction for side barriers
            double perpX = 0, perpZ = 1; // Default perpendicular (for X-direction paths)
            if (deltaX == 0) { // Z-direction path
                perpX = 1;
                perpZ = 0;
            }

            // Left side barriers
            int leftX = (int) (pathX + barrierOffset * perpX);
            int leftZ = (int) (pathZ + barrierOffset * perpZ);
            for (int y = 90; y <= pathY + 20; y++) {
                level.setBlock(new BlockPos(leftX, y, leftZ),
                    net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
            }

            // Right side barriers
            int rightX = (int) (pathX - barrierOffset * perpX);
            int rightZ = (int) (pathZ - barrierOffset * perpZ);
            for (int y = 90; y <= pathY + 20; y++) {
                level.setBlock(new BlockPos(rightX, y, rightZ),
                    net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
            }
        }
    }

    private boolean isPointOnAnyIsland(int x, int z) {
        // Check if point is within any island's square bounds
        BlockPos[] islandCenters = {smallIslandCenter, mediumIslandCenter, largeIslandCenter};
        IslandType[] islandTypes = {IslandType.SMALL, IslandType.MEDIUM, IslandType.LARGE};

        for (int i = 0; i < islandCenters.length; i++) {
            BlockPos center = islandCenters[i];
            int radius = islandTypes[i].getRadius();

            if (x >= center.getX() - radius && x <= center.getX() + radius &&
                z >= center.getZ() - radius && z <= center.getZ() + radius) {
                return true;
            }
        }
        return false;
    }

    private void generatePathOpeningBarriers(ServerLevel level) {
        // Add barriers next to path openings to close gaps
        int wallHeight = 20;

        // For each island, add barriers on the sides of path openings
        generatePathOpeningBarriersForIsland(level, smallIslandCenter, IslandType.SMALL, wallHeight);
        generatePathOpeningBarriersForIsland(level, mediumIslandCenter, IslandType.MEDIUM, wallHeight);
        generatePathOpeningBarriersForIsland(level, largeIslandCenter, IslandType.LARGE, wallHeight);
    }

    private void generatePathOpeningBarriersForIsland(ServerLevel level, BlockPos center, IslandType type, int wallHeight) {
        int size = type.getRadius();

        // For medium island, add barriers next to both path openings (east and west)
        if (center.equals(mediumIslandCenter)) {
            // West path opening - barriers north and south of opening
            int westX = center.getX() - size;
            for (int offsetZ = 2; offsetZ <= 4; offsetZ++) { // 3 blocks north of path
                for (int y = 90; y <= center.getY() + wallHeight; y++) {
                    level.setBlock(new BlockPos(westX, y, center.getZ() - offsetZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(westX, y, center.getZ() + offsetZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }

            // East path opening - barriers north and south of opening
            int eastX = center.getX() + size;
            for (int offsetZ = 2; offsetZ <= 4; offsetZ++) { // 3 blocks south of path
                for (int y = 90; y <= center.getY() + wallHeight; y++) {
                    level.setBlock(new BlockPos(eastX, y, center.getZ() - offsetZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(eastX, y, center.getZ() + offsetZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }

        // For small island, add barriers next to east path opening
        if (center.equals(smallIslandCenter)) {
            int eastX = center.getX() + size;
            for (int offsetZ = 2; offsetZ <= 4; offsetZ++) {
                for (int y = 90; y <= center.getY() + wallHeight; y++) {
                    level.setBlock(new BlockPos(eastX, y, center.getZ() - offsetZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(eastX, y, center.getZ() + offsetZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }

        // For large island, add barriers next to west path opening
        if (center.equals(largeIslandCenter)) {
            int westX = center.getX() - size;
            for (int offsetZ = 2; offsetZ <= 4; offsetZ++) {
                for (int y = 90; y <= center.getY() + wallHeight; y++) {
                    level.setBlock(new BlockPos(westX, y, center.getZ() - offsetZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(westX, y, center.getZ() + offsetZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }
    }

    private void generateSpectatorPlatform(ServerLevel level) {
        // Generate a simple platform for spectators
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                level.setBlock(spectatorPlatform.offset(x, 0, z),
                    net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 3);
            }
        }

        // Add invisible barriers around the platform
        for (int x = -11; x <= 11; x++) {
            for (int z = -11; z <= 11; z++) {
                // Skip inner platform area
                if (x >= -10 && x <= 10 && z >= -10 && z <= 10) continue;

                // Place barriers 1-3 blocks high around the platform
                for (int y = 1; y <= 3; y++) {
                    level.setBlock(spectatorPlatform.offset(x, y, z),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }
    }

    private void clearMap(ServerLevel level) {
        if (level == null) return;

        // Clear islands first (slower)
        IslandGenerator.clearIsland(level, smallIslandCenter, IslandType.SMALL);
        IslandGenerator.clearIsland(level, mediumIslandCenter, IslandType.MEDIUM);
        IslandGenerator.clearIsland(level, largeIslandCenter, IslandType.LARGE);

        // Clear paths between islands
        clearPaths(level);

        // Clear invisible walls
        clearInvisibleWalls(level);

        // Clear spectator platform last (faster)
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                for (int y = -5; y <= 10; y++) {
                    level.setBlock(spectatorPlatform.offset(x, y, z),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private void clearPaths(ServerLevel level) {
        // Clear path from small to medium island
        clearPath(level, smallIslandCenter, mediumIslandCenter, 3);

        // Clear path from medium to large island
        clearPath(level, mediumIslandCenter, largeIslandCenter, 3);
    }

    private void clearPath(ServerLevel level, BlockPos start, BlockPos end, int width) {
        int deltaX = end.getX() - start.getX();
        int deltaZ = end.getZ() - start.getZ();
        int length = Math.max(Math.abs(deltaX), Math.abs(deltaZ));

        for (int i = 0; i <= length; i++) {
            double progress = (double) i / length;
            int pathX = (int) (start.getX() + deltaX * progress);
            int pathY = start.getY();
            int pathZ = (int) (start.getZ() + deltaZ * progress);

            for (int w = -width/2; w <= width/2; w++) {
                for (int l = -width/2; l <= width/2; l++) {
                    for (int h = -2; h <= 5; h++) {
                        level.setBlock(new BlockPos(pathX + w, pathY + h, pathZ + l),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private void clearInvisibleWalls(ServerLevel level) {
        // Clear barriers around each island
        clearIslandBarriers(level, smallIslandCenter, IslandType.SMALL);
        clearIslandBarriers(level, mediumIslandCenter, IslandType.MEDIUM);
        clearIslandBarriers(level, largeIslandCenter, IslandType.LARGE);

        // Clear path barriers
        clearPathBarriers(level);
    }

    private void clearIslandBarriers(ServerLevel level, BlockPos center, IslandType type) {
        int radius = type.getRadius() - 2; // Same tighter radius as generation
        int wallHeight = 20;

        // Clear barriers inside the island perimeter (same positions as generation)
        // North and South walls
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            // North wall
            int northZ = center.getZ() - radius;
            for (int y = 90; y <= center.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(x, y, northZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }

            // South wall
            int southZ = center.getZ() + radius;
            for (int y = 90; y <= center.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(x, y, southZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }

        // East and West walls
        for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
            // West wall
            int westX = center.getX() - radius;
            for (int y = 90; y <= center.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(westX, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }

            // East wall
            int eastX = center.getX() + radius;
            for (int y = 90; y <= center.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(eastX, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private void clearPathBarriers(ServerLevel level) {
        clearPathSideBarriers(level, smallIslandCenter, mediumIslandCenter, 3);
        clearPathSideBarriers(level, mediumIslandCenter, largeIslandCenter, 3);
    }

    private void clearPathSideBarriers(ServerLevel level, BlockPos start, BlockPos end, int pathWidth) {
        int deltaX = end.getX() - start.getX();
        int deltaZ = end.getZ() - start.getZ();
        int length = Math.max(Math.abs(deltaX), Math.abs(deltaZ));

        for (int i = 0; i <= length; i++) {
            double progress = (double) i / length;
            int pathX = (int) (start.getX() + deltaX * progress);
            int pathY = start.getY();
            int pathZ = (int) (start.getZ() + deltaZ * progress);

            // Skip clearing if path point was on an island (no barriers were placed there)
            if (isPointOnAnyIsland(pathX, pathZ)) {
                continue;
            }

            int barrierOffset = pathWidth/2 + 2;

            double perpX = 0, perpZ = 1;
            if (deltaX == 0) {
                perpX = 1;
                perpZ = 0;
            }

            // Clear left side barriers
            int leftX = (int) (pathX + barrierOffset * perpX);
            int leftZ = (int) (pathZ + barrierOffset * perpZ);
            for (int y = 90; y <= pathY + 20; y++) {
                level.setBlock(new BlockPos(leftX, y, leftZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }

            // Clear right side barriers
            int rightX = (int) (pathX - barrierOffset * perpX);
            int rightZ = (int) (pathZ - barrierOffset * perpZ);
            for (int y = 90; y <= pathY + 20; y++) {
                level.setBlock(new BlockPos(rightX, y, rightZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private void teleportAdminsToSpectator(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (SubModeManager.getInstance().isAdmin(player)) {
                teleportToSpectator(player);
            }
        }
    }

    private void teleportAllPlayersToSmallIsland(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SubModeManager.getInstance().isAdmin(player)) {
                // Store and clear inventory
                storePlayerInventory(player);
                clearPlayerInventory(player);

                // Teleport to small island temporarily (they will choose their final island)
                BlockPos spawnPos = smallIslandCenter;
                player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5, 0.0f, 0.0f);
                alivePlayers.add(player.getUUID());
            }
        }
    }

    private void startIslandSelection(MinecraftServer server) {
        selectionPhase = true;

        // Send island selection GUI to all non-admin players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SubModeManager.getInstance().isAdmin(player)) {
                storePlayerInventory(player);
                clearPlayerInventory(player);
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new IslandSelectionPacket());
            }
        }

        // Start 30 second timer
        selectionTimer = new Timer();
        selectionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                endSelectionPhase(server);
            }
        }, SELECTION_TIME_SECONDS * 1000);

        // Broadcast countdown
        broadcastMessage(server, "§eChoisissez votre île ! Temps restant: " + SELECTION_TIME_SECONDS + " secondes");
    }

    public void selectIsland(ServerPlayer player, IslandType island) {
        if (!selectionPhase) return;

        playerIslandSelections.put(player.getUUID(), island);
        player.sendSystemMessage(Component.literal("§aVous avez sélectionné: " + island.getDisplayName()));
    }

    private void endSelectionPhase(MinecraftServer server) {
        selectionPhase = false;

        // Assign random islands to players who didn't select
        IslandType[] islands = IslandType.values();
        Random random = new Random();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SubModeManager.getInstance().isAdmin(player) &&
                !playerIslandSelections.containsKey(player.getUUID())) {
                IslandType randomIsland = islands[random.nextInt(islands.length)];
                playerIslandSelections.put(player.getUUID(), randomIsland);
                player.sendSystemMessage(Component.literal("§eÎle assignée automatiquement: " + randomIsland.getDisplayName()));
            }
        }

        // Teleport players to their islands
        teleportPlayersToIslands(server);

        // Start the game
        startGame(server);
    }

    private void teleportPlayersToIslands(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (SubModeManager.getInstance().isAdmin(player)) continue;

            IslandType selectedIsland = playerIslandSelections.get(player.getUUID());
            if (selectedIsland != null) {
                BlockPos spawnPos = getIslandSpawnPosition(selectedIsland);
                player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5, 0.0f, 0.0f);
                alivePlayers.add(player.getUUID());
            }
        }
    }

    private BlockPos getIslandSpawnPosition(IslandType island) {
        switch (island) {
            case SMALL: return smallIslandCenter;
            case MEDIUM: return mediumIslandCenter;
            case LARGE: return largeIslandCenter;
            default: return mediumIslandCenter;
        }
    }

    private void startGame(MinecraftServer server) {
        gameActive = true;

        // Reset all players' health to 100%
        resetAllPlayersHealth(server);

        // Start game timer
        gameTimer = new GameTimer(GAME_TIME_MINUTES, server);
        gameTimer.start();

        // Start health degradation
        SubMode1HealthManager.getInstance().startHealthDegradation(server);

        // Start candy spawning
        SubMode1CandyManager.getInstance().startCandySpawning(server);

        broadcastMessage(server, "§aLa partie commence ! Survivez " + GAME_TIME_MINUTES + " minutes !");
    }

    public void endGame(MinecraftServer server) {
        // Prevent multiple calls
        if (gameEnding) {
            return;
        }
        gameEnding = true;
        gameActive = false;

        MySubMod.LOGGER.info("Ending SubMode1 game");

        // Stop systems immediately to prevent further operations
        try {
            SubMode1HealthManager.getInstance().stopHealthDegradation();
            SubMode1CandyManager.getInstance().stopCandySpawning();

            // Stop game timer to prevent duplicate calls
            if (gameTimer != null) {
                gameTimer.stop();
            }
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error stopping game systems during endGame", e);
        }

        // Show congratulations
        showCongratulations(server);

        // Change to waiting room and deactivate after a short delay
        Timer delayTimer = new Timer("SubMode1-EndGame-Timer");
        delayTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    server.execute(() -> {
                        try {
                            // Change to waiting room first (this will call deactivate automatically)
                            SubModeManager.getInstance().changeSubMode(com.example.mysubmod.submodes.SubMode.WAITING_ROOM, null, server);
                        } catch (Exception e) {
                            MySubMod.LOGGER.error("Error changing to waiting room during game end", e);
                        } finally {
                            delayTimer.cancel(); // Clean up timer
                        }
                    });
                } catch (Exception e) {
                    MySubMod.LOGGER.error("Error scheduling mode change", e);
                    delayTimer.cancel();
                }
            }
        }, 5000); // 5 seconds delay
    }

    private void showCongratulations(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal("§6§l=== FÉLICITATIONS ==="));
            player.sendSystemMessage(Component.literal("§eMerci d'avoir participé à cette expérience !"));
            player.sendSystemMessage(Component.literal("§aRetour à la salle d'attente dans 5 secondes..."));
        }
    }

    private void storePlayerInventory(ServerPlayer player) {
        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            inventory.add(player.getInventory().getItem(i).copy());
        }
        storedInventories.put(player.getUUID(), inventory);
    }

    private void clearPlayerInventory(ServerPlayer player) {
        player.getInventory().clearContent();
    }

    private void restoreAllInventories(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            restorePlayerInventory(player);
        }
    }

    private void restorePlayerInventory(ServerPlayer player) {
        List<ItemStack> inventory = storedInventories.remove(player.getUUID());
        if (inventory != null) {
            for (int i = 0; i < Math.min(inventory.size(), player.getInventory().getContainerSize()); i++) {
                player.getInventory().setItem(i, inventory.get(i));
            }
        }
    }

    public void teleportToSpectator(ServerPlayer player) {
        ServerLevel overworld = player.server.getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            player.teleportTo(overworld,
                spectatorPlatform.getX() + 0.5,
                spectatorPlatform.getY() + 1,
                spectatorPlatform.getZ() + 0.5,
                0.0f, 0.0f);
            spectatorPlayers.add(player.getUUID());
            alivePlayers.remove(player.getUUID());
        }
    }

    private void stopSelectionTimer() {
        if (selectionTimer != null) {
            selectionTimer.cancel();
            selectionTimer = null;
        }
    }

    private void broadcastMessage(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private void resetAllPlayersHealth(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Reset health to maximum (20.0f = 10 hearts)
            player.setHealth(player.getMaxHealth());

            // Set hunger to 50% (10 out of 20)
            player.getFoodData().setFoodLevel(10);
            player.getFoodData().setSaturation(5.0f);

            MySubMod.LOGGER.debug("Reset health to 100% and hunger to 50% for player: {}", player.getName().getString());
        }

        MySubMod.LOGGER.info("Reset health to 100% and hunger to 50% for all players at game start");
    }


    // Getters
    public boolean isGameActive() { return gameActive; }
    public boolean isSelectionPhase() { return selectionPhase; }
    public boolean isPlayerAlive(UUID playerId) { return alivePlayers.contains(playerId); }
    public boolean isPlayerSpectator(UUID playerId) { return spectatorPlayers.contains(playerId); }
    public Set<UUID> getAlivePlayers() { return new HashSet<>(alivePlayers); }
    public SubMode1DataLogger getDataLogger() { return dataLogger; }
    public BlockPos getSmallIslandCenter() { return smallIslandCenter; }
    public BlockPos getMediumIslandCenter() { return mediumIslandCenter; }
    public BlockPos getLargeIslandCenter() { return largeIslandCenter; }
}
package com.example.mysubmod.submodes.submode1;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode1.data.CandySpawnEntry;
import com.example.mysubmod.submodes.submode1.data.CandySpawnFileManager;
import com.example.mysubmod.submodes.submode1.data.SubMode1DataLogger;
import com.example.mysubmod.submodes.submode1.islands.IslandGenerator;
import com.example.mysubmod.submodes.submode1.islands.IslandType;
import com.example.mysubmod.submodes.submode1.network.CandyFileListPacket;
import com.example.mysubmod.submodes.submode1.network.IslandSelectionPacket;
import com.example.mysubmod.submodes.submode1.network.GameTimerPacket;
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
    private boolean islandsGenerated = false;
    private Timer selectionTimer;
    private GameTimer gameTimer;
    private SubMode1DataLogger dataLogger;
    private String selectedCandySpawnFile;
    private List<CandySpawnEntry> candySpawnConfig;
    private ServerPlayer gameInitiator; // The admin who started the game

    // Island positions
    private BlockPos smallIslandCenter;
    private BlockPos mediumIslandCenter;
    private BlockPos largeIslandCenter;
    private BlockPos extraLargeIslandCenter;
    private BlockPos centralSquare;
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
        activate(server, null);
    }

    public void activate(MinecraftServer server, ServerPlayer initiator) {
        MySubMod.LOGGER.info("Activating SubMode1");
        this.gameInitiator = initiator;

        // Send loading message to all players
        server.getPlayerList().getPlayers().forEach(player ->
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e§lChargement du sous-mode 1..."))
        );

        // Clean up any leftover candies from previous games first
        try {
            SubMode1CandyManager.getInstance().removeAllCandiesFromWorld(server);
            MySubMod.LOGGER.info("Cleaned up leftover candies from previous games");
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error cleaning up leftover candies", e);
        }

        // Ensure candy spawn directory exists
        CandySpawnFileManager.getInstance().ensureDirectoryExists();

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

        // Clean up any candies again after island generation (just to be sure)
        try {
            SubMode1CandyManager.getInstance().removeAllCandiesFromWorld(server);
            MySubMod.LOGGER.info("Final cleanup of candies before player teleportation");
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error in final candy cleanup", e);
        }

        // Initialize data logger
        dataLogger = new SubMode1DataLogger();
        dataLogger.startNewGame();

        // Teleport admins to spectator platform
        teleportAdminsToSpectator(server);

        // Teleport all non-admin players to small island temporarily
        teleportAllPlayersToSmallIsland(server);

        // Show candy file selection to the initiating admin
        if (initiator != null && SubModeManager.getInstance().isPlayerAdmin(initiator)) {
            showCandyFileSelection(initiator);
        } else {
            // Auto-select default file if no admin initiated or fallback
            String defaultFile = CandySpawnFileManager.getInstance().getDefaultFile();
            setCandySpawnFile(defaultFile);
            startIslandSelection(server);
        }
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
                // Always send deactivation signal to all clients to clear any lingering timers
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new GameTimerPacket(-1)); // -1 means deactivate

                // Send empty candy counts to deactivate HUD
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new com.example.mysubmod.submodes.submode1.network.CandyCountUpdatePacket(new java.util.HashMap<>()));
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

            // Stop health degradation
            try {
                SubMode1HealthManager.getInstance().stopHealthDegradation();
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error stopping health degradation", e);
            }

            // Stop candy spawning and remove all existing candies
            try {
                SubMode1CandyManager.getInstance().stopCandySpawning();
                SubMode1CandyManager.getInstance().removeAllCandiesFromWorld(server);
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error stopping candy spawning", e);
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
            islandsGenerated = false;
            playerIslandSelections.clear();
            alivePlayers.clear();
            spectatorPlayers.clear();

            MySubMod.LOGGER.info("SubMode1 deactivation completed");
        }
    }


    private void initializePositions() {
        // Central square at origin where players spawn initially
        centralSquare = new BlockPos(0, 100, 0);

        // 4 islands positioned around the central square, each 360 blocks away
        // North island (SMALL)
        smallIslandCenter = new BlockPos(0, 100, -360);
        // East island (MEDIUM)
        mediumIslandCenter = new BlockPos(360, 100, 0);
        // South island (LARGE)
        largeIslandCenter = new BlockPos(0, 100, 360);
        // West island (EXTRA_LARGE)
        extraLargeIslandCenter = new BlockPos(-360, 100, 0);

        // Spectator platform above central square
        spectatorPlatform = new BlockPos(0, 150, 0);
    }

    private void generateMap(ServerLevel level) {
        IslandGenerator generator = new IslandGenerator();

        // Generate central square (20x20)
        generateCentralSquare(level);

        // Generate 4 islands
        generator.generateIsland(level, smallIslandCenter, IslandType.SMALL);
        generator.generateIsland(level, mediumIslandCenter, IslandType.MEDIUM);
        generator.generateIsland(level, largeIslandCenter, IslandType.LARGE);
        generator.generateIsland(level, extraLargeIslandCenter, IslandType.EXTRA_LARGE);

        // Generate spawn points for all islands
        SpawnPointManager.getInstance().generateSpawnPoints(
            smallIslandCenter, mediumIslandCenter, largeIslandCenter, extraLargeIslandCenter);

        islandsGenerated = true; // Mark islands as generated
    }

    private void generateCentralSquare(ServerLevel level) {
        MySubMod.LOGGER.info("Generating central square at {}", centralSquare);

        int halfSize = 10; // 20x20 square

        // Generate glass floor
        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                level.setBlock(centralSquare.offset(x, -1, z),
                    net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 3);
                // Clear air above
                for (int y = 0; y <= 5; y++) {
                    level.setBlock(centralSquare.offset(x, y, z),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        // Add directional signs with a slight delay to ensure chunks are loaded
        level.getServer().execute(() -> addDirectionalSigns(level));
    }

    private void addDirectionalSigns(ServerLevel level) {
        MySubMod.LOGGER.info("Adding directional markers at central square");

        // Place colored wool pillars as directional markers
        // North - White wool tower for SMALL island (3 blocks high)
        BlockPos northBase = new BlockPos(centralSquare.getX(), centralSquare.getY(), centralSquare.getZ() - 8);
        for (int i = 0; i < 3; i++) {
            level.setBlock(northBase.above(i), net.minecraft.world.level.block.Blocks.WHITE_WOOL.defaultBlockState(), 3);
        }
        createHologram(level, northBase.above(3), "§f§lP E T I T E   Î L E", "§7§l6 0 x 6 0");

        // East - Lime wool tower for MEDIUM island
        BlockPos eastBase = new BlockPos(centralSquare.getX() + 8, centralSquare.getY(), centralSquare.getZ());
        for (int i = 0; i < 3; i++) {
            level.setBlock(eastBase.above(i), net.minecraft.world.level.block.Blocks.LIME_WOOL.defaultBlockState(), 3);
        }
        createHologram(level, eastBase.above(3), "§a§lM O Y E N N E   Î L E", "§7§l9 0 x 9 0");

        // South - Blue wool tower for LARGE island
        BlockPos southBase = new BlockPos(centralSquare.getX(), centralSquare.getY(), centralSquare.getZ() + 8);
        for (int i = 0; i < 3; i++) {
            level.setBlock(southBase.above(i), net.minecraft.world.level.block.Blocks.BLUE_WOOL.defaultBlockState(), 3);
        }
        createHologram(level, southBase.above(3), "§9§lG R A N D E   Î L E", "§7§l1 2 0 x 1 2 0");

        // West - Orange wool tower for EXTRA_LARGE island
        BlockPos westBase = new BlockPos(centralSquare.getX() - 8, centralSquare.getY(), centralSquare.getZ());
        for (int i = 0; i < 3; i++) {
            level.setBlock(westBase.above(i), net.minecraft.world.level.block.Blocks.ORANGE_WOOL.defaultBlockState(), 3);
        }
        createHologram(level, westBase.above(3), "§6§lT R È S   G R A N D E   Î L E", "§7§l1 5 0 x 1 5 0");

        MySubMod.LOGGER.info("Directional markers placed successfully (White=Petite, Lime=Moyenne, Blue=Grande, Orange=T.Grande)");
    }

    private void createHologram(ServerLevel level, BlockPos pos, String line1, String line2) {
        // Create armor stand for line 1
        net.minecraft.world.entity.decoration.ArmorStand hologram1 = new net.minecraft.world.entity.decoration.ArmorStand(
            net.minecraft.world.entity.EntityType.ARMOR_STAND,
            level
        );
        hologram1.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        hologram1.setInvisible(true);
        hologram1.setNoGravity(true);
        hologram1.setCustomNameVisible(true);
        hologram1.setCustomName(net.minecraft.network.chat.Component.literal(line1));
        hologram1.setInvulnerable(true);
        hologram1.setSilent(true);
        hologram1.setNoBasePlate(true);
        level.addFreshEntity(hologram1);

        // Create armor stand for line 2 (slightly below)
        net.minecraft.world.entity.decoration.ArmorStand hologram2 = new net.minecraft.world.entity.decoration.ArmorStand(
            net.minecraft.world.entity.EntityType.ARMOR_STAND,
            level
        );
        hologram2.setPos(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5);
        hologram2.setInvisible(true);
        hologram2.setNoGravity(true);
        hologram2.setCustomNameVisible(true);
        hologram2.setCustomName(net.minecraft.network.chat.Component.literal(line2));
        hologram2.setInvulnerable(true);
        hologram2.setSilent(true);
        hologram2.setNoBasePlate(true);
        level.addFreshEntity(hologram2);
    }


    private void generatePaths(ServerLevel level) {
        MySubMod.LOGGER.info("Generating paths from central square to islands");

        // Path from central square to each island (360 blocks each)
        generatePath(level, centralSquare, smallIslandCenter, 3);      // North
        generatePath(level, centralSquare, mediumIslandCenter, 3);     // East
        generatePath(level, centralSquare, largeIslandCenter, 3);      // South
        generatePath(level, centralSquare, extraLargeIslandCenter, 3); // West
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

        // Generate barriers around central square
        generateCentralSquareBarriers(level);

        // Generate barriers around each island
        generateIslandBarriers(level, smallIslandCenter, IslandType.SMALL);
        generateIslandBarriers(level, mediumIslandCenter, IslandType.MEDIUM);
        generateIslandBarriers(level, largeIslandCenter, IslandType.LARGE);
        generateIslandBarriers(level, extraLargeIslandCenter, IslandType.EXTRA_LARGE);

        // Generate barriers along paths (preventing falling into water)
        generatePathBarriers(level);

        // Generate additional barriers next to path openings
        generatePathOpeningBarriers(level);
    }

    private void generateCentralSquareBarriers(ServerLevel level) {
        int halfSize = 10;
        int wallHeight = 20;

        // North, South, East, West walls around central square
        for (int x = -halfSize; x <= halfSize; x++) {
            // North wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                if (!isPathConnectionPoint(centralSquare, x, centralSquare.getZ() - halfSize)) {
                    level.setBlock(new BlockPos(centralSquare.getX() + x, y, centralSquare.getZ() - halfSize),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
            // South wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                if (!isPathConnectionPoint(centralSquare, x, centralSquare.getZ() + halfSize)) {
                    level.setBlock(new BlockPos(centralSquare.getX() + x, y, centralSquare.getZ() + halfSize),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }

        for (int z = -halfSize; z <= halfSize; z++) {
            // West wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                if (!isPathConnectionPoint(centralSquare, centralSquare.getX() - halfSize, z)) {
                    level.setBlock(new BlockPos(centralSquare.getX() - halfSize, y, centralSquare.getZ() + z),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
            // East wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                if (!isPathConnectionPoint(centralSquare, centralSquare.getX() + halfSize, z)) {
                    level.setBlock(new BlockPos(centralSquare.getX() + halfSize, y, centralSquare.getZ() + z),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }
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

    private boolean isPathConnectionPoint(BlockPos center, int x, int z) {
        int pathHalfWidth = 1; // 3 blocks total opening to match path width

        // For central square - paths connect on all 4 sides
        if (center.equals(centralSquare)) {
            int halfSize = 10;
            // North path to small island
            if (z == centralSquare.getZ() - halfSize) {
                return Math.abs(x - centralSquare.getX()) <= pathHalfWidth;
            }
            // East path to medium island
            if (x == centralSquare.getX() + halfSize) {
                return Math.abs(z - centralSquare.getZ()) <= pathHalfWidth;
            }
            // South path to large island
            if (z == centralSquare.getZ() + halfSize) {
                return Math.abs(x - centralSquare.getX()) <= pathHalfWidth;
            }
            // West path to extra large island
            if (x == centralSquare.getX() - halfSize) {
                return Math.abs(z - centralSquare.getZ()) <= pathHalfWidth;
            }
        }

        // For small island (North): path connects on south side to central square
        if (center.equals(smallIslandCenter)) {
            if (z == smallIslandCenter.getZ() + IslandType.SMALL.getRadius()) {
                return Math.abs(x - smallIslandCenter.getX()) <= pathHalfWidth;
            }
        }

        // For medium island (East): path connects on west side to central square
        if (center.equals(mediumIslandCenter)) {
            if (x == mediumIslandCenter.getX() - IslandType.MEDIUM.getRadius()) {
                return Math.abs(z - mediumIslandCenter.getZ()) <= pathHalfWidth;
            }
        }

        // For large island (South): path connects on north side to central square
        if (center.equals(largeIslandCenter)) {
            if (z == largeIslandCenter.getZ() - IslandType.LARGE.getRadius()) {
                return Math.abs(x - largeIslandCenter.getX()) <= pathHalfWidth;
            }
        }

        // For extra large island (West): path connects on east side to central square
        if (center.equals(extraLargeIslandCenter)) {
            if (x == extraLargeIslandCenter.getX() + IslandType.EXTRA_LARGE.getRadius()) {
                return Math.abs(z - extraLargeIslandCenter.getZ()) <= pathHalfWidth;
            }
        }

        return false;
    }

    private void generatePathBarriers(ServerLevel level) {
        // Barriers along paths from central square to each island
        generatePathSideBarriers(level, centralSquare, smallIslandCenter, 3);
        generatePathSideBarriers(level, centralSquare, mediumIslandCenter, 3);
        generatePathSideBarriers(level, centralSquare, largeIslandCenter, 3);
        generatePathSideBarriers(level, centralSquare, extraLargeIslandCenter, 3);
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
        // Check if point is within any island's square bounds or central square
        BlockPos[] islandCenters = {smallIslandCenter, mediumIslandCenter, largeIslandCenter, extraLargeIslandCenter};
        IslandType[] islandTypes = {IslandType.SMALL, IslandType.MEDIUM, IslandType.LARGE, IslandType.EXTRA_LARGE};

        // Check central square
        int centralHalfSize = 10;
        if (x >= centralSquare.getX() - centralHalfSize && x <= centralSquare.getX() + centralHalfSize &&
            z >= centralSquare.getZ() - centralHalfSize && z <= centralSquare.getZ() + centralHalfSize) {
            return true;
        }

        // Check islands
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
        generatePathOpeningBarriersForIsland(level, extraLargeIslandCenter, IslandType.EXTRA_LARGE, wallHeight);
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

        // Check if islands exist physically in the world (in case of server crash/unexpected shutdown)
        boolean islandsExistPhysically = doIslandsExistInWorld(level);

        // Only clear if islands were generated OR if residual islands exist physically
        if (!islandsGenerated && !islandsExistPhysically) {
            MySubMod.LOGGER.debug("Skipping map clearing - no islands found in world");
            return;
        }

        if (islandsExistPhysically && !islandsGenerated) {
            MySubMod.LOGGER.info("Found residual islands from previous session - cleaning up");
        } else {
            MySubMod.LOGGER.info("Clearing generated SubMode1 map");
        }

        // Also clean up any leftover candies
        try {
            SubMode1CandyManager.getInstance().removeAllCandiesFromWorld(level.getServer());
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error cleaning up leftover candies during map clearing", e);
        }

        // Clear central square
        clearCentralSquare(level);

        // Wait a tick for dropped items to spawn, then remove sign items
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 1, () -> {
            removeHolograms(level);
        }));

        // Clear islands first (slower)
        IslandGenerator.clearIsland(level, smallIslandCenter, IslandType.SMALL);
        IslandGenerator.clearIsland(level, mediumIslandCenter, IslandType.MEDIUM);
        IslandGenerator.clearIsland(level, largeIslandCenter, IslandType.LARGE);
        IslandGenerator.clearIsland(level, extraLargeIslandCenter, IslandType.EXTRA_LARGE);

        // Clear paths between islands
        clearPaths(level);

        // Clear invisible walls
        clearInvisibleWalls(level);

        // Clear spawn points
        SpawnPointManager.getInstance().clear();

        // Clear spectator platform last (faster)
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                for (int y = -5; y <= 10; y++) {
                    level.setBlock(spectatorPlatform.offset(x, y, z),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        islandsGenerated = false; // Reset the flag after clearing
    }

    private void clearCentralSquare(ServerLevel level) {
        int halfSize = 10;
        // Clear a larger area to include signs
        for (int x = -halfSize - 5; x <= halfSize + 5; x++) {
            for (int z = -halfSize - 5; z <= halfSize + 5; z++) {
                for (int y = -5; y <= 10; y++) {
                    level.setBlock(centralSquare.offset(x, y, z),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private boolean doIslandsExistInWorld(ServerLevel level) {
        // Check if any island areas have characteristic blocks that indicate an island was generated
        // We'll check for solid blocks (not air) in a small area around each island center

        try {
            // Initialize positions if not done yet
            if (smallIslandCenter == null) {
                initializePositions();
            }

            // Check for any solid blocks in a 3x3 area around each island center
            boolean smallExists = checkIslandArea(level, smallIslandCenter);
            boolean mediumExists = checkIslandArea(level, mediumIslandCenter);
            boolean largeExists = checkIslandArea(level, largeIslandCenter);

            // If any island area has solid blocks, we consider islands exist
            boolean islandsExist = smallExists || mediumExists || largeExists;

            if (islandsExist) {
                MySubMod.LOGGER.info("Physical islands detected in world - Small: {}, Medium: {}, Large: {}",
                    smallExists, mediumExists, largeExists);
            } else {
                MySubMod.LOGGER.debug("No physical islands detected in world");
            }

            return islandsExist;
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error checking for physical islands in world", e);
            return false; // Assume no islands if we can't check
        }
    }

    private boolean checkIslandArea(ServerLevel level, BlockPos center) {
        // Check a 3x3x3 area around the island center for any solid blocks
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos checkPos = center.offset(x, y, z);
                    net.minecraft.world.level.block.state.BlockState blockState = level.getBlockState(checkPos);

                    // If we find any solid block (not air), the island likely exists
                    if (!blockState.isAir()) {
                        MySubMod.LOGGER.debug("Found solid block at {} near island center {}: {}",
                            checkPos, center, blockState.getBlock().getName().getString());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void clearPaths(ServerLevel level) {
        // Clear paths from central square to each island
        clearPath(level, centralSquare, smallIslandCenter, 3);
        clearPath(level, centralSquare, mediumIslandCenter, 3);
        clearPath(level, centralSquare, largeIslandCenter, 3);
        clearPath(level, centralSquare, extraLargeIslandCenter, 3);
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
        clearIslandBarriers(level, extraLargeIslandCenter, IslandType.EXTRA_LARGE);

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
        clearPathSideBarriers(level, centralSquare, smallIslandCenter, 3);
        clearPathSideBarriers(level, centralSquare, mediumIslandCenter, 3);
        clearPathSideBarriers(level, centralSquare, largeIslandCenter, 3);
        clearPathSideBarriers(level, centralSquare, extraLargeIslandCenter, 3);
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

                // Teleport to central square temporarily (they will choose their final island)
                BlockPos spawnPos = centralSquare;
                safeTeleport(player, overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
                alivePlayers.add(player.getUUID());
            }
        }
    }

    private void startIslandSelection(MinecraftServer server) {
        selectionPhase = true;

        // Force load all island chunks and remove dandelion items once
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            forceLoadIslandChunksForSelection(overworld);
            // Wait a few ticks for chunks to load, then remove dandelions
            server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 5, () -> {
                removeDandelionItems(overworld);
            }));
        }

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

        // Log island selection
        if (dataLogger != null) {
            dataLogger.logIslandSelection(player, island);
        }
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

                // Log automatic island assignment
                if (dataLogger != null) {
                    dataLogger.logIslandSelection(player, randomIsland);
                }
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

        // Remove dandelions one more time before teleporting (they may have respawned during selection)
        removeDandelionItems(overworld);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (SubModeManager.getInstance().isAdmin(player)) continue;

            IslandType selectedIsland = playerIslandSelections.get(player.getUUID());
            if (selectedIsland != null) {
                BlockPos spawnPos = getIslandSpawnPosition(selectedIsland);
                safeTeleport(player, overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
                alivePlayers.add(player.getUUID());
            }
        }
    }

    private BlockPos getIslandSpawnPosition(IslandType island) {
        switch (island) {
            case SMALL: return smallIslandCenter;
            case MEDIUM: return mediumIslandCenter;
            case LARGE: return largeIslandCenter;
            case EXTRA_LARGE: return extraLargeIslandCenter;
            default: return centralSquare;
        }
    }

    private void startGame(MinecraftServer server) {
        gameActive = true;

        // Final cleanup: Remove all candies one last time before starting
        try {
            SubMode1CandyManager.getInstance().removeAllCandiesFromWorld(server);
            MySubMod.LOGGER.info("Final cleanup of all candies before game start");
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error in final candy cleanup before game start", e);
        }

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


    private void removeDandelionItems(net.minecraft.server.level.ServerLevel level) {
        int removedCount = 0;
        int totalDandelions = 0;

        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
                if (itemEntity.getItem().getItem() == net.minecraft.world.item.Items.DANDELION) {
                    totalDandelions++;
                    // Only remove if near an island or path
                    BlockPos itemPos = itemEntity.blockPosition();
                    if (isNearIslandOrPath(itemPos)) {
                        itemEntity.discard();
                        removedCount++;
                    }
                }
            }
        }

        MySubMod.LOGGER.info("Dandelion cleanup: found {} total, removed {} from islands/paths", totalDandelions, removedCount);
    }

    public boolean isNearIslandOrPath(BlockPos pos) {
        // Check if near small island (60x60, so radius = 30, add margin = 40)
        if (isWithinRadius(pos, smallIslandCenter, 40)) {
            return true;
        }

        // Check if near medium island (90x90, so radius = 45, add margin = 55)
        if (isWithinRadius(pos, mediumIslandCenter, 55)) {
            return true;
        }

        // Check if near large island (120x120, so radius = 60, add margin = 70)
        if (isWithinRadius(pos, largeIslandCenter, 70)) {
            return true;
        }

        // Check if near extra large island (150x150, so radius = 75, add margin = 85)
        if (isWithinRadius(pos, extraLargeIslandCenter, 85)) {
            return true;
        }

        // Check if near central square (20x20, so radius = 10, add margin = 20)
        if (isWithinRadius(pos, centralSquare, 20)) {
            return true;
        }

        // Check if on any path between center and islands
        // Paths go from center (0,0) to each island center
        if (isOnPath(pos, centralSquare, smallIslandCenter, 5)) return true;
        if (isOnPath(pos, centralSquare, mediumIslandCenter, 5)) return true;
        if (isOnPath(pos, centralSquare, largeIslandCenter, 5)) return true;
        if (isOnPath(pos, centralSquare, extraLargeIslandCenter, 5)) return true;

        return false;
    }

    private boolean isOnPath(BlockPos pos, BlockPos start, BlockPos end, int pathWidth) {
        if (start == null || end == null) return false;

        // Check if position is near the line between start and end
        // Using distance from point to line formula
        double lineLength = Math.sqrt(Math.pow(end.getX() - start.getX(), 2) +
                                     Math.pow(end.getZ() - start.getZ(), 2));
        if (lineLength == 0) return false;

        // Calculate perpendicular distance from pos to the line
        double distance = Math.abs(
            (end.getZ() - start.getZ()) * pos.getX() -
            (end.getX() - start.getX()) * pos.getZ() +
            end.getX() * start.getZ() -
            end.getZ() * start.getX()
        ) / lineLength;

        // Check if within path width and between start and end points
        if (distance <= pathWidth) {
            int minX = Math.min(start.getX(), end.getX()) - pathWidth;
            int maxX = Math.max(start.getX(), end.getX()) + pathWidth;
            int minZ = Math.min(start.getZ(), end.getZ()) - pathWidth;
            int maxZ = Math.max(start.getZ(), end.getZ()) + pathWidth;

            return pos.getX() >= minX && pos.getX() <= maxX &&
                   pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        return false;
    }

    private boolean isWithinRadius(BlockPos pos1, BlockPos pos2, int radius) {
        if (pos2 == null) return false;
        double distance = Math.sqrt(Math.pow(pos1.getX() - pos2.getX(), 2) +
                                  Math.pow(pos1.getZ() - pos2.getZ(), 2));
        return distance <= radius;
    }

    private void forceLoadIslandChunksForSelection(ServerLevel level) {
        // Force load chunks for all islands to ensure dandelions can be detected
        forceLoadChunkAt(level, smallIslandCenter);
        forceLoadChunkAt(level, mediumIslandCenter);
        forceLoadChunkAt(level, largeIslandCenter);
        forceLoadChunkAt(level, extraLargeIslandCenter);
        forceLoadChunkAt(level, centralSquare);
        MySubMod.LOGGER.info("Force loaded island chunks for dandelion cleanup");
    }

    private void forceLoadChunkAt(ServerLevel level, BlockPos pos) {
        if (pos != null) {
            level.getChunkAt(pos);
        }
    }

    private void removeHolograms(net.minecraft.server.level.ServerLevel level) {
        int hologramsRemoved = 0;

        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            // Remove hologram armor stands (invisible, no base plate)
            if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand armorStand) {
                if (armorStand.isInvisible() && armorStand.isNoBasePlate()) {
                    armorStand.discard();
                    hologramsRemoved++;
                }
            }
        }

        if (hologramsRemoved > 0) {
            MySubMod.LOGGER.info("Removed {} hologram armor stands from the world", hologramsRemoved);
        }
    }

    private void removeAllFlowersInArea(net.minecraft.server.level.ServerLevel level, BlockPos center, int radius) {
        if (center == null) return;

        int dandylionsRemoved = 0;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = center.getY() - 5; y < center.getY() + 10; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);

                    // Remove ONLY dandelion blocks, not other flowers
                    if (state.is(net.minecraft.world.level.block.Blocks.DANDELION)) {
                        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                        dandylionsRemoved++;
                    }
                }
            }
        }

        if (dandylionsRemoved > 0) {
            MySubMod.LOGGER.info("Removed {} dandelions from island at {}", dandylionsRemoved, center);
        }
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
            SubMode1CandyManager.getInstance().removeAllCandiesFromWorld(server);

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
        // First, clear current inventory to remove any candies collected during the game
        player.getInventory().clearContent();

        // Then restore the saved inventory from before the game
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
            safeTeleport(player, overworld,
                spectatorPlatform.getX() + 0.5,
                spectatorPlatform.getY() + 1,
                spectatorPlatform.getZ() + 0.5);
            spectatorPlayers.add(player.getUUID());
            alivePlayers.remove(player.getUUID());
        }
    }

    private void safeTeleport(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        // Force load the chunk at destination before teleporting
        BlockPos destination = new BlockPos((int)x, (int)y, (int)z);
        level.getChunkAt(destination); // Force chunk load

        // Force sync the player's position to avoid "Invalid move player packet received"
        player.connection.resetPosition();

        // Set player position directly first
        player.moveTo(x, y, z, 0.0f, 0.0f);

        // Then use teleportTo for proper world syncing
        player.teleportTo(level, x, y, z, 0.0f, 0.0f);

        // Force update the player's position on the client with relative flag cleared
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket(
            x, y, z, 0.0f, 0.0f, java.util.Collections.emptySet(), 0));

        MySubMod.LOGGER.debug("Teleported player {} to ({}, {}, {})", player.getName().getString(), x, y, z);
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

    // Candy spawn file management
    private void showCandyFileSelection(ServerPlayer player) {
        List<String> availableFiles = CandySpawnFileManager.getInstance().getAvailableFiles();

        if (availableFiles.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cAucun fichier de spawn de bonbons trouvé. Utilisation de la configuration par défaut."));
            setCandySpawnFile(null);
            startIslandSelection(player.getServer());
            return;
        }

        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new CandyFileListPacket(availableFiles));

        // Start auto-selection timer (30 seconds)
        Timer autoSelectTimer = new Timer();
        autoSelectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (selectedCandySpawnFile == null) {
                    String defaultFile = CandySpawnFileManager.getInstance().getDefaultFile();
                    setCandySpawnFile(defaultFile);
                    player.getServer().execute(() -> {
                        player.sendSystemMessage(Component.literal("§eAucun fichier sélectionné, utilisation de: " + defaultFile));
                        startIslandSelection(player.getServer());
                    });
                }
            }
        }, 30000); // 30 seconds
    }

    public void setCandySpawnFile(String filename) {
        this.selectedCandySpawnFile = filename;

        if (filename != null) {
            this.candySpawnConfig = CandySpawnFileManager.getInstance().loadSpawnConfig(filename);
            MySubMod.LOGGER.info("Selected candy spawn file: {} with {} entries", filename, candySpawnConfig.size());

            // If we have a game initiator, notify them and start island selection
            if (gameInitiator != null && gameInitiator.getServer() != null) {
                gameInitiator.sendSystemMessage(Component.literal("§aFichier de spawn sélectionné: " + filename));
                startIslandSelection(gameInitiator.getServer());
            }
        } else {
            this.candySpawnConfig = new ArrayList<>();
            MySubMod.LOGGER.warn("No candy spawn file selected, using empty configuration");
        }
    }

    public List<CandySpawnEntry> getCandySpawnConfig() {
        return candySpawnConfig != null ? new ArrayList<>(candySpawnConfig) : new ArrayList<>();
    }

    public String getSelectedCandySpawnFile() {
        return selectedCandySpawnFile;
    }

    public void sendCandyFileListToPlayer(ServerPlayer player) {
        List<String> availableFiles = CandySpawnFileManager.getInstance().getAvailableFiles();
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new CandyFileListPacket(availableFiles));
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
    public BlockPos getExtraLargeIslandCenter() { return extraLargeIslandCenter; }
    public BlockPos getCentralSquare() { return centralSquare; }
}
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
    private final Map<UUID, DisconnectInfo> disconnectedPlayers = new ConcurrentHashMap<>(); // Track disconnect time and position
    private final Map<UUID, Integer> playerCandyCount = new ConcurrentHashMap<>(); // Track candies collected
    private final Map<UUID, Long> playerDeathTime = new ConcurrentHashMap<>(); // Track when players died
    private final Set<UUID> playersInSelectionPhase = ConcurrentHashMap.newKeySet(); // Players who were present during selection
    private final List<net.minecraft.world.entity.decoration.ArmorStand> holograms = new ArrayList<>(); // Track hologram entities
    private final List<PendingLogEvent> pendingLogEvents = new ArrayList<>(); // Events before dataLogger exists
    private long gameStartTime; // Track game start time
    private long selectionStartTime; // Track selection phase start time

    // Inner class to store disconnection info
    private static class DisconnectInfo {
        long disconnectTime;
        double x, y, z;

        DisconnectInfo(long disconnectTime, double x, double y, double z) {
            this.disconnectTime = disconnectTime;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private boolean gameActive = false;
    private boolean selectionPhase = false;
    private boolean fileSelectionPhase = false; // Admin is selecting candy file
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

        // Cleanup any orphaned holograms from previous sessions
        try {
            cleanupOrphanedHolograms(server.getLevel(ServerLevel.OVERWORLD));
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error cleaning up orphaned holograms", e);
        }

        // Data logger will be initialized when file is selected (in startIslandSelection)

        // Teleport admins to spectator platform
        teleportAdminsToSpectator(server);

        // Teleport all non-admin players to small island temporarily
        teleportAllPlayersToSmallIsland(server);

        // Show candy file selection to the initiating admin
        if (initiator != null && SubModeManager.getInstance().isAdmin(initiator)) {
            fileSelectionPhase = true; // Mark that we're in file selection phase
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
            // Close all open screens for all players
            try {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.closeContainer();
                }
                MySubMod.LOGGER.info("Closed all open screens for players");
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error closing player screens", e);
            }

            // Stop all timers
            try {
                stopSelectionTimer();
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error stopping selection timer", e);
            }

            try {
                if (gameTimer != null) {
                    gameTimer.stop();
                    gameTimer = null;
                }
                // Always send deactivation signal to all clients to clear any lingering timers
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new GameTimerPacket(-1)); // -1 means deactivate

                // Send empty candy counts to deactivate HUD
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new com.example.mysubmod.submodes.submode1.network.CandyCountUpdatePacket(new java.util.HashMap<>()));

                // Send empty file list to clear client-side storage (without opening screen)
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new com.example.mysubmod.submodes.submode1.network.CandyFileListPacket(new java.util.ArrayList<>(), false));
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
            fileSelectionPhase = false;
            gameEnding = false;
            islandsGenerated = false;
            playerIslandSelections.clear();
            alivePlayers.clear();
            spectatorPlayers.clear();
            disconnectedPlayers.clear(); // Clear disconnect tracking
            playerCandyCount.clear(); // Clear candy counts
            playerDeathTime.clear(); // Clear death times
            playersInSelectionPhase.clear(); // Clear selection phase participants
            gameStartTime = 0; // Reset game start time
            selectionStartTime = 0; // Reset selection start time
            selectedCandySpawnFile = null; // Clear selected file
            candySpawnConfig = null; // Clear spawn config
            gameInitiator = null; // Clear game initiator
            dataLogger = null; // Clear data logger reference
            holograms.clear(); // Clear hologram tracking list

            MySubMod.LOGGER.info("SubMode1 deactivation completed");
        }
    }

    /**
     * Remove all orphaned hologram armor stands in the world (for cleanup of untracked holograms)
     */
    public void cleanupOrphanedHolograms(net.minecraft.server.level.ServerLevel level) {
        int hologramsRemoved = 0;

        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            // Remove hologram armor stands (invisible, no base plate, custom name visible)
            if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand armorStand) {
                if (armorStand.isInvisible() && armorStand.isNoBasePlate() && armorStand.isCustomNameVisible()) {
                    armorStand.discard();
                    hologramsRemoved++;
                }
            }
        }

        if (hologramsRemoved > 0) {
            MySubMod.LOGGER.info("Cleaned up {} orphaned hologram armor stands", hologramsRemoved);
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
        // Add custom tag to identify SubMode1 holograms
        hologram1.addTag("SubMode1Hologram");
        level.addFreshEntity(hologram1);
        holograms.add(hologram1); // Track this hologram

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
        // Add custom tag to identify SubMode1 holograms
        hologram2.addTag("SubMode1Hologram");
        level.addFreshEntity(hologram2);
        holograms.add(hologram2); // Track this hologram
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

        // Calculate the actual path length and direction
        double pathLength = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double dirX = deltaX / pathLength;
        double dirZ = deltaZ / pathLength;

        // Perpendicular is 90 degrees rotation: (x,z) -> (-z, x)
        double perpX = -dirZ;
        double perpZ = dirX;

        int barrierStart = pathWidth/2 + 1; // Start right after path edge
        int barrierRows = 3; // 3 rows of barriers on each side

        // Iterate over every single block along the path
        int steps = (int) Math.ceil(pathLength);
        for (int i = 0; i <= steps; i++) {
            // Use floating point for smooth progression
            double t = (steps > 0) ? (double) i / steps : 0;
            int pathX = (int) Math.round(start.getX() + deltaX * t);
            int pathY = start.getY();
            int pathZ = (int) Math.round(start.getZ() + deltaZ * t);

            // Skip barriers if path point is on an island
            if (isPointOnAnyIsland(pathX, pathZ)) {
                continue;
            }

            // Place multiple rows of barriers on each side
            for (int row = 0; row < barrierRows; row++) {
                int offset = barrierStart + row;

                // Left side barriers
                int leftX = (int) Math.round(pathX + offset * perpX);
                int leftZ = (int) Math.round(pathZ + offset * perpZ);
                for (int y = 90; y <= pathY + 20; y++) {
                    level.setBlock(new BlockPos(leftX, y, leftZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }

                // Right side barriers
                int rightX = (int) Math.round(pathX - offset * perpX);
                int rightZ = (int) Math.round(pathZ - offset * perpZ);
                for (int y = 90; y <= pathY + 20; y++) {
                    level.setBlock(new BlockPos(rightX, y, rightZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
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

        // Remove holograms FIRST before clearing anything else
        removeHolograms(level);

        // Clear central square
        clearCentralSquare(level);

        // Clear islands first (slower)
        IslandGenerator.clearIsland(level, smallIslandCenter, IslandType.SMALL);
        IslandGenerator.clearIsland(level, mediumIslandCenter, IslandType.MEDIUM);
        IslandGenerator.clearIsland(level, largeIslandCenter, IslandType.LARGE);
        IslandGenerator.clearIsland(level, extraLargeIslandCenter, IslandType.EXTRA_LARGE);

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
        MySubMod.LOGGER.info("Clearing invisible walls (barriers)");

        // Clear barriers around central square
        clearCentralSquareBarriers(level);

        // Clear barriers around each island
        clearIslandBarriers(level, smallIslandCenter, IslandType.SMALL);
        clearIslandBarriers(level, mediumIslandCenter, IslandType.MEDIUM);
        clearIslandBarriers(level, largeIslandCenter, IslandType.LARGE);
        clearIslandBarriers(level, extraLargeIslandCenter, IslandType.EXTRA_LARGE);

        // Clear path barriers
        clearPathBarriers(level);
    }

    private void clearCentralSquareBarriers(ServerLevel level) {
        int halfSize = 10;
        int wallHeight = 20;

        // Clear barriers on all sides of central square
        for (int x = -halfSize; x <= halfSize; x++) {
            // North wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(centralSquare.getX() + x, y, centralSquare.getZ() - halfSize),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            // South wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(centralSquare.getX() + x, y, centralSquare.getZ() + halfSize),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }

        for (int z = -halfSize; z <= halfSize; z++) {
            // West wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(centralSquare.getX() - halfSize, y, centralSquare.getZ() + z),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            // East wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(centralSquare.getX() + halfSize, y, centralSquare.getZ() + z),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
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

        // Calculate the actual path length and direction
        double pathLength = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double dirX = deltaX / pathLength;
        double dirZ = deltaZ / pathLength;

        // Perpendicular is 90 degrees rotation: (x,z) -> (-z, x)
        double perpX = -dirZ;
        double perpZ = dirX;

        int barrierStart = pathWidth/2 + 1;
        int barrierRows = 3; // Match generation - 3 rows on each side

        // Iterate over every single block along the path
        int steps = (int) Math.ceil(pathLength);
        for (int i = 0; i <= steps; i++) {
            // Use floating point for smooth progression
            double t = (steps > 0) ? (double) i / steps : 0;
            int pathX = (int) Math.round(start.getX() + deltaX * t);
            int pathY = start.getY();
            int pathZ = (int) Math.round(start.getZ() + deltaZ * t);

            // Skip clearing if path point was on an island (no barriers were placed there)
            if (isPointOnAnyIsland(pathX, pathZ)) {
                continue;
            }

            // Clear multiple rows of barriers on each side
            for (int row = 0; row < barrierRows; row++) {
                int offset = barrierStart + row;

                // Clear left side barriers
                int leftX = (int) Math.round(pathX + offset * perpX);
                int leftZ = (int) Math.round(pathZ + offset * perpZ);
                for (int y = 90; y <= pathY + 20; y++) {
                    level.setBlock(new BlockPos(leftX, y, leftZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }

                // Clear right side barriers
                int rightX = (int) Math.round(pathX - offset * perpX);
                int rightZ = (int) Math.round(pathZ - offset * perpZ);
                for (int y = 90; y <= pathY + 20; y++) {
                    level.setBlock(new BlockPos(rightX, y, rightZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
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

        // Track all non-admin players participating (even if they disconnect later)
        playersInSelectionPhase.clear();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SubModeManager.getInstance().isAdmin(player)) {
                // Add to selection phase tracking
                playersInSelectionPhase.add(player.getUUID());

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

    public void startIslandSelection(MinecraftServer server) {
        fileSelectionPhase = false; // File selection is done
        selectionPhase = true;
        selectionStartTime = System.currentTimeMillis(); // Record when selection started

        // Initialize data logger NOW (when file is selected and game really starts)
        if (dataLogger == null) {
            dataLogger = new SubMode1DataLogger();
            dataLogger.startNewGame();
            MySubMod.LOGGER.info("Data logging started for game with file: {}", selectedCandySpawnFile);

            // Flush any pending log events that occurred before logger was created
            if (!pendingLogEvents.isEmpty()) {
                MySubMod.LOGGER.info("Flushing {} pending log events retroactively", pendingLogEvents.size());
                for (PendingLogEvent event : pendingLogEvents) {
                    dataLogger.logPlayerAction(event.player, event.action);
                }
                pendingLogEvents.clear();
            }
        }

        // Force load all island chunks and remove dandelion items once
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            forceLoadIslandChunksForSelection(overworld);
            // Wait a few ticks for chunks to load, then remove dandelions
            server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 5, () -> {
                removeDandelionItems(overworld);
            }));
        }

        // Add any newly connected non-admin players (don't clear - keep players from file selection phase)
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SubModeManager.getInstance().isAdmin(player)) {
                playersInSelectionPhase.add(player.getUUID());
            }
        }

        // Send island selection GUI to all non-admin players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SubModeManager.getInstance().isAdmin(player)) {
                storePlayerInventory(player);
                clearPlayerInventory(player);
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new IslandSelectionPacket(SELECTION_TIME_SECONDS));
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

        // Assign random islands to players who didn't select (including disconnected players)
        IslandType[] islands = IslandType.values();
        Random random = new Random();

        // Process ALL players who were in selection phase (connected or disconnected)
        for (UUID playerId : playersInSelectionPhase) {
            if (!playerIslandSelections.containsKey(playerId)) {
                IslandType randomIsland = islands[random.nextInt(islands.length)];
                playerIslandSelections.put(playerId, randomIsland);

                // Mark them as alive so they'll be properly handled on reconnection
                alivePlayers.add(playerId);

                // Notify if player is currently connected
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("§eÎle assignée automatiquement: " + randomIsland.getDisplayName()));
                } else {
                    MySubMod.LOGGER.info("Assigned random island {} to disconnected player {} and marked as alive",
                        randomIsland.name(), playerId);
                }

                // Log automatic island assignment
                if (dataLogger != null && player != null) {
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
        gameStartTime = System.currentTimeMillis(); // Record start time for leaderboard

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
        // Check if within square bounds of small island (60x60, so half-size = 30, add margin = 5)
        if (isWithinSquareBounds(pos, smallIslandCenter, 30 + 5)) {
            return true;
        }

        // Check if within square bounds of medium island (90x90, so half-size = 45, add margin = 5)
        if (isWithinSquareBounds(pos, mediumIslandCenter, 45 + 5)) {
            return true;
        }

        // Check if within square bounds of large island (120x120, so half-size = 60, add margin = 5)
        if (isWithinSquareBounds(pos, largeIslandCenter, 60 + 5)) {
            return true;
        }

        // Check if within square bounds of extra large island (150x150, so half-size = 75, add margin = 5)
        if (isWithinSquareBounds(pos, extraLargeIslandCenter, 75 + 5)) {
            return true;
        }

        // Check if within square bounds of central square (20x20, so half-size = 10, add margin = 5)
        if (isWithinSquareBounds(pos, centralSquare, 10 + 5)) {
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

    /**
     * Check if position is within square bounds (not circular)
     * This properly covers all corners of square islands
     */
    private boolean isWithinSquareBounds(BlockPos pos, BlockPos center, int halfSize) {
        if (center == null) return false;

        int distX = Math.abs(pos.getX() - center.getX());
        int distZ = Math.abs(pos.getZ() - center.getZ());

        return distX <= halfSize && distZ <= halfSize;
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

        // First, remove tracked holograms directly
        for (net.minecraft.world.entity.decoration.ArmorStand hologram : holograms) {
            if (hologram != null && hologram.isAlive()) {
                hologram.discard();
                hologramsRemoved++;
            }
        }
        holograms.clear(); // Clear the tracking list

        // Second, scan for any orphaned holograms with our tag (backup cleanup)
        // This catches any holograms that weren't in the tracking list
        int orphanedRemoved = 0;
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand armorStand) {
                if (armorStand.getTags().contains("SubMode1Hologram") && armorStand.isAlive()) {
                    armorStand.discard();
                    orphanedRemoved++;
                }
            }
        }

        if (hologramsRemoved > 0 || orphanedRemoved > 0) {
            MySubMod.LOGGER.info("Removed {} tracked + {} orphaned hologram armor stands from the world",
                hologramsRemoved, orphanedRemoved);
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

        // Display leaderboard
        displayLeaderboard(server);

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

        // Send file list without opening screen automatically (openScreen = false)
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new CandyFileListPacket(availableFiles, false));

        player.sendSystemMessage(Component.literal("§eAppuyez sur N pour ouvrir le menu de sélection de fichier"));
    }

    public void setCandySpawnFile(String filename) {
        this.selectedCandySpawnFile = filename;

        if (filename != null) {
            this.candySpawnConfig = CandySpawnFileManager.getInstance().loadSpawnConfig(filename);
            MySubMod.LOGGER.info("Selected candy spawn file: {} with {} entries", filename, candySpawnConfig.size());

            // Notify game initiator (but DON'T start island selection - caller will do that)
            if (gameInitiator != null) {
                gameInitiator.sendSystemMessage(Component.literal("§aFichier de spawn sélectionné: " + filename));
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

    /**
     * Handle player disconnection - track when and where they left
     */
    public void handlePlayerDisconnection(ServerPlayer player) {
        DisconnectInfo info = new DisconnectInfo(
            System.currentTimeMillis(),
            player.getX(),
            player.getY(),
            player.getZ()
        );
        disconnectedPlayers.put(player.getUUID(), info);
        MySubMod.LOGGER.info("Player {} disconnected during SubMode1 at ({}, {}, {}) - tracking disconnect time and position",
            player.getName().getString(), player.getX(), player.getY(), player.getZ());

        // Log disconnection (if logger exists, otherwise queue for later)
        if (dataLogger != null) {
            dataLogger.logPlayerAction(player, "DISCONNECTED");
        } else {
            // Queue event for retroactive logging when dataLogger is created
            pendingLogEvents.add(new PendingLogEvent(player, "DISCONNECTED"));
        }
    }

    /**
     * Check if player was disconnected during the game
     */
    public boolean wasPlayerDisconnected(UUID playerId) {
        return disconnectedPlayers.containsKey(playerId);
    }

    /**
     * Check if we're in file selection phase (admin choosing candy file)
     */
    public boolean isFileSelectionPhase() {
        return fileSelectionPhase;
    }

    /**
     * Get remaining selection time in seconds
     */
    public int getRemainingSelectionTime() {
        if (!selectionPhase || selectionStartTime == 0) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - selectionStartTime) / 1000;
        int remaining = (int) (SELECTION_TIME_SECONDS - elapsed);
        return Math.max(0, remaining);
    }

    /**
     * Handle player reconnection - restore their state and apply health penalty
     */
    public void handlePlayerReconnection(ServerPlayer player) {
        UUID playerId = player.getUUID();
        DisconnectInfo disconnectInfo = disconnectedPlayers.remove(playerId);

        if (disconnectInfo == null) {
            MySubMod.LOGGER.warn("No disconnect info found for reconnecting player {}", player.getName().getString());
            return;
        }

        long disconnectTime = disconnectInfo.disconnectTime;

        // Calculate time disconnected DURING THE ACTIVE GAME only
        long currentTime = System.currentTimeMillis();
        long effectiveDisconnectStart = disconnectTime;

        // If player disconnected before game started, only count time during active game
        if (gameStartTime > 0 && disconnectTime < gameStartTime) {
            effectiveDisconnectStart = gameStartTime;
        }

        // Calculate time disconnected during active game
        long timeDisconnectedDuringGame = currentTime - effectiveDisconnectStart;
        long secondsDisconnected = timeDisconnectedDuringGame / 1000;
        float healthLoss = (float) (secondsDisconnected / 10.0) * 1.0f;

        MySubMod.LOGGER.info("Player {} reconnected - {} seconds disconnected (during game), health loss: {}",
            player.getName().getString(), secondsDisconnected, healthLoss);

        // Restore player to game state
        if (alivePlayers.contains(playerId)) {
            // Player was alive when they disconnected

            // Get their island selection
            IslandType selectedIsland = playerIslandSelections.get(playerId);
            boolean islandAssignedDuringReconnection = false;

            // If player disconnected during selection and reconnects after game started without island
            if (selectedIsland == null && gameActive && playersInSelectionPhase.contains(playerId)) {
                // Assign random island to player who missed selection
                IslandType[] islands = IslandType.values();
                Random random = new Random();
                selectedIsland = islands[random.nextInt(islands.length)];
                playerIslandSelections.put(playerId, selectedIsland);
                islandAssignedDuringReconnection = true;

                MySubMod.LOGGER.info("Assigned random island {} to player {} who reconnected after selection phase",
                    selectedIsland.name(), player.getName().getString());

                player.sendSystemMessage(Component.literal("§eÎle assignée automatiquement (reconnexion tardive): " + selectedIsland.getDisplayName()));
            }

            if (selectedIsland != null && gameActive) {
                // Determine if player disconnected before game started
                boolean disconnectedBeforeGameStart = (gameStartTime > 0 && disconnectTime < gameStartTime);

                // Teleport logic
                MinecraftServer server = player.getServer();
                if (server != null) {
                    ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
                    if (overworld != null) {
                        if (disconnectedBeforeGameStart) {
                            // Player disconnected before game started - teleport to island center (they never got to their island)
                            BlockPos spawnPos = getIslandSpawnPosition(selectedIsland);
                            safeTeleport(player, overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
                            MySubMod.LOGGER.info("Teleported player {} to island center (disconnected before game started)",
                                player.getName().getString());
                        } else {
                            // Player disconnected during active game - teleport to exact position where they disconnected
                            safeTeleport(player, overworld, disconnectInfo.x, disconnectInfo.y, disconnectInfo.z);
                            MySubMod.LOGGER.info("Teleported player {} to exact disconnect position ({}, {}, {})",
                                player.getName().getString(), disconnectInfo.x, disconnectInfo.y, disconnectInfo.z);
                        }
                    }
                }

                // Ensure inventory is cleared (should already be done, but double-check for safety)
                if (!player.getInventory().isEmpty()) {
                    MySubMod.LOGGER.warn("Player {} reconnected with non-empty inventory - clearing it", player.getName().getString());
                    clearPlayerInventory(player);
                }

                // Reset health to 100% ONLY if player disconnected BEFORE game started
                // (Players who disconnect during active game keep their current health minus penalty)
                float currentHealth = player.getHealth();

                if (disconnectedBeforeGameStart) {
                    // Player was disconnected before game started - reset to 100% THEN apply penalty
                    player.setHealth(player.getMaxHealth());
                    player.getFoodData().setFoodLevel(10);
                    player.getFoodData().setSaturation(5.0f);
                    currentHealth = player.getMaxHealth(); // Update to new health value
                    MySubMod.LOGGER.info("Reset health to 100% for player {} (was disconnected before game start)", player.getName().getString());
                } else {
                    // Player disconnected during active game - keep current health
                    MySubMod.LOGGER.info("Player {} disconnected during active game - keeping current health {}",
                        player.getName().getString(), currentHealth);
                }

                // Log island selection (either assigned now or previously while disconnected)
                if (dataLogger != null) {
                    dataLogger.logIslandSelection(player, selectedIsland);
                    if (islandAssignedDuringReconnection) {
                        MySubMod.LOGGER.info("Logged island selection for player {} (assigned during reconnection)", player.getName().getString());
                    } else {
                        MySubMod.LOGGER.info("Logged island selection for player {} (was disconnected during selection phase)", player.getName().getString());
                    }
                }

                // ALWAYS apply health penalty for time disconnected (whether before or during game)
                float newHealth = Math.max(0.5f, currentHealth - healthLoss); // Minimum 0.5 HP to avoid instant death
                player.setHealth(newHealth);

                player.sendSystemMessage(Component.literal(String.format(
                    "§eVous avez été reconnecté. Perte de santé: %.1f cœurs (%.0f secondes déconnecté)",
                    healthLoss / 2.0f, (float)secondsDisconnected)));

                MySubMod.LOGGER.info("Player {} health reduced by {} (from {} to {})",
                    player.getName().getString(), healthLoss, currentHealth, newHealth);

                // Log reconnection (if logger exists, otherwise queue for later)
                if (dataLogger != null) {
                    dataLogger.logPlayerAction(player, String.format("RECONNECTED (-%d seconds, -%.1f HP)",
                        secondsDisconnected, healthLoss));
                } else {
                    // Queue event for retroactive logging when dataLogger is created
                    pendingLogEvents.add(new PendingLogEvent(player, String.format("RECONNECTED (-%d seconds, -%.1f HP)",
                        secondsDisconnected, healthLoss)));
                }

                // Check if health penalty killed the player
                if (newHealth <= 0.5f) {
                    player.sendSystemMessage(Component.literal("§cVous êtes mort pendant votre déconnexion !"));
                    SubMode1HealthManager.getInstance().stopHealthDegradation();
                    teleportToSpectator(player);
                }
            } else if (!gameActive && fileSelectionPhase) {
                // Reconnect to central square during file selection phase (admin choosing candy file)
                MinecraftServer server = player.getServer();
                if (server != null) {
                    ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
                    if (overworld != null) {
                        safeTeleport(player, overworld,
                            centralSquare.getX() + 0.5,
                            centralSquare.getY() + 1,
                            centralSquare.getZ() + 0.5);
                    }
                }
                player.sendSystemMessage(Component.literal("§eVous avez été reconnecté. En attente de la sélection de fichier par l'admin..."));

                // Log reconnection (queue for later since dataLogger doesn't exist yet)
                pendingLogEvents.add(new PendingLogEvent(player, "RECONNECTED (file selection phase)"));

            } else if (!gameActive && selectionPhase) {
                // Reconnect to central square during island selection phase
                MinecraftServer server = player.getServer();
                if (server != null) {
                    ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
                    if (overworld != null) {
                        safeTeleport(player, overworld,
                            centralSquare.getX() + 0.5,
                            centralSquare.getY() + 1,
                            centralSquare.getZ() + 0.5);
                    }
                }

                // Check if player hasn't selected an island yet
                if (!playerIslandSelections.containsKey(playerId)) {
                    // Send island selection screen with remaining time
                    int remainingTime = getRemainingSelectionTime();
                    if (remainingTime > 0) {
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                            new IslandSelectionPacket(remainingTime));
                        player.sendSystemMessage(Component.literal("§eVous avez été reconnecté pendant la phase de sélection. Temps restant: " + remainingTime + "s"));
                    } else {
                        player.sendSystemMessage(Component.literal("§eVous avez été reconnecté. La phase de sélection se termine..."));
                    }
                } else {
                    player.sendSystemMessage(Component.literal("§eVous avez été reconnecté. Île déjà sélectionnée: " +
                        playerIslandSelections.get(playerId).getDisplayName()));
                }

                // Log reconnection (if logger exists, otherwise queue for later)
                if (dataLogger != null) {
                    dataLogger.logPlayerAction(player, "RECONNECTED (island selection phase)");
                } else {
                    pendingLogEvents.add(new PendingLogEvent(player, "RECONNECTED (island selection phase)"));
                }
            }
        } else if (spectatorPlayers.contains(playerId)) {
            // Player was spectator when they disconnected
            teleportToSpectator(player);
            player.sendSystemMessage(Component.literal("§eVous avez été reconnecté en mode spectateur"));
        }
    }

    /**
     * Increment candy count for a player
     */
    public void incrementCandyCount(UUID playerId, int amount) {
        playerCandyCount.put(playerId, playerCandyCount.getOrDefault(playerId, 0) + amount);
    }

    /**
     * Record when a player died for leaderboard
     */
    public void recordPlayerDeath(UUID playerId) {
        playerDeathTime.put(playerId, System.currentTimeMillis());
    }

    /**
     * Display leaderboard at end of game
     */
    private void displayLeaderboard(MinecraftServer server) {
        MySubMod.LOGGER.info("Displaying leaderboard");

        // Create leaderboard entries for all players who participated
        List<LeaderboardEntry> entries = new ArrayList<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();

            // Only include players who selected an island (participated in the game)
            if (!playerIslandSelections.containsKey(playerId)) {
                MySubMod.LOGGER.debug("Skipping player {} - did not participate (no island selection)", player.getName().getString());
                continue;
            }

            String playerName = player.getName().getString();
            boolean isAlive = alivePlayers.contains(playerId);
            int candyCount = playerCandyCount.getOrDefault(playerId, 0);
            long survivalTime = 0;

            if (!isAlive && playerDeathTime.containsKey(playerId)) {
                // Calculate survival time for dead players
                survivalTime = playerDeathTime.get(playerId) - gameStartTime;
            }

            entries.add(new LeaderboardEntry(playerName, isAlive, candyCount, survivalTime));
            MySubMod.LOGGER.debug("Added player {} to leaderboard - alive: {}, candies: {}, survival: {}ms",
                playerName, isAlive, candyCount, survivalTime);
        }

        // Sort leaderboard:
        // 1. Alive players first (by candy count descending)
        // 2. Dead players second (by survival time descending)
        entries.sort((e1, e2) -> {
            // Alive players always ranked higher than dead players
            if (e1.isAlive && !e2.isAlive) return -1;
            if (!e1.isAlive && e2.isAlive) return 1;

            // Both alive: sort by candy count (descending)
            if (e1.isAlive && e2.isAlive) {
                return Integer.compare(e2.candyCount, e1.candyCount);
            }

            // Both dead: sort by survival time (descending)
            return Long.compare(e2.survivalTimeMs, e1.survivalTimeMs);
        });

        // Display leaderboard to all players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§6§l========== CLASSEMENT FINAL =========="));
            player.sendSystemMessage(Component.literal(""));

            for (int i = 0; i < entries.size(); i++) {
                LeaderboardEntry entry = entries.get(i);
                int rank = i + 1;

                String rankColor;
                if (rank == 1) rankColor = "§6"; // Gold for 1st
                else if (rank == 2) rankColor = "§7"; // Silver for 2nd
                else if (rank == 3) rankColor = "§c"; // Bronze-ish for 3rd
                else rankColor = "§f"; // White for others

                String statusInfo;
                if (entry.isAlive) {
                    statusInfo = String.format("§a✓ Vivant §7- §e%d bonbons", entry.candyCount);
                } else {
                    long survivalSeconds = entry.survivalTimeMs / 1000;
                    long minutes = survivalSeconds / 60;
                    long seconds = survivalSeconds % 60;
                    statusInfo = String.format("§c✗ Mort §7- Survie: %dm%ds", minutes, seconds);
                }

                player.sendSystemMessage(Component.literal(
                    String.format("%s#%d §f- %s %s", rankColor, rank, entry.playerName, statusInfo)
                ));
            }

            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§6§l======================================="));
            player.sendSystemMessage(Component.literal(""));
        }

        // Log leaderboard to data logger
        if (dataLogger != null) {
            StringBuilder leaderboardLog = new StringBuilder("\n=== FINAL LEADERBOARD ===\n");
            for (int i = 0; i < entries.size(); i++) {
                LeaderboardEntry entry = entries.get(i);
                leaderboardLog.append(String.format("#%d - %s: %s\n",
                    i + 1,
                    entry.playerName,
                    entry.isAlive ?
                        String.format("Alive (%d candies)", entry.candyCount) :
                        String.format("Dead (survived %dms)", entry.survivalTimeMs)
                ));
            }
            try {
                java.io.File eventFile = new java.io.File(dataLogger.getGameSessionId() != null ?
                    new java.io.File(".", "mysubmod_data/submode1_game_" + dataLogger.getGameSessionId()) :
                    new java.io.File(".", "mysubmod_data"), "game_events.txt");
                java.io.FileWriter eventLogger = new java.io.FileWriter(eventFile, true);
                eventLogger.write(leaderboardLog.toString());
                eventLogger.close();
            } catch (java.io.IOException e) {
                MySubMod.LOGGER.error("Error logging leaderboard", e);
            }
        }
    }

    /**
     * Inner class to store log events that occur before dataLogger is created
     */
    private static class PendingLogEvent {
        final ServerPlayer player;
        final String action;
        final long timestamp;

        PendingLogEvent(ServerPlayer player, String action) {
            this.player = player;
            this.action = action;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Inner class to represent a leaderboard entry
     */
    private static class LeaderboardEntry {
        String playerName;
        boolean isAlive;
        int candyCount;
        long survivalTimeMs;

        LeaderboardEntry(String playerName, boolean isAlive, int candyCount, long survivalTimeMs) {
            this.playerName = playerName;
            this.isAlive = isAlive;
            this.candyCount = candyCount;
            this.survivalTimeMs = survivalTimeMs;
        }
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
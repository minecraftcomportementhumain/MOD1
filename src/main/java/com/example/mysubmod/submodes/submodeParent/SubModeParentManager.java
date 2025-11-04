package com.example.mysubmod.submodes.submodeParent;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.auth.AuthManager;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode1.network.SubMode1CandyCountUpdatePacket;
import com.example.mysubmod.submodes.submodeParent.data.CandySpawnEntry;
import com.example.mysubmod.submodes.submode1.data.SubMode1DataLogger;
import com.example.mysubmod.submodes.submodeParent.data.DataLogger;
import com.example.mysubmod.submodes.submodeParent.data.SpawnFileManager;
import com.example.mysubmod.submodes.submodeParent.network.FileListPacket;
import com.example.mysubmod.submodes.submodeParent.network.GameEndPacket;
import com.example.mysubmod.submodes.submodeParent.network.GameTimerPacket;
import com.example.mysubmod.submodes.submodeParent.network.IslandSelectionPacket;
import com.example.mysubmod.submodes.submodeParent.timer.GameTimer;
import com.example.mysubmod.submodes.submodeParent.islands.IslandGenerator;
import com.example.mysubmod.submodes.submodeParent.islands.IslandType;
import com.example.mysubmod.util.PlayerFilterUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SubModeParentManager {
    protected static SubModeParentManager instance;
    protected final Map<UUID, List<ItemStack>> storedInventories = new ConcurrentHashMap<>();
    protected final Map<UUID, IslandType> playerIslandSelections = new ConcurrentHashMap<>();
    protected final Map<UUID, Boolean> playerIslandManualSelection = new ConcurrentHashMap<>(); // Track if island was manually selected (true) or auto-assigned (false)
    protected final Set<UUID> playerIslandSelectionLogged = ConcurrentHashMap.newKeySet(); // Track which players had their island selection logged
    protected final Set<UUID> alivePlayers = ConcurrentHashMap.newKeySet();
    protected final Set<UUID> spectatorPlayers = ConcurrentHashMap.newKeySet();
    protected final Map<String, DisconnectInfo> disconnectedPlayers = new ConcurrentHashMap<>(); // Track disconnect time and position by account name
    protected final Map<UUID, Integer> playerCandyCount = new ConcurrentHashMap<>(); // Track candies collected
    protected final Map<UUID, Long> playerDeathTime = new ConcurrentHashMap<>(); // Track when players died
    protected final Map<UUID, String> playerNames = new ConcurrentHashMap<>(); // Track player names (for disconnected players in leaderboard)
    protected final Set<UUID> playersInSelectionPhase = ConcurrentHashMap.newKeySet(); // Players who were present during selection
    protected final List<net.minecraft.world.entity.decoration.ArmorStand> holograms = new ArrayList<>(); // Track hologram entities
    protected final List<PendingLogEvent> pendingLogEvents = new ArrayList<>(); // Events before dataLogger exists
    protected long gameStartTime; // Track game start time
    protected long selectionStartTime; // Track selection phase start time
    protected SpawnFileManager spawnFileManager = new SpawnFileManager();
    protected HealthManager healthManager = new HealthManager();
    protected CandyManager candyManager = new CandyManager();

    // Lock to prevent race conditions between UUID migration (handlePlayerReconnection) and server-side death (handleDisconnectedPlayerDeath)
    protected final Object reconnectionLock = new Object();

    public static SubModeParentManager getInstance() {
        if (instance == null) {
            instance = new SubModeParentManager();
        }

        return instance;
    }

    // Inner class to store disconnection info
    protected static class DisconnectInfo {
        UUID oldUUID; // Store old UUID so we can migrate data when someone else connects on same account
        long disconnectTime;
        double x, y, z;
        float healthAtDisconnect; // Store health at disconnect to track server-side deaths
        List<ItemStack> savedInventory; // Save inventory items
        boolean isDead; // Flag if player died server-side while disconnected

        DisconnectInfo(UUID oldUUID, long disconnectTime, double x, double y, double z, float health, List<ItemStack> inventory) {
            this.oldUUID = oldUUID;
            this.disconnectTime = disconnectTime;
            this.x = x;
            this.y = y;
            this.z = z;
            this.healthAtDisconnect = health;
            this.savedInventory = inventory;
            this.isDead = false; // Initially alive
        }
    }

    protected boolean gameActive = false;
    protected boolean selectionPhase = false;
    protected boolean fileSelectionPhase = false; // Admin is selecting candy file
    protected boolean gameEnding = false;
    protected boolean islandsGenerated = false;
    protected Timer selectionTimer;
    protected GameTimer gameTimer;
    protected DataLogger dataLogger;
    protected String selectedCandySpawnFile;
    protected List<? extends CandySpawnEntry> candySpawnConfig;
    protected ServerPlayer gameInitiator; // The admin who started the game

    // Island positions
    protected BlockPos smallIslandCenter;
    protected BlockPos mediumIslandCenter;
    protected BlockPos largeIslandCenter;
    protected BlockPos extraLargeIslandCenter;
    protected BlockPos centralSquare;
    protected BlockPos spectatorPlatform;

    protected static final int SELECTION_TIME_SECONDS = 30;
    protected static final int GAME_TIME_MINUTES = 15;

    public SubModeParentManager() {
    }


    /**
     * Check if a player should be excluded from SubMode1 (queue candidates OR unauthenticated protected/admin)
     */
    protected static boolean isRestrictedPlayer(ServerPlayer player) {
        String playerName = player.getName().getString();
        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();

        // Check if queue candidate (temporary name)
        if (parkingLobby.isTemporaryQueueName(playerName)) {
            return true;
        }

        // Check if unauthenticated protected/admin
        com.example.mysubmod.auth.AuthManager authManager = com.example.mysubmod.auth.AuthManager.getInstance();
        com.example.mysubmod.auth.AdminAuthManager adminAuthManager = com.example.mysubmod.auth.AdminAuthManager.getInstance();

        com.example.mysubmod.auth.AuthManager.AccountType accountType = authManager.getAccountType(playerName);
        boolean isProtectedOrAdmin = (accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER ||
                                      accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN);

        if (isProtectedOrAdmin) {
            // Check authentication
            if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN) {
                return !adminAuthManager.isAuthenticated(player);
            } else {
                return !authManager.isAuthenticated(player.getUUID());
            }
        }

        return false;
    }

    public void activate(MinecraftServer server) {
        activate(server, null);
    }

    public void activate(MinecraftServer server, ServerPlayer initiator) {
        MySubMod.LOGGER.info("Activating SubMode1");
        this.gameInitiator = initiator;

        // Send loading message to all players
        PlayerFilterUtil.getAuthenticatedPlayers(server).forEach(player -> {
            player.sendSystemMessage(Component.literal("§e§lChargement du sous-mode "+SubModeManager.getInstance().getCurrentMode().getNumberMode()+"..."));
        });

        // Clean up any leftover candies from previous games first
        try {
            candyManager.removeAllCandiesFromWorld(server);
            MySubMod.LOGGER.info("Cleaned up leftover candies from previous games");
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error cleaning up leftover candies", e);
        }

        // Ensure candy spawn directory exists

        spawnFileManager.ensureDirectoryExists();

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
            candyManager.removeAllCandiesFromWorld(server);
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
            String defaultFile = spawnFileManager.getDefaultFile();
            setCandySpawnFile(defaultFile);
            startIslandSelection(server);
        }
    }

    public void deactivate(MinecraftServer server) {
        MySubMod.LOGGER.info("Deactivating SubMode1");

        try {
            // Close all open screens for all players
            try {
                for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
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
                System.out.println(gameTimer);
                if (gameTimer != null) {
                    gameTimer.stop();
                    gameTimer = null;
                }
                // Always send deactivation signal to all clients to clear any lingering timers
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new GameTimerPacket(-1)); // -1 means deactivate

                // Send empty candy counts to deactivate HUD
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new SubMode1CandyCountUpdatePacket(new HashMap<>()));

                // Send empty file list to clear client-side storage (without opening screen)
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new FileListPacket(new ArrayList<>(), false));
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
                healthManager.stopHealthDegradation();
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error stopping health degradation", e);
            }

            // Stop candy spawning and remove all existing candies
            try {
                candyManager.stopCandySpawning();
                candyManager.removeAllCandiesFromWorld(server);
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
            playerIslandManualSelection.clear();
            playerIslandSelectionLogged.clear();
            alivePlayers.clear();
            spectatorPlayers.clear();
            disconnectedPlayers.clear(); // Clear disconnect tracking
            playerCandyCount.clear(); // Clear candy counts
            playerDeathTime.clear(); // Clear death times
            playerNames.clear(); // Clear player names tracking
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
    public void cleanupOrphanedHolograms(ServerLevel level) {
        int hologramsRemoved = 0;

        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            // Remove hologram armor stands (invisible, no base plate, custom name visible)
            if (entity instanceof ArmorStand armorStand) {
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


    protected void initializePositions() {
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

    protected void generateMap(ServerLevel level) {
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

    protected void generateCentralSquare(ServerLevel level) {
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

    protected void addDirectionalSigns(ServerLevel level) {
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

    protected void createHologram(ServerLevel level, BlockPos pos, String line1, String line2) {
        // Create armor stand for line 1
        ArmorStand hologram1 = new ArmorStand(
            EntityType.ARMOR_STAND,
            level
        );
        hologram1.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        hologram1.setInvisible(true);
        hologram1.setNoGravity(true);
        hologram1.setCustomNameVisible(true);
        hologram1.setCustomName(Component.literal(line1));
        hologram1.setInvulnerable(true);
        hologram1.setSilent(true);
        hologram1.setNoBasePlate(true);
        // Add custom tag to identify SubMode1 holograms
        hologram1.addTag("SubMode1Hologram");
        level.addFreshEntity(hologram1);
        holograms.add(hologram1); // Track this hologram

        // Create armor stand for line 2 (slightly below)
        ArmorStand hologram2 = new ArmorStand(
            EntityType.ARMOR_STAND,
            level
        );
        hologram2.setPos(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5);
        hologram2.setInvisible(true);
        hologram2.setNoGravity(true);
        hologram2.setCustomNameVisible(true);
        hologram2.setCustomName(Component.literal(line2));
        hologram2.setInvulnerable(true);
        hologram2.setSilent(true);
        hologram2.setNoBasePlate(true);
        // Add custom tag to identify SubMode1 holograms
        hologram2.addTag("SubMode2Hologram");
        level.addFreshEntity(hologram2);
        holograms.add(hologram2); // Track this hologram
    }


    protected void generatePaths(ServerLevel level) {
        MySubMod.LOGGER.info("Generating paths from central square to islands");

        // Path from central square to each island (360 blocks each)
        generatePath(level, centralSquare, smallIslandCenter, 3);      // North
        generatePath(level, centralSquare, mediumIslandCenter, 3);     // East
        generatePath(level, centralSquare, largeIslandCenter, 3);      // South
        generatePath(level, centralSquare, extraLargeIslandCenter, 3); // West
    }

    protected void generatePath(ServerLevel level, BlockPos start, BlockPos end, int width) {
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

    protected void generateInvisibleWalls(ServerLevel level) {
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

    protected void generateCentralSquareBarriers(ServerLevel level) {
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

    protected void generateIslandBarriers(ServerLevel level, BlockPos center, IslandType type) {
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

    protected boolean isPathConnectionPoint(BlockPos center, int x, int z) {
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

    protected void generatePathBarriers(ServerLevel level) {
        // Barriers along paths from central square to each island
        generatePathSideBarriers(level, centralSquare, smallIslandCenter, 3);
        generatePathSideBarriers(level, centralSquare, mediumIslandCenter, 3);
        generatePathSideBarriers(level, centralSquare, largeIslandCenter, 3);
        generatePathSideBarriers(level, centralSquare, extraLargeIslandCenter, 3);
    }

    protected void generatePathSideBarriers(ServerLevel level, BlockPos start, BlockPos end, int pathWidth) {
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

    protected boolean isPointOnAnyIsland(int x, int z) {
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

    protected void generatePathOpeningBarriers(ServerLevel level) {
        // Add barriers next to path openings to close gaps
        int wallHeight = 20;

        // For each island, add barriers on the sides of path openings
        generatePathOpeningBarriersForIsland(level, smallIslandCenter, IslandType.SMALL, wallHeight);
        generatePathOpeningBarriersForIsland(level, mediumIslandCenter, IslandType.MEDIUM, wallHeight);
        generatePathOpeningBarriersForIsland(level, largeIslandCenter, IslandType.LARGE, wallHeight);
        generatePathOpeningBarriersForIsland(level, extraLargeIslandCenter, IslandType.EXTRA_LARGE, wallHeight);
    }

    protected void generatePathOpeningBarriersForIsland(ServerLevel level, BlockPos center, IslandType type, int wallHeight) {
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

    protected void generateSpectatorPlatform(ServerLevel level) {
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

    protected void clearMap(ServerLevel level) {
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
            candyManager.removeAllCandiesFromWorld(level.getServer());
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

    protected void clearCentralSquare(ServerLevel level) {
        int halfSize = 10;
        // Clear a larger area to include signs
        for (int x = -halfSize - 5; x <= halfSize + 5; x++) {
            for (int z = -halfSize - 5; z <= halfSize + 5; z++) {
                for (int y = -5; y <= 10; y++) {
                    level.setBlock(centralSquare.offset(x, y, z),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    protected boolean doIslandsExistInWorld(ServerLevel level) {
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

    protected boolean checkIslandArea(ServerLevel level, BlockPos center) {
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

    protected void clearPaths(ServerLevel level) {
        // Clear paths from central square to each island
        clearPath(level, centralSquare, smallIslandCenter, 3);
        clearPath(level, centralSquare, mediumIslandCenter, 3);
        clearPath(level, centralSquare, largeIslandCenter, 3);
        clearPath(level, centralSquare, extraLargeIslandCenter, 3);
    }

    protected void clearPath(ServerLevel level, BlockPos start, BlockPos end, int width) {
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
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    protected void clearInvisibleWalls(ServerLevel level) {
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

    protected void clearCentralSquareBarriers(ServerLevel level) {
        int halfSize = 10;
        int wallHeight = 20;

        // Clear barriers on all sides of central square
        for (int x = -halfSize; x <= halfSize; x++) {
            // North wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(centralSquare.getX() + x, y, centralSquare.getZ() - halfSize),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            // South wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(centralSquare.getX() + x, y, centralSquare.getZ() + halfSize),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }

        for (int z = -halfSize; z <= halfSize; z++) {
            // West wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(centralSquare.getX() - halfSize, y, centralSquare.getZ() + z),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            // East wall
            for (int y = 90; y <= centralSquare.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(centralSquare.getX() + halfSize, y, centralSquare.getZ() + z),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    protected void clearIslandBarriers(ServerLevel level, BlockPos center, IslandType type) {
        int radius = type.getRadius() - 2; // Same tighter radius as generation
        int wallHeight = 20;

        // Clear barriers inside the island perimeter (same positions as generation)
        // North and South walls
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            // North wall
            int northZ = center.getZ() - radius;
            for (int y = 90; y <= center.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(x, y, northZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }

            // South wall
            int southZ = center.getZ() + radius;
            for (int y = 90; y <= center.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(x, y, southZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }

        // East and West walls
        for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
            // West wall
            int westX = center.getX() - radius;
            for (int y = 90; y <= center.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(westX, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }

            // East wall
            int eastX = center.getX() + radius;
            for (int y = 90; y <= center.getY() + wallHeight; y++) {
                level.setBlock(new BlockPos(eastX, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    protected void clearPathBarriers(ServerLevel level) {
        clearPathSideBarriers(level, centralSquare, smallIslandCenter, 3);
        clearPathSideBarriers(level, centralSquare, mediumIslandCenter, 3);
        clearPathSideBarriers(level, centralSquare, largeIslandCenter, 3);
        clearPathSideBarriers(level, centralSquare, extraLargeIslandCenter, 3);
    }

    protected void clearPathSideBarriers(ServerLevel level, BlockPos start, BlockPos end, int pathWidth) {
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

    protected void teleportAdminsToSpectator(MinecraftServer server) {
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
            if (com.example.mysubmod.submodes.SubModeManager.getInstance().isAdmin(player)) {
                teleportToSpectator(player);
            }
        }
    }

    protected void teleportAllPlayersToSmallIsland(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        // Track all non-admin players participating (even if they disconnect later)
        playersInSelectionPhase.clear();

        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
            if (!com.example.mysubmod.submodes.SubModeManager.getInstance().isAdmin(player)) {
                // Check if protected player is authenticated
                com.example.mysubmod.auth.AuthManager authManager = com.example.mysubmod.auth.AuthManager.getInstance();
                com.example.mysubmod.auth.AuthManager.AccountType accountType = authManager.getAccountType(player.getName().getString());
                boolean isProtectedPlayer = (accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER);
                boolean isAuthenticated = authManager.isAuthenticated(player.getUUID());

                if (isProtectedPlayer && !isAuthenticated) {
                    // Protected player not authenticated - send to spectator
                    MySubMod.LOGGER.info("Protected player {} not authenticated - sending to spectator", player.getName().getString());
                    teleportToSpectator(player);
                    player.sendSystemMessage(Component.literal("§c§lVous devez être authentifié pour participer au jeu\n\n§7Veuillez vous authentifier et rejoindre le serveur."));
                    continue;
                }

                // Add to selection phase tracking
                playersInSelectionPhase.add(player.getUUID());

                // Store and clear inventory
                storePlayerInventory(player);
                clearPlayerInventory(player);

                // Teleport to central square temporarily (they will choose their final island)
                BlockPos spawnPos = centralSquare;
                safeTeleport(player, overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
                alivePlayers.add(player.getUUID());
                playerNames.put(player.getUUID(), player.getName().getString());
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
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
            if (!SubModeManager.getInstance().isAdmin(player)) {
                playersInSelectionPhase.add(player.getUUID());
            }
        }

        // Send island selection GUI to all non-admin players
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
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

        // Skip temporary queue candidate accounts
        if (isRestrictedPlayer(player)) {
            return;
        }

        playerIslandSelections.put(player.getUUID(), island);
        playerIslandManualSelection.put(player.getUUID(), true); // Manual selection
        player.sendSystemMessage(Component.literal("§aVous avez sélectionné: " + island.getDisplayName()));

        // Log island selection
        if (dataLogger != null) {
            dataLogger.logIslandSelection(player, island);
            playerIslandSelectionLogged.add(player.getUUID());
        }
    }

    protected void endSelectionPhase(MinecraftServer server) {
        selectionPhase = false;

        // Assign random islands to players who didn't select (including disconnected players)
        IslandType[] islands = IslandType.values();
        Random random = new Random();

        // Process ALL players who were in selection phase (connected or disconnected)
        for (UUID playerId : playersInSelectionPhase) {
            if (!playerIslandSelections.containsKey(playerId)) {
                IslandType randomIsland = islands[random.nextInt(islands.length)];
                playerIslandSelections.put(playerId, randomIsland);
                playerIslandManualSelection.put(playerId, false); // Automatic assignment

                // Mark them as alive so they'll be properly handled on reconnection
                alivePlayers.add(playerId);

                // Notify if player is currently connected and store their name
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    playerNames.put(playerId, player.getName().getString());
                    player.sendSystemMessage(Component.literal("§eÎle assignée automatiquement: " + randomIsland.getDisplayName()));
                } else {
                    MySubMod.LOGGER.info("Assigned random island {} to disconnected player {} and marked as alive",
                        randomIsland.name(), playerId);
                }

                // Log automatic island assignment
                if (dataLogger != null && player != null) {
                    dataLogger.logIslandSelection(player, randomIsland, "AUTOMATIC");
                    playerIslandSelectionLogged.add(playerId);
                }
            }
        }

        // Teleport players to their islands
        teleportPlayersToIslands(server);

        // Start the game
        startGame(server);
    }

    protected void teleportPlayersToIslands(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        // Remove dandelions one more time before teleporting (they may have respawned during selection)
        removeDandelionItems(overworld);

        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
            if (SubModeManager.getInstance().isAdmin(player)) continue;

            // Check if protected player is authenticated
            AuthManager authManager = AuthManager.getInstance();
            AuthManager.AccountType accountType = authManager.getAccountType(player.getName().getString());
            boolean isProtectedPlayer = (accountType == AuthManager.AccountType.PROTECTED_PLAYER);
            boolean isAuthenticated = authManager.isAuthenticated(player.getUUID());

            if (isProtectedPlayer && !isAuthenticated) {
                // Protected player lost authentication - send to spectator
                MySubMod.LOGGER.warn("Protected player {} lost authentication - sending to spectator", player.getName().getString());
                teleportToSpectator(player);
                player.sendSystemMessage(Component.literal("§c§lAuthentification perdue\n\n§7Vous avez été placé en spectateur."));
                continue;
            }

            IslandType selectedIsland = playerIslandSelections.get(player.getUUID());
            if (selectedIsland != null) {
                BlockPos spawnPos = getIslandSpawnPosition(selectedIsland);
                safeTeleport(player, overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
                alivePlayers.add(player.getUUID());
                playerNames.put(player.getUUID(), player.getName().getString());
            }
        }
    }

    protected BlockPos getIslandSpawnPosition(IslandType island) {
        switch (island) {
            case SMALL: return smallIslandCenter;
            case MEDIUM: return mediumIslandCenter;
            case LARGE: return largeIslandCenter;
            case EXTRA_LARGE: return extraLargeIslandCenter;
            default: return centralSquare;
        }
    }

    protected void startGame(MinecraftServer server) {
        gameActive = true;
        gameStartTime = System.currentTimeMillis(); // Record start time for leaderboard

        // Final cleanup: Remove all candies one last time before starting
        try {
            candyManager.removeAllCandiesFromWorld(server);
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
        healthManager.startHealthDegradation(server);

        // Start candy spawning
        candyManager.startCandySpawning(server);

        broadcastMessage(server, "§aLa partie commence ! Survivez " + GAME_TIME_MINUTES + " minutes !");
    }


    protected void removeDandelionItems(ServerLevel level) {
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
    protected boolean isWithinSquareBounds(BlockPos pos, BlockPos center, int halfSize) {
        if (center == null) return false;

        int distX = Math.abs(pos.getX() - center.getX());
        int distZ = Math.abs(pos.getZ() - center.getZ());

        return distX <= halfSize && distZ <= halfSize;
    }

    protected boolean isOnPath(BlockPos pos, BlockPos start, BlockPos end, int pathWidth) {
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

    protected boolean isWithinRadius(BlockPos pos1, BlockPos pos2, int radius) {
        if (pos2 == null) return false;
        double distance = Math.sqrt(Math.pow(pos1.getX() - pos2.getX(), 2) +
                                  Math.pow(pos1.getZ() - pos2.getZ(), 2));
        return distance <= radius;
    }

    protected void forceLoadIslandChunksForSelection(ServerLevel level) {
        // Force load chunks for all islands to ensure dandelions can be detected
        forceLoadChunkAt(level, smallIslandCenter);
        forceLoadChunkAt(level, mediumIslandCenter);
        forceLoadChunkAt(level, largeIslandCenter);
        forceLoadChunkAt(level, extraLargeIslandCenter);
        forceLoadChunkAt(level, centralSquare);
        MySubMod.LOGGER.info("Force loaded island chunks for dandelion cleanup");
    }

    protected void forceLoadChunkAt(ServerLevel level, BlockPos pos) {
        if (pos != null) {
            level.getChunkAt(pos);
        }
    }

    protected void removeHolograms(ServerLevel level) {
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
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ArmorStand armorStand) {
                if (armorStand.getTags().contains("SubMode2Hologram") && armorStand.isAlive()) {
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

    protected void removeAllFlowersInArea(ServerLevel level, BlockPos center, int radius) {
        if (center == null) return;

        int dandylionsRemoved = 0;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = center.getY() - 5; y < center.getY() + 10; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);

                    // Remove ONLY dandelion blocks, not other flowers
                    if (state.is(net.minecraft.world.level.block.Blocks.DANDELION)) {
                        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
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

        MySubMod.LOGGER.info("Ending SubMode game");

        // Notify authenticated players that game has ended
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new GameEndPacket());
        }

        // Stop systems immediately to prevent further operations
        try {
            healthManager.stopHealthDegradation();
            candyManager.stopCandySpawning();
            candyManager.removeAllCandiesFromWorld(server);

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
        Timer delayTimer = new Timer(SubModeManager.getInstance().getCurrentMode().getDisplayName() +"-EndGame-Timer");
        delayTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    server.execute(() -> {
                        try {
                            // Change to waiting room first (this will call deactivate automatically)
                            SubModeManager.getInstance().changeSubMode(SubMode.WAITING_ROOM, null, server);
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

    protected void showCongratulations(MinecraftServer server) {
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
            player.sendSystemMessage(Component.literal("§6§l=== FÉLICITATIONS ==="));
            player.sendSystemMessage(Component.literal("§eMerci d'avoir participé à cette expérience !"));
            player.sendSystemMessage(Component.literal("§aRetour à la salle d'attente dans 5 secondes..."));
        }
    }

    protected void storePlayerInventory(ServerPlayer player) {
        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            inventory.add(player.getInventory().getItem(i).copy());
        }
        storedInventories.put(player.getUUID(), inventory);
    }

    protected void clearPlayerInventory(ServerPlayer player) {
        player.getInventory().clearContent();
    }

    protected void restoreAllInventories(MinecraftServer server) {
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
            restorePlayerInventory(player);
        }
    }

    protected void restorePlayerInventory(ServerPlayer player) {
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

    protected void safeTeleport(ServerPlayer player, ServerLevel level, double x, double y, double z) {
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
            x, y, z, 0.0f, 0.0f, Collections.emptySet(), 0));
    }

    protected void stopSelectionTimer() {
        if (selectionTimer != null) {
            selectionTimer.cancel();
            selectionTimer = null;
        }
    }

    protected void broadcastMessage(MinecraftServer server, String message) {
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    protected void resetAllPlayersHealth(MinecraftServer server) {
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
            // Reset health to maximum (20.0f = 10 hearts)
            player.setHealth(player.getMaxHealth());

            // Set hunger to 50% (10 out of 20)
            player.getFoodData().setFoodLevel(10);
            player.getFoodData().setSaturation(5.0f);
        }

        MySubMod.LOGGER.info("Reset health to 100% and hunger to 50% for all players at game start");
    }

    // Candy spawn file management
    protected void showCandyFileSelection(ServerPlayer player) {
        List<String> availableFiles = spawnFileManager.getAvailableFiles();

        if (availableFiles.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cAucun fichier de spawn de bonbons trouvé. Utilisation de la configuration par défaut."));
            setCandySpawnFile(null);
            startIslandSelection(player.getServer());
            return;
        }

        // Send file list without opening screen automatically (openScreen = false)
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new FileListPacket(availableFiles, false));

        player.sendSystemMessage(Component.literal("§eAppuyez sur N pour ouvrir le menu de sélection de fichier"));
    }

    public void setCandySpawnFile(String filename) {
        this.selectedCandySpawnFile = filename;

        if (filename != null) {
            this.candySpawnConfig = spawnFileManager.loadSpawnConfig(filename);
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

    public List<?> getCandySpawnConfig() {
        return candySpawnConfig != null ? new ArrayList<>(candySpawnConfig) : new ArrayList<>();
    }

    public String getSelectedCandySpawnFile() {
        return selectedCandySpawnFile;
    }

    public void sendCandyFileListToPlayer(ServerPlayer player) {
        List<String> availableFiles = spawnFileManager.getAvailableFiles();
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new FileListPacket(availableFiles));
    }

    /**
     * Handle player disconnection - track when and where they left
     */
    public void handlePlayerDisconnection(ServerPlayer player) {
        MySubMod.LOGGER.info("DEBUG: handlePlayerDisconnection called for {} (UUID: {})",
            player.getName().getString(), player.getUUID());

        // Skip temporary queue candidate accounts
        // NOTE: Don't use isRestrictedPlayer() here because authenticatedUsers may already be cleared during logout
        String playerName = player.getName().getString();
        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();

        if (parkingLobby.isTemporaryQueueName(playerName)) {
            MySubMod.LOGGER.info("DEBUG: Player {} is queue candidate, skipping disconnection tracking",
                playerName);
            return;
        }

        // IMPORTANT: Never overwrite existing DisconnectInfo
        // This is crucial for the queue system where multiple people share the same account:
        // - Indiv1 disconnects from game → DisconnectInfo saved with island position
        // - Indiv1 reconnects but doesn't authenticate → goes to parking lobby
        // - Indiv1 disconnects from parking lobby → we must NOT overwrite with parking lobby position
        // - Indiv2 gets queue place and authenticates → must restore Indiv1's island position
        if (disconnectedPlayers.containsKey(playerName)) {
            MySubMod.LOGGER.info("DEBUG: Player {} already has disconnect info - NOT overwriting (keeping original position for queue system)",
                playerName);
            return;
        }

        // Save player's inventory (copy all items)
        List<ItemStack> savedInventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                savedInventory.add(stack.copy()); // Make a copy to preserve the item
            } else {
                savedInventory.add(ItemStack.EMPTY);
            }
        }

        DisconnectInfo info = new DisconnectInfo(
            player.getUUID(),
            System.currentTimeMillis(),
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getHealth(),
            savedInventory
        );
        disconnectedPlayers.put(playerName, info);
        MySubMod.LOGGER.info("Player {} (UUID: {}) disconnected during SubMode at ({}, {}, {}) with {} HP - saved {} inventory items - tracking disconnect (disconnectedPlayers size now: {})",
            playerName, player.getUUID(), player.getX(), player.getY(), player.getZ(), player.getHealth(), savedInventory.stream().filter(s -> !s.isEmpty()).count(), disconnectedPlayers.size());

        // Log disconnection (if logger exists, otherwise queue for later)
        if (dataLogger != null) {
            dataLogger.logPlayerAction(player, "DISCONNECTED");
        } else {
            // Queue event for retroactive logging when dataLogger is created
            pendingLogEvents.add(new PendingLogEvent(player, "DISCONNECTED"));
        }
    }

    /**
     * Check if player was disconnected during the game (by account name)
     */
    public boolean wasPlayerDisconnected(String playerName) {
        boolean result = disconnectedPlayers.containsKey(playerName);
        MySubMod.LOGGER.info("DEBUG: wasPlayerDisconnected({}) = {} (disconnectedPlayers size: {})",
            playerName, result, disconnectedPlayers.size());
        return result;
    }

    /**
     * Clear disconnect info for a player (called when they reconnect to server)
     * This prevents a second person from being teleported to first person's position
     * if they authenticate on the same account
     */
    public void clearDisconnectInfo(String playerName) {
        DisconnectInfo removed = disconnectedPlayers.remove(playerName);
        if (removed != null) {
            MySubMod.LOGGER.info("Cleared disconnect info for player {} on server reconnection", playerName);
        }
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
        String playerName = player.getName().getString();
        UUID playerId = player.getUUID();

        MySubMod.LOGGER.info("DEBUG: handlePlayerReconnection called for {} (UUID: {})",
            playerName, playerId);

        // Skip temporary queue candidate accounts
        if (isRestrictedPlayer(player)) {
            MySubMod.LOGGER.info("DEBUG: Player {} is restricted, aborting reconnection",
                playerName);
            return;
        }

        // CRITICAL SECTION: Synchronize access to disconnectedPlayers and UUID migration
        // to prevent race condition with handleDisconnectedPlayerDeath() in Timer thread
        DisconnectInfo disconnectInfo;
        UUID oldUUID;
        UUID newUUID = player.getUUID();
        boolean playerIsDead = false;

        synchronized (reconnectionLock) {
            // Get disconnect info by account name (not UUID, so multiple people can share same account)
            disconnectInfo = disconnectedPlayers.remove(playerName);

            if (disconnectInfo == null) {
                MySubMod.LOGGER.warn("No disconnect info found for reconnecting player {} - disconnectedPlayers size: {}",
                    playerName, disconnectedPlayers.size());
                return;
            }

            // Check if player died server-side while disconnected
            playerIsDead = disconnectInfo.isDead;
        } // End synchronized block - release lock BEFORE teleporting

        // Handle dead player OUTSIDE synchronized block to avoid deadlock
        if (playerIsDead) {
            MySubMod.LOGGER.info("Player {} died server-side while disconnected - sending to spectator", playerName);
            teleportToSpectator(player);
            player.sendSystemMessage(Component.literal(
                "§c§lVous êtes mort pendant votre déconnexion\n\n" +
                "§7Vous avez été téléporté en zone spectateur."
            ));
            // No need to migrate UUID since they're already in spectatorPlayers
            return;
        }

        synchronized (reconnectionLock) {

            MySubMod.LOGGER.info("DEBUG: Found disconnect info for {}, proceeding with reconnection",
                player.getName().getString());

            // Migrate UUID-based data from old UUID to new UUID
            // This is necessary when multiple people share the same account (e.g., queue system)
            oldUUID = disconnectInfo.oldUUID;

            MySubMod.LOGGER.info("DEBUG: oldUUID from DisconnectInfo: {}", oldUUID);
            MySubMod.LOGGER.info("DEBUG: newUUID from player: {}", newUUID);
            MySubMod.LOGGER.info("DEBUG: Are they equal? {}", oldUUID.equals(newUUID));

            if (!oldUUID.equals(newUUID)) {
                MySubMod.LOGGER.info("Migrating player data from old UUID {} to new UUID {} for account {}",
                    oldUUID, newUUID, playerName);

                // Migrate alivePlayers
                MySubMod.LOGGER.info("DEBUG: alivePlayers before migration: {}", alivePlayers);
                MySubMod.LOGGER.info("DEBUG: Does alivePlayers contain oldUUID? {}", alivePlayers.contains(oldUUID));
                if (alivePlayers.remove(oldUUID)) {
                    alivePlayers.add(newUUID);
                    MySubMod.LOGGER.info("  - Migrated alivePlayers");
                } else {
                    MySubMod.LOGGER.warn("DEBUG: Failed to remove oldUUID from alivePlayers!");
                }
                MySubMod.LOGGER.info("DEBUG: alivePlayers after migration: {}", alivePlayers);

                // Migrate spectatorPlayers
                if (spectatorPlayers.remove(oldUUID)) {
                    spectatorPlayers.add(newUUID);
                    MySubMod.LOGGER.info("  - Migrated spectatorPlayers");
                }

                // Migrate playersInSelectionPhase
                if (playersInSelectionPhase.remove(oldUUID)) {
                    playersInSelectionPhase.add(newUUID);
                    MySubMod.LOGGER.info("  - Migrated playersInSelectionPhase");
                }

                // Migrate playerIslandSelections
                if (playerIslandSelections.containsKey(oldUUID)) {
                    IslandType island = playerIslandSelections.remove(oldUUID);
                    playerIslandSelections.put(newUUID, island);
                    MySubMod.LOGGER.info("  - Migrated playerIslandSelections: {}", island);
                }

                // Migrate playerIslandManualSelection
                if (playerIslandManualSelection.containsKey(oldUUID)) {
                    Boolean manual = playerIslandManualSelection.remove(oldUUID);
                    playerIslandManualSelection.put(newUUID, manual);
                    MySubMod.LOGGER.info("  - Migrated playerIslandManualSelection");
                }

                // Migrate playerCandyCount
                if (playerCandyCount.containsKey(oldUUID)) {
                    Integer candies = playerCandyCount.remove(oldUUID);
                    playerCandyCount.put(newUUID, candies);
                    MySubMod.LOGGER.info("  - Migrated playerCandyCount: {}", candies);
                }

                // Migrate playerNames (use current player name for new UUID)
                if (playerNames.containsKey(oldUUID)) {
                    playerNames.remove(oldUUID);
                }
                playerNames.put(newUUID, player.getName().getString());
                MySubMod.LOGGER.info("  - Migrated/updated playerNames");

                // Migrate playerIslandSelectionLogged
                if (playerIslandSelectionLogged.remove(oldUUID)) {
                    playerIslandSelectionLogged.add(newUUID);
                    MySubMod.LOGGER.info("  - Migrated playerIslandSelectionLogged");
                }

                MySubMod.LOGGER.info("UUID migration complete for account {}", playerName);
            } else {
                MySubMod.LOGGER.info("No UUID migration needed - same UUID reconnecting");
            }
        } // End synchronized block

        long disconnectTime = disconnectInfo.disconnectTime;

        // Calculate health loss based on how many health degradation ticks occurred during disconnection
        // Health ticks occur at fixed intervals: gameStartTime + 10s, +20s, +30s, etc.
        long currentTime = System.currentTimeMillis();

        // Only calculate if game is active and player disconnected during/after game start
        int missedHealthTicks = 0;
        float healthLoss = 0.0f;

        if (gameActive && gameStartTime > 0) {
            long effectiveDisconnectTime = Math.max(disconnectTime, gameStartTime);

            // Calculate which health tick number we're at for disconnect and reconnect times
            // Tick 0 happens at gameStartTime, tick 1 at gameStartTime+10s, etc.
            long msFromGameStartAtDisconnect = effectiveDisconnectTime - gameStartTime;
            long msFromGameStartAtReconnect = currentTime - gameStartTime;

            // Calculate tick numbers (which 10-second interval)
            long tickNumberAtDisconnect = msFromGameStartAtDisconnect / 10000;
            long tickNumberAtReconnect = msFromGameStartAtReconnect / 10000;

            // Missed ticks = ticks that occurred between disconnect and reconnect
            missedHealthTicks = (int) (tickNumberAtReconnect - tickNumberAtDisconnect);

            if (missedHealthTicks > 0) {
                healthLoss = (float) missedHealthTicks * 1.0f; // 1 HP per tick
            }
        }

        MySubMod.LOGGER.info("Player {} reconnected - missed {} health ticks, health loss: {} HP",
            player.getName().getString(), missedHealthTicks, healthLoss);

        // Check if protected player is authenticated before allowing reconnection to game
        com.example.mysubmod.auth.AuthManager authManager = com.example.mysubmod.auth.AuthManager.getInstance();
        com.example.mysubmod.auth.AuthManager.AccountType accountType = authManager.getAccountType(player.getName().getString());
        boolean isProtectedPlayer = (accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER);
        boolean isAuthenticated = authManager.isAuthenticated(player.getUUID());

        if (isProtectedPlayer && !isAuthenticated) {
            // Protected player not authenticated - send to spectator
            MySubMod.LOGGER.warn("Protected player {} attempting to reconnect without authentication - sending to spectator", player.getName().getString());
            teleportToSpectator(player);
            player.sendSystemMessage(Component.literal("§c§lVous devez être authentifié pour participer au jeu\n\n§7Veuillez vous authentifier et rejoindre le serveur."));
            // Remove from alivePlayers if they were in it
            alivePlayers.remove(playerId);
            return;
        }

        // Handle reconnection during file selection phase (BEFORE checking alivePlayers)
        if (!gameActive && fileSelectionPhase) {
            // Add player to selection phase participants so they're not treated as spectator
            playersInSelectionPhase.add(playerId);

            // Restore to disconnect position
            MinecraftServer server = player.getServer();
            if (server != null) {
                ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
                if (overworld != null) {
                    safeTeleport(player, overworld, disconnectInfo.x, disconnectInfo.y, disconnectInfo.z);
                    MySubMod.LOGGER.info("Teleported player {} to disconnect position during file selection phase", player.getName().getString());
                }
            }
            player.sendSystemMessage(Component.literal("§eVous avez été reconnecté. En attente de la sélection de fichier par l'admin..."));

            // Log reconnection (queue for later since dataLogger doesn't exist yet)
            pendingLogEvents.add(new PendingLogEvent(player, "RECONNECTED (file selection phase)"));
            return;
        }

        // Handle reconnection during island selection phase (BEFORE checking alivePlayers)
        if (!gameActive && selectionPhase) {
            // Restore to disconnect position
            MinecraftServer server = player.getServer();
            if (server != null) {
                ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
                if (overworld != null) {
                    safeTeleport(player, overworld, disconnectInfo.x, disconnectInfo.y, disconnectInfo.z);
                    MySubMod.LOGGER.info("Teleported player {} to disconnect position during island selection phase", player.getName().getString());
                }
            }

            // Check if player hasn't selected an island yet
            if (!playerIslandSelections.containsKey(playerId)) {
                // Send island selection screen with remaining time
                int remainingTime = getRemainingSelectionTime();
                if (remainingTime > 0) {
                    // Schedule packet send after 10 ticks (0.5 seconds) to ensure client is ready
                    final int timeToSend = remainingTime;
                    Timer timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            MinecraftServer server = player.getServer();
                            if (server != null && player.isAlive()) {
                                server.execute(() -> {
                                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                                        new IslandSelectionPacket(timeToSend));
                                    MySubMod.LOGGER.info("Sent island selection packet to reconnected player {} with {} seconds remaining",
                                        player.getName().getString(), timeToSend);
                                });
                            }
                        }
                    }, 500); // 500ms delay
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
            return;
        }

        // Handle reconnection when game started while player was in selection phase
        // BUT only if player was NOT in active game (if in alivePlayers, handle below with health penalty)
        if (gameActive && playersInSelectionPhase.contains(playerId) && !alivePlayers.contains(playerId)) {
            // Check if player disconnected BEFORE island selection phase started
            // If they disconnected during file selection phase, they should be spectator
            if (selectionStartTime > 0 && disconnectTime < selectionStartTime) {
                MySubMod.LOGGER.info("Player {} disconnected during file selection phase (before island selection started) - sending to spectator", player.getName().getString());
                spectatorPlayers.add(playerId);
                teleportToSpectator(player);
                player.sendSystemMessage(Component.literal("§cVous vous êtes déconnecté avant la phase de sélection des îles.\n§7Vous êtes maintenant spectateur."));

                // Log reconnection as spectator
                if (dataLogger != null) {
                    dataLogger.logPlayerAction(player, "RECONNECTED (spectator - missed island selection)");
                }
                return;
            }

            // Player was in island selection phase when they disconnected, but game started while they were offline
            MySubMod.LOGGER.info("Player {} was in island selection phase but game started while offline - adding to game", player.getName().getString());

            alivePlayers.add(playerId);
            playerNames.put(playerId, player.getName().getString());

            // Check if they have an island selected
            IslandType selectedIsland = playerIslandSelections.get(playerId);
            if (selectedIsland == null) {
                // Assign random island
                IslandType[] islands = IslandType.values();
                Random random = new Random();
                selectedIsland = islands[random.nextInt(islands.length)];
                playerIslandSelections.put(playerId, selectedIsland);
                playerIslandManualSelection.put(playerId, false);
                MySubMod.LOGGER.info("Assigned random island {} to player {}", selectedIsland.name(), player.getName().getString());
            }

            // Teleport to disconnect position (should be central square)
            MinecraftServer server = player.getServer();
            if (server != null) {
                ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
                if (overworld != null) {
                    safeTeleport(player, overworld, disconnectInfo.x, disconnectInfo.y, disconnectInfo.z);
                }
            }

            player.sendSystemMessage(Component.literal(String.format(
                "§eVous avez été reconnecté. Le jeu a démarré pendant votre absence.\n§7Île assignée: %s",
                selectedIsland.getDisplayName())));

            // Log reconnection
            if (dataLogger != null) {
                dataLogger.logPlayerAction(player, "RECONNECTED (missed game start)");
            }
            return;
        }

        // Restore player to game state (during active game)
        MySubMod.LOGGER.info("DEBUG: Checking if player {} is in alivePlayers - result: {}", playerId, alivePlayers.contains(playerId));
        MySubMod.LOGGER.info("DEBUG: alivePlayers content: {}", alivePlayers);
        MySubMod.LOGGER.info("DEBUG: gameActive: {}, selectionPhase: {}, fileSelectionPhase: {}", gameActive, selectionPhase, fileSelectionPhase);

        if (alivePlayers.contains(playerId)) {
            MySubMod.LOGGER.info("DEBUG: Player {} is in alivePlayers, proceeding with game state restoration", playerId);
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
                playerIslandManualSelection.put(playerId, false); // Automatic assignment
                islandAssignedDuringReconnection = true;

                MySubMod.LOGGER.info("Assigned random island {} to player {} who reconnected after selection phase",
                    selectedIsland.name(), player.getName().getString());

                player.sendSystemMessage(Component.literal("§eÎle assignée automatiquement (reconnexion tardive): " + selectedIsland.getDisplayName()));
            }

            if (selectedIsland != null && gameActive) {
                // Determine if player disconnected before game started
                boolean disconnectedBeforeGameStart = (gameStartTime > 0 && disconnectTime < gameStartTime);
                MySubMod.LOGGER.info("DEBUG: selectedIsland={}, gameActive={}, disconnectedBeforeGameStart={}",
                    selectedIsland, gameActive, disconnectedBeforeGameStart);
                MySubMod.LOGGER.info("DEBUG: gameStartTime={}, disconnectTime={}", gameStartTime, disconnectTime);

                // Teleport logic
                MinecraftServer server = player.getServer();
                if (server != null) {
                    ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
                    if (overworld != null) {
                        if (disconnectedBeforeGameStart) {
                            // Player disconnected before game started - teleport to island center (they never got to their island)
                            BlockPos spawnPos = getIslandSpawnPosition(selectedIsland);
                            MySubMod.LOGGER.info("DEBUG: Teleporting to island center: ({}, {}, {})",
                                spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
                            safeTeleport(player, overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
                            MySubMod.LOGGER.info("Teleported player {} to island center (disconnected before game started)",
                                player.getName().getString());
                        } else {
                            // Player disconnected during active game - teleport to exact position where they disconnected
                            MySubMod.LOGGER.info("DEBUG: Teleporting to disconnect position: ({}, {}, {})",
                                disconnectInfo.x, disconnectInfo.y, disconnectInfo.z);
                            safeTeleport(player, overworld, disconnectInfo.x, disconnectInfo.y, disconnectInfo.z);
                            MySubMod.LOGGER.info("Teleported player {} to exact disconnect position ({}, {}, {})",
                                player.getName().getString(), disconnectInfo.x, disconnectInfo.y, disconnectInfo.z);
                        }
                    } else {
                        MySubMod.LOGGER.error("DEBUG: overworld is null!");
                    }
                } else {
                    MySubMod.LOGGER.error("DEBUG: server is null!");
                }

                // Restore saved inventory from disconnect
                if (disconnectInfo.savedInventory != null && !disconnectInfo.savedInventory.isEmpty()) {
                    player.getInventory().clearContent(); // Clear current inventory first
                    for (int i = 0; i < Math.min(disconnectInfo.savedInventory.size(), player.getInventory().getContainerSize()); i++) {
                        player.getInventory().setItem(i, disconnectInfo.savedInventory.get(i).copy());
                    }
                    int restoredItems = (int) disconnectInfo.savedInventory.stream().filter(s -> !s.isEmpty()).count();
                    MySubMod.LOGGER.info("Restored {} inventory items for reconnected player {}", restoredItems, player.getName().getString());
                } else {
                    // No saved inventory, ensure it's cleared
                    if (!player.getInventory().isEmpty()) {
                        MySubMod.LOGGER.warn("Player {} reconnected with no saved inventory but non-empty current inventory - clearing it", player.getName().getString());
                        clearPlayerInventory(player);
                    }
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

                // Log island selection if:
                // 1. Island was assigned during reconnection (missed selection phase)
                // 2. Player disconnected before game start (never teleported to island before) AND not already logged
                if (dataLogger != null && !playerIslandSelectionLogged.contains(playerId)) {
                    if (islandAssignedDuringReconnection) {
                        dataLogger.logIslandSelection(player, selectedIsland, "AUTOMATIC");
                        playerIslandSelectionLogged.add(playerId);
                        MySubMod.LOGGER.info("Logged island selection for player {} (assigned during reconnection)", player.getName().getString());
                    } else if (disconnectedBeforeGameStart) {
                        // Check if island was manually selected or auto-assigned
                        Boolean wasManualSelection = playerIslandManualSelection.get(playerId);
                        String selectionType = (wasManualSelection != null && wasManualSelection) ? "MANUAL" : "AUTOMATIC";
                        dataLogger.logIslandSelection(player, selectedIsland, selectionType);
                        playerIslandSelectionLogged.add(playerId);
                        MySubMod.LOGGER.info("Logged island selection for player {} (first teleport to island after reconnection, type: {})",
                            player.getName().getString(), selectionType);
                    }
                }

                // ALWAYS apply health penalty for time disconnected (whether before or during game)
                float newHealth = Math.max(0.5f, currentHealth - healthLoss); // Minimum 0.5 HP to avoid instant death
                player.setHealth(newHealth);

                player.sendSystemMessage(Component.literal(String.format(
                    "§eVous avez été reconnecté. Perte de santé: %.1f cœurs (%d ticks de dégradation manqués)",
                    healthLoss / 2.0f, missedHealthTicks)));

                MySubMod.LOGGER.info("Player {} health reduced by {} (from {} to {})",
                    player.getName().getString(), healthLoss, currentHealth, newHealth);

                // Log reconnection (if logger exists, otherwise queue for later)
                if (dataLogger != null) {
                    dataLogger.logPlayerAction(player, String.format("RECONNECTED (-%d ticks, -%.1f HP)",
                        missedHealthTicks, healthLoss));
                } else {
                    // Queue event for retroactive logging when dataLogger is created
                    pendingLogEvents.add(new PendingLogEvent(player, String.format("RECONNECTED (-%d ticks, -%.1f HP)",
                        missedHealthTicks, healthLoss)));
                }

                // Check if health penalty killed the player
                if (newHealth <= 0.5f) {
                    player.sendSystemMessage(Component.literal("§cVous êtes mort pendant votre déconnexion !"));

                    // Record death time for leaderboard
                    recordPlayerDeath(playerId);

                    healthManager.stopHealthDegradation();
                    teleportToSpectator(player);

                    // Broadcast death message
                    String deathMessage = "§e" + player.getName().getString() + " §cest mort pendant sa déconnexion !";
                    MinecraftServer gameServer = player.getServer();
                    if (gameServer != null) {
                        for (ServerPlayer p : PlayerFilterUtil.getAuthenticatedPlayers(gameServer)) {
                            p.sendSystemMessage(Component.literal(deathMessage));
                        }

                        // Check if all players are dead (no alive players left)
                        if (getAlivePlayers().isEmpty()) {
                            MySubMod.LOGGER.info("All players are dead after reconnection death - ending game");
                            gameServer.execute(() -> {
                                for (ServerPlayer p : PlayerFilterUtil.getAuthenticatedPlayers(gameServer)) {
                                    p.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                                }
                                endGame(gameServer);
                            });
                        }
                    }
                }
            }
        } else if (spectatorPlayers.contains(playerId)) {
            MySubMod.LOGGER.info("DEBUG: Player {} is in spectatorPlayers, teleporting to spectator", playerId);
            // Player was spectator when they disconnected
            teleportToSpectator(player);
            player.sendSystemMessage(Component.literal("§eVous avez été reconnecté en mode spectateur"));
        } else {
            MySubMod.LOGGER.warn("DEBUG: Player {} is neither in alivePlayers nor spectatorPlayers after migration!", playerId);
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
    protected void displayLeaderboard(MinecraftServer server) {
        MySubMod.LOGGER.info("Displaying leaderboard");

        // Create leaderboard entries for all players who participated
        List<LeaderboardEntry> entries = new ArrayList<>();

        // Iterate over ALL players who selected an island (participated), not just connected ones
        for (Map.Entry<UUID, IslandType> entry : playerIslandSelections.entrySet()) {
            UUID playerId = entry.getKey();

            // Get player name - try to find online player first, then use stored name
            String playerName = null;
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
            if (onlinePlayer != null) {
                playerName = onlinePlayer.getName().getString();
            } else if (playerNames.containsKey(playerId)) {
                playerName = playerNames.get(playerId);
            } else {
                // Fallback: try to get from GameProfile
                com.mojang.authlib.GameProfile profile = server.getProfileCache().get(playerId).orElse(null);
                if (profile != null) {
                    playerName = profile.getName();
                } else {
                    MySubMod.LOGGER.warn("Could not find name for player UUID {} - skipping from leaderboard", playerId);
                    continue;
                }
            }

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
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
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
                String fileNumber ="";
                fileNumber = SubModeManager.getInstance().getCurrentMode().getDisplayName().substring(10,
                        SubModeManager.getInstance().getCurrentMode().getDisplayName().length());
                java.io.File eventFile = new java.io.File(dataLogger.getGameSessionId() != null ?
                    new java.io.File(".", "mysubmod_data/submode"+fileNumber+"_game_" + dataLogger.getGameSessionId()) :
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
    protected static class PendingLogEvent {
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
    protected static class LeaderboardEntry {
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
    public long getGameStartTime() { return gameStartTime; }

    public void addPlayerToSelectionPhase(ServerPlayer player) {
        playersInSelectionPhase.add(player.getUUID());

        // Teleport to central square
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
    }
    public boolean isSelectionPhase() { return selectionPhase; }
    public boolean isPlayerAlive(UUID playerId) { return alivePlayers.contains(playerId); }
    public boolean isPlayerSpectator(UUID playerId) { return spectatorPlayers.contains(playerId); }
    public boolean isInSelectionPhase(UUID playerId) { return playersInSelectionPhase.contains(playerId); }
    public Set<UUID> getAlivePlayers() { return new HashSet<>(alivePlayers); }
    public DataLogger getDataLogger() { return dataLogger; }

    /**
     * Get disconnected players info for server-side health tracking
     * Returns a map of player names to their disconnect info
     */
    public Map<String, DisconnectInfo> getDisconnectedPlayersInfo() {
        // Synchronize to prevent reading during UUID migration
        synchronized (reconnectionLock) {
            return new HashMap<>(disconnectedPlayers);
        }
    }

    /**
     * Handle death of a disconnected player (server-side tracking)
     * Called when a disconnected player's health reaches 0 based on time elapsed
     */
    public void handleDisconnectedPlayerDeath(String playerName, UUID playerId) {
        // CRITICAL SECTION: Synchronize with handlePlayerReconnection() to prevent race condition
        // during UUID migration
        synchronized (reconnectionLock) {
            // Double-check: If player reconnected between health check and now, DisconnectInfo will be gone
            if (!disconnectedPlayers.containsKey(playerName)) {
                MySubMod.LOGGER.info("Player {} reconnected before death could be processed - skipping death", playerName);
                return;
            }

            // Verify the UUID matches what's in DisconnectInfo (in case migration happened)
            DisconnectInfo info = disconnectedPlayers.get(playerName);
            if (!info.oldUUID.equals(playerId)) {
                MySubMod.LOGGER.info("Player {} UUID changed (reconnection) before death - skipping death", playerName);
                return;
            }

            if (!alivePlayers.contains(playerId)) {
                return; // Already dead or spectator
            }

            MySubMod.LOGGER.info("Disconnected player {} died server-side due to health degradation", playerName);

            // Remove from alive players
            alivePlayers.remove(playerId);

            // Add to spectators
            spectatorPlayers.add(playerId);

            // Record death time for leaderboard
            recordPlayerDeath(playerId);

            // Mark as dead in DisconnectInfo instead of removing it
            // This way if someone reconnects on this account, they'll be sent to spectator
            info.isDead = true;
            // Clear inventory since player is dead
            info.savedInventory = new ArrayList<>();
            MySubMod.LOGGER.info("Marked DisconnectInfo as dead for {} - reconnection will send to spectator", playerName);
        }
    }

    public void setSpawnFileManager(SpawnFileManager newSpawnFileManager){
        this.spawnFileManager = newSpawnFileManager;
    }

    public void setHealthManager(HealthManager newHealthManager){
        this.healthManager = newHealthManager;
    }

    public void setCandyManager(CandyManager newCandyManager){
        this.candyManager = newCandyManager;
    }

    protected static void setInstance(SubModeParentManager newInstance) {
        instance = newInstance;
    }

    public BlockPos getSmallIslandCenter() { return smallIslandCenter; }
    public BlockPos getMediumIslandCenter() { return mediumIslandCenter; }
    public BlockPos getLargeIslandCenter() { return largeIslandCenter; }
    public BlockPos getExtraLargeIslandCenter() { return extraLargeIslandCenter; }
    public BlockPos getCentralSquare() { return centralSquare; }
}
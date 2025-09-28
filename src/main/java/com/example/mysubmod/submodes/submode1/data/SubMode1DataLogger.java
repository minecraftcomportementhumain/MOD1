package com.example.mysubmod.submodes.submode1.data;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submode1.islands.IslandType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SubMode1DataLogger {
    private final Map<String, FileWriter> playerLoggers = new ConcurrentHashMap<>();
    private String gameSessionId;
    private File gameDirectory;

    public void startNewGame() {
        gameSessionId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        // Create game directory
        File modsDirectory = new File(".", "mysubmod_data");
        gameDirectory = new File(modsDirectory, "submode1_game_" + gameSessionId);

        if (!gameDirectory.exists()) {
            gameDirectory.mkdirs();
        }

        MySubMod.LOGGER.info("Started data logging for SubMode1 game session: {}", gameSessionId);
        logEvent("GAME_START", "Game session started at " + LocalDateTime.now());
    }

    public void endGame() {
        logEvent("GAME_END", "Game session ended at " + LocalDateTime.now());

        // Close all player loggers
        for (FileWriter writer : playerLoggers.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error closing player logger", e);
            }
        }
        playerLoggers.clear();

        MySubMod.LOGGER.info("Ended data logging for SubMode1 game session: {}", gameSessionId);
    }

    private FileWriter getPlayerLogger(ServerPlayer player) {
        String playerName = player.getName().getString();
        return playerLoggers.computeIfAbsent(playerName, name -> {
            try {
                File playerFile = new File(gameDirectory, name + "_log.txt");
                FileWriter writer = new FileWriter(playerFile, true);
                writer.write("=== SubMode1 Data Log for " + name + " ===\n");
                writer.write("Game Session: " + gameSessionId + "\n");
                writer.write("Start Time: " + LocalDateTime.now() + "\n\n");
                writer.flush();
                return writer;
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error creating player logger for {}", name, e);
                return null;
            }
        });
    }

    public void logPlayerPosition(ServerPlayer player) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                String positionData = String.format("[%s] POSITION: %.2f, %.2f, %.2f\n",
                    timestamp, player.getX(), player.getY(), player.getZ());
                logger.write(positionData);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging position for player {}", player.getName().getString(), e);
            }
        }
    }

    public void logCandyConsumption(ServerPlayer player) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                String candyData = String.format("[%s] CANDY_CONSUMED: %.2f, %.2f, %.2f | Health: %.1f\n",
                    timestamp, player.getX(), player.getY(), player.getZ(), player.getHealth());
                logger.write(candyData);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging candy consumption for player {}", player.getName().getString(), e);
            }
        }
    }

    public void logCandyPickup(ServerPlayer player, BlockPos position) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                String pickupData = String.format("[%s] CANDY_PICKUP: %d, %d, %d | Player: %.2f, %.2f, %.2f\n",
                    timestamp, position.getX(), position.getY(), position.getZ(),
                    player.getX(), player.getY(), player.getZ());
                logger.write(pickupData);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging candy pickup for player {}", player.getName().getString(), e);
            }
        }
    }

    public void logHealthChange(ServerPlayer player, float oldHealth, float newHealth) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                String healthData = String.format("[%s] HEALTH_CHANGE: %.1f -> %.1f | Position: %.2f, %.2f, %.2f\n",
                    timestamp, oldHealth, newHealth, player.getX(), player.getY(), player.getZ());
                logger.write(healthData);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging health change for player {}", player.getName().getString(), e);
            }
        }
    }

    public void logPlayerDeath(ServerPlayer player) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                String deathData = String.format("[%s] PLAYER_DEATH: %.2f, %.2f, %.2f\n",
                    timestamp, player.getX(), player.getY(), player.getZ());
                logger.write(deathData);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging player death for {}", player.getName().getString(), e);
            }
        }
    }

    public void logIslandSelection(ServerPlayer player, IslandType island) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                String selectionData = String.format("[%s] ISLAND_SELECTION: %s\n",
                    timestamp, island.getDisplayName());
                logger.write(selectionData);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging island selection for player {}", player.getName().getString(), e);
            }
        }
    }

    public void logCandySpawn(BlockPos position, IslandType island) {
        logEvent("CANDY_SPAWN", String.format("Position: %d, %d, %d | Island: %s",
            position.getX(), position.getY(), position.getZ(), island.getDisplayName()));
    }

    private void logEvent(String eventType, String data) {
        try {
            File eventFile = new File(gameDirectory, "game_events.txt");
            FileWriter eventLogger = new FileWriter(eventFile, true);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            eventLogger.write(String.format("[%s] %s: %s\n", timestamp, eventType, data));
            eventLogger.close();
        } catch (IOException e) {
            MySubMod.LOGGER.error("Error logging game event", e);
        }
    }

    public String getGameSessionId() {
        return gameSessionId;
    }
}
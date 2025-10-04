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

/**
 * CSV-based data logger for SubMode1
 * Format: timestamp,player,event_type,x,y,z,health,additional_data
 */
public class SubMode1DataLogger {
    private final Map<String, FileWriter> playerLoggers = new ConcurrentHashMap<>();
    private String gameSessionId;
    private File gameDirectory;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public void startNewGame() {
        gameSessionId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        // Create game directory
        File modsDirectory = new File(".", "mysubmod_data");
        gameDirectory = new File(modsDirectory, "submode1_game_" + gameSessionId);

        if (!gameDirectory.exists()) {
            gameDirectory.mkdirs();
        }

        MySubMod.LOGGER.info("Started CSV data logging for SubMode1 game session: {}", gameSessionId);
    }

    public void endGame() {
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
                File playerFile = new File(gameDirectory, name + "_log.csv");
                FileWriter writer = new FileWriter(playerFile, true);

                // Write CSV header
                writer.write("timestamp,player,event_type,x,y,z,health,additional_data\n");
                writer.flush();
                return writer;
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error creating player logger for {}", name, e);
                return null;
            }
        });
    }

    /**
     * Log player position change
     */
    public void logPlayerPosition(ServerPlayer player) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String csvLine = String.format("%s,%s,POSITION,%.2f,%.2f,%.2f,%.1f,\n",
                    timestamp,
                    player.getName().getString(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getHealth());
                logger.write(csvLine);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging position for player {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log candy consumption (eating)
     */
    public void logCandyConsumption(ServerPlayer player) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String csvLine = String.format("%s,%s,CANDY_CONSUMED,%.2f,%.2f,%.2f,%.1f,\n",
                    timestamp,
                    player.getName().getString(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getHealth());
                logger.write(csvLine);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging candy consumption for player {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log candy pickup (looting)
     */
    public void logCandyPickup(ServerPlayer player, BlockPos position) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String additionalData = String.format("candy_pos=%d;%d;%d",
                    position.getX(), position.getY(), position.getZ());
                String csvLine = String.format("%s,%s,CANDY_PICKUP,%.2f,%.2f,%.2f,%.1f,%s\n",
                    timestamp,
                    player.getName().getString(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getHealth(),
                    additionalData);
                logger.write(csvLine);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging candy pickup for player {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log health change (degradation or healing)
     */
    public void logHealthChange(ServerPlayer player, float oldHealth, float newHealth) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String additionalData = String.format("old_health=%.1f;new_health=%.1f", oldHealth, newHealth);
                String csvLine = String.format("%s,%s,HEALTH_CHANGE,%.2f,%.2f,%.2f,%.1f,%s\n",
                    timestamp,
                    player.getName().getString(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    newHealth,
                    additionalData);
                logger.write(csvLine);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging health change for player {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log player death
     */
    public void logPlayerDeath(ServerPlayer player) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String csvLine = String.format("%s,%s,DEATH,%.2f,%.2f,%.2f,0.0,\n",
                    timestamp,
                    player.getName().getString(),
                    player.getX(),
                    player.getY(),
                    player.getZ());
                logger.write(csvLine);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging player death for {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log island selection at start of game
     */
    public void logIslandSelection(ServerPlayer player, IslandType island) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String additionalData = String.format("island=%s", island.name());
                String csvLine = String.format("%s,%s,ISLAND_SELECTION,%.2f,%.2f,%.2f,%.1f,%s\n",
                    timestamp,
                    player.getName().getString(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getHealth(),
                    additionalData);
                logger.write(csvLine);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging island selection for player {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log player actions (connection/disconnection events)
     */
    public void logPlayerAction(ServerPlayer player, String action) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String csvLine = String.format("%s,%s,%s,%.2f,%.2f,%.2f,%.1f,\n",
                    timestamp,
                    player.getName().getString(),
                    action, // CONNECTED, DISCONNECTED, RECONNECTED
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getHealth());
                logger.write(csvLine);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging player action for {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log candy spawn (global event, not player-specific)
     */
    public void logCandySpawn(BlockPos position) {
        // This could be logged to a separate global events CSV if needed
    }

    public String getGameSessionId() {
        return gameSessionId;
    }
}

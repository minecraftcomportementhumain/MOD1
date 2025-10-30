package com.example.mysubmod.submodes.submode2.data;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submode2.ResourceType;
import com.example.mysubmod.submodes.submode2.islands.IslandType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CSV-based data logger for SubMode2
 * Format: timestamp,player,event_type,x,y,z,health,additional_data
 * Extended to track resource types (A/B), specialization changes, and penalties
 */
public class SubMode2DataLogger {
    private final Map<String, FileWriter> playerLoggers = new ConcurrentHashMap<>();
    private String gameSessionId;
    private File gameDirectory;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public void startNewGame() {
        gameSessionId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        // Create game directory
        File modsDirectory = new File(".", "mysubmod_data");
        gameDirectory = new File(modsDirectory, "submode2_game_" + gameSessionId);

        if (!gameDirectory.exists()) {
            gameDirectory.mkdirs();
        }

        MySubMod.LOGGER.info("Started CSV data logging for SubMode2 game session: {}", gameSessionId);
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

        MySubMod.LOGGER.info("Ended data logging for SubMode2 game session: {}", gameSessionId);
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
                String csvLine = String.format(Locale.US, "%s,%s,POSITION,%.2f,%.2f,%.2f,%.1f,\n",
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
     * Log candy consumption (eating) with resource type
     */
    public void logCandyConsumption(ServerPlayer player, ResourceType resourceType) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String additionalData = String.format(Locale.US, "resource_type=%s", resourceType.name());
                String csvLine = String.format(Locale.US, "%s,%s,CANDY_CONSUMED,%.2f,%.2f,%.2f,%.1f,%s\n",
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
                MySubMod.LOGGER.error("Error logging candy consumption for player {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log candy pickup (looting) with resource type
     */
    public void logCandyPickup(ServerPlayer player, BlockPos position, ResourceType resourceType) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String additionalData = String.format(Locale.US, "candy_pos=%d;%d;%d;resource_type=%s",
                    position.getX(), position.getY(), position.getZ(), resourceType.name());
                String csvLine = String.format(Locale.US, "%s,%s,CANDY_PICKUP,%.2f,%.2f,%.2f,%.1f,%s\n",
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
     * Log specialization initialization (first resource collected)
     */
    public void logSpecializationInit(ServerPlayer player, ResourceType resourceType) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String additionalData = String.format(Locale.US, "specialization=%s;first_collection=true", resourceType.name());
                String csvLine = String.format(Locale.US, "%s,%s,SPECIALIZATION_INIT,%.2f,%.2f,%.2f,%.1f,%s\n",
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
                MySubMod.LOGGER.error("Error logging specialization init for player {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log specialization change (switching from one type to another)
     */
    public void logSpecializationChange(ServerPlayer player, ResourceType oldType, ResourceType newType) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String additionalData = String.format(Locale.US, "old_specialization=%s;new_specialization=%s", oldType.name(), newType.name());
                String csvLine = String.format(Locale.US, "%s,%s,SPECIALIZATION_CHANGE,%.2f,%.2f,%.2f,%.1f,%s\n",
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
                MySubMod.LOGGER.error("Error logging specialization change for player {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log penalty application (when player switches specialization)
     */
    public void logPenaltyApplied(ServerPlayer player, long durationMs) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String additionalData = String.format(Locale.US, "penalty_duration_ms=%d;penalty_duration_sec=%d", durationMs, durationMs / 1000);
                String csvLine = String.format(Locale.US, "%s,%s,PENALTY_APPLIED,%.2f,%.2f,%.2f,%.1f,%s\n",
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
                MySubMod.LOGGER.error("Error logging penalty applied for player {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log penalty expiration
     */
    public void logPenaltyExpired(ServerPlayer player) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String csvLine = String.format(Locale.US, "%s,%s,PENALTY_EXPIRED,%.2f,%.2f,%.2f,%.1f,\n",
                    timestamp,
                    player.getName().getString(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getHealth());
                logger.write(csvLine);
                logger.flush();
            } catch (IOException e) {
                MySubMod.LOGGER.error("Error logging penalty expired for player {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Log health change with resource type context (for tracking penalty effects)
     */
    public void logHealthChange(ServerPlayer player, float oldHealth, float newHealth, ResourceType resourceType, boolean hasPenalty) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String additionalData = String.format(Locale.US, "old_health=%.1f;new_health=%.1f;resource_type=%s;has_penalty=%s",
                    oldHealth, newHealth, resourceType.name(), hasPenalty);
                String csvLine = String.format(Locale.US, "%s,%s,HEALTH_CHANGE,%.2f,%.2f,%.2f,%.1f,%s\n",
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
                String csvLine = String.format(Locale.US, "%s,%s,DEATH,%.2f,%.2f,%.2f,0.0,\n",
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
        logIslandSelection(player, island, "MANUAL");
    }

    /**
     * Log island selection at start of game with selection type
     */
    public void logIslandSelection(ServerPlayer player, IslandType island, String selectionType) {
        FileWriter logger = getPlayerLogger(player);
        if (logger != null) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String additionalData = String.format(Locale.US, "island=%s,selection=%s", island.name(), selectionType);
                String csvLine = String.format(Locale.US, "%s,%s,ISLAND_SELECTION,%.2f,%.2f,%.2f,%.1f,%s\n",
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
                String csvLine = String.format(Locale.US, "%s,%s,%s,%.2f,%.2f,%.2f,%.1f,\n",
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
     * Log candy spawn with resource type (global event, not player-specific)
     */
    public void logCandySpawn(BlockPos position, ResourceType resourceType) {
        // This could be logged to a separate global events CSV if needed
        MySubMod.LOGGER.info("SubMode2 candy spawn: {} at ({},{},{}) - Type: {}",
            resourceType.getDisplayName(), position.getX(), position.getY(), position.getZ(), resourceType.name());
    }

    public String getGameSessionId() {
        return gameSessionId;
    }
}

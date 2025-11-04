package com.example.mysubmod.submodes.submode2;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode1.SubMode1HealthManager;
import com.example.mysubmod.submodes.submode1.data.SubMode1DataLogger;
import com.example.mysubmod.submodes.submode2.data.SubMode2DataLogger;
import com.example.mysubmod.submodes.submode2.data.SubMode2SpawnEntry;
import com.example.mysubmod.submodes.submode2.data.SubMode2SpawnFileManager;
import com.example.mysubmod.submodes.submodeParent.HealthManager;
import com.example.mysubmod.submodes.submodeParent.data.CandySpawnEntry;
import com.example.mysubmod.submodes.submodeParent.data.DataLogger;
import com.example.mysubmod.submodes.submodeParent.islands.IslandType;
import com.example.mysubmod.submodes.submodeParent.network.FileListPacket;
import com.example.mysubmod.submodes.submodeParent.network.GameTimerPacket;
import com.example.mysubmod.util.PlayerFilterUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import com.example.mysubmod.submodes.submodeParent.SubModeParentManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.mysubmod.submodes.submode2.SubMode2CandyManager.getResourceTypeFromCandy;

public class SubMode2Manager extends SubModeParentManager {

    public static void initialize(){
        setInstance(new SubMode2Manager());
        instance.setSpawnFileManager(new SubMode2SpawnFileManager());
        instance.setHealthManager(new SubMode2HealthManager());
        instance.setCandyManager(new SubMode2CandyManager());
        instance.setDataLogger(new SubMode2DataLogger());
    }

    public static SubMode2DataLogger getRealDataLogger() {
        return (SubMode2DataLogger) getDataLogger();
    }
    public static SubMode2Manager getRealInstance() {return (SubMode2Manager) getInstance();}

    @Override
    public void deactivate(MinecraftServer server) {
        MySubMod.LOGGER.info("Deactivating SubMode2");
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
                if (gameTimer != null) {
                    gameTimer.stop();
                    gameTimer = null;
                }

                // Always send deactivation signal to all clients to clear any lingering timers

                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),

                        new GameTimerPacket(-1)); // -1 means deactivate

                // Send empty candy counts to deactivate HUD

                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new com.example.mysubmod.submodes.submode2.network.CandyCountUpdatePacket(new java.util.HashMap<>()));

                // Send empty file list to clear client-side storage (without opening screen)
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new FileListPacket(new java.util.ArrayList<>(), false));

                // Deactivate penalty timer HUD for all clients

                for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                       new com.example.mysubmod.submodes.submode2.network.PenaltySyncPacket(false, player.getUUID()));
                }

            } catch (Exception e) {
                MySubMod.LOGGER.error("Error stopping game timer", e);
            }
            // Stop data logging
            try {
                if (dataLogger != null) {
                    System.out.println(dataLogger.getGameSessionId());
                    System.out.println("HELLLLLOOOOOOOOOOOOOOOOOOOO");
                    dataLogger.endGame();

                }
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error ending data logging", e);
            }
            // Stop health degradation
            try {
                SubMode2HealthManager.getInstance().stopHealthDegradation();
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error stopping health degradation", e);
            }
            // Stop candy spawning and remove all existing candies

            try {
                SubMode2CandyManager.getInstance().stopCandySpawning();
                SubMode2CandyManager.getInstance().removeAllCandiesFromWorld(server);
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
            // Reset specialization manager (clears penalties and specializations)
            SpecializationManager.getInstance().reset();
            MySubMod.LOGGER.info("SubMode2 deactivation completed");

        }
    }


    @Override
    public List<? extends CandySpawnEntry> getCandySpawnConfig() {

        return candySpawnConfig != null ? new ArrayList<>(candySpawnConfig) : new ArrayList<>();

    }
}
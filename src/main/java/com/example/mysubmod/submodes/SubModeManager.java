package com.example.mysubmod.submodes;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.network.SubModeChangePacket;
import com.example.mysubmod.submodes.waitingroom.WaitingRoomManager;
import com.example.mysubmod.submodes.submode1.SubMode1Manager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

public class SubModeManager {
    private static SubModeManager instance;
    private SubMode currentMode = SubMode.WAITING_ROOM;
    private final Set<String> admins = new HashSet<>();

    private SubModeManager() {}

    public static SubModeManager getInstance() {
        if (instance == null) {
            instance = new SubModeManager();
        }
        return instance;
    }

    public void forceDeactivateAllSubModes(MinecraftServer server) {
        MySubMod.LOGGER.info("Force deactivating all sub-modes at server startup");

        try {
            // Force deactivate all possible sub-modes to clean up any leftover state
            // Note: SubMode1Manager.deactivate() now includes clearMap() with improved detection
            WaitingRoomManager.getInstance().deactivate(server);
            SubMode1Manager.getInstance().deactivate(server);
            // TODO: Add SubMode2Manager.getInstance().deactivate(server) when implemented

            // Reset to a clean state
            currentMode = SubMode.WAITING_ROOM; // Will be properly activated next

            MySubMod.LOGGER.info("Successfully deactivated all sub-modes");
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error during force deactivation of sub-modes", e);
        }
    }

    public void startWaitingRoom() {
        changeSubMode(SubMode.WAITING_ROOM, null);
        MySubMod.LOGGER.info("Waiting room started automatically");
    }

    public boolean changeSubMode(SubMode newMode, ServerPlayer requestingPlayer) {
        return changeSubMode(newMode, requestingPlayer, requestingPlayer != null ? requestingPlayer.server : null);
    }

    public boolean changeSubMode(SubMode newMode, ServerPlayer requestingPlayer, MinecraftServer server) {
        if (requestingPlayer != null && !isAdmin(requestingPlayer)) {
            MySubMod.LOGGER.warn("Player {} attempted to change sub-mode without admin privileges", requestingPlayer.getName().getString());
            return false;
        }

        SubMode previousMode = currentMode;

        // Deactivate previous mode
        if (server != null) {
            switch (previousMode) {
                case WAITING_ROOM:
                    WaitingRoomManager.getInstance().deactivate(server);
                    break;
                case SUB_MODE_1:
                    SubMode1Manager.getInstance().deactivate(server);
                    break;
                case SUB_MODE_2:
                    // TODO: Implement SubMode2Manager when ready
                    break;
            }
        }

        currentMode = newMode;

        // Activate new mode
        if (server != null) {
            switch (newMode) {
                case WAITING_ROOM:
                    WaitingRoomManager.getInstance().activate(server);
                    break;
                case SUB_MODE_1:
                    SubMode1Manager.getInstance().activate(server, requestingPlayer);
                    break;
                case SUB_MODE_2:
                    // TODO: Implement SubMode2Manager when ready
                    break;
            }
        }

        MySubMod.LOGGER.info("Sub-mode changed from {} to {} by {}",
            previousMode.getDisplayName(),
            newMode.getDisplayName(),
            requestingPlayer != null ? requestingPlayer.getName().getString() : "SERVER");

        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SubModeChangePacket(newMode));

        return true;
    }

    public SubMode getCurrentMode() {
        return currentMode;
    }

    public void addAdmin(String playerName) {
        admins.add(playerName.toLowerCase());
        MySubMod.LOGGER.info("Added {} as admin", playerName);
    }

    public void removeAdmin(String playerName) {
        admins.remove(playerName.toLowerCase());
        MySubMod.LOGGER.info("Removed {} from admins", playerName);
    }

    public boolean isAdmin(ServerPlayer player) {
        return admins.contains(player.getName().getString().toLowerCase()) ||
               player.hasPermissions(2);
    }

    public boolean isPlayerAdmin(ServerPlayer player) {
        return isAdmin(player);
    }

    public Set<String> getAdmins() {
        return new HashSet<>(admins);
    }
}
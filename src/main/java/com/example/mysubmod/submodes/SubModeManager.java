package com.example.mysubmod.submodes;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.network.SubModeChangePacket;
import com.example.mysubmod.submodes.submodeParent.SubModeParentManager;
import com.example.mysubmod.submodes.waitingroom.WaitingRoomManager;
import com.example.mysubmod.submodes.submode1.SubMode1Manager;
import com.example.mysubmod.submodes.submode2.SubMode2Manager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

public class SubModeManager {
    private static SubModeManager instance;
    private SubMode currentMode = SubMode.WAITING_ROOM;
    private final Set<String> admins = new HashSet<>();
    private boolean isChangingMode = false; // Lock to prevent simultaneous mode changes
    private long lastModeChangeTime = 0; // Track last mode change timestamp
    private static final long MODE_CHANGE_COOLDOWN_MS = 5000; // 5 seconds cooldown

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
            SubMode2Manager.getInstance().deactivate(server);

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

        // Check cooldown - prevent rapid mode changes
        long currentTime = System.currentTimeMillis();
        long timeSinceLastChange = currentTime - lastModeChangeTime;
        if (lastModeChangeTime > 0 && timeSinceLastChange < MODE_CHANGE_COOLDOWN_MS) {
            long remainingCooldown = (MODE_CHANGE_COOLDOWN_MS - timeSinceLastChange) / 1000;
            MySubMod.LOGGER.warn("Sub-mode change cooldown active, rejecting request from {}. {} seconds remaining",
                requestingPlayer != null ? requestingPlayer.getName().getString() : "SERVER", remainingCooldown);
            if (requestingPlayer != null) {
                requestingPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cChangement de sous-mode trop rapide ! Veuillez attendre " + remainingCooldown + " seconde(s)..."));
            }
            return false;
        }

        // Check if already changing mode
        if (isChangingMode) {
            MySubMod.LOGGER.warn("Sub-mode change already in progress, rejecting new request from {}",
                requestingPlayer != null ? requestingPlayer.getName().getString() : "SERVER");
            if (requestingPlayer != null) {
                requestingPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cChangement de sous-mode déjà en cours, veuillez attendre..."));
            }
            return false;
        }

        // Lock the mode change
        isChangingMode = true;

        try {
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
                        SubMode2Manager.getRealInstance().deactivate(server);
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
                        SubMode1Manager.initialize();
                        SubMode1Manager.getInstance().activate(server, requestingPlayer);
                        break;
                    case SUB_MODE_2:
                        SubMode2Manager.initialize();
                        SubMode2Manager.getInstance().activate(server, requestingPlayer);
                        break;
                }
            }

            MySubMod.LOGGER.info("Sub-mode changed from {} to {} by {}",
                previousMode.getDisplayName(),
                newMode.getDisplayName(),
                requestingPlayer != null ? requestingPlayer.getName().getString() : "SERVER");

            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SubModeChangePacket(newMode));

            // Update last mode change timestamp for cooldown tracking
            lastModeChangeTime = System.currentTimeMillis();

            return true;
        } finally {
            // Always unlock, even if an exception occurs
            isChangingMode = false;
        }
    }

    public SubMode getCurrentMode() {
        return currentMode;
    }

    public void addAdmin(String playerName, MinecraftServer server) {
        admins.add(playerName.toLowerCase());
        MySubMod.LOGGER.info("Added {} as admin", playerName);

        // Disconnect the player to force privilege refresh
        disconnectPlayer(playerName, server, "§aVos privilèges ont été modifiés. Veuillez vous reconnecter.");
    }

    public void removeAdmin(String playerName, MinecraftServer server) {
        admins.remove(playerName.toLowerCase());
        MySubMod.LOGGER.info("Removed {} from admins", playerName);

        // Disconnect the player to force privilege refresh
        disconnectPlayer(playerName, server, "§cVos privilèges ont été modifiés. Veuillez vous reconnecter.");
    }

    private void disconnectPlayer(String playerName, MinecraftServer server, String reason) {
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
            if (player != null) {
                player.connection.disconnect(net.minecraft.network.chat.Component.literal(reason));
                MySubMod.LOGGER.info("Disconnected player {} due to privilege change", playerName);
            }
        }
    }

    public boolean isAdmin(ServerPlayer player) {
        String playerName = player.getName().getString().toLowerCase();
        com.example.mysubmod.auth.AdminAuthManager authManager = com.example.mysubmod.auth.AdminAuthManager.getInstance();

        // Check if player is in admin list
        if (admins.contains(playerName)) {
            // Admin accounts MUST be authenticated
            boolean authenticated = authManager.isAuthenticated(player);
            MySubMod.LOGGER.info("isAdmin check (admin list): {} -> authenticated={}", playerName, authenticated);
            return authenticated;
        }

        // Server operators (permission level 2+) are also admins but must authenticate
        if (player.hasPermissions(2)) {
            if (authManager.isAdminAccount(playerName)) {
                // They have a password set - require authentication
                boolean authenticated = authManager.isAuthenticated(player);
                MySubMod.LOGGER.info("isAdmin check (op with password): {} -> authenticated={}", playerName, authenticated);
                return authenticated;
            }
            // No password set yet - allow access (they can set their password)
            MySubMod.LOGGER.info("isAdmin check (op without password): {} -> true", playerName);
            return true;
        }

        // Check if player has an admin account (even if not op and not in admin list)
        if (authManager.isAdminAccount(playerName)) {
            boolean authenticated = authManager.isAuthenticated(player);
            MySubMod.LOGGER.info("isAdmin check (admin account): {} -> authenticated={}", playerName, authenticated);
            return authenticated;
        }

        MySubMod.LOGGER.info("isAdmin check: {} -> false (not op, not in admin list, no admin account)", playerName);
        return false;
    }

    public Set<String> getAdmins() {
        return new HashSet<>(admins);
    }
}
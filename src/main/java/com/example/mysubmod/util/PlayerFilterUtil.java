package com.example.mysubmod.util;

import com.example.mysubmod.auth.AdminAuthManager;
import com.example.mysubmod.auth.AuthManager;
import com.example.mysubmod.auth.ParkingLobbyManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to filter players and exclude restricted players
 * (queue candidates and unauthenticated protected/admin players)
 * from submode processing
 */
public class PlayerFilterUtil {

    /**
     * Get all authenticated players on the server.
     * Excludes:
     * - Queue candidates (temporary _Q_ names)
     * - Unauthenticated protected players
     * - Unauthenticated admins
     *
     * Use this method instead of server.getPlayerList().getPlayers() in submodes.
     */
    public static List<ServerPlayer> getAuthenticatedPlayers(MinecraftServer server) {
        List<ServerPlayer> authenticatedPlayers = new ArrayList<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isRestrictedPlayer(player)) {
                authenticatedPlayers.add(player);
            }
        }

        return authenticatedPlayers;
    }

    /**
     * Check if a player is restricted (should not be processed by submodes)
     * Returns true for:
     * - Players in parking lobby (waiting for authentication)
     * - Queue candidates (temporary _Q_ names)
     * - Unauthenticated protected players
     * - Unauthenticated admins
     */
    public static boolean isRestrictedPlayer(ServerPlayer player) {
        String playerName = player.getName().getString();
        ParkingLobbyManager parkingLobby = ParkingLobbyManager.getInstance();

        // Check if player is in parking lobby (waiting for authentication)
        // This is the primary check - if they're in parking lobby, they're restricted
        boolean inParkingLobby = parkingLobby.isInParkingLobby(player.getUUID());
        if (inParkingLobby) {
            com.example.mysubmod.MySubMod.LOGGER.info("DEBUG: Player {} is restricted (in parking lobby)", playerName);
            return true;
        }

        // Check if queue candidate (temporary name)
        if (parkingLobby.isTemporaryQueueName(playerName)) {
            return true;
        }

        // Check if unauthenticated protected/admin
        AuthManager authManager = AuthManager.getInstance();
        AdminAuthManager adminAuthManager = AdminAuthManager.getInstance();

        AuthManager.AccountType accountType = authManager.getAccountType(playerName);
        boolean isProtectedOrAdmin = (accountType == AuthManager.AccountType.PROTECTED_PLAYER ||
                                      accountType == AuthManager.AccountType.ADMIN);

        if (isProtectedOrAdmin) {
            if (accountType == AuthManager.AccountType.ADMIN) {
                return !adminAuthManager.isAuthenticated(player);
            } else {
                return !authManager.isAuthenticated(player.getUUID());
            }
        }

        return false;
    }
}

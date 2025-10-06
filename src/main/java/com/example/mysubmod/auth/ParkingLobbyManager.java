package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages players in the parking lobby waiting for authentication
 * - 60 second timeout before kick
 * - Blocks all player actions until authenticated
 */
public class ParkingLobbyManager {
    private static ParkingLobbyManager instance;

    private final Map<UUID, AuthSession> activeSessions = new ConcurrentHashMap<>();
    private final Timer timeoutTimer = new Timer("ParkingLobby-Timeout", true);

    private static final long AUTH_TIMEOUT_MS = 60 * 1000; // 60 seconds

    private ParkingLobbyManager() {}

    public static ParkingLobbyManager getInstance() {
        if (instance == null) {
            instance = new ParkingLobbyManager();
        }
        return instance;
    }

    /**
     * Session info for a player awaiting authentication
     */
    private static class AuthSession {
        final long startTime;
        final String accountType; // "ADMIN" or "PROTECTED_PLAYER"
        TimerTask timeoutTask;

        AuthSession(String accountType) {
            this.startTime = System.currentTimeMillis();
            this.accountType = accountType;
        }
    }

    /**
     * Add player to parking lobby and start timeout
     */
    public void addPlayer(ServerPlayer player, String accountType) {
        UUID playerId = player.getUUID();

        // Create session
        AuthSession session = new AuthSession(accountType);
        activeSessions.put(playerId, session);

        // Schedule timeout kick
        session.timeoutTask = new TimerTask() {
            @Override
            public void run() {
                // Execute on server thread
                if (player.server != null) {
                    player.server.execute(() -> {
                        if (isInParkingLobby(playerId)) {
                            MySubMod.LOGGER.warn("Player {} timed out during authentication (60s)", player.getName().getString());
                            player.connection.disconnect(Component.literal(
                                "§c§lTemps d'authentification écoulé\n\n" +
                                "§7Vous aviez 60 secondes pour vous authentifier."
                            ));
                            removePlayer(playerId);
                        }
                    });
                }
            }
        };

        timeoutTimer.schedule(session.timeoutTask, AUTH_TIMEOUT_MS);

        MySubMod.LOGGER.info("Player {} added to parking lobby as {} (60s timeout)",
            player.getName().getString(), accountType);
    }

    /**
     * Remove player from parking lobby (authenticated or kicked)
     */
    public void removePlayer(UUID playerId) {
        AuthSession session = activeSessions.remove(playerId);
        if (session != null && session.timeoutTask != null) {
            session.timeoutTask.cancel();
        }
    }

    /**
     * Check if player is in parking lobby
     */
    public boolean isInParkingLobby(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Get account type for player in parking lobby
     */
    public String getAccountType(UUID playerId) {
        AuthSession session = activeSessions.get(playerId);
        return session != null ? session.accountType : null;
    }

    /**
     * Get remaining time before timeout (in seconds)
     */
    public int getRemainingTime(UUID playerId) {
        AuthSession session = activeSessions.get(playerId);
        if (session == null) return 0;

        long elapsed = System.currentTimeMillis() - session.startTime;
        long remaining = AUTH_TIMEOUT_MS - elapsed;
        return Math.max(0, (int)(remaining / 1000));
    }

    /**
     * Cleanup on server shutdown
     */
    public void shutdown() {
        timeoutTimer.cancel();
        activeSessions.clear();
    }
}

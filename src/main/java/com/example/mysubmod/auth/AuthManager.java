package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified authentication manager for both admins and protected players
 * Handles password hashing, blacklisting, and rate limiting
 */
public class AuthManager {
    private static AuthManager instance;

    // Runtime state
    private final Set<UUID> authenticatedUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> authenticationProtectionByAccount = new ConcurrentHashMap<>(); // accountName -> protection end time

    private static final int MAX_ATTEMPTS = 3;
    private static final long ACCOUNT_BLACKLIST_DURATION = 3 * 60 * 1000; // 3 minutes
    private static final long BASE_IP_BLACKLIST_DURATION = 3 * 60 * 1000; // 3 minutes (base for escalation)
    private static final long FAILURE_RESET_TIME = 24 * 60 * 60 * 1000; // 24 hours
    private static final long AUTH_PROTECTION_DURATION = 30 * 1000; // 30 seconds

    public enum AccountType {
        ADMIN,
        PROTECTED_PLAYER,
        FREE_PLAYER
    }

    private AuthManager() {
        // No initialization needed - using CredentialsStore
    }

    private JsonObject getCredentials() {
        return CredentialsStore.getInstance().getCredentials();
    }

    public static AuthManager getInstance() {
        if (instance == null) {
            instance = new AuthManager();
        }
        return instance;
    }


    /**
     * Get account type for a player name
     */
    public AccountType getAccountType(String playerName) {
        if (AdminAuthManager.getInstance().isAdminAccount(playerName)) {
            return AccountType.ADMIN;
        }

        JsonObject protectedPlayers = getCredentials().getAsJsonObject("protected_players");
        if (protectedPlayers.has(playerName)) {
            return AccountType.PROTECTED_PLAYER;
        }

        return AccountType.FREE_PLAYER;
    }

    /**
     * Check if account needs authentication
     */
    public boolean needsAuthentication(String playerName) {
        AccountType type = getAccountType(playerName);
        return type == AccountType.ADMIN || type == AccountType.PROTECTED_PLAYER;
    }

    /**
     * Add a protected player account
     */
    public boolean addProtectedPlayer(String playerName, String password) {
        if (playerName == null || password == null || password.isEmpty()) {
            return false;
        }

        JsonObject protectedPlayers = getCredentials().getAsJsonObject("protected_players");
        if (protectedPlayers.has(playerName)) {
            MySubMod.LOGGER.warn("Protected player {} already exists", playerName);
            return false;
        }

        // Generate salt and hash
        String salt = generateSalt();
        String hashedPassword = hashPassword(password, salt);

        JsonObject playerData = new JsonObject();
        playerData.addProperty("salt", salt);
        playerData.addProperty("password", hashedPassword);
        playerData.addProperty("failures", 0);
        playerData.addProperty("lastFailure", 0);

        protectedPlayers.add(playerName, playerData);
        CredentialsStore.getInstance().save();

        MySubMod.LOGGER.info("Added protected player: {}", playerName);
        return true;
    }

    /**
     * Remove a protected player account
     */
    public boolean removeProtectedPlayer(String playerName) {
        JsonObject protectedPlayers = getCredentials().getAsJsonObject("protected_players");
        if (!protectedPlayers.has(playerName)) {
            return false;
        }

        protectedPlayers.remove(playerName);
        CredentialsStore.getInstance().save();

        MySubMod.LOGGER.info("Removed protected player: {}", playerName);
        return true;
    }

    /**
     * List all protected players
     */
    public List<String> listProtectedPlayers() {
        JsonObject protectedPlayers = getCredentials().getAsJsonObject("protected_players");
        return new ArrayList<>(protectedPlayers.keySet());
    }

    /**
     * Set password for a protected player
     */
    public boolean setProtectedPlayerPassword(String playerName, String newPassword) {
        if (newPassword == null || newPassword.isEmpty()) {
            return false;
        }

        JsonObject protectedPlayers = getCredentials().getAsJsonObject("protected_players");
        if (!protectedPlayers.has(playerName)) {
            return false;
        }

        // Generate new salt and hash
        String salt = generateSalt();
        String hashedPassword = hashPassword(newPassword, salt);

        JsonObject playerData = protectedPlayers.getAsJsonObject(playerName);
        playerData.addProperty("salt", salt);
        playerData.addProperty("password", hashedPassword);
        playerData.addProperty("failures", 0);
        playerData.addProperty("lastFailure", 0);

        CredentialsStore.getInstance().save();

        MySubMod.LOGGER.info("Updated password for protected player: {}", playerName);
        return true;
    }

    /**
     * Verify password for a protected player
     */
    public boolean verifyProtectedPlayerPassword(String playerName, String password) {
        JsonObject protectedPlayers = getCredentials().getAsJsonObject("protected_players");

        // Try exact match first, then lowercase (for compatibility)
        JsonObject playerData = null;
        if (protectedPlayers.has(playerName)) {
            playerData = protectedPlayers.getAsJsonObject(playerName);
        } else if (protectedPlayers.has(playerName.toLowerCase())) {
            playerData = protectedPlayers.getAsJsonObject(playerName.toLowerCase());
        }

        if (playerData == null) {
            return false;
        }

        String salt = playerData.get("salt").getAsString();
        String storedHash = playerData.get("password").getAsString();
        String providedHash = hashPassword(password, salt);

        return storedHash.equals(providedHash);
    }

    /**
     * Attempt login for protected player with blacklist tracking
     * Returns: 0 = success, -1 = wrong password, -2 = account blacklisted
     */
    public int attemptProtectedPlayerLogin(ServerPlayer player, String password) {
        String playerName = player.getName().getString(); // Keep original case to match JSON keys

        // Check if account is blacklisted
        if (isProtectedPlayerBlacklisted(playerName)) {
            return -2; // Blacklisted
        }

        // Get current attempt count from account_blacklist
        JsonObject accountBlacklist = getCredentials().getAsJsonObject("account_blacklist");
        int attempts = 0;
        long lastAttempt = 0;

        if (accountBlacklist.has(playerName)) {
            JsonObject entry = accountBlacklist.getAsJsonObject(playerName);
            if (entry.has("currentAttempts")) {
                attempts = entry.get("currentAttempts").getAsInt();
            }
            if (entry.has("lastAttempt")) {
                lastAttempt = entry.get("lastAttempt").getAsLong();
            }

            // Reset attempts if 24h elapsed
            if (System.currentTimeMillis() - lastAttempt > FAILURE_RESET_TIME) {
                attempts = 0;
            }
        }

        attempts++;

        // Verify password
        if (verifyProtectedPlayerPassword(playerName, password)) {
            // Success - clear attempts and authenticate
            clearProtectedPlayerAttempts(playerName);
            authenticatedUsers.add(player.getUUID());
            clearAuthenticationProtection(playerName);

            // Handle transition from restricted to normal player
            handleAuthenticationTransition(player.getUUID());

            MySubMod.LOGGER.info("Protected player {} successfully authenticated", playerName);
            return 0;
        } else {
            // Wrong password
            MySubMod.LOGGER.warn("Failed login attempt {}/{} for protected player {}", attempts, MAX_ATTEMPTS, playerName);

            if (attempts >= MAX_ATTEMPTS) {
                // Max attempts reached - blacklist account (3 minutes)
                blacklistProtectedPlayer(playerName);
                return -2;
            } else {
                // Save current attempts to persist across reconnects
                saveProtectedPlayerAttempts(playerName, attempts);
            }

            return -1;
        }
    }

    /**
     * Check if a protected player is blacklisted
     */
    public boolean isProtectedPlayerBlacklisted(String playerName) {
        JsonObject accountBlacklist = getCredentials().getAsJsonObject("account_blacklist");
        if (!accountBlacklist.has(playerName.toLowerCase())) {
            return false;
        }

        JsonObject entry = accountBlacklist.getAsJsonObject(playerName.toLowerCase());
        if (!entry.has("until")) {
            return false;
        }

        long until = entry.get("until").getAsLong();

        if (System.currentTimeMillis() < until) {
            return true;
        } else {
            // Blacklist expired, remove it
            accountBlacklist.remove(playerName.toLowerCase());
            CredentialsStore.getInstance().save();
            return false;
        }
    }

    /**
     * Get remaining blacklist time for protected player (in milliseconds)
     */
    public long getRemainingProtectedPlayerBlacklistTime(String playerName) {
        JsonObject accountBlacklist = getCredentials().getAsJsonObject("account_blacklist");
        if (!accountBlacklist.has(playerName.toLowerCase())) {
            return 0;
        }

        JsonObject entry = accountBlacklist.getAsJsonObject(playerName.toLowerCase());
        if (!entry.has("until")) {
            return 0;
        }

        long until = entry.get("until").getAsLong();
        long remaining = until - System.currentTimeMillis();

        return Math.max(0, remaining);
    }

    /**
     * Get remaining attempts for protected player
     */
    public int getRemainingProtectedPlayerAttempts(String playerName) {
        JsonObject accountBlacklist = getCredentials().getAsJsonObject("account_blacklist");
        int attempts = 0;

        if (accountBlacklist.has(playerName.toLowerCase())) {
            JsonObject entry = accountBlacklist.getAsJsonObject(playerName.toLowerCase());
            if (entry.has("currentAttempts")) {
                attempts = entry.get("currentAttempts").getAsInt();
            }
        }

        return MAX_ATTEMPTS - attempts;
    }

    /**
     * Save current attempts for protected player
     */
    private void saveProtectedPlayerAttempts(String playerName, int attempts) {
        JsonObject accountBlacklist = getCredentials().getAsJsonObject("account_blacklist");
        JsonObject entry;

        if (accountBlacklist.has(playerName)) {
            entry = accountBlacklist.getAsJsonObject(playerName);
        } else {
            entry = new JsonObject();
            accountBlacklist.add(playerName, entry);
        }

        entry.addProperty("currentAttempts", attempts);
        entry.addProperty("lastAttempt", System.currentTimeMillis());
        CredentialsStore.getInstance().save();
    }

    /**
     * Clear attempts for protected player
     */
    private void clearProtectedPlayerAttempts(String playerName) {
        JsonObject accountBlacklist = getCredentials().getAsJsonObject("account_blacklist");
        if (accountBlacklist.has(playerName)) {
            accountBlacklist.remove(playerName);
            CredentialsStore.getInstance().save();
        }
    }

    /**
     * Blacklist a protected player account for 3 minutes
     */
    private void blacklistProtectedPlayer(String playerName) {
        JsonObject accountBlacklist = getCredentials().getAsJsonObject("account_blacklist");

        // Fixed 3 minutes blacklist
        long until = System.currentTimeMillis() + ACCOUNT_BLACKLIST_DURATION;

        JsonObject entry = new JsonObject();
        entry.addProperty("until", until);
        entry.addProperty("lastAttempt", System.currentTimeMillis());

        accountBlacklist.add(playerName.toLowerCase(), entry);
        CredentialsStore.getInstance().save();

        MySubMod.LOGGER.warn("Protected player account {} blacklisted for 3 minutes", playerName);
    }

    /**
     * Set authentication protection to current time + 30 seconds
     * Used when someone connects during monopoly window (with authorized IP)
     * This gives them 30 seconds to authenticate without others connecting
     */
    public void updateAuthenticationProtection(String accountName) {
        long protectionEndTime = System.currentTimeMillis() + AUTH_PROTECTION_DURATION;
        authenticationProtectionByAccount.put(accountName, protectionEndTime);
        MySubMod.LOGGER.info("Set 30-second authentication protection for {} (end time: {})", accountName, protectionEndTime);
    }

    /**
     * Check if account has active authentication protection
     * Returns remaining time in milliseconds, or 0 if no protection
     */
    public long getRemainingProtectionTime(String accountName) {
        Long protectionEndTime = authenticationProtectionByAccount.get(accountName);
        if (protectionEndTime == null) {
            return 0;
        }

        long remaining = protectionEndTime - System.currentTimeMillis();
        if (remaining <= 0) {
            // Protection expired
            authenticationProtectionByAccount.remove(accountName);
            return 0;
        }

        return remaining;
    }

    /**
     * Clear authentication protection for an account
     */
    public void clearAuthenticationProtection(String accountName) {
        authenticationProtectionByAccount.remove(accountName);
        MySubMod.LOGGER.info("Cleared authentication protection for {}", accountName);
    }


    /**
     * Mark user as authenticated
     */
    public void markAuthenticated(UUID playerId) {
        authenticatedUsers.add(playerId);

        // Clear protection by finding the player's account name
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                clearAuthenticationProtection(player.getName().getString());
            }
        }

        // Handle transition from restricted to normal player
        handleAuthenticationTransition(playerId);
    }

    /**
     * Handle player transition from unauthenticated (restricted) to authenticated (normal)
     * Treats the player as if they just connected for the first time
     */
    private void handleAuthenticationTransition(UUID playerId) {
        // Find the player by UUID
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return;

        com.example.mysubmod.MySubMod.LOGGER.info("Handling authentication transition for protected player: {}", player.getName().getString());

        // Check if server is full and kick a FREE_PLAYER if necessary
        // Must be called BEFORE removing from parking lobby so the count is accurate
        com.example.mysubmod.ServerEventHandler.checkAndKickForPriority(player, AccountType.PROTECTED_PLAYER);

        // Remove invisibility
        player.setInvisible(false);

        // Remove from parking lobby
        com.example.mysubmod.auth.ParkingLobbyManager.getInstance().removePlayer(playerId);

        // Send player info to all clients NOW that they're authenticated (wasn't sent during initial connection)
        net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket addPacket =
            net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(java.util.List.of(player));
        server.getPlayerList().broadcastAll(addPacket);
        com.example.mysubmod.MySubMod.LOGGER.info("Broadcasting authenticated player {} to all clients", player.getName().getString());

        // Send success message
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a§lAuthentification réussie!"));

        // Check if player was disconnected during SubMode1 - if so, restore their state directly
        com.example.mysubmod.submodes.SubModeManager subModeManager = com.example.mysubmod.submodes.SubModeManager.getInstance();
        com.example.mysubmod.submodes.SubMode currentMode = subModeManager.getCurrentMode();

        if (currentMode == com.example.mysubmod.submodes.SubMode.SUB_MODE_1) {
            com.example.mysubmod.submodes.submode1.SubMode1Manager subMode1Manager =
                com.example.mysubmod.submodes.submode1.SubMode1Manager.getInstance();

            if (subMode1Manager.wasPlayerDisconnected(player.getName().getString())) {
                // Player was disconnected during the game - restore their position and state
                com.example.mysubmod.MySubMod.LOGGER.info("Player {} was disconnected during SubMode1, restoring state", player.getName().getString());
                subMode1Manager.handlePlayerReconnection(player);
                return;
            }
        }

        // Player is authenticating for the first time this session - treat as new join
        // Call the appropriate submode event handler
        if (currentMode == com.example.mysubmod.submodes.SubMode.SUB_MODE_1) {
            com.example.mysubmod.submodes.submode1.SubMode1EventHandler.onPlayerJoin(
                new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player)
            );
        } else if (currentMode == com.example.mysubmod.submodes.SubMode.WAITING_ROOM) {
            com.example.mysubmod.submodes.waitingroom.WaitingRoomEventHandler.onPlayerJoin(
                new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player)
            );
        } else {
            // No active submode - teleport to spawn
            net.minecraft.server.level.ServerLevel overworld = server.overworld();
            if (overworld != null) {
                net.minecraft.core.BlockPos spawnPos = overworld.getSharedSpawnPos();
                net.minecraft.core.BlockPos safePos = overworld.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPos);
                player.teleportTo(overworld, safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, 0, 0);
            }
        }

        com.example.mysubmod.MySubMod.LOGGER.info("Protected player {} successfully transitioned from restricted to normal", player.getName().getString());
    }

    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated(UUID playerId) {
        return authenticatedUsers.contains(playerId);
    }

    /**
     * Handle player disconnect
     */
    public void handleDisconnect(ServerPlayer player) {
        authenticatedUsers.remove(player.getUUID());
        // Note: We intentionally do NOT clear authenticationProtectionByAccount here
        // The protection should persist even after disconnect to prevent rapid reconnections
    }

    // Hash and salt utilities
    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String combined = password + salt;
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            MySubMod.LOGGER.error("SHA-256 not available", e);
            return "";
        }
    }
}

package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submode1.SubMode1Manager;
import com.example.mysubmod.submodes.submodeParent.SubModeParentManager;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdminAuthManager {
    private static AdminAuthManager instance;

    // Runtime state
    private final Set<UUID> authenticatedAdmins = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> authenticationProtectionByAccount = new ConcurrentHashMap<>(); // accountName -> protection end time

    private static final int MAX_ATTEMPTS = 3;
    private static final long ACCOUNT_BLACKLIST_DURATION = 3 * 60 * 1000; // 3 minutes in ms (fixed for accounts)
    private static final long BASE_IP_BLACKLIST_DURATION = 3 * 60 * 1000; // 3 minutes in ms (base for IP escalation)
    private static final long FAILURE_RESET_TIME = 24 * 60 * 60 * 1000; // 24 hours in ms
    private static final long AUTH_PROTECTION_DURATION = 30 * 1000; // 30 seconds protection

    private AdminAuthManager() {
        // No initialization needed - using CredentialsStore
    }

    private JsonObject getCredentials() {
        return CredentialsStore.getInstance().getCredentials();
    }

    public static AdminAuthManager getInstance() {
        if (instance == null) {
            instance = new AdminAuthManager();
        }
        return instance;
    }


    /**
     * Check if a player name is registered as an admin (has credentials)
     */
    public boolean isAdminAccount(String playerName) {
        JsonObject admins = getCredentials().getAsJsonObject("admins");
        return admins.has(playerName.toLowerCase());
    }

    /**
     * Check if a player is currently authenticated
     */
    public boolean isAuthenticated(ServerPlayer player) {
        return authenticatedAdmins.contains(player.getUUID());
    }

    /**
     * Check if a player is currently blacklisted
     */
    public boolean isBlacklisted(String playerName) {
        JsonObject blacklist = getCredentials().getAsJsonObject("blacklist");
        if (!blacklist.has(playerName.toLowerCase())) {
            return false;
        }

        JsonObject entry = blacklist.getAsJsonObject(playerName.toLowerCase());

        // Check if this entry has an "until" field (actual blacklist)
        if (!entry.has("until")) {
            // This is just attempt tracking, not a blacklist
            return false;
        }

        long until = entry.get("until").getAsLong();

        if (System.currentTimeMillis() < until) {
            return true;
        } else {
            // Blacklist expired, remove it
            blacklist.remove(playerName.toLowerCase());
            CredentialsStore.getInstance().save();
            return false;
        }
    }

    /**
     * Get remaining blacklist time in milliseconds
     */
    public long getRemainingBlacklistTime(String playerName) {
        JsonObject blacklist = getCredentials().getAsJsonObject("blacklist");
        if (!blacklist.has(playerName.toLowerCase())) {
            return 0;
        }

        JsonObject entry = blacklist.getAsJsonObject(playerName.toLowerCase());
        long until = entry.get("until").getAsLong();
        long remaining = until - System.currentTimeMillis();

        return Math.max(0, remaining);
    }

    /**
     * Attempt to authenticate a player
     * Returns: 0 = success, -1 = wrong password, -2 = max attempts reached (account), -3 = IP blacklisted
     */
    public int attemptLogin(ServerPlayer player, String password) {
        String playerName = player.getName().getString().toLowerCase();
        UUID playerId = player.getUUID();
        String ipAddress = player.getIpAddress();

        // Check if IP is blacklisted
        if (isIPBlacklisted(ipAddress)) {
            return -3;
        }

        // Check if account is blacklisted
        if (isBlacklisted(playerName)) {
            return -2;
        }

        // Get current attempt count from blacklist entry (persistent across reconnects)
        JsonObject blacklist = getCredentials().getAsJsonObject("blacklist");
        int attempts = 0;
        long lastAttempt = 0;

        if (blacklist.has(playerName)) {
            JsonObject entry = blacklist.getAsJsonObject(playerName);
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
        if (verifyPassword(playerName, password)) {
            // Success - clear attempts and authenticate
            clearAttempts(playerName);
            clearIPAttempts(ipAddress);
            authenticatedAdmins.add(playerId);
            clearAuthenticationProtection(playerName);

            // Handle transition from restricted to normal player
            handleAuthenticationTransition(player);

            MySubMod.LOGGER.info("Admin {} successfully authenticated", playerName);
            return 0;
        } else {
            // Wrong password
            MySubMod.LOGGER.warn("Failed login attempt {}/{} for admin {} from IP {}", attempts, MAX_ATTEMPTS, playerName, ipAddress);

            if (attempts >= MAX_ATTEMPTS) {
                // Max attempts reached - blacklist account (3 minutes fixed) and IP (with escalation)
                blacklistPlayer(playerName);
                blacklistIP(ipAddress);
                return -2;
            } else {
                // Save current attempts to persist across reconnects
                saveAttempts(playerName, attempts);
            }

            return -1;
        }
    }

    /**
     * Get remaining attempts for a player by name
     */
    public int getRemainingAttemptsByName(String playerName) {
        JsonObject blacklist = getCredentials().getAsJsonObject("blacklist");
        int attempts = 0;

        if (blacklist.has(playerName.toLowerCase())) {
            JsonObject entry = blacklist.getAsJsonObject(playerName.toLowerCase());
            if (entry.has("currentAttempts")) {
                attempts = entry.get("currentAttempts").getAsInt();
            }
        }

        return MAX_ATTEMPTS - attempts;
    }

    /**
     * Save current attempts to JSON (persists across reconnects)
     */
    private void saveAttempts(String playerName, int attempts) {
        JsonObject blacklist = getCredentials().getAsJsonObject("blacklist");
        JsonObject entry;

        if (blacklist.has(playerName)) {
            entry = blacklist.getAsJsonObject(playerName);
        } else {
            entry = new JsonObject();
            blacklist.add(playerName, entry);
        }

        entry.addProperty("currentAttempts", attempts);
        entry.addProperty("lastAttempt", System.currentTimeMillis());
        CredentialsStore.getInstance().save();
    }

    /**
     * Clear attempts after successful login
     */
    private void clearAttempts(String playerName) {
        JsonObject blacklist = getCredentials().getAsJsonObject("blacklist");

        if (blacklist.has(playerName)) {
            blacklist.remove(playerName);
            CredentialsStore.getInstance().save();
        }
    }

    private void blacklistPlayer(String playerName) {
        JsonObject blacklist = getCredentials().getAsJsonObject("blacklist");

        // Fixed 3 minutes blacklist for accounts
        long until = System.currentTimeMillis() + ACCOUNT_BLACKLIST_DURATION;

        // Create blacklist entry
        JsonObject entry = new JsonObject();
        entry.addProperty("until", until);
        entry.addProperty("lastAttempt", System.currentTimeMillis());

        blacklist.add(playerName.toLowerCase(), entry);
        CredentialsStore.getInstance().save();

        MySubMod.LOGGER.warn("Account {} blacklisted for 3 minutes", playerName);
    }

    private void blacklistIP(String ipAddress) {
        JsonObject ipBlacklist = getCredentials().getAsJsonObject("ipBlacklist");

        // Get current failure count for IP
        int failureCount = 0;
        long lastAttempt = System.currentTimeMillis();

        if (ipBlacklist.has(ipAddress)) {
            JsonObject entry = ipBlacklist.getAsJsonObject(ipAddress);
            if (entry.has("failureCount")) {
                failureCount = entry.get("failureCount").getAsInt();
            }
            if (entry.has("lastAttempt")) {
                lastAttempt = entry.get("lastAttempt").getAsLong();
            }
        }

        // Check if we should reset failure count (24h since last attempt)
        if (System.currentTimeMillis() - lastAttempt > FAILURE_RESET_TIME) {
            failureCount = 0;
            MySubMod.LOGGER.info("Reset IP failure count for {} (24h elapsed)", ipAddress);
        }

        failureCount++;

        // Calculate blacklist duration with escalation (3min * 10^(failureCount-1))
        long duration = (long) (BASE_IP_BLACKLIST_DURATION * Math.pow(10, failureCount - 1));
        long until = System.currentTimeMillis() + duration;

        // Create IP blacklist entry
        JsonObject entry = new JsonObject();
        entry.addProperty("until", until);
        entry.addProperty("failureCount", failureCount);
        entry.addProperty("lastAttempt", System.currentTimeMillis());

        ipBlacklist.add(ipAddress, entry);
        CredentialsStore.getInstance().save();

        MySubMod.LOGGER.warn("IP {} blacklisted for {} minutes (failure count: {})",
            ipAddress, duration / 60000, failureCount);
    }

    /**
     * Check if an IP is blacklisted
     */
    public boolean isIPBlacklisted(String ipAddress) {
        JsonObject ipBlacklist = getCredentials().getAsJsonObject("ipBlacklist");
        if (!ipBlacklist.has(ipAddress)) {
            return false;
        }

        JsonObject entry = ipBlacklist.getAsJsonObject(ipAddress);

        // Check if this entry has an "until" field
        if (!entry.has("until")) {
            return false;
        }

        long until = entry.get("until").getAsLong();

        if (System.currentTimeMillis() < until) {
            return true;
        } else {
            // Blacklist expired, remove it
            ipBlacklist.remove(ipAddress);
            CredentialsStore.getInstance().save();
            return false;
        }
    }

    /**
     * Get remaining IP blacklist time in milliseconds
     */
    public long getRemainingIPBlacklistTime(String ipAddress) {
        JsonObject ipBlacklist = getCredentials().getAsJsonObject("ipBlacklist");
        if (!ipBlacklist.has(ipAddress)) {
            return 0;
        }

        JsonObject entry = ipBlacklist.getAsJsonObject(ipAddress);
        if (!entry.has("until")) {
            return 0;
        }

        long until = entry.get("until").getAsLong();
        long remaining = until - System.currentTimeMillis();

        return Math.max(0, remaining);
    }

    /**
     * Clear IP attempts after successful login
     */
    private void clearIPAttempts(String ipAddress) {
        JsonObject ipBlacklist = getCredentials().getAsJsonObject("ipBlacklist");

        if (ipBlacklist.has(ipAddress)) {
            ipBlacklist.remove(ipAddress);
            CredentialsStore.getInstance().save();
        }
    }

    /**
     * Verify a password against stored credentials
     */
    private boolean verifyPassword(String playerName, String password) {
        JsonObject admins = getCredentials().getAsJsonObject("admins");
        if (!admins.has(playerName.toLowerCase())) {
            return false;
        }

        JsonObject admin = admins.getAsJsonObject(playerName.toLowerCase());
        String storedHash = admin.get("passwordHash").getAsString();
        String salt = admin.get("salt").getAsString();

        String inputHash = hashPassword(password, salt);
        return storedHash.equals(inputHash);
    }

    /**
     * Verify password without tracking attempts (for queue candidates)
     * Returns true if password is correct, false otherwise
     */
    public boolean verifyPasswordOnly(String playerName, String password) {
        return verifyPassword(playerName, password);
    }

    /**
     * Set or update admin password
     */
    public void setAdminPassword(String playerName, String password) {
        JsonObject admins = getCredentials().getAsJsonObject("admins");

        // Generate salt
        String salt = generateSalt();
        String hash = hashPassword(password, salt);

        JsonObject admin = new JsonObject();
        admin.addProperty("passwordHash", hash);
        admin.addProperty("salt", salt);

        admins.add(playerName.toLowerCase(), admin);
        CredentialsStore.getInstance().save();

        MySubMod.LOGGER.info("Set password for admin {}", playerName);
    }

    /**
     * Reset blacklist for a player
     */
    public void resetBlacklist(String playerName) {
        JsonObject blacklist = getCredentials().getAsJsonObject("blacklist");
        blacklist.remove(playerName.toLowerCase());
        CredentialsStore.getInstance().save();
        MySubMod.LOGGER.info("Reset blacklist for {}", playerName);
    }

    /**
     * Reset failure count for a player (keeps active blacklist)
     */
    public void resetFailureCount(String playerName) {
        JsonObject blacklist = getCredentials().getAsJsonObject("blacklist");
        if (blacklist.has(playerName.toLowerCase())) {
            JsonObject entry = blacklist.getAsJsonObject(playerName.toLowerCase());
            entry.addProperty("failureCount", 0);
            entry.addProperty("lastAttempt", System.currentTimeMillis());
            CredentialsStore.getInstance().save();
            MySubMod.LOGGER.info("Reset failure count for {}", playerName);
        }
    }

    /**
     * Reset IP blacklist
     */
    public void resetIPBlacklist(String ipAddress) {
        JsonObject ipBlacklist = getCredentials().getAsJsonObject("ipBlacklist");
        ipBlacklist.remove(ipAddress);
        CredentialsStore.getInstance().save();
        MySubMod.LOGGER.info("Reset IP blacklist for {}", ipAddress);
    }

    /**
     * Record IP failure and blacklist if necessary (for protected players)
     */
    public void recordIPFailure(String ipAddress) {
        blacklistIP(ipAddress);
    }

    /**
     * Remove admin account
     */
    public void removeAdmin(String playerName) {
        JsonObject admins = getCredentials().getAsJsonObject("admins");
        admins.remove(playerName.toLowerCase());
        CredentialsStore.getInstance().save();
        MySubMod.LOGGER.info("Removed admin account for {}", playerName);
    }

    /**
     * Handle player disconnection
     */
    public void handleDisconnect(ServerPlayer player) {
        authenticatedAdmins.remove(player.getUUID());
        // Note: We intentionally do NOT clear authenticationProtectionByAccount here
        // The protection should persist even after disconnect to prevent rapid reconnections
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
     * Generate a random salt
     */
    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hash a password with salt using SHA-256
     */
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedPassword = password + salt;
            byte[] hash = digest.digest(saltedPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            MySubMod.LOGGER.error("SHA-256 algorithm not found", e);
            return "";
        }
    }

    /**
     * Get list of all admin accounts
     */
    public Set<String> getAdminAccounts() {
        JsonObject admins = getCredentials().getAsJsonObject("admins");
        Set<String> accounts = new HashSet<>();
        admins.keySet().forEach(accounts::add);
        return accounts;
    }

    /**
     * Handle player transition from unauthenticated (restricted) to authenticated (normal)
     * Treats the player as if they just connected for the first time
     */
    private void handleAuthenticationTransition(ServerPlayer player) {
        MySubMod.LOGGER.info("Handling authentication transition for admin: {}", player.getName().getString());

        // Admins don't count towards player limit and NEVER kick anyone
        // So we skip the priority kick check entirely

        // Remove invisibility
        player.setInvisible(false);

        // Remove from parking lobby
        com.example.mysubmod.auth.ParkingLobbyManager.getInstance().removePlayer(player.getUUID(), player.serverLevel());

        // Send success message
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a§lAuthentification admin réussie!"));

        // Check if player was disconnected during an active submode - if so, restore their state directly
        com.example.mysubmod.submodes.SubModeManager subModeManager = com.example.mysubmod.submodes.SubModeManager.getInstance();
        com.example.mysubmod.submodes.SubMode currentMode = subModeManager.getCurrentMode();

        if (currentMode == com.example.mysubmod.submodes.SubMode.SUB_MODE_1) {
            SubModeParentManager subMode1Manager =
                SubMode1Manager.getInstance();

            if (subMode1Manager.wasPlayerDisconnected(player.getName().getString())) {
                // Player was disconnected during the game - restore their position and state
                MySubMod.LOGGER.info("Admin {} was disconnected during SubMode1, restoring state", player.getName().getString());
                subMode1Manager.handlePlayerReconnection(player);
                return;
            }
        } else if (currentMode == com.example.mysubmod.submodes.SubMode.SUB_MODE_2) {
            com.example.mysubmod.submodes.submode2.SubMode2Manager subMode2Manager =
                com.example.mysubmod.submodes.submode2.SubMode2Manager.getInstance();

            if (subMode2Manager.wasPlayerDisconnected(player.getName().getString())) {
                // Player was disconnected during the game - restore their position and state
                MySubMod.LOGGER.info("Admin {} was disconnected during SubMode2, restoring state", player.getName().getString());
                subMode2Manager.handlePlayerReconnection(player);
                return;
            }
        }

        // Player is authenticating for the first time this session - treat as new join
        // Call the appropriate submode event handler
        if (currentMode == com.example.mysubmod.submodes.SubMode.SUB_MODE_1) {
            com.example.mysubmod.submodes.submode1.SubMode1EventHandler.onPlayerJoin(
                new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player)
            );
        } else if (currentMode == com.example.mysubmod.submodes.SubMode.SUB_MODE_2) {
            com.example.mysubmod.submodes.submode2.SubMode2EventHandler.onPlayerJoin(
                new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player)
            );
        } else if (currentMode == com.example.mysubmod.submodes.SubMode.WAITING_ROOM) {
            com.example.mysubmod.submodes.waitingroom.WaitingRoomEventHandler.onPlayerJoin(
                new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player)
            );
        } else {
            // No active submode - teleport to spawn
            net.minecraft.server.MinecraftServer server = player.getServer();
            if (server != null) {
                net.minecraft.server.level.ServerLevel overworld = server.overworld();
                if (overworld != null) {
                    net.minecraft.core.BlockPos spawnPos = overworld.getSharedSpawnPos();
                    net.minecraft.core.BlockPos safePos = overworld.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPos);
                    player.teleportTo(overworld, safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, 0, 0);
                }
            }
        }

        MySubMod.LOGGER.info("Admin {} successfully transitioned from restricted to normal", player.getName().getString());
    }
}

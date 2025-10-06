package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdminAuthManager {
    private static AdminAuthManager instance;

    // Runtime state
    private final Set<UUID> authenticatedAdmins = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> authenticationStartTime = new ConcurrentHashMap<>(); // Track when auth started

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
        authenticationStartTime.remove(player.getUUID());
    }

    /**
     * Start authentication protection for an admin
     */
    public void startAuthenticationProtection(ServerPlayer player) {
        authenticationStartTime.put(player.getUUID(), System.currentTimeMillis());
        MySubMod.LOGGER.info("Started 30-second authentication protection for {}", player.getName().getString());
    }

    /**
     * Check if admin is protected from disconnect during authentication
     */
    public boolean isProtectedDuringAuth(ServerPlayer player) {
        Long startTime = authenticationStartTime.get(player.getUUID());
        if (startTime == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        boolean isProtected = elapsed < AUTH_PROTECTION_DURATION;

        if (!isProtected) {
            // Protection expired, remove entry
            authenticationStartTime.remove(player.getUUID());
        }

        return isProtected;
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
}

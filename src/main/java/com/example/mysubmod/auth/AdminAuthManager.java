package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdminAuthManager {
    private static AdminAuthManager instance;
    private final File credentialsFile;
    private final Gson gson;

    // Runtime state
    private final Set<UUID> authenticatedAdmins = ConcurrentHashMap.newKeySet();

    // Persistent data
    private JsonObject credentials;

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BLACKLIST_DURATION = 3 * 60 * 1000; // 3 minutes in ms
    private static final long FAILURE_RESET_TIME = 24 * 60 * 60 * 1000; // 24 hours in ms

    private AdminAuthManager() {
        this.credentialsFile = new File("admin_credentials.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadCredentials();
    }

    public static AdminAuthManager getInstance() {
        if (instance == null) {
            instance = new AdminAuthManager();
        }
        return instance;
    }

    private void loadCredentials() {
        if (!credentialsFile.exists()) {
            createDefaultCredentials();
        }

        try (Reader reader = new FileReader(credentialsFile, StandardCharsets.UTF_8)) {
            credentials = gson.fromJson(reader, JsonObject.class);
            if (credentials == null) {
                credentials = new JsonObject();
            }
            if (!credentials.has("admins")) {
                credentials.add("admins", new JsonObject());
            }
            if (!credentials.has("blacklist")) {
                credentials.add("blacklist", new JsonObject());
            }
        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to load admin credentials", e);
            credentials = new JsonObject();
            credentials.add("admins", new JsonObject());
            credentials.add("blacklist", new JsonObject());
        }
    }

    private void createDefaultCredentials() {
        credentials = new JsonObject();
        credentials.add("admins", new JsonObject());
        credentials.add("blacklist", new JsonObject());
        saveCredentials();
        MySubMod.LOGGER.info("Created default admin_credentials.json");
    }

    private void saveCredentials() {
        try (Writer writer = new FileWriter(credentialsFile, StandardCharsets.UTF_8)) {
            gson.toJson(credentials, writer);
        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to save admin credentials", e);
        }
    }

    /**
     * Check if a player name is registered as an admin (has credentials)
     */
    public boolean isAdminAccount(String playerName) {
        JsonObject admins = credentials.getAsJsonObject("admins");
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
        JsonObject blacklist = credentials.getAsJsonObject("blacklist");
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
            saveCredentials();
            return false;
        }
    }

    /**
     * Get remaining blacklist time in milliseconds
     */
    public long getRemainingBlacklistTime(String playerName) {
        JsonObject blacklist = credentials.getAsJsonObject("blacklist");
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
     * Returns: 0 = success, -1 = wrong password, -2 = max attempts reached
     */
    public int attemptLogin(ServerPlayer player, String password) {
        String playerName = player.getName().getString().toLowerCase();
        UUID playerId = player.getUUID();

        // Check if blacklisted
        if (isBlacklisted(playerName)) {
            return -2;
        }

        // Get current attempt count from blacklist entry (persistent across reconnects)
        JsonObject blacklist = credentials.getAsJsonObject("blacklist");
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
            authenticatedAdmins.add(playerId);
            MySubMod.LOGGER.info("Admin {} successfully authenticated", playerName);
            return 0;
        } else {
            // Wrong password
            MySubMod.LOGGER.warn("Failed login attempt {}/{} for admin {}", attempts, MAX_ATTEMPTS, playerName);

            if (attempts >= MAX_ATTEMPTS) {
                // Max attempts reached - blacklist
                blacklistPlayer(playerName);
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
        JsonObject blacklist = credentials.getAsJsonObject("blacklist");
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
        JsonObject blacklist = credentials.getAsJsonObject("blacklist");
        JsonObject entry;

        if (blacklist.has(playerName)) {
            entry = blacklist.getAsJsonObject(playerName);
        } else {
            entry = new JsonObject();
            blacklist.add(playerName, entry);
        }

        entry.addProperty("currentAttempts", attempts);
        entry.addProperty("lastAttempt", System.currentTimeMillis());
        saveCredentials();
    }

    /**
     * Clear attempts after successful login
     */
    private void clearAttempts(String playerName) {
        JsonObject blacklist = credentials.getAsJsonObject("blacklist");

        if (blacklist.has(playerName)) {
            blacklist.remove(playerName);
            saveCredentials();
        }
    }

    private void blacklistPlayer(String playerName) {
        JsonObject blacklist = credentials.getAsJsonObject("blacklist");

        // Get current failure count
        int failureCount = 0;
        long lastAttempt = System.currentTimeMillis();

        if (blacklist.has(playerName.toLowerCase())) {
            JsonObject entry = blacklist.getAsJsonObject(playerName.toLowerCase());
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
            MySubMod.LOGGER.info("Reset failure count for {} (24h elapsed)", playerName);
        }

        failureCount++;

        // Calculate blacklist duration (3min * 10^(failureCount-1))
        long duration = (long) (BASE_BLACKLIST_DURATION * Math.pow(10, failureCount - 1));
        long until = System.currentTimeMillis() + duration;

        // Create blacklist entry
        JsonObject entry = new JsonObject();
        entry.addProperty("until", until);
        entry.addProperty("failureCount", failureCount);
        entry.addProperty("lastAttempt", System.currentTimeMillis());

        blacklist.add(playerName.toLowerCase(), entry);
        saveCredentials();

        MySubMod.LOGGER.warn("Player {} blacklisted for {} minutes (failure count: {})",
            playerName, duration / 60000, failureCount);
    }

    /**
     * Verify a password against stored credentials
     */
    private boolean verifyPassword(String playerName, String password) {
        JsonObject admins = credentials.getAsJsonObject("admins");
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
        JsonObject admins = credentials.getAsJsonObject("admins");

        // Generate salt
        String salt = generateSalt();
        String hash = hashPassword(password, salt);

        JsonObject admin = new JsonObject();
        admin.addProperty("passwordHash", hash);
        admin.addProperty("salt", salt);

        admins.add(playerName.toLowerCase(), admin);
        saveCredentials();

        MySubMod.LOGGER.info("Set password for admin {}", playerName);
    }

    /**
     * Reset blacklist for a player
     */
    public void resetBlacklist(String playerName) {
        JsonObject blacklist = credentials.getAsJsonObject("blacklist");
        blacklist.remove(playerName.toLowerCase());
        saveCredentials();
        MySubMod.LOGGER.info("Reset blacklist for {}", playerName);
    }

    /**
     * Reset failure count for a player (keeps active blacklist)
     */
    public void resetFailureCount(String playerName) {
        JsonObject blacklist = credentials.getAsJsonObject("blacklist");
        if (blacklist.has(playerName.toLowerCase())) {
            JsonObject entry = blacklist.getAsJsonObject(playerName.toLowerCase());
            entry.addProperty("failureCount", 0);
            entry.addProperty("lastAttempt", System.currentTimeMillis());
            saveCredentials();
            MySubMod.LOGGER.info("Reset failure count for {}", playerName);
        }
    }

    /**
     * Remove admin account
     */
    public void removeAdmin(String playerName) {
        JsonObject admins = credentials.getAsJsonObject("admins");
        admins.remove(playerName.toLowerCase());
        saveCredentials();
        MySubMod.LOGGER.info("Removed admin account for {}", playerName);
    }

    /**
     * Handle player disconnection
     */
    public void handleDisconnect(ServerPlayer player) {
        authenticatedAdmins.remove(player.getUUID());
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
        JsonObject admins = credentials.getAsJsonObject("admins");
        Set<String> accounts = new HashSet<>();
        admins.keySet().forEach(accounts::add);
        return accounts;
    }
}

package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side storage for queue tokens
 * Stores tokens in memory for automatic reconnection
 */
public class QueueTokenStorage {
    private static final Map<String, TokenData> tokens = new HashMap<>();

    public static class TokenData {
        public final String token;
        public final long monopolyStartMs;
        public final long monopolyEndMs;

        public TokenData(String token, long monopolyStartMs, long monopolyEndMs) {
            this.token = token;
            this.monopolyStartMs = monopolyStartMs;
            this.monopolyEndMs = monopolyEndMs;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > monopolyEndMs;
        }

        public boolean isActive() {
            long now = System.currentTimeMillis();
            return now >= monopolyStartMs && now <= monopolyEndMs;
        }

        public long getTimeUntilStart() {
            return Math.max(0, monopolyStartMs - System.currentTimeMillis());
        }
    }

    /**
     * Store a token for an account
     */
    public static void storeToken(String accountName, String token, long monopolyStartMs, long monopolyEndMs) {
        tokens.put(accountName.toLowerCase(), new TokenData(token, monopolyStartMs, monopolyEndMs));
        MySubMod.LOGGER.info("Stored token for {}: {} (valid from {} to {})",
            accountName, token, monopolyStartMs, monopolyEndMs);
    }

    /**
     * Get token for an account
     */
    public static String getToken(String accountName) {
        TokenData data = tokens.get(accountName.toLowerCase());
        if (data == null || data.isExpired()) {
            return null;
        }
        return data.token;
    }

    /**
     * Get token data for an account
     */
    public static TokenData getTokenData(String accountName) {
        TokenData data = tokens.get(accountName.toLowerCase());
        if (data == null || data.isExpired()) {
            return null;
        }
        return data;
    }

    /**
     * Check if we have a valid token for an account
     */
    public static boolean hasToken(String accountName) {
        return getToken(accountName) != null;
    }

    /**
     * Remove token for an account (after successful connection)
     */
    public static void removeToken(String accountName) {
        tokens.remove(accountName.toLowerCase());
        MySubMod.LOGGER.info("Removed token for {}", accountName);
    }

    /**
     * Clear all tokens
     */
    public static void clearAll() {
        tokens.clear();
    }
}

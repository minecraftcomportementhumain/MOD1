package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages players in the parking lobby waiting for authentication
 * - 60 second timeout before kick
 * - Queue system for waiting connections (by IP)
 * - Anti-monopolization protections
 */
public class ParkingLobbyManager {
    private static ParkingLobbyManager instance;

    private final Map<UUID, AuthSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Queue<QueueEntry>> waitingQueues = new ConcurrentHashMap<>(); // accountName -> queue
    private final Map<String, String> temporaryWhitelist = new ConcurrentHashMap<>(); // accountName -> authorizedIP
    private final Map<String, Long> whitelistExpiry = new ConcurrentHashMap<>(); // accountName -> expiryTime
    private final Map<String, Boolean> authorizedIPsFromQueue = new ConcurrentHashMap<>(); // Track IPs authorized from queue (accountName -> true)
    private final Timer timeoutTimer = new Timer("ParkingLobby-Timeout", true);

    private static final long AUTH_TIMEOUT_MS = 60 * 1000; // 60 seconds (default)
    private static final long AUTH_TIMEOUT_FROM_QUEUE_MS = 30 * 1000; // 30 seconds (from queue)
    private static final long WHITELIST_EXPIRY_MS = 30 * 1000; // 30 seconds to reconnect
    private static final long QUEUE_ENTRY_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes max in queue
    private static final int MAX_QUEUE_ENTRIES_PER_IP = 3; // Max 3 queue positions per IP globally

    private ParkingLobbyManager() {
        // Start cleanup task for expired queue entries and whitelists
        timeoutTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupExpiredEntries();
            }
        }, 30000, 30000); // Every 30 seconds
    }

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
        final long timeoutMs; // Actual timeout duration for this session
        final String accountType; // "ADMIN" or "PROTECTED_PLAYER"
        final String playerName; // Track player name for queue notification
        final String ipAddress; // Track IP address
        TimerTask timeoutTask;

        AuthSession(String accountType, String playerName, String ipAddress, long timeoutMs) {
            this.startTime = System.currentTimeMillis();
            this.timeoutMs = timeoutMs;
            this.accountType = accountType;
            this.playerName = playerName;
            this.ipAddress = ipAddress;
        }

        long getTimeoutEndMs() {
            return startTime + timeoutMs;
        }
    }

    /**
     * Queue entry for waiting connection
     */
    private static class QueueEntry {
        final String ipAddress;
        final long timestamp;
        long monopolyStartMs; // Guaranteed start time for monopoly window
        long monopolyEndMs;   // Guaranteed end time for monopoly window

        QueueEntry(String ipAddress, long monopolyStartMs, long monopolyEndMs) {
            this.ipAddress = ipAddress;
            this.timestamp = System.currentTimeMillis();
            this.monopolyStartMs = monopolyStartMs;
            this.monopolyEndMs = monopolyEndMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > QUEUE_ENTRY_EXPIRY_MS;
        }
    }

    /**
     * Add player to parking lobby and start timeout
     */
    public void addPlayer(ServerPlayer player, String accountType) {
        UUID playerId = player.getUUID();
        String playerName = player.getName().getString();
        String ipAddress = player.getIpAddress();

        // Check if player came from queue (shorter timeout) by checking if their account+IP was authorized
        String accountIPKey = playerName.toLowerCase() + ":" + ipAddress;
        boolean fromQueue = authorizedIPsFromQueue.remove(accountIPKey) != null;
        long timeout = fromQueue ? AUTH_TIMEOUT_FROM_QUEUE_MS : AUTH_TIMEOUT_MS;
        int timeoutSeconds = (int)(timeout / 1000);

        // Create session
        AuthSession session = new AuthSession(accountType, playerName, ipAddress, timeout);
        activeSessions.put(playerId, session);

        // Schedule timeout kick
        session.timeoutTask = new TimerTask() {
            @Override
            public void run() {
                // Execute on server thread
                if (player.server != null) {
                    player.server.execute(() -> {
                        if (isInParkingLobby(playerId)) {
                            MySubMod.LOGGER.warn("Player {} timed out during authentication ({}s)", playerName, timeoutSeconds);
                            player.connection.disconnect(Component.literal(
                                "§c§lTemps d'authentification écoulé\n\n" +
                                "§7Vous aviez " + timeoutSeconds + " secondes pour vous authentifier."
                            ));
                            removePlayer(playerId);

                            // Notify queue: authorize next person waiting (0ms remaining since timeout)
                            authorizeNextInQueue(playerName, 0);
                        }
                    });
                }
            }
        };

        timeoutTimer.schedule(session.timeoutTask, timeout);

        MySubMod.LOGGER.info("Player {} added to parking lobby as {} ({}s timeout, fromQueue: {})",
            playerName, accountType, timeoutSeconds, fromQueue);
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
     * Clear queue for account (called when authentication succeeds)
     */
    public void clearQueueForAccount(String accountName) {
        Queue<QueueEntry> queue = waitingQueues.remove(accountName);
        if (queue != null) {
            int cleared = queue.size();
            MySubMod.LOGGER.info("Cleared queue for {} ({} entries removed after successful auth)", accountName, cleared);
        }

        // Also remove any temporary whitelist
        temporaryWhitelist.remove(accountName);
        whitelistExpiry.remove(accountName);
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
     * Check if an IP is currently authenticating on an account
     */
    public boolean isIPAuthenticatingOnAccount(String accountName, String ipAddress) {
        String ipOnly = extractIPWithoutPort(ipAddress);
        for (AuthSession session : activeSessions.values()) {
            String sessionIP = extractIPWithoutPort(session.ipAddress);
            if (session.playerName.equalsIgnoreCase(accountName) && sessionIP.equals(ipOnly)) {
                MySubMod.LOGGER.info("IP {} is currently authenticating on {}", ipOnly, accountName);
                return true;
            }
        }
        return false;
    }

    /**
     * Extract IP address without port
     * Handles both IPv4 (/127.0.0.1:port) and IPv6 (/[::1]:port) formats
     */
    private String extractIPWithoutPort(String ipAddress) {
        if (ipAddress == null) return ipAddress;

        // Check for IPv6 format: /[address]:port
        int bracketIndex = ipAddress.lastIndexOf(']');
        if (bracketIndex > 0) {
            // IPv6 - extract everything up to and including the closing bracket
            return ipAddress.substring(0, bracketIndex + 1);
        }

        // IPv4 format: /address:port
        int colonIndex = ipAddress.lastIndexOf(':');
        if (colonIndex > 0) {
            return ipAddress.substring(0, colonIndex);
        }

        return ipAddress;
    }

    /**
     * Add IP to queue for account (returns position in queue, or -1 if rejected, -2 if IP is authenticating)
     */
    public int addToQueue(String accountName, String ipAddress) {
        // Extract IP without port for comparison
        String ipOnly = extractIPWithoutPort(ipAddress);

        // Check if this IP is currently authenticating on this account
        if (isIPAuthenticatingOnAccount(accountName, ipOnly)) {
            MySubMod.LOGGER.warn("IP {} rejected from queue - already authenticating on {}", ipOnly, accountName);
            return -2; // Special code: IP is authenticating
        }

        // Check if IP already has too many queue positions globally
        int globalCount = countQueueEntriesForIP(ipOnly);
        if (globalCount >= MAX_QUEUE_ENTRIES_PER_IP) {
            MySubMod.LOGGER.warn("IP {} rejected from queue - already has {} positions (max {})",
                ipOnly, globalCount, MAX_QUEUE_ENTRIES_PER_IP);
            return -1;
        }

        // Get or create queue for this account
        Queue<QueueEntry> queue = waitingQueues.computeIfAbsent(accountName, k -> new ConcurrentLinkedQueue<>());

        // Check if this IP is already in queue for this account (avoid duplicates)
        for (QueueEntry entry : queue) {
            if (extractIPWithoutPort(entry.ipAddress).equals(ipOnly)) {
                // Already in queue - return current position
                int position = getPositionInQueue(queue, entry.ipAddress);
                MySubMod.LOGGER.info("IP {} already in queue for {} at position {}", ipOnly, accountName, position);
                return position;
            }
        }

        // Calculate guaranteed monopoly window for this new entry
        long[] window = calculateGuaranteedMonopolyWindow(accountName, queue.size() + 1);

        // Add to queue with guaranteed window
        queue.add(new QueueEntry(ipAddress, window[0], window[1]));
        int position = queue.size();

        MySubMod.LOGGER.info("IP {} added to queue for {} at position {} with guaranteed window {}-{}",
            ipAddress, accountName, position, window[0], window[1]);
        return position;
    }

    /**
     * Calculate guaranteed monopoly window based on worst-case scenario
     * This window is GUARANTEED to be valid regardless of what happens
     */
    private long[] calculateGuaranteedMonopolyWindow(String accountName, int position) {
        // Get the active session for this account to get exact timeout
        AuthSession activeSession = null;
        for (AuthSession session : activeSessions.values()) {
            if (session.playerName.equalsIgnoreCase(accountName)) {
                activeSession = session;
                break;
            }
        }

        long monopolyStartMs;
        if (activeSession != null && position == 1) {
            // First in queue - guaranteed to start when current session times out AT THE LATEST
            monopolyStartMs = activeSession.getTimeoutEndMs();
        } else if (position == 1) {
            // First in queue but no active session - immediate
            monopolyStartMs = System.currentTimeMillis();
        } else {
            // Not first in queue - calculate based on worst case (everyone before times out fully)
            long baseTime = activeSession != null ? activeSession.getTimeoutEndMs() : System.currentTimeMillis();
            // Each person ahead gets their full timeout (worst case)
            // Position 2 = baseTime + 1*60s, Position 3 = baseTime + 2*60s, etc.
            monopolyStartMs = baseTime + ((position - 1) * AUTH_TIMEOUT_MS);
        }

        long monopolyEndMs = monopolyStartMs + WHITELIST_EXPIRY_MS;

        return new long[]{monopolyStartMs, monopolyEndMs};
    }

    /**
     * Get monopoly window for queued IP (returns stored guaranteed window)
     * Returns array: [startTimeMs, endTimeMs] or null if not in queue
     */
    public long[] getMonopolyWindow(String accountName, String ipAddress) {
        Queue<QueueEntry> queue = waitingQueues.get(accountName);
        if (queue == null || queue.isEmpty()) {
            return null;
        }

        // Extract IP without port for comparison
        String ipOnly = extractIPWithoutPort(ipAddress);

        // Find entry and return its guaranteed window
        for (QueueEntry entry : queue) {
            if (extractIPWithoutPort(entry.ipAddress).equals(ipOnly)) {
                return new long[]{entry.monopolyStartMs, entry.monopolyEndMs};
            }
        }

        return null;
    }

    /**
     * Check if IP is authorized for account (temporary whitelist)
     */
    public boolean isAuthorized(String accountName, String ipAddress) {
        String authorizedIP = temporaryWhitelist.get(accountName);
        if (authorizedIP == null) {
            return false;
        }

        // Compare IPs without port (allow reconnection with different port)
        String ipOnly = extractIPWithoutPort(ipAddress);
        String authorizedIPOnly = extractIPWithoutPort(authorizedIP);
        if (!authorizedIPOnly.equals(ipOnly)) {
            return false;
        }

        // Check expiry
        Long expiry = whitelistExpiry.get(accountName);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            // Expired - remove
            temporaryWhitelist.remove(accountName);
            whitelistExpiry.remove(accountName);
            MySubMod.LOGGER.info("Whitelist expired for {} (IP: {})", accountName, ipAddress);
            return false;
        }

        return true;
    }

    /**
     * Consume authorization (remove from whitelist after use and mark IP as from queue)
     */
    public void consumeAuthorization(String accountName, String ipAddress) {
        temporaryWhitelist.remove(accountName);
        whitelistExpiry.remove(accountName);

        // Mark this account+IP combination as coming from queue
        String accountIPKey = accountName.toLowerCase() + ":" + ipAddress;
        authorizedIPsFromQueue.put(accountIPKey, true);

        // Remove from queue as well (compare IPs without port)
        Queue<QueueEntry> queue = waitingQueues.get(accountName);
        if (queue != null) {
            String ipOnly = extractIPWithoutPort(ipAddress);
            queue.removeIf(entry -> extractIPWithoutPort(entry.ipAddress).equals(ipOnly));
        }

        MySubMod.LOGGER.info("Authorization consumed for {} (IP: {}, marked as fromQueue)", accountName, ipAddress);
    }

    /**
     * Get player name from parking lobby session
     */
    public String getPlayerName(UUID playerId) {
        AuthSession session = activeSessions.get(playerId);
        return session != null ? session.playerName : null;
    }

    /**
     * Authorize next person in queue (public for use by event handlers)
     * @param accountName The account name
     * @param remainingTimeMs Remaining time from previous session (0 if timeout, >0 if disconnect)
     */
    public void authorizeNextInQueue(String accountName, long remainingTimeMs) {
        Queue<QueueEntry> queue = waitingQueues.get(accountName);
        if (queue == null || queue.isEmpty()) {
            MySubMod.LOGGER.info("No one waiting in queue for {}", accountName);
            return;
        }

        // Remove expired entries first
        queue.removeIf(QueueEntry::isExpired);

        // Get next in queue
        QueueEntry next = queue.poll();
        if (next == null) {
            MySubMod.LOGGER.info("Queue empty after cleanup for {}", accountName);
            return;
        }

        // Calculate actual monopoly window (may be earlier than guaranteed)
        long actualStartMs = System.currentTimeMillis();
        long monopolyDuration = remainingTimeMs + WHITELIST_EXPIRY_MS;
        long actualEndMs = actualStartMs + monopolyDuration;

        // The actual end must be at least the guaranteed end (extend if early disconnect gives more time)
        if (actualEndMs > next.monopolyEndMs) {
            next.monopolyEndMs = actualEndMs;
            MySubMod.LOGGER.info("Extended monopoly window for {} due to early disconnect", next.ipAddress);
        }

        // Add to temporary whitelist - use the guaranteed end time to honor the promise
        temporaryWhitelist.put(accountName, next.ipAddress);
        whitelistExpiry.put(accountName, next.monopolyEndMs);

        MySubMod.LOGGER.info("Authorized IP {} for account {} (window until {}, {} still waiting)",
            next.ipAddress, accountName, next.monopolyEndMs, queue.size());

        // Update monopoly windows for remaining queue entries (they may get their turn earlier)
        updateQueueWindowsAfterAuthorization(queue, actualStartMs);
    }

    /**
     * Update monopoly windows for queue after someone gets authorized early
     */
    private void updateQueueWindowsAfterAuthorization(Queue<QueueEntry> queue, long newBaseTime) {
        if (queue.isEmpty()) return;

        int position = 1;
        for (QueueEntry entry : queue) {
            // Calculate new potential start time (if everyone before gets their turn early)
            long newStartMs = newBaseTime + ((position - 1) * AUTH_TIMEOUT_MS);

            // Only update if new time is EARLIER (we never delay the guaranteed window)
            // But we keep the guaranteed end time (they still get their full 30s from guaranteed start)
            if (newStartMs < entry.monopolyStartMs) {
                long guaranteedDuration = entry.monopolyEndMs - entry.monopolyStartMs;
                entry.monopolyStartMs = newStartMs;
                entry.monopolyEndMs = newStartMs + guaranteedDuration;
                MySubMod.LOGGER.info("Updated monopoly window for position {} to start at {} (earlier)",
                    position, newStartMs);
            }
            position++;
        }
    }

    /**
     * Get remaining time for active session on account (in milliseconds)
     */
    public long getRemainingTimeForAccount(String accountName) {
        for (AuthSession session : activeSessions.values()) {
            if (session.playerName.equalsIgnoreCase(accountName)) {
                long elapsed = System.currentTimeMillis() - session.startTime;
                long remaining = session.timeoutMs - elapsed;
                return Math.max(0, remaining);
            }
        }
        return 0;
    }

    /**
     * Count total queue entries for an IP across all accounts
     */
    private int countQueueEntriesForIP(String ipAddress) {
        String ipOnly = extractIPWithoutPort(ipAddress);
        int count = 0;
        for (Queue<QueueEntry> queue : waitingQueues.values()) {
            for (QueueEntry entry : queue) {
                String entryIP = extractIPWithoutPort(entry.ipAddress);
                if (entryIP.equals(ipOnly) && !entry.isExpired()) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Get position of IP in queue
     */
    private int getPositionInQueue(Queue<QueueEntry> queue, String ipAddress) {
        String ipOnly = extractIPWithoutPort(ipAddress);
        int position = 1;
        for (QueueEntry entry : queue) {
            if (extractIPWithoutPort(entry.ipAddress).equals(ipOnly)) {
                return position;
            }
            position++;
        }
        return -1;
    }

    /**
     * Cleanup expired queue entries and whitelists
     */
    private void cleanupExpiredEntries() {
        // Clean expired queue entries
        for (Map.Entry<String, Queue<QueueEntry>> entry : waitingQueues.entrySet()) {
            Queue<QueueEntry> queue = entry.getValue();
            queue.removeIf(QueueEntry::isExpired);

            // Remove empty queues
            if (queue.isEmpty()) {
                waitingQueues.remove(entry.getKey());
            }
        }

        // Clean expired whitelists
        long now = System.currentTimeMillis();
        whitelistExpiry.entrySet().removeIf(entry -> {
            if (now > entry.getValue()) {
                temporaryWhitelist.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Get queue size for account
     */
    public int getQueueSize(String accountName) {
        Queue<QueueEntry> queue = waitingQueues.get(accountName);
        return queue != null ? queue.size() : 0;
    }

    /**
     * Cleanup on server shutdown
     */
    public void shutdown() {
        timeoutTimer.cancel();
        activeSessions.clear();
        waitingQueues.clear();
        temporaryWhitelist.clear();
        whitelistExpiry.clear();
        authorizedIPsFromQueue.clear();
    }
}

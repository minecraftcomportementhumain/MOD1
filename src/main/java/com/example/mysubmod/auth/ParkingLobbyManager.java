package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

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
    private final Map<String, Set<UUID>> queueCandidates = new ConcurrentHashMap<>(); // accountName -> Set of UUIDs awaiting password verification
    private final Map<String, String> temporaryWhitelist = new ConcurrentHashMap<>(); // accountName -> authorizedIP
    private final Map<String, Long> whitelistExpiry = new ConcurrentHashMap<>(); // accountName -> expiryTime
    private final Map<String, Boolean> authorizedIPsFromQueue = new ConcurrentHashMap<>(); // Track IPs authorized from queue (accountName -> true)
    private final Map<String, String> temporaryNameToAccount = new ConcurrentHashMap<>(); // temporaryName -> original accountName
    private final Map<String, String> activeTokens = new ConcurrentHashMap<>(); // accountName -> active token for current monopoly window
    private final Map<UUID, String> candidateIPs = new ConcurrentHashMap<>(); // UUID -> IP address (for DoS protection tracking)
    private final Map<UUID, Long> candidateJoinTime = new ConcurrentHashMap<>(); // UUID -> join timestamp (for age-based eviction)
    private final Timer timeoutTimer = new Timer("ParkingLobby-Timeout", true);

    private static final long AUTH_TIMEOUT_MS = 60 * 1000; // 60 seconds (default)
    private static final long AUTH_TIMEOUT_FROM_QUEUE_MS = 30 * 1000; // 30 seconds (from queue)
    private static final long WHITELIST_EXPIRY_MS = 45 * 1000; // 45 seconds to reconnect (monopoly window)
    private static final long QUEUE_ENTRY_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes max in queue
    private static final int MAX_QUEUE_SIZE = 1; // Max 1 person waiting in queue per account
    private static final int MAX_CANDIDATES_PER_ACCOUNT_PER_IP = 4; // Max 4 parallel attempts per account from same IP (DoS protection)
    private static final int MAX_CANDIDATES_PER_IP_GLOBAL = 10; // Max 10 _Q_ accounts from same IP across all accounts (DoS protection)
    private static final long CANDIDATE_MIN_AGE_FOR_EVICTION_MS = 20 * 1000; // 20 seconds - min age before a candidate can be evicted

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
        final String token; // Unique token for this queue entry
        long monopolyStartMs; // Guaranteed start time for monopoly window
        long monopolyEndMs;   // Guaranteed end time for monopoly window

        QueueEntry(String ipAddress, long monopolyStartMs, long monopolyEndMs) {
            this.ipAddress = ipAddress;
            this.timestamp = System.currentTimeMillis();
            this.monopolyStartMs = monopolyStartMs;
            this.monopolyEndMs = monopolyEndMs;
            this.token = generateToken();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > QUEUE_ENTRY_EXPIRY_MS;
        }

        private static String generateToken() {
            // Generate a 6-character alphanumeric token
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            Random random = new Random();
            StringBuilder token = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                token.append(chars.charAt(random.nextInt(chars.length())));
            }
            return token.toString();
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

        // Create invisible barrier around parking lobby position
        createParkingLobbyBarriers(player.serverLevel());

        MySubMod.LOGGER.info("Player {} added to parking lobby as {} ({}s timeout, fromQueue: {})",
            playerName, accountType, timeoutSeconds, fromQueue);
    }

    /**
     * Create invisible barriers (barrier blocks) around the parking lobby position
     * to prevent any movement
     * Only creates barriers if they don't already exist (multiple players can be in parking lobby)
     */
    private void createParkingLobbyBarriers(ServerLevel world) {
        BlockPos center = new BlockPos(10000, 200, 10000);
        int radius = 3; // 3 blocks radius around player
        int height = 5; // 5 blocks high

        // Create a box of barriers around the player
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= height; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // Only place barriers on the edges (hollow box)
                    boolean isEdge = Math.abs(x) == radius || Math.abs(z) == radius || y == -1 || y == height;

                    if (isEdge) {
                        BlockPos barrierPos = center.offset(x, y, z);
                        // Only place if not already a barrier (might be from another player)
                        if (!world.getBlockState(barrierPos).is(Blocks.BARRIER)) {
                            world.setBlock(barrierPos, Blocks.BARRIER.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    /**
     * Remove barriers around parking lobby position
     */
    private void removeParkingLobbyBarriers(ServerLevel world) {
        BlockPos center = new BlockPos(10000, 200, 10000);
        int radius = 3;
        int height = 5;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= height; y++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean isEdge = Math.abs(x) == radius || Math.abs(z) == radius || y == -1 || y == height;

                    if (isEdge) {
                        BlockPos barrierPos = center.offset(x, y, z);
                        // Only remove if it's a barrier block
                        if (world.getBlockState(barrierPos).is(Blocks.BARRIER)) {
                            world.setBlock(barrierPos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
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
     * Remove player from parking lobby and clean up barriers
     */
    public void removePlayer(UUID playerId, ServerLevel world) {
        AuthSession session = activeSessions.remove(playerId);
        if (session != null && session.timeoutTask != null) {
            session.timeoutTask.cancel();
        }

        // Clean up candidate tracking if this player was a queue candidate
        boolean wasCandidate = false;
        String accountName = null;

        // Find which account this candidate belongs to
        for (Map.Entry<String, Set<UUID>> entry : queueCandidates.entrySet()) {
            if (entry.getValue().contains(playerId)) {
                accountName = entry.getKey();
                wasCandidate = true;
                break;
            }
        }

        // If player was a queue candidate, clean up all tracking
        if (wasCandidate && accountName != null) {
            removeQueueCandidate(accountName, playerId);
            MySubMod.LOGGER.info("Cleaned up queue candidate tracking for player {} from account {} on disconnect",
                playerId, accountName);
        }

        // Only remove barriers if no other players are in parking lobby
        if (activeSessions.isEmpty()) {
            removeParkingLobbyBarriers(world);
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

        // Also clear all queue candidates if any
        Set<UUID> candidates = queueCandidates.remove(accountName);
        if (candidates != null && !candidates.isEmpty()) {
            MySubMod.LOGGER.info("Cleared {} queue candidate(s) for {} after successful auth", candidates.size(), accountName);
            // Clean up IP tracking and join time for these candidates
            for (UUID candidateId : candidates) {
                candidateIPs.remove(candidateId);
                candidateJoinTime.remove(candidateId);
            }
        }

        // Also remove any temporary whitelist
        temporaryWhitelist.remove(accountName);
        whitelistExpiry.remove(accountName);
    }

    /**
     * Check if a queue exists for this account (either waiting queue or active IP authorization)
     */
    public boolean hasQueue(String accountName) {
        // Check if there are people waiting in queue
        Queue<QueueEntry> queue = waitingQueues.get(accountName);
        if (queue != null && !queue.isEmpty()) {
            return true;
        }

        // Check if there's an active IP authorization (monopoly window)
        if (temporaryWhitelist.containsKey(accountName)) {
            // Verify the authorization hasn't expired
            Long expiryTime = whitelistExpiry.get(accountName);
            if (expiryTime != null && System.currentTimeMillis() < expiryTime) {
                return true; // Active monopoly window
            }
        }

        return false;
    }

    /**
     * Check if there's room in the queue for this account
     * Only counts actual queue entries (people who entered correct password)
     * Queue candidates (awaiting password) do NOT count - they can be multiple
     * Returns true if queue size < MAX_QUEUE_SIZE
     */
    public boolean hasQueueRoom(String accountName) {
        // Check current queue size (only people who entered password)
        Queue<QueueEntry> queue = waitingQueues.get(accountName);
        int queueSize = (queue != null) ? queue.size() : 0;

        // Room in queue if actual queue size is below limit
        // Candidates don't count - multiple people can try to enter password
        return queueSize < MAX_QUEUE_SIZE;
    }

    /**
     * Mark a player as queue candidate (awaiting password verification)
     * Multiple candidates can exist for the same account
     * DoS Protection: Enforces limits per IP with age-based eviction
     * @param accountName The account name
     * @param playerId The player UUID
     * @param ipAddress The player IP address
     * @param server The server instance (for kicking old candidates if needed)
     * @return true if added successfully, false if limits exceeded and no evictable candidates found
     */
    public boolean addQueueCandidate(String accountName, UUID playerId, String ipAddress, net.minecraft.server.MinecraftServer server) {
        String ipOnly = extractIPWithoutPort(ipAddress);
        long now = System.currentTimeMillis();

        // Check limit 1: Max candidates per account from same IP
        int countFromThisIP = 0;
        UUID oldestEvictableForAccount = null;
        long oldestTimeForAccount = Long.MAX_VALUE;

        Set<UUID> candidates = queueCandidates.get(accountName);
        if (candidates != null) {
            for (UUID candidateId : candidates) {
                String candidateIP = candidateIPs.get(candidateId);
                if (candidateIP != null && extractIPWithoutPort(candidateIP).equals(ipOnly)) {
                    countFromThisIP++;

                    // Track oldest evictable candidate (≥20s old)
                    Long joinTime = candidateJoinTime.get(candidateId);
                    if (joinTime != null) {
                        long age = now - joinTime;
                        if (age >= CANDIDATE_MIN_AGE_FOR_EVICTION_MS && joinTime < oldestTimeForAccount) {
                            oldestTimeForAccount = joinTime;
                            oldestEvictableForAccount = candidateId;
                        }
                    }
                }
            }
        }

        // Enforce limit: if adding this candidate would exceed the limit, we must evict before adding
        if (countFromThisIP >= MAX_CANDIDATES_PER_ACCOUNT_PER_IP) {
            // Try to evict oldest candidate if available
            if (oldestEvictableForAccount != null) {
                evictCandidate(oldestEvictableForAccount, accountName, server, "evicted_for_new_candidate_account");
                MySubMod.LOGGER.info("DoS Protection: Evicted old candidate {} from account {} to make room for new candidate (age: {}s)",
                    oldestEvictableForAccount, accountName, (now - oldestTimeForAccount) / 1000);
                countFromThisIP--; // Decrement count after eviction
            } else {
                MySubMod.LOGGER.warn("DoS Protection: IP {} exceeded max candidates per account for {} ({}/{}) - no evictable candidates",
                    ipOnly, accountName, countFromThisIP, MAX_CANDIDATES_PER_ACCOUNT_PER_IP);
                return false;
            }
        }

        // Check limit 2: Max total _Q_ candidates from this IP across all accounts
        int totalCandidatesFromIP = 0;
        UUID oldestEvictableGlobal = null;
        long oldestTimeGlobal = Long.MAX_VALUE;
        String oldestEvictableAccount = null;

        for (Map.Entry<String, Set<UUID>> entry : queueCandidates.entrySet()) {
            for (UUID candidateId : entry.getValue()) {
                String candidateIP = candidateIPs.get(candidateId);
                if (candidateIP != null && extractIPWithoutPort(candidateIP).equals(ipOnly)) {
                    totalCandidatesFromIP++;

                    // Track oldest evictable candidate globally
                    Long joinTime = candidateJoinTime.get(candidateId);
                    if (joinTime != null) {
                        long age = now - joinTime;
                        if (age >= CANDIDATE_MIN_AGE_FOR_EVICTION_MS && joinTime < oldestTimeGlobal) {
                            oldestTimeGlobal = joinTime;
                            oldestEvictableGlobal = candidateId;
                            oldestEvictableAccount = entry.getKey();
                        }
                    }
                }
            }
        }

        // Enforce limit: if we're at the limit, we must evict before adding
        if (totalCandidatesFromIP >= MAX_CANDIDATES_PER_IP_GLOBAL) {
            // Try to evict oldest candidate globally if available
            if (oldestEvictableGlobal != null) {
                evictCandidate(oldestEvictableGlobal, oldestEvictableAccount, server, "evicted_for_new_candidate_global");
                MySubMod.LOGGER.info("DoS Protection: Evicted old candidate {} (account: {}) globally to make room for new candidate (age: {}s)",
                    oldestEvictableGlobal, oldestEvictableAccount, (now - oldestTimeGlobal) / 1000);
                totalCandidatesFromIP--; // Decrement count after eviction
            } else {
                MySubMod.LOGGER.warn("DoS Protection: IP {} exceeded global max candidates ({}/{}) - no evictable candidates",
                    ipOnly, totalCandidatesFromIP, MAX_CANDIDATES_PER_IP_GLOBAL);
                return false;
            }
        }

        // Add candidate
        queueCandidates.computeIfAbsent(accountName, k -> ConcurrentHashMap.newKeySet()).add(playerId);
        candidateIPs.put(playerId, ipAddress);
        candidateJoinTime.put(playerId, now);

        // Log with accurate counts (after eviction and addition)
        MySubMod.LOGGER.info("Player {} marked as queue candidate for {} (IP: {}, account candidates from IP: {}, global candidates from IP: {})",
            playerId, accountName, ipOnly, countFromThisIP + 1, totalCandidatesFromIP + 1);
        return true;
    }

    /**
     * Evict a candidate (kick them to make room for a new candidate)
     */
    private void evictCandidate(UUID candidateId, String accountName, net.minecraft.server.MinecraftServer server, String reason) {
        // Remove from tracking
        removeQueueCandidate(accountName, candidateId);

        // Kick the player
        net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(candidateId);
        if (player != null) {
            String message;
            if ("evicted_for_new_candidate_account".equals(reason)) {
                message = "§c§lConnexion remplacée\n\n" +
                          "§eUn nouveau candidat a pris votre place.\n" +
                          "§7Vous étiez en attente depuis trop longtemps (>20s).\n\n" +
                          "§7Veuillez réessayer.";
            } else if ("evicted_for_new_candidate_global".equals(reason)) {
                message = "§c§lConnexion remplacée\n\n" +
                          "§eTrop de tentatives depuis votre IP.\n" +
                          "§7Un nouveau candidat a pris votre place.\n\n" +
                          "§7Veuillez réessayer.";
            } else {
                message = "§c§lConnexion remplacée\n\n" +
                          "§7" + reason;
            }

            player.connection.disconnect(Component.literal(message));
            MySubMod.LOGGER.info("Evicted candidate {} from account {} - reason: {}", candidateId, accountName, reason);
        }
    }

    /**
     * Check if a player is a queue candidate
     */
    public boolean isQueueCandidate(UUID playerId) {
        for (Set<UUID> candidates : queueCandidates.values()) {
            if (candidates.contains(playerId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove a specific queue candidate by UUID (failed password or kicked)
     */
    public void removeQueueCandidate(String accountName, UUID playerId) {
        Set<UUID> candidates = queueCandidates.get(accountName);
        if (candidates != null) {
            boolean removed = candidates.remove(playerId);
            if (removed) {
                MySubMod.LOGGER.info("Queue candidate removed for {}: {}", accountName, playerId);
                // Clean up empty sets
                if (candidates.isEmpty()) {
                    queueCandidates.remove(accountName);
                }
            }
        }

        // Clean up IP tracking and join time
        candidateIPs.remove(playerId);
        candidateJoinTime.remove(playerId);
    }

    /**
     * Promote queue candidate to actual queue (after successful password verification)
     * Returns the queue position, or -1 if queue is full
     * Thread-safe: uses synchronized block to prevent race conditions
     */
    public synchronized int promoteQueueCandidateToQueue(String accountName, UUID playerId, String ipAddress) {
        Set<UUID> candidates = queueCandidates.get(accountName);
        if (candidates == null || !candidates.remove(playerId)) {
            MySubMod.LOGGER.warn("Attempted to promote non-existent queue candidate {} for {}", playerId, accountName);
            return -1;
        }

        // Clean up empty sets
        if (candidates.isEmpty()) {
            queueCandidates.remove(accountName);
        }

        // Check if queue is full BEFORE adding (thread-safe check)
        Queue<QueueEntry> queue = waitingQueues.get(accountName);
        int currentSize = (queue != null) ? queue.size() : 0;

        if (currentSize >= MAX_QUEUE_SIZE) {
            // Queue is full - re-add as candidate and reject
            candidates = queueCandidates.computeIfAbsent(accountName, k -> ConcurrentHashMap.newKeySet());
            candidates.add(playerId);
            MySubMod.LOGGER.warn("Queue full for {} - candidate {} rejected (current size: {})",
                accountName, playerId, currentSize);
            return -1;
        }

        // Add to actual queue (but DON'T use the calculated future window - start immediately)
        int position = addToQueueImmediately(accountName, ipAddress);
        MySubMod.LOGGER.info("Queue candidate promoted to queue for {} at position {}", accountName, position);

        return position;
    }

    /**
     * Add IP to queue with IMMEDIATE monopoly window (starts NOW, not in the future)
     * Used when someone enters password while indiv1 is authenticating
     * @return position in queue (always 1 for first entry)
     */
    private synchronized int addToQueueImmediately(String accountName, String ipAddress) {
        // Extract IP without port
        String ipOnly = extractIPWithoutPort(ipAddress);

        // Get or create queue for this account
        Queue<QueueEntry> queue = waitingQueues.computeIfAbsent(accountName, k -> new ConcurrentLinkedQueue<>());

        // Check if queue is full
        if (queue.size() >= MAX_QUEUE_SIZE) {
            MySubMod.LOGGER.warn("Queue full for {} - rejecting IP {}", accountName, ipOnly);
            return -1;
        }

        // Calculate IMMEDIATE monopoly window (starts NOW)
        long monopolyStartMs = System.currentTimeMillis();
        long monopolyEndMs = monopolyStartMs + WHITELIST_EXPIRY_MS; // 45 seconds from NOW

        // Add to queue with immediate window
        QueueEntry newEntry = new QueueEntry(ipOnly, monopolyStartMs, monopolyEndMs);
        queue.add(newEntry);
        int position = queue.size();

        MySubMod.LOGGER.info("IP {} added to queue for {} at position {} with IMMEDIATE monopoly window (starts NOW, ends in 45s, token: {})",
            ipOnly, accountName, position, newEntry.token);

        return position;
    }

    /**
     * Kick the player currently authenticating on this account
     * Called when someone from the queue gets promoted (indiv2 takes over)
     * @param accountName The account name
     * @param server The Minecraft server instance
     */
    public void kickAuthenticatingPlayer(String accountName, net.minecraft.server.MinecraftServer server) {
        // Find the player who is currently authenticating on this account
        for (Map.Entry<UUID, AuthSession> entry : activeSessions.entrySet()) {
            if (entry.getValue().playerName.equalsIgnoreCase(accountName)) {
                UUID playerUUID = entry.getKey();
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);

                if (player != null) {
                    MySubMod.LOGGER.info("Kicking authenticating player {} - queue takes over", accountName);

                    // Kick the authenticating player
                    player.connection.disconnect(Component.literal(
                        "§c§lConnexion interrompue\n\n" +
                        "§eUn joueur en file d'attente a priorité.\n" +
                        "§7Vous avez été déconnecté pour libérer la place."
                    ));

                    // Remove from parking lobby
                    removePlayer(playerUUID);
                }
                break;
            }
        }
    }

    /**
     * Kick all remaining queue candidates for an account
     * Called when someone successfully enters the queue OR when authentication succeeds
     * @param accountName The account name
     * @param server The Minecraft server instance
     * @param reason The reason for kicking (for custom message)
     */
    public void kickRemainingQueueCandidates(String accountName, net.minecraft.server.MinecraftServer server, String reason) {
        MySubMod.LOGGER.info("DEBUG: kickRemainingQueueCandidates called for account: {}, reason: {}", accountName, reason);
        MySubMod.LOGGER.info("DEBUG: queueCandidates content: {}", queueCandidates.keySet());

        Set<UUID> candidates = queueCandidates.remove(accountName);
        if (candidates == null || candidates.isEmpty()) {
            MySubMod.LOGGER.warn("DEBUG: No queue candidates found for account: {}", accountName);
            return;
        }

        MySubMod.LOGGER.info("Kicking {} remaining queue candidate(s) for {} - reason: {}",
            candidates.size(), accountName, reason);

        // Find and kick all players who are still queue candidates
        for (UUID candidateUUID : candidates) {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(candidateUUID);
            if (player != null) {
                // Find the player name from active sessions
                AuthSession session = activeSessions.get(candidateUUID);
                String candidateName = session != null ? session.playerName : "unknown";

                MySubMod.LOGGER.info("Kicking queue candidate {} (temp name: {}) - {}",
                    candidateUUID, candidateName, reason);

                // Kick the player with appropriate message
                String message;
                if ("queue_full".equals(reason)) {
                    message = "§c§lFile d'attente complète\n\n" +
                              "§eUn autre joueur a entré le mot de passe avant vous.\n" +
                              "§7La file d'attente est limitée à §e1 personne§7.";
                } else if ("auth_success".equals(reason)) {
                    message = "§c§lFile d'attente annulée\n\n" +
                              "§eLe joueur s'est authentifié avec succès.\n" +
                              "§7La file d'attente n'est plus nécessaire.";
                } else {
                    message = "§c§lFile d'attente annulée\n\n" +
                              "§7" + reason;
                }

                player.connection.disconnect(Component.literal(message));
            }

            // Clean up IP tracking and join time
            candidateIPs.remove(candidateUUID);
            candidateJoinTime.remove(candidateUUID);
        }
    }

    /**
     * Kick all remaining queue candidates (convenience method with default reason)
     */
    public void kickRemainingQueueCandidates(String accountName, net.minecraft.server.MinecraftServer server) {
        kickRemainingQueueCandidates(accountName, server, "queue_full");
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
     * Check if anyone is currently authenticating on an account (regardless of IP)
     */
    public boolean isAccountBeingAuthenticated(String accountName) {
        for (AuthSession session : activeSessions.values()) {
            if (session.playerName.equalsIgnoreCase(accountName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get active sessions (for checking queue candidates)
     */
    public Map<UUID, String> getActiveSessionPlayers() {
        Map<UUID, String> result = new java.util.HashMap<>();
        for (Map.Entry<UUID, AuthSession> entry : activeSessions.entrySet()) {
            result.put(entry.getKey(), entry.getValue().playerName);
        }
        return result;
    }

    /**
     * Register a temporary name mapping for queue candidate
     */
    public void registerTemporaryName(String temporaryName, String originalAccountName) {
        temporaryNameToAccount.put(temporaryName, originalAccountName);
        MySubMod.LOGGER.info("Registered temporary name: {} -> {}", temporaryName, originalAccountName);
    }

    /**
     * Get original account name from temporary name
     */
    public String getOriginalAccountName(String temporaryName) {
        return temporaryNameToAccount.get(temporaryName);
    }

    /**
     * Remove temporary name mapping
     */
    public void removeTemporaryName(String temporaryName) {
        temporaryNameToAccount.remove(temporaryName);
    }

    /**
     * Check if a player name is a temporary queue candidate name
     */
    public boolean isTemporaryQueueName(String playerName) {
        // Check if the name exists in our temporary name mapping
        // This prevents exploits where a free player creates an account starting with "_Q_"
        return playerName != null && temporaryNameToAccount.containsKey(playerName);
    }

    /**
     * Extract IP address without port and without leading slash
     * Handles both IPv4 (/127.0.0.1:port) and IPv6 (/[::1]:port) formats
     */
    private String extractIPWithoutPort(String ipAddress) {
        if (ipAddress == null) return ipAddress;

        String result = ipAddress;

        // Remove leading slash if present
        if (result.startsWith("/")) {
            result = result.substring(1);
        }

        // Check for IPv6 format: [address]:port
        int bracketIndex = result.lastIndexOf(']');
        if (bracketIndex > 0) {
            // IPv6 - extract everything up to and including the closing bracket
            return result.substring(0, bracketIndex + 1);
        }

        // IPv4 format: address:port
        int colonIndex = result.lastIndexOf(':');
        if (colonIndex > 0) {
            return result.substring(0, colonIndex);
        }

        return result;
    }

    /**
     * Add IP to queue for account
     * Multiple entries with the same IP are allowed (distinguished by unique tokens)
     * @return position in queue (1-based), or -1 if rejected
     */
    public int addToQueue(String accountName, String ipAddress) {
        // Extract IP without port for comparison
        String ipOnly = extractIPWithoutPort(ipAddress);

        // Note: We do NOT check if IP is currently authenticating
        // The token system allows multiple clients from the same IP to queue
        // Each will get a unique token to distinguish them

        // Get or create queue for this account
        Queue<QueueEntry> queue = waitingQueues.computeIfAbsent(accountName, k -> new ConcurrentLinkedQueue<>());

        // Check if queue is full (MAX_QUEUE_SIZE = 1 person max)
        if (queue.size() >= MAX_QUEUE_SIZE) {
            MySubMod.LOGGER.warn("Queue full for {} - rejecting IP {}", accountName, ipOnly);
            return -1; // Queue full
        }

        // Note: We allow multiple queue entries with the same IP (for different accounts)
        // Each entry will have a unique token to distinguish different clients
        // This supports multiple players from the same IP (e.g., same household, same NAT)

        // Calculate guaranteed monopoly window for this new entry
        long[] window = calculateGuaranteedMonopolyWindow(accountName, queue.size() + 1);

        // Add to queue with guaranteed window using normalized IP (without port, for consistent comparison)
        QueueEntry newEntry = new QueueEntry(ipOnly, window[0], window[1]);
        queue.add(newEntry);
        int position = queue.size();

        MySubMod.LOGGER.info("IP {} added to queue for {} at position {} with token {} (guaranteed window {}-{})",
            ipOnly, accountName, position, newEntry.token, window[0], window[1]);
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
     * Get token for queued IP
     * Returns the token or null if not in queue
     */
    public String getQueueToken(String accountName, String ipAddress) {
        Queue<QueueEntry> queue = waitingQueues.get(accountName);
        if (queue == null || queue.isEmpty()) {
            return null;
        }

        // Extract IP without port for comparison
        String ipOnly = extractIPWithoutPort(ipAddress);

        // Find entry and return its token
        for (QueueEntry entry : queue) {
            if (extractIPWithoutPort(entry.ipAddress).equals(ipOnly)) {
                return entry.token;
            }
        }

        return null;
    }

    /**
     * Check if IP is authorized for account (temporary whitelist)
     * Note: This only checks IP, not token. Use isAuthorizedWithToken() for full verification.
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
            activeTokens.remove(accountName);
            MySubMod.LOGGER.info("Whitelist expired for {} (IP: {})", accountName, ipAddress);
            return false;
        }

        return true;
    }

    /**
     * Check if IP + token combination is authorized for account
     */
    public boolean isAuthorizedWithToken(String accountName, String ipAddress, String token) {
        // First check if IP is authorized
        if (!isAuthorized(accountName, ipAddress)) {
            return false;
        }

        // Then verify token
        String expectedToken = activeTokens.get(accountName);
        if (expectedToken == null || !expectedToken.equals(token)) {
            MySubMod.LOGGER.warn("Token mismatch for {} - expected: {}, got: {}",
                accountName, expectedToken, token);
            return false;
        }

        return true;
    }

    /**
     * Get the active token for an account (for displaying to player)
     */
    public String getActiveToken(String accountName) {
        return activeTokens.get(accountName);
    }

    /**
     * Consume authorization (remove from whitelist after use and mark IP as from queue)
     */
    public void consumeAuthorization(String accountName, String ipAddress) {
        temporaryWhitelist.remove(accountName);
        whitelistExpiry.remove(accountName);
        activeTokens.remove(accountName); // Clean up token

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
        activeTokens.put(accountName, next.token); // Store the token for verification

        MySubMod.LOGGER.info("Authorized IP {} for account {} with token {} (window until {}, {} still waiting)",
            next.ipAddress, accountName, next.token, next.monopolyEndMs, queue.size());

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
        candidateIPs.clear();
        candidateJoinTime.clear();
    }
}

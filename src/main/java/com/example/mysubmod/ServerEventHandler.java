package com.example.mysubmod;

import com.example.mysubmod.commands.SubModeCommand;
import com.example.mysubmod.network.AdminStatusPacket;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.network.SubModeChangePacket;
import com.example.mysubmod.submodes.SubModeManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SubModeCommand.register(event.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.example.mysubmod.auth.AdminAuthManager adminAuthManager = com.example.mysubmod.auth.AdminAuthManager.getInstance();
            com.example.mysubmod.auth.AuthManager authManager = com.example.mysubmod.auth.AuthManager.getInstance();
            com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();

            String playerName = player.getName().getString();

            // Check if this is a temporary name (queue candidate) OR unauthenticated protected/admin
            boolean isQueueCandidate = false;
            boolean isUnauthenticatedRestricted = false; // Protected/Admin not authenticated
            String actualAccountName = playerName;

            if (playerName.startsWith("_Q_")) {
                // This is a temporary name - get the original account name
                actualAccountName = parkingLobby.getOriginalAccountName(playerName);
                if (actualAccountName != null) {
                    isQueueCandidate = true;
                    MySubMod.LOGGER.info("Detected queue candidate with temporary name: {} -> actual account: {}",
                        playerName, actualAccountName);
                }
            } else {
                // Check if this is a protected/admin account that is not authenticated
                com.example.mysubmod.auth.AuthManager.AccountType accountType = authManager.getAccountType(playerName);
                boolean isProtectedOrAdmin = (accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER ||
                                              accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN);

                if (isProtectedOrAdmin) {
                    // Check authentication
                    boolean isAuthenticated = false;
                    if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN) {
                        isAuthenticated = adminAuthManager.isAuthenticated(player);
                    } else {
                        isAuthenticated = authManager.isAuthenticated(player.getUUID());
                    }

                    if (!isAuthenticated) {
                        isUnauthenticatedRestricted = true;
                        MySubMod.LOGGER.info("Detected unauthenticated protected/admin: {}", playerName);
                    }
                }
            }

            // Apply restrictions to both queue candidates AND unauthenticated protected/admin
            if (isQueueCandidate || isUnauthenticatedRestricted) {
                // Determine account type for parking lobby
                com.example.mysubmod.auth.AuthManager.AccountType accountType = authManager.getAccountType(actualAccountName);
                String accountTypeStr;
                if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN) {
                    accountTypeStr = "ADMIN";
                } else if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER) {
                    accountTypeStr = "PROTECTED_PLAYER";
                } else {
                    accountTypeStr = "TEMPORARY"; // For queue candidates with free player accounts
                }

                // Make INVISIBLE first
                player.setInvisible(true);

                // Teleport to isolated authentication platform at (10000, 200, 10000) BEFORE adding to parking lobby
                net.minecraft.server.level.ServerLevel world = player.getServer().overworld();
                net.minecraft.core.BlockPos platformPos = new net.minecraft.core.BlockPos(10000, 200, 10000);

                // Create a 3x3 bedrock platform
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        world.setBlock(
                            platformPos.offset(x, -1, z),
                            net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(),
                            3
                        );
                    }
                }

                // Teleport player to center of platform
                player.teleportTo(world, 10000.5, 200, 10000.5, 0, 0);

                // Add to parking lobby AFTER teleportation to prevent position check kick
                parkingLobby.addPlayer(player, accountTypeStr);

                // Clear any SubMode1 UI elements (timer, candy counts) from client
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new com.example.mysubmod.submodes.submode1.network.GameTimerPacket(-1)); // -1 = deactivate
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new com.example.mysubmod.submodes.submode1.network.CandyCountUpdatePacket(new java.util.HashMap<>())); // Empty map = clear

                String type = isQueueCandidate ? "Queue candidate" : "Unauthenticated protected/admin";
                MySubMod.LOGGER.info("{} {} made invisible, teleported to isolated authentication platform, and added to parking lobby", type, playerName);
            }

            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SubModeChangePacket(SubModeManager.getInstance().getCurrentMode())
            );

            // Check account type using ACTUAL account name (not temporary name)
            com.example.mysubmod.auth.AuthManager.AccountType accountType = authManager.getAccountType(actualAccountName);
            MySubMod.LOGGER.info("Player {} (actual: {}) login check: accountType={}", playerName, actualAccountName, accountType);

            // NOTE: Priority kick system is now handled AFTER authentication in handleAuthenticationTransition()
            // Players in parking lobby (authenticating) do NOT kick anyone - only after successful auth

            // Check if this player is connecting from the queue (IP authorized, needs token verification)
            boolean isFromQueue = parkingLobby.isAuthorized(actualAccountName, player.getIpAddress());
            if (isFromQueue && !isQueueCandidate) {
                // Player is connecting during monopoly window - request token
                MySubMod.LOGGER.info("Player {} connecting from queue - requesting token verification", actualAccountName);
                NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new com.example.mysubmod.auth.QueueTokenRequestPacket(actualAccountName)
                );
                // Don't put in parking lobby yet - wait for token verification
                // Token verification will happen in QueueTokenVerifyPacket handler
                return;
            }

            // If account needs authentication, put in parking lobby
            if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN ||
                accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER) {

                // Check if account is blacklisted (use actual account name)
                boolean isBlacklisted = false;
                long remainingTime = 0;

                if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN) {
                    isBlacklisted = adminAuthManager.isBlacklisted(actualAccountName);
                    if (isBlacklisted) {
                        remainingTime = adminAuthManager.getRemainingBlacklistTime(actualAccountName);
                    }
                } else if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER) {
                    isBlacklisted = authManager.isProtectedPlayerBlacklisted(actualAccountName);
                    if (isBlacklisted) {
                        remainingTime = authManager.getRemainingProtectedPlayerBlacklistTime(actualAccountName);
                    }
                }

                if (isBlacklisted) {
                    long minutes = remainingTime / 60000;
                    player.getServer().execute(() -> {
                        player.connection.disconnect(Component.literal(
                            "§4§lCompte Blacklisté\n\n§cTrop de tentatives échouées.\n§7Temps restant: §e" + minutes + " minute(s)"
                        ));
                    });
                    return;
                }

                // Determine account type string
                String accountTypeStr = accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN ? "ADMIN" : "PROTECTED_PLAYER";

                // Put player in parking lobby with 60s timeout (if not already added)
                if (!parkingLobby.isInParkingLobby(player.getUUID())) {
                    parkingLobby.addPlayer(player, accountTypeStr);
                }

                if (isQueueCandidate) {
                    // This is a queue candidate - mark them as such using the ACTUAL account name
                    parkingLobby.addQueueCandidate(actualAccountName, player.getUUID());
                    MySubMod.LOGGER.info("Player {} (temporary name: {}) marked as QUEUE CANDIDATE for {}",
                        player.getUUID(), playerName, actualAccountName);
                }

                // Get remaining attempts for this account (use actual account name)
                int remainingAttempts;
                if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN) {
                    remainingAttempts = com.example.mysubmod.auth.AdminAuthManager.getInstance().getRemainingAttemptsByName(actualAccountName);
                } else {
                    remainingAttempts = authManager.getRemainingProtectedPlayerAttempts(actualAccountName);
                }

                // Send auth request with account type and timeout
                NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new com.example.mysubmod.auth.AdminAuthRequestPacket(accountTypeStr, remainingAttempts, 60)
                );

                MySubMod.LOGGER.info("Player {} added to parking lobby as {} (queue candidate: {})",
                    playerName, accountTypeStr, isQueueCandidate);
            }

            // Send admin status (will be false for unauthenticated)
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new AdminStatusPacket(SubModeManager.getInstance().isAdmin(player))
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String playerName = player.getName().getString();
            com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();

            // Check if this is a temporary name and get the actual account name
            String actualAccountName = playerName;
            if (playerName.startsWith("_Q_")) {
                String originalName = parkingLobby.getOriginalAccountName(playerName);
                if (originalName != null) {
                    actualAccountName = originalName;
                    MySubMod.LOGGER.info("Logout: Detected temporary name {} -> actual account: {}", playerName, actualAccountName);
                }
            }

            // Check if player was in parking lobby (unauthenticated)
            boolean wasInLobby = parkingLobby.isInParkingLobby(player.getUUID());

            // Get remaining time BEFORE removing player (use playerName for session lookup)
            long remainingTime = wasInLobby ? parkingLobby.getRemainingTimeForAccount(playerName) : 0;

            // Clear authentication state when player disconnects
            com.example.mysubmod.auth.AdminAuthManager.getInstance().handleDisconnect(player);
            com.example.mysubmod.auth.AuthManager.getInstance().handleDisconnect(player);
            parkingLobby.removePlayer(player.getUUID(), player.serverLevel());

            // Clean up temporary name mapping if this was a REAL queue candidate
            if (parkingLobby.isTemporaryQueueName(playerName)) {
                parkingLobby.removeTemporaryName(playerName);
                MySubMod.LOGGER.info("Cleaned up temporary name mapping for {}", playerName);
            }

            // If player was in lobby (unauthenticated), authorize next in queue with remaining time
            // Use ACTUAL account name for queue lookup
            if (wasInLobby) {
                MySubMod.LOGGER.info("Player {} (actual: {}) disconnected from parking lobby with {}ms remaining - authorizing next in queue",
                    playerName, actualAccountName, remainingTime);
                parkingLobby.authorizeNextInQueue(actualAccountName, remainingTime);
            }
        }
    }

    /**
     * Priority kick system: If server is full (10 players) and a protected account connects,
     * kick a random FREE_PLAYER to make room
     * NOTE: Queue candidates, authenticated admins, and unauthenticated players are NOT counted as real players
     */
    public static void checkAndKickForPriority(ServerPlayer protectedPlayer, com.example.mysubmod.auth.AuthManager.AccountType accountType) {
        int maxPlayers = protectedPlayer.server.getMaxPlayers();
        java.util.List<ServerPlayer> onlinePlayers = protectedPlayer.server.getPlayerList().getPlayers();
        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();
        com.example.mysubmod.auth.AuthManager authManager = com.example.mysubmod.auth.AuthManager.getInstance();
        com.example.mysubmod.auth.AdminAuthManager adminAuthManager = com.example.mysubmod.auth.AdminAuthManager.getInstance();

        // Count only real players (exclude queue candidates, authenticated admins, and unauthenticated players)
        int realPlayerCount = 0;
        int adminCount = 0;
        int queueCandidateCount = 0;
        int unauthenticatedCount = 0;
        for (ServerPlayer p : onlinePlayers) {
            String name = p.getName().getString();
            if (parkingLobby.isTemporaryQueueName(name)) {
                queueCandidateCount++;
            } else if (parkingLobby.isInParkingLobby(p.getUUID())) {
                // Player in parking lobby (not authenticated) - don't count as real player
                unauthenticatedCount++;
            } else {
                // Check if this is an authenticated admin (authenticated admins don't count towards max_players)
                com.example.mysubmod.auth.AuthManager.AccountType type = authManager.getAccountType(name);
                boolean isAuthenticatedAdmin = (type == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN &&
                                                adminAuthManager.isAuthenticated(p));

                if (isAuthenticatedAdmin) {
                    adminCount++;
                } else {
                    realPlayerCount++;
                }
            }
        }

        // Check if server is full (including the protected player who just joined)
        // If we have exactly maxPlayers, we need to kick someone to make room
        if (realPlayerCount < maxPlayers) {
            return; // Not full, no kick needed
        }

        MySubMod.LOGGER.info("Server full ({}/{} real players, {} admins, {} unauthenticated, {} queue candidates), checking for free players to kick for protected account {}",
            realPlayerCount, maxPlayers, adminCount, unauthenticatedCount, queueCandidateCount, protectedPlayer.getName().getString());

        // Find all FREE_PLAYER accounts currently online (exclude the protected player who just joined, queue candidates, admins, and unauthenticated)
        java.util.List<ServerPlayer> freePlayers = new java.util.ArrayList<>();

        for (ServerPlayer p : onlinePlayers) {
            if (p.getUUID().equals(protectedPlayer.getUUID())) {
                continue; // Skip the protected player who just joined
            }

            String name = p.getName().getString();

            // Skip queue candidates - they are NOT real players and should never be kicked
            if (parkingLobby.isTemporaryQueueName(name)) {
                continue;
            }

            // Skip unauthenticated players - they are NOT real players and should never be kicked
            if (parkingLobby.isInParkingLobby(p.getUUID())) {
                continue;
            }

            // Skip authenticated admins - they don't count and can't be kicked
            com.example.mysubmod.auth.AuthManager.AccountType type = authManager.getAccountType(name);
            boolean isAuthenticatedAdmin = (type == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN &&
                                            adminAuthManager.isAuthenticated(p));
            if (isAuthenticatedAdmin) {
                continue;
            }

            if (type == com.example.mysubmod.auth.AuthManager.AccountType.FREE_PLAYER) {
                freePlayers.add(p);
            }
        }

        if (freePlayers.isEmpty()) {
            MySubMod.LOGGER.warn("Server full but no free players to kick for {}", protectedPlayer.getName().getString());
            return;
        }

        // Kick a random free player
        java.util.Random random = new java.util.Random();
        ServerPlayer victimPlayer = freePlayers.get(random.nextInt(freePlayers.size()));

        String accountTypeStr = accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN ? "administrateur" : "joueur protégé";

        MySubMod.LOGGER.info("Kicking free player {} to make room for protected account {}",
            victimPlayer.getName().getString(), protectedPlayer.getName().getString());

        victimPlayer.getServer().execute(() -> {
            victimPlayer.connection.disconnect(Component.literal(
                "§e§lServeur Complet\n\n" +
                "§7Un " + accountTypeStr + " s'est connecté.\n" +
                "§7Vous avez été déconnecté pour libérer une place."
            ));
        });
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }

        // Keep queue candidates and unauthenticated protected/admin invisible
        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();
        com.example.mysubmod.auth.AuthManager authManager = com.example.mysubmod.auth.AuthManager.getInstance();
        com.example.mysubmod.auth.AdminAuthManager adminAuthManager = com.example.mysubmod.auth.AdminAuthManager.getInstance();

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            String playerName = player.getName().getString();
            boolean shouldBeInvisible = false;

            // Check if queue candidate
            if (parkingLobby.isTemporaryQueueName(playerName)) {
                shouldBeInvisible = true;
            } else {
                // Check if unauthenticated protected/admin
                com.example.mysubmod.auth.AuthManager.AccountType accountType = authManager.getAccountType(playerName);
                boolean isProtectedOrAdmin = (accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER ||
                                              accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN);

                if (isProtectedOrAdmin) {
                    boolean isAuthenticated = false;
                    if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN) {
                        isAuthenticated = adminAuthManager.isAuthenticated(player);
                    } else {
                        isAuthenticated = authManager.isAuthenticated(player.getUUID());
                    }

                    if (!isAuthenticated) {
                        shouldBeInvisible = true;
                    }
                }
            }

            // Ensure they stay invisible if they should be
            if (shouldBeInvisible && !player.isInvisible()) {
                player.setInvisible(true);
            }
        }
    }

    /**
     * Kick queue candidate and remove from queue
     */
    private static void kickQueueCandidate(ServerPlayer player, String reason) {
        String playerName = player.getName().getString();
        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();

        // Get actual account name
        String actualAccountName = parkingLobby.getOriginalAccountName(playerName);
        if (actualAccountName == null) {
            actualAccountName = playerName;
        }

        MySubMod.LOGGER.warn("Kicking queue candidate {} (account: {}) - Reason: {}", playerName, actualAccountName, reason);

        // Remove from queue candidate status
        parkingLobby.removeQueueCandidate(actualAccountName, player.getUUID());

        // Kick player
        player.getServer().execute(() -> {
            player.connection.disconnect(Component.literal(
                "§c§lAction non autorisée\n\n" +
                "§7Vous ne pouvez pas effectuer d'actions pendant l'authentification.\n" +
                "§7Vous avez été retiré de la file d'attente.\n\n" +
                "§eRaison: §f" + reason
            ));
        });
    }

    /**
     * Check if player is a queue candidate OR unauthenticated protected/admin (both are restricted)
     */
    /**
     * Check if player is a REAL queue candidate (temporary _Q_ name)
     * NOT regular protected/admin accounts waiting for authentication
     */
    private static boolean isQueueCandidate(ServerPlayer player) {
        String playerName = player.getName().getString();
        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();

        // ONLY return true for temporary queue candidate names (_Q_)
        // Regular protected/admin accounts should NOT be restricted by these event handlers
        return parkingLobby.isTemporaryQueueName(playerName);
    }

    /**
     * Kick queue candidates if they try to move
     * ONLY applies to REAL queue candidates (temporary _Q_ names), not regular protected/admin accounts
     */
    @SubscribeEvent
    public static void onPlayerMove(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // ONLY check movement for REAL queue candidates (temporary names)
        // NOT for regular protected/admin accounts waiting for authentication
        String playerName = player.getName().getString();
        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();
        if (!parkingLobby.isTemporaryQueueName(playerName)) return;

        // Check if player has moved significantly (more than 0.1 blocks)
        double dx = player.getX() - player.xOld;
        double dy = player.getY() - player.yOld;
        double dz = player.getZ() - player.zOld;
        double distanceSq = dx * dx + dy * dy + dz * dz;

        if (distanceSq > 0.01) { // 0.1 * 0.1
            kickQueueCandidate(player, "Mouvement détecté");
        }
    }

    /**
     * Kick queue candidates if they try to interact with anything
     * ONLY applies to REAL queue candidates (temporary _Q_ names), not regular protected/admin accounts
     */
    @SubscribeEvent
    public static void onPlayerInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // ONLY check interaction for REAL queue candidates (temporary names)
        String playerName = player.getName().getString();
        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();
        if (!parkingLobby.isTemporaryQueueName(playerName)) return;

        kickQueueCandidate(player, "Interaction détectée");
        event.setCanceled(true);
    }

    /**
     * Kick queue candidates if they try to attack
     * ONLY applies to REAL queue candidates (temporary _Q_ names), not regular protected/admin accounts
     */
    @SubscribeEvent
    public static void onPlayerAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // ONLY check attacks for REAL queue candidates (temporary names)
        String playerName = player.getName().getString();
        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();
        if (!parkingLobby.isTemporaryQueueName(playerName)) return;

        kickQueueCandidate(player, "Attaque détectée");
        event.setCanceled(true);
    }

    /**
     * Kick queue candidates if they try to break blocks
     */
    @SubscribeEvent
    public static void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!isQueueCandidate(player)) return;

        kickQueueCandidate(player, "Tentative de casser un bloc");
        event.setCanceled(true);
    }

    /**
     * Kick queue candidates if they try to place blocks
     */
    @SubscribeEvent
    public static void onBlockPlace(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isQueueCandidate(player)) return;

        kickQueueCandidate(player, "Tentative de placer un bloc");
        event.setCanceled(true);
    }

    /**
     * Kick queue candidates if they try to drop items
     */
    @SubscribeEvent
    public static void onItemDrop(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!isQueueCandidate(player)) return;

        kickQueueCandidate(player, "Tentative de jeter un item");
        event.setCanceled(true);
    }

    /**
     * Kick queue candidates if they try to pick up items
     */
    @SubscribeEvent
    public static void onItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isQueueCandidate(player)) return;

        kickQueueCandidate(player, "Tentative de ramasser un item");
        event.setCanceled(true);
    }
}
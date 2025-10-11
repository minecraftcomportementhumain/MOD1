package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server with authentication attempt
 */
public class AdminAuthPacket {
    private final String password;

    public AdminAuthPacket(String password) {
        this.password = password;
    }

    public static void encode(AdminAuthPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.password, 50);
    }

    public static AdminAuthPacket decode(FriendlyByteBuf buf) {
        return new AdminAuthPacket(buf.readUtf(50));
    }

    public static void handle(AdminAuthPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            AdminAuthManager adminAuthManager = AdminAuthManager.getInstance();
            AuthManager authManager = AuthManager.getInstance();
            ParkingLobbyManager parkingLobby = ParkingLobbyManager.getInstance();

            String playerName = player.getName().getString();

            // Check if player is in parking lobby
            if (!parkingLobby.isInParkingLobby(player.getUUID())) {
                MySubMod.LOGGER.warn("Player {} attempted auth but not in parking lobby", playerName);
                return;
            }

            String accountType = parkingLobby.getAccountType(player.getUUID());
            boolean isAdmin = "ADMIN".equals(accountType);
            boolean isQueueCandidate = parkingLobby.isQueueCandidate(player.getUUID());

            // For queue candidates, we only verify password without actually authenticating
            if (isQueueCandidate) {
                // Get the actual account name (playerName might be temporary)
                String actualAccountName = parkingLobby.getOriginalAccountName(playerName);
                if (actualAccountName == null) {
                    actualAccountName = playerName; // Fallback if not found
                }

                MySubMod.LOGGER.info("Processing queue candidate authentication for {} (temp name: {})",
                    actualAccountName, playerName);

                // Verify password without tracking failures (read-only check)
                boolean passwordCorrect;
                if (isAdmin) {
                    passwordCorrect = adminAuthManager.verifyPasswordOnly(actualAccountName, packet.password);
                } else {
                    passwordCorrect = authManager.verifyProtectedPlayerPassword(actualAccountName, packet.password);
                }

                if (passwordCorrect) {
                    // Password correct - try to add to queue
                    int position = parkingLobby.promoteQueueCandidateToQueue(actualAccountName, player.getUUID(), player.getIpAddress());

                    if (position == -1) {
                        // Queue is full - someone else got in first
                        // Clean up temporary name mapping
                        parkingLobby.removeTemporaryName(playerName);
                        MySubMod.LOGGER.warn("Queue candidate {} rejected - queue full (someone else entered password first)", actualAccountName);

                        // Kick player with rejection message
                        player.connection.disconnect(net.minecraft.network.chat.Component.literal(
                            "§c§lFile d'attente complète\n\n" +
                            "§eUn autre joueur a entré le mot de passe avant vous.\n" +
                            "§7La file d'attente est limitée à §e1 personne§7."
                        ));
                        return;
                    }

                    long[] monopolyWindow = parkingLobby.getMonopolyWindow(actualAccountName, player.getIpAddress());
                    String token = parkingLobby.getQueueToken(actualAccountName, player.getIpAddress());

                    // Don't clean up temporary name mapping here - it will be cleaned up in onPlayerLogout
                    // This allows the logout handler to still access the original account name

                    MySubMod.LOGGER.info("Queue candidate {} promoted to queue at position {} with token {}",
                        actualAccountName, position, token);

                    // Kick the authenticating player (indiv1) - they are being replaced
                    parkingLobby.kickAuthenticatingPlayer(actualAccountName, player.getServer());

                    // Kick all other queue candidates (queue is now full)
                    parkingLobby.kickRemainingQueueCandidates(actualAccountName, player.getServer());

                    // Send token to client BEFORE kicking
                    if (monopolyWindow != null && token != null) {
                        com.example.mysubmod.network.NetworkHandler.sendToPlayer(
                            new QueueTokenPacket(actualAccountName, token, monopolyWindow[0], monopolyWindow[1]),
                            player
                        );
                        MySubMod.LOGGER.info("Sent token {} to client for {}", token, actualAccountName);
                    }

                    // Small delay to ensure packet is sent before disconnect
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // Ignore
                    }

                    // Kick player with queue info (token is already sent to client via packet)
                    player.getServer().execute(() -> {
                        String message = String.format(
                            "§a§lMot de passe correct!\n\n" +
                            "§eVous êtes en file d'attente\n" +
                            "§7Position: §f%d\n\n" +
                            "§6Reconnectez-vous, vous avez 45 secondes pour vous connecter.\n\n" +
                            "§aVotre client a reçu un token de connexion automatique.",
                            position
                        );
                        player.connection.disconnect(net.minecraft.network.chat.Component.literal(message));
                    });
                } else {
                    // Password incorrect - kick without adding to queue
                    parkingLobby.removeQueueCandidate(actualAccountName, player.getUUID());
                    parkingLobby.removeTemporaryName(playerName);
                    MySubMod.LOGGER.warn("Queue candidate {} (temp: {}) entered wrong password - kicked",
                        actualAccountName, playerName);

                    player.getServer().execute(() -> {
                        player.connection.disconnect(net.minecraft.network.chat.Component.literal(
                            "§c§lMot de passe incorrect\n\n" +
                            "§eVous n'avez pas été ajouté à la file d'attente."
                        ));
                    });
                }
                return;
            }

            // Normal authentication flow (not a queue candidate)
            int result;

            if (isAdmin) {
                // Admin authentication
                result = adminAuthManager.attemptLogin(player, packet.password);
            } else {
                // Protected player authentication (with blacklist tracking)
                result = authManager.attemptProtectedPlayerLogin(player, packet.password);
            }

            if (result == 0) {
                // Success - Kick queue candidates FIRST, then remove from parking lobby
                // IMPORTANT: Must kick before clearing queue, otherwise candidates are removed from Map
                parkingLobby.kickRemainingQueueCandidates(playerName, player.getServer(), "auth_success");

                parkingLobby.removePlayer(player.getUUID(), player.serverLevel());
                parkingLobby.clearQueueForAccount(playerName);

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§a§lAuthentification réussie! Bienvenue, " + playerName + "."));

                // Send success response
                com.example.mysubmod.network.NetworkHandler.sendToPlayer(
                    new AdminAuthResponsePacket(true, 0, "Authentification réussie"), player);

                // Update admin status now that player is authenticated
                com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.example.mysubmod.network.AdminStatusPacket(
                        com.example.mysubmod.submodes.SubModeManager.getInstance().isAdmin(player))
                );

            } else if (result == -1) {
                // Wrong password
                int remaining = isAdmin ?
                    adminAuthManager.getRemainingAttemptsByName(playerName) :
                    authManager.getRemainingProtectedPlayerAttempts(playerName);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c§lMot de passe incorrect! Tentatives restantes: " + remaining));

                // Send failure response with remaining attempts
                com.example.mysubmod.network.NetworkHandler.sendToPlayer(
                    new AdminAuthResponsePacket(false, remaining, "Mot de passe incorrect"), player);

            } else if (result == -2) {
                // Max attempts - account blacklisted (3 minutes)
                long remainingTime = isAdmin ?
                    adminAuthManager.getRemainingBlacklistTime(playerName) :
                    authManager.getRemainingProtectedPlayerBlacklistTime(playerName);
                long minutes = remainingTime / 60000;

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§4§lTrop de tentatives échouées! Compte blacklisté pour " + minutes + " minute(s)."));

                // Kick player
                player.getServer().execute(() -> {
                    player.connection.disconnect(net.minecraft.network.chat.Component.literal(
                        "§4§lCompte Blacklisté\n\n§cTrop de tentatives de connexion échouées.\n§7Temps restant: §e" + minutes + " minute(s)"));
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

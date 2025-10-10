package com.example.mysubmod.mixin;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.auth.AdminAuthManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class MixinServerLoginPacketListenerImplPlaceNewPlayer {

    @Shadow @Final
    Connection connection;

    @Shadow
    GameProfile gameProfile;

    @Shadow @Final
    MinecraftServer server;

    // Store the original account name for queue candidates
    private String originalAccountName = null;

    @Inject(
        method = "handleAcceptedLogin",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onHandleAcceptedLoginBeforeKick(CallbackInfo ci) {
        MySubMod.LOGGER.info("=== MIXIN CALLED: ServerLoginPacketListenerImpl.handleAcceptedLogin (BEFORE VANILLA) ===");

        String playerName = this.gameProfile.getName();
        MySubMod.LOGGER.info("MIXIN: Processing login for player: {}", playerName);

        // SECURITY: Block free players trying to exploit the "_Q_" prefix
        // Only queue candidates created by our system should have this prefix
        if (playerName.startsWith("_Q_")) {
            com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();
            if (!parkingLobby.isTemporaryQueueName(playerName)) {
                // This is NOT a legitimate queue candidate - block connection
                MySubMod.LOGGER.warn("MIXIN: SECURITY - Blocking unauthorized use of reserved prefix '_Q_' by {}", playerName);
                this.connection.send(new ClientboundLoginDisconnectPacket(
                    Component.literal("§c§lNom invalide\n\n§7Le préfixe '_Q_' est réservé au système.\n§7Veuillez choisir un autre nom.")
                ));
                this.connection.disconnect(Component.literal("Invalid name - reserved prefix"));
                ci.cancel();
                return;
            }
        }

        AdminAuthManager adminAuthManager = AdminAuthManager.getInstance();
        com.example.mysubmod.auth.AuthManager authManager = com.example.mysubmod.auth.AuthManager.getInstance();

        // Check if this is a protected account (admin or protected player)
        com.example.mysubmod.auth.AuthManager.AccountType accountType = authManager.getAccountType(playerName);
        boolean isProtectedAccount = (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN ||
                                      accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER);

        MySubMod.LOGGER.info("MIXIN: Player {} account type: {}, isProtected: {}", playerName, accountType, isProtectedAccount);

        // Get the player list to check for existing connection
        PlayerList playerList = this.server.getPlayerList();

        // PRIORITY SYSTEM: If server is full and this is a protected account, allow connection
        // The actual kick will happen in ServerEventHandler after the player joins
        int currentPlayers = playerList.getPlayerCount();
        int maxPlayers = playerList.getMaxPlayers();

        if (currentPlayers >= maxPlayers && isProtectedAccount) {
            MySubMod.LOGGER.info("MIXIN: Server full ({}/{}) but allowing protected account {} to connect. Will kick FREE player after join.",
                currentPlayers, maxPlayers, playerName);
            // Don't cancel - allow the connection to proceed
            // The ServerEventHandler will kick a FREE player after this player joins
        }

        // Check by iterating through all players to find duplicate by NAME (since UUID is null at this point)
        ServerPlayer existingPlayer = null;
        for (ServerPlayer player : playerList.getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase(playerName)) {
                existingPlayer = player;
                break;
            }
        }

        if (existingPlayer != null) {
            MySubMod.LOGGER.info("MIXIN: Found existing player with name {}", playerName);

            // Get IP address for queue system
            String ipAddress = this.connection.getRemoteAddress().toString();

            if (isProtectedAccount) {
                // Protected account (admin or protected player)
                boolean isAuthenticated = false;
                if (adminAuthManager.isAdminAccount(playerName)) {
                    isAuthenticated = adminAuthManager.isAuthenticated(existingPlayer);
                } else {
                    isAuthenticated = authManager.isAuthenticated(existingPlayer.getUUID());
                }

                if (isAuthenticated) {
                    // Deny new connection - keep authenticated user (admin or protected player)
                    String accountLabel = adminAuthManager.isAdminAccount(playerName) ? "administrateur" : "joueur protégé";
                    MySubMod.LOGGER.warn("MIXIN: Denying connection for {} - {} already authenticated", playerName, accountLabel);
                    this.connection.send(new ClientboundLoginDisconnectPacket(
                        Component.literal(String.format("§c§lConnexion refusée\n\n§eUn %s authentifié utilise déjà ce compte.", accountLabel))
                    ));
                    this.connection.disconnect(Component.literal("Authenticated user already connected"));
                    ci.cancel();
                } else {
                    // Not authenticated - check authorization, queue status, or request password
                    com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();

                    // Check if authentication protection is active
                    long protectionRemaining = 0;
                    if (adminAuthManager.isAdminAccount(playerName)) {
                        protectionRemaining = adminAuthManager.getRemainingProtectionTime(playerName);
                    } else {
                        protectionRemaining = authManager.getRemainingProtectionTime(playerName);
                    }

                    if (protectionRemaining > 0 && !parkingLobby.isAuthorized(playerName, ipAddress)) {
                        // Protection active and IP not authorized - BLOCK
                        long remainingSeconds = (protectionRemaining + 999) / 1000;
                        MySubMod.LOGGER.warn("MIXIN: IP {} blocked for {} - authentication protection active ({} seconds remaining)",
                            ipAddress, playerName, remainingSeconds);
                        this.connection.send(new ClientboundLoginDisconnectPacket(
                            Component.literal(String.format(
                                "§c§lCompte en cours d'utilisation\n\n" +
                                "§eQuelqu'un s'authentifie actuellement sur ce compte.\n" +
                                "§7Temps restant: §e%d seconde(s)",
                                remainingSeconds
                            ))
                        ));
                        this.connection.disconnect(Component.literal("Authentication protection active"));
                        ci.cancel();
                        return;
                    }

                    if (parkingLobby.isAuthorized(playerName, ipAddress)) {
                        // IP is authorized from monopoly window - SET/UPDATE protection to current time + 30s
                        MySubMod.LOGGER.info("MIXIN: IP {} authorized for {} - setting protection to current time + 30s", ipAddress, playerName);
                        if (adminAuthManager.isAdminAccount(playerName)) {
                            adminAuthManager.updateAuthenticationProtection(playerName);
                        } else {
                            authManager.updateAuthenticationProtection(playerName);
                        }

                        // Don't consume authorization yet - will be consumed after token verification
                        MySubMod.LOGGER.info("MIXIN: IP {} authorized for {} - allowing connection (token verification pending)", ipAddress, playerName);
                        // Don't cancel - let vanilla kick the old player
                    } else if (parkingLobby.isAccountBeingAuthenticated(playerName) && parkingLobby.hasQueueRoom(playerName)) {
                        // Someone is authenticating AND there's room in queue - assign temporary name to avoid kicking existing player
                        // Max 16 chars for Minecraft username limit
                        // Format: _Q_<shortName>_<shortTimestamp>
                        String shortTimestamp = String.valueOf(System.currentTimeMillis() % 100000); // Last 5 digits
                        String shortName = playerName.length() > 7 ? playerName.substring(0, 7) : playerName;
                        String temporaryName = "_Q_" + shortName + "_" + shortTimestamp;

                        // Register the temporary name mapping
                        parkingLobby.registerTemporaryName(temporaryName, playerName);

                        // Modify the GameProfile to use temporary name
                        this.gameProfile = new com.mojang.authlib.GameProfile(
                            this.gameProfile.getId(),
                            temporaryName
                        );

                        MySubMod.LOGGER.info("MIXIN: IP {} connecting as queue candidate for {} with temporary name: {}",
                            ipAddress, playerName, temporaryName);

                        // Don't cancel - let them connect with temporary name and verify password
                    } else if (!parkingLobby.isAccountBeingAuthenticated(playerName)) {
                        // No one is authenticating - deny connection (can't join queue if no one is authenticating)
                        MySubMod.LOGGER.warn("MIXIN: IP {} rejected - no authentication in progress for {}", ipAddress, playerName);
                        this.connection.send(new ClientboundLoginDisconnectPacket(
                            Component.literal("§c§lConnexion refusée\n\n§eCe compte n'est pas en cours d'authentification.\n§7Impossible de rejoindre la file d'attente.")
                        ));
                        this.connection.disconnect(Component.literal("No authentication in progress"));
                        ci.cancel();
                    } else {
                        // Someone is authenticating but queue is full - reject connection
                        MySubMod.LOGGER.warn("MIXIN: IP {} rejected - queue full for {}", ipAddress, playerName);
                        this.connection.send(new ClientboundLoginDisconnectPacket(
                            Component.literal("§c§lConnexion refusée\n\n§eFile d'attente complète pour ce compte.\n§7Maximum: 1 personne en attente.")
                        ));
                        this.connection.disconnect(Component.literal("Queue full"));
                        ci.cancel();
                    }
                }
            } else {
                // Free player - deny new connection (no queue for free players)
                MySubMod.LOGGER.warn("MIXIN: Denying connection for {} - free player already connected", playerName);
                this.connection.send(new ClientboundLoginDisconnectPacket(
                    Component.literal("§c§lConnexion refusée\n\n§eCe compte est déjà utilisé par un autre joueur.")
                ));
                this.connection.disconnect(Component.literal("Player already connected"));
                ci.cancel();
            }
        } else {
            // No existing player with this name
            MySubMod.LOGGER.info("MIXIN: No existing player found with name {}", playerName);

            // For protected accounts, check if there's an active queue with IP authorization
            if (isProtectedAccount) {
                com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();
                String ipAddress = this.connection.getRemoteAddress().toString();

                // Check if authentication protection is active (30 seconds after someone started authenticating)
                long protectionRemaining = 0;
                if (adminAuthManager.isAdminAccount(playerName)) {
                    protectionRemaining = adminAuthManager.getRemainingProtectionTime(playerName);
                } else {
                    protectionRemaining = authManager.getRemainingProtectionTime(playerName);
                }

                if (protectionRemaining > 0 && !parkingLobby.isAuthorized(playerName, ipAddress)) {
                    // Protection active but IP not authorized - BLOCK connection
                    long remainingSeconds = (protectionRemaining + 999) / 1000; // Round up
                    MySubMod.LOGGER.warn("MIXIN: IP {} blocked for {} - authentication protection active ({} seconds remaining)",
                        ipAddress, playerName, remainingSeconds);
                    this.connection.send(new ClientboundLoginDisconnectPacket(
                        Component.literal(String.format(
                            "§c§lCompte en cours d'utilisation\n\n" +
                            "§eQuelqu'un s'authentifie actuellement sur ce compte.\n" +
                            "§7Temps restant: §e%d seconde(s)",
                            remainingSeconds
                        ))
                    ));
                    this.connection.disconnect(Component.literal("Authentication protection active"));
                    ci.cancel();
                    return;
                }

                // Check if this IP is authorized (from monopoly window)
                if (parkingLobby.isAuthorized(playerName, ipAddress)) {
                    // IP is authorized from monopoly window - SET/UPDATE protection to current time + 30s
                    MySubMod.LOGGER.info("MIXIN: IP {} authorized for {} - setting protection to current time + 30s", ipAddress, playerName);
                    if (adminAuthManager.isAdminAccount(playerName)) {
                        adminAuthManager.updateAuthenticationProtection(playerName);
                    } else {
                        authManager.updateAuthenticationProtection(playerName);
                    }

                    MySubMod.LOGGER.info("MIXIN: No existing player, IP {} authorized for {} - allowing connection (token verification pending)", ipAddress, playerName);
                    // Don't consume authorization yet - will be consumed after token verification
                    // Don't cancel - allow connection
                } else if (parkingLobby.hasQueue(playerName)) {
                    // Queue exists but this IP is not authorized
                    MySubMod.LOGGER.warn("MIXIN: IP {} rejected - queue exists for {} but IP not authorized", ipAddress, playerName);
                    this.connection.send(new ClientboundLoginDisconnectPacket(
                        Component.literal("§c§lConnexion refusée\n\n§eUne file d'attente existe pour ce compte.\n§7Seule l'IP autorisée peut se connecter pendant la fenêtre de monopole.")
                    ));
                    this.connection.disconnect(Component.literal("IP not authorized"));
                    ci.cancel();
                } else {
                    // No queue, no authorization - allow normal authentication
                    MySubMod.LOGGER.info("MIXIN: No queue for {}, allowing login for authentication", playerName);
                }
            } else {
                // Free player - allow login
                MySubMod.LOGGER.info("MIXIN: Free player {}, allowing login", playerName);
            }
        }
    }
}

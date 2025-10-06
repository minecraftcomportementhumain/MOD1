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

    @Inject(
        method = "handleAcceptedLogin",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onHandleAcceptedLoginBeforeKick(CallbackInfo ci) {
        MySubMod.LOGGER.info("=== MIXIN CALLED: ServerLoginPacketListenerImpl.handleAcceptedLogin (BEFORE VANILLA) ===");

        String playerName = this.gameProfile.getName();
        MySubMod.LOGGER.info("MIXIN: Processing login for player: {}", playerName);

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
                    // Check if user is protected during authentication (30s window)
                    boolean isProtectedDuringAuth = false;
                    if (adminAuthManager.isAdminAccount(playerName)) {
                        isProtectedDuringAuth = adminAuthManager.isProtectedDuringAuth(existingPlayer);
                    } else {
                        isProtectedDuringAuth = authManager.isProtectedDuringAuth(existingPlayer);
                    }

                    if (isProtectedDuringAuth) {
                        // User is currently authenticating (30s protection) - deny new connection
                        String accountLabel = adminAuthManager.isAdminAccount(playerName) ? "administrateur" : "joueur protégé";
                        MySubMod.LOGGER.warn("MIXIN: Denying connection for {} - {} is authenticating (protected)", playerName, accountLabel);
                        this.connection.send(new ClientboundLoginDisconnectPacket(
                            Component.literal(String.format("§c§lConnexion refusée\n\n§eUn %s est en cours d'authentification sur ce compte.\n§7Veuillez patienter 30 secondes.", accountLabel))
                        ));
                        this.connection.disconnect(Component.literal("User authenticating"));
                        ci.cancel();
                    } else {
                        // Check if this IP has temporary authorization
                        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();
                        if (parkingLobby.isAuthorized(playerName, ipAddress)) {
                            // Authorized - allow connection and consume authorization
                            MySubMod.LOGGER.info("MIXIN: IP {} authorized for {} - allowing connection", ipAddress, playerName);
                            parkingLobby.consumeAuthorization(playerName, ipAddress);
                            // Don't cancel - let vanilla kick the old player
                        } else {
                            // Not authorized - add to queue
                            int queuePosition = parkingLobby.addToQueue(playerName, ipAddress);
                            if (queuePosition == -2) {
                                // IP is currently authenticating on this account
                                MySubMod.LOGGER.warn("MIXIN: IP {} rejected - already authenticating on {}", ipAddress, playerName);
                                this.connection.send(new ClientboundLoginDisconnectPacket(
                                    Component.literal("§c§lConnexion refusée\n\n§eVotre IP est déjà en cours d'authentification sur ce compte.")
                                ));
                                this.connection.disconnect(Component.literal("IP already authenticating"));
                                ci.cancel();
                            } else if (queuePosition == -1) {
                                // Too many queue positions for this IP
                                MySubMod.LOGGER.warn("MIXIN: IP {} rejected - too many queue positions", ipAddress);
                                this.connection.send(new ClientboundLoginDisconnectPacket(
                                    Component.literal("§c§lConnexion refusée\n\n§eTrop de tentatives de connexion simultanées.\n§7Limite: 3 comptes en attente par IP.")
                                ));
                                this.connection.disconnect(Component.literal("Too many queue positions"));
                                ci.cancel();
                            } else {
                                // Added to queue - calculate monopoly window
                                long[] monopolyWindow = parkingLobby.getMonopolyWindow(playerName, ipAddress);
                                MySubMod.LOGGER.info("MIXIN: IP {} added to queue for {} at position {}", ipAddress, playerName, queuePosition);

                                String message;
                                if (monopolyWindow != null) {
                                    // Format times (HH:MM:SS)
                                    java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm:ss");
                                    String startTime = timeFormat.format(new java.util.Date(monopolyWindow[0]));
                                    String endTime = timeFormat.format(new java.util.Date(monopolyWindow[1]));

                                    message = String.format(
                                        "§c§lCe compte est occupé\n\n" +
                                        "§eVous êtes en file d'attente\n" +
                                        "§7Position: §f%d\n\n" +
                                        "§eFenêtre de monopole:\n" +
                                        "§7De §f%s §7à §f%s\n\n" +
                                        "§7Vous aurez §e30 secondes§7 pour vous connecter pendant cette fenêtre.",
                                        queuePosition, startTime, endTime
                                    );
                                } else {
                                    // Fallback if calculation fails
                                    message = String.format(
                                        "§c§lCe compte est occupé\n\n" +
                                        "§eVous êtes en file d'attente\n" +
                                        "§7Position: §f%d\n\n" +
                                        "§7Réessayez dans §e60 secondes maximum§7.\n" +
                                        "§7Vous aurez §e30 secondes§7 pour vous connecter quand ce sera votre tour.",
                                        queuePosition
                                    );
                                }

                                this.connection.send(new ClientboundLoginDisconnectPacket(Component.literal(message)));
                                this.connection.disconnect(Component.literal("Account occupied - queued"));
                                ci.cancel();
                            }
                        }
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
            MySubMod.LOGGER.info("MIXIN: No existing player found with name {}, allowing login", playerName);
        }
    }
}

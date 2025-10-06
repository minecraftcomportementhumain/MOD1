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
                    // Not authenticated - check if same IP is already connected
                    // Normalize IPs for comparison (handle different formats)
                    String newIPNormalized = normalizeIP(ipAddress);
                    String existingIPNormalized = normalizeIP(existingPlayer.getIpAddress());

                    if (newIPNormalized.equals(existingIPNormalized)) {
                        // Same IP already connected on this account - deny without queue
                        MySubMod.LOGGER.warn("MIXIN: IP {} denied - same IP already connected on {}", newIPNormalized, playerName);
                        this.connection.send(new ClientboundLoginDisconnectPacket(
                            Component.literal("§c§lConnexion refusée\n\n§eVous êtes déjà connecté sur ce compte.")
                        ));
                        this.connection.disconnect(Component.literal("Same IP already connected"));
                        ci.cancel();
                    } else {
                        // Different IP - check authorization or add to queue
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

    /**
     * Normalize IP address to a comparable format
     * Handles: /[0:0:0:0:0:0:0:1]:port, /127.0.0.1:port, ::1, 127.0.0.1, etc.
     */
    private String normalizeIP(String ip) {
        if (ip == null) return "";

        // Remove leading slash if present
        String normalized = ip.startsWith("/") ? ip.substring(1) : ip;

        // Handle IPv6 in brackets: [0:0:0:0:0:0:0:1]:port -> 0:0:0:0:0:0:0:1
        if (normalized.startsWith("[")) {
            int closeBracket = normalized.indexOf(']');
            if (closeBracket > 0) {
                normalized = normalized.substring(1, closeBracket);
            }
        } else {
            // Handle IPv4 or simple format: remove port if present
            // But be careful with IPv6 (has multiple colons)
            int colonCount = normalized.length() - normalized.replace(":", "").length();
            if (colonCount == 1) {
                // IPv4 with port: 127.0.0.1:25565
                int lastColon = normalized.lastIndexOf(':');
                normalized = normalized.substring(0, lastColon);
            }
            // If colonCount > 1, it's IPv6 without brackets, keep as is
        }

        // Normalize IPv6: ::1 and 0:0:0:0:0:0:0:1 should be same
        // Expand ::1 to full form
        if (normalized.contains("::")) {
            // Simple expansion for localhost
            if (normalized.equals("::1")) {
                normalized = "0:0:0:0:0:0:0:1";
            }
        }

        return normalized;
    }
}

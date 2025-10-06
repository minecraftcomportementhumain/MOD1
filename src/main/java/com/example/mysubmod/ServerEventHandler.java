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

            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SubModeChangePacket(SubModeManager.getInstance().getCurrentMode())
            );

            // Check account type
            com.example.mysubmod.auth.AuthManager.AccountType accountType = authManager.getAccountType(playerName);
            MySubMod.LOGGER.info("Player {} login check: accountType={}", playerName, accountType);

            // Priority kick system: If server is full and this is a protected account, kick a free player
            if ((accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN ||
                 accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER)) {
                checkAndKickForPriority(player, accountType);
            }

            // If account needs authentication, put in parking lobby
            if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN ||
                accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER) {

                // Check if account is blacklisted
                boolean isBlacklisted = false;
                long remainingTime = 0;

                if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN) {
                    isBlacklisted = adminAuthManager.isBlacklisted(playerName);
                    if (isBlacklisted) {
                        remainingTime = adminAuthManager.getRemainingBlacklistTime(playerName);
                    }
                } else if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER) {
                    isBlacklisted = authManager.isProtectedPlayerBlacklisted(playerName);
                    if (isBlacklisted) {
                        remainingTime = authManager.getRemainingProtectedPlayerBlacklistTime(playerName);
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

                // Put player in parking lobby with 60s timeout
                String accountTypeStr = accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN ? "ADMIN" : "PROTECTED_PLAYER";
                parkingLobby.addPlayer(player, accountTypeStr);

                // Start 30-second protection against duplicate connections
                authManager.startAuthenticationProtection(player);

                // Get remaining attempts for this account
                int remainingAttempts;
                if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN) {
                    remainingAttempts = com.example.mysubmod.auth.AdminAuthManager.getInstance().getRemainingAttemptsByName(playerName);
                } else {
                    remainingAttempts = authManager.getRemainingProtectedPlayerAttempts(playerName);
                }

                // Send auth request with account type and timeout
                NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new com.example.mysubmod.auth.AdminAuthRequestPacket(accountTypeStr, remainingAttempts, 60)
                );

                MySubMod.LOGGER.info("Player {} added to parking lobby as {}", playerName, accountTypeStr);
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

            // Check if player was in parking lobby (unauthenticated)
            boolean wasInLobby = parkingLobby.isInParkingLobby(player.getUUID());

            // Get remaining time BEFORE removing player
            long remainingTime = wasInLobby ? parkingLobby.getRemainingTimeForAccount(playerName) : 0;

            // Clear authentication state when player disconnects
            com.example.mysubmod.auth.AdminAuthManager.getInstance().handleDisconnect(player);
            com.example.mysubmod.auth.AuthManager.getInstance().handleDisconnect(player);
            parkingLobby.removePlayer(player.getUUID());

            // If player was in lobby (unauthenticated), authorize next in queue with remaining time
            if (wasInLobby) {
                MySubMod.LOGGER.info("Player {} disconnected from parking lobby with {}ms remaining - authorizing next in queue",
                    playerName, remainingTime);
                parkingLobby.authorizeNextInQueue(playerName, remainingTime);
            }
        }
    }

    /**
     * Priority kick system: If server is full (10 players) and a protected account connects,
     * kick a random FREE_PLAYER to make room
     */
    private static void checkAndKickForPriority(ServerPlayer protectedPlayer, com.example.mysubmod.auth.AuthManager.AccountType accountType) {
        int maxPlayers = protectedPlayer.server.getMaxPlayers();
        java.util.List<ServerPlayer> onlinePlayers = protectedPlayer.server.getPlayerList().getPlayers();

        // Check if server is full (including the protected player who just joined)
        // If we have exactly maxPlayers, we need to kick someone to make room
        if (onlinePlayers.size() < maxPlayers) {
            return; // Not full, no kick needed
        }

        MySubMod.LOGGER.info("Server full ({}/{}), checking for free players to kick for protected account {}",
            onlinePlayers.size(), maxPlayers, protectedPlayer.getName().getString());

        // Find all FREE_PLAYER accounts currently online (exclude the protected player who just joined)
        java.util.List<ServerPlayer> freePlayers = new java.util.ArrayList<>();
        com.example.mysubmod.auth.AuthManager authManager = com.example.mysubmod.auth.AuthManager.getInstance();

        for (ServerPlayer p : onlinePlayers) {
            if (p.getUUID().equals(protectedPlayer.getUUID())) {
                continue; // Skip the protected player who just joined
            }

            com.example.mysubmod.auth.AuthManager.AccountType type = authManager.getAccountType(p.getName().getString());
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
}
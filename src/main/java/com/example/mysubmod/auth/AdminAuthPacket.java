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

            int result;

            if (isAdmin) {
                // Admin authentication
                result = adminAuthManager.attemptLogin(player, packet.password);
            } else {
                // Protected player authentication (with blacklist tracking)
                result = authManager.attemptProtectedPlayerLogin(player, packet.password);
            }

            if (result == 0) {
                // Success - Remove from parking lobby
                parkingLobby.removePlayer(player.getUUID());

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

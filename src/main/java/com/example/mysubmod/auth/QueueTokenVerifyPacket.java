package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submode1.network.SubMode1CandyCountUpdatePacket;
import com.example.mysubmod.submodes.submodeParent.network.GameTimerPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server with stored queue token
 * Sent automatically when connecting during monopoly window
 */
public class QueueTokenVerifyPacket {
    private final String accountName;
    private final String token;

    public QueueTokenVerifyPacket(String accountName, String token) {
        this.accountName = accountName;
        this.token = token;
    }

    public static void encode(QueueTokenVerifyPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.accountName, 50);
        buf.writeUtf(packet.token, 10);
    }

    public static QueueTokenVerifyPacket decode(FriendlyByteBuf buf) {
        return new QueueTokenVerifyPacket(
            buf.readUtf(50),
            buf.readUtf(10)
        );
    }

    public static void handle(QueueTokenVerifyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ParkingLobbyManager parkingLobby = ParkingLobbyManager.getInstance();
            String playerName = player.getName().getString();

            MySubMod.LOGGER.info("Received token verification from {} for account {} with token {}",
                playerName, packet.accountName, packet.token);

            // Verify that player is trying to access the correct account
            if (!playerName.equalsIgnoreCase(packet.accountName)) {
                MySubMod.LOGGER.warn("Token verification mismatch - player: {}, account: {}",
                    playerName, packet.accountName);
                return;
            }

            // Verify IP + token
            String ipAddress = player.getIpAddress();
            boolean authorized = parkingLobby.isAuthorizedWithToken(packet.accountName, ipAddress, packet.token);

            if (authorized) {
                MySubMod.LOGGER.info("Token verification successful for {} from IP {} - consuming authorization",
                    packet.accountName, ipAddress);
                // Token is valid - consume the authorization
                parkingLobby.consumeAuthorization(packet.accountName, ipAddress);

                // Clear the token from client storage (send packet to remove it)
                com.example.mysubmod.network.NetworkHandler.sendToPlayer(
                    new QueueTokenPacket(packet.accountName, "", 0, 0), // Empty token = clear
                    player
                );

                // Now teleport player to authentication platform and add to parking lobby
                // Make player invisible
                player.setInvisible(true);

                // Teleport to isolated authentication platform at (10000, 200, 10000)
                net.minecraft.server.level.ServerLevel world = player.getServer().overworld();
                net.minecraft.core.BlockPos platformPos = new net.minecraft.core.BlockPos(10000, 200, 10000);

                // Create a 3x3 bedrock platform
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        world.setBlock(platformPos.offset(x, -1, z),
                            net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(), 3);
                    }
                }

                // Teleport player to center of platform
                player.teleportTo(world, 10000.5, 200, 10000.5, 0, 0);

                // Determine account type
                com.example.mysubmod.auth.AuthManager authManager = com.example.mysubmod.auth.AuthManager.getInstance();
                com.example.mysubmod.auth.AuthManager.AccountType accountType = authManager.getAccountType(packet.accountName);
                String accountTypeStr;
                if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN) {
                    accountTypeStr = "ADMIN";
                } else if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER) {
                    accountTypeStr = "PROTECTED_PLAYER";
                } else {
                    accountTypeStr = "TEMPORARY";
                }

                // Add to parking lobby
                parkingLobby.addPlayer(player, accountTypeStr);

                // Clear SubMode1 UI elements
                com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new GameTimerPacket(-1));
                com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new SubMode1CandyCountUpdatePacket(new java.util.HashMap<>()));

                // Open authentication screen based on account type
                int remainingAttempts = 3; // Default value
                int timeoutSeconds = 0;

                if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.ADMIN) {
                    com.example.mysubmod.auth.AdminAuthManager adminAuthManager = com.example.mysubmod.auth.AdminAuthManager.getInstance();
                    remainingAttempts = adminAuthManager.getRemainingAttemptsByName(packet.accountName);
                } else if (accountType == com.example.mysubmod.auth.AuthManager.AccountType.PROTECTED_PLAYER) {
                    remainingAttempts = authManager.getRemainingProtectedPlayerAttempts(packet.accountName);
                }

                // Send authentication request to open password screen
                com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new AdminAuthRequestPacket(accountTypeStr, remainingAttempts, timeoutSeconds)
                );

                player.sendSystemMessage(Component.literal(
                    "§a§lToken vérifié avec succès!\n\n§eVeuillez vous authentifier avec votre mot de passe."
                ));

                MySubMod.LOGGER.info("Player {} teleported and added to parking lobby after token verification, authentication screen sent", packet.accountName);
            } else {
                MySubMod.LOGGER.warn("Token verification failed for {} from IP {} with token {}",
                    packet.accountName, ipAddress, packet.token);
                // Invalid token - kick player
                player.getServer().execute(() -> {
                    player.connection.disconnect(Component.literal(
                        "§c§lToken de connexion invalide\n\n" +
                        "§eVotre token n'est pas valide pour cette fenêtre de monopole."
                    ));
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

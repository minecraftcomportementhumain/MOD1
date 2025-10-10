package com.example.mysubmod.mixin;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.auth.AdminAuthManager;
import com.example.mysubmod.auth.AuthManager;
import com.example.mysubmod.auth.ParkingLobbyManager;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hides unauthenticated players and authenticated admins from clients
 * This ensures the player count in menu M is correct
 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerListBroadcastFilter {

    @Shadow
    public abstract List<ServerPlayer> getPlayers();

    /**
     * After a player is placed, hide unauthenticated players from each other
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("TAIL")
    )
    private void hideUnauthenticatedPlayers(Connection connection, ServerPlayer joiningPlayer, CallbackInfo ci) {
        ParkingLobbyManager parkingLobby = ParkingLobbyManager.getInstance();
        AuthManager authManager = AuthManager.getInstance();
        AdminAuthManager adminAuthManager = AdminAuthManager.getInstance();

        net.minecraft.server.MinecraftServer server = joiningPlayer.getServer();
        if (server == null) return;

        server.execute(() -> {
            // Step 1: Hide the joining player from other clients if they're unauthenticated/admin
            String joiningPlayerName = joiningPlayer.getName().getString();
            boolean shouldHideJoiningPlayer = false;
            String reason = "";

            if (parkingLobby.isTemporaryQueueName(joiningPlayerName)) {
                shouldHideJoiningPlayer = true;
                reason = "queue candidate";
            } else if (parkingLobby.isInParkingLobby(joiningPlayer.getUUID())) {
                shouldHideJoiningPlayer = true;
                reason = "unauthenticated";
            } else {
                AuthManager.AccountType accountType = authManager.getAccountType(joiningPlayerName);
                if (accountType == AuthManager.AccountType.ADMIN) {
                    shouldHideJoiningPlayer = true;
                    reason = "admin";
                }
            }

            if (shouldHideJoiningPlayer) {
                MySubMod.LOGGER.info("Hiding {} player {} from clients", reason, joiningPlayerName);
                ClientboundPlayerInfoRemovePacket removePacket = new ClientboundPlayerInfoRemovePacket(
                    List.of(joiningPlayer.getUUID())
                );

                int clientCount = 0;
                for (ServerPlayer client : getPlayers()) {
                    client.connection.send(removePacket);
                    clientCount++;
                }
                MySubMod.LOGGER.info("Sent REMOVE packet for {} to {} clients", joiningPlayerName, clientCount);
            }

            // Step 2: Hide existing unauthenticated/admin players from the joining player
            List<java.util.UUID> playersToHide = new java.util.ArrayList<>();
            for (ServerPlayer existingPlayer : getPlayers()) {
                if (existingPlayer.getUUID().equals(joiningPlayer.getUUID())) {
                    continue; // Skip the joining player themselves
                }

                String existingPlayerName = existingPlayer.getName().getString();
                boolean shouldHideExisting = false;

                if (parkingLobby.isTemporaryQueueName(existingPlayerName)) {
                    shouldHideExisting = true;
                } else if (parkingLobby.isInParkingLobby(existingPlayer.getUUID())) {
                    shouldHideExisting = true;
                } else {
                    AuthManager.AccountType accountType = authManager.getAccountType(existingPlayerName);
                    if (accountType == AuthManager.AccountType.ADMIN && adminAuthManager.isAuthenticated(existingPlayer)) {
                        shouldHideExisting = true;
                    }
                }

                if (shouldHideExisting) {
                    playersToHide.add(existingPlayer.getUUID());
                }
            }

            if (!playersToHide.isEmpty()) {
                ClientboundPlayerInfoRemovePacket hideExistingPacket = new ClientboundPlayerInfoRemovePacket(playersToHide);
                joiningPlayer.connection.send(hideExistingPacket);
                MySubMod.LOGGER.info("Sent REMOVE packet for {} existing players to {}", playersToHide.size(), joiningPlayerName);
            }
        });
    }
}

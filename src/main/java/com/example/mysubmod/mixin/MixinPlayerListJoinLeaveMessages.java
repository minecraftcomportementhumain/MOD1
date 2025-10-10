package com.example.mysubmod.mixin;

import com.example.mysubmod.MySubMod;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class MixinPlayerListJoinLeaveMessages {

    @Shadow
    public abstract void broadcastSystemMessage(Component message, boolean overlay);

    /**
     * Suppress join messages for temporary queue candidates
     */
    @Redirect(
        method = "placeNewPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
        )
    )
    private void redirectJoinBroadcast(PlayerList instance, Component message, boolean overlay, Connection connection, ServerPlayer player) {
        String playerName = player.getName().getString();

        // Check if this is a REAL temporary queue candidate (not just a name starting with "_Q_")
        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();
        if (parkingLobby.isTemporaryQueueName(playerName)) {
            MySubMod.LOGGER.info("MIXIN: Suppressing join message for queue candidate: {}", playerName);
            // Don't broadcast the message
            return;
        }

        // Check if this is an unauthenticated protected/admin
        com.example.mysubmod.auth.AuthManager authManager = com.example.mysubmod.auth.AuthManager.getInstance();
        com.example.mysubmod.auth.AdminAuthManager adminAuthManager = com.example.mysubmod.auth.AdminAuthManager.getInstance();

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
                MySubMod.LOGGER.info("MIXIN: Suppressing join message for unauthenticated protected/admin: {}", playerName);
                // Don't broadcast the message
                return;
            }
        }

        // Normal player - broadcast as usual
        instance.broadcastSystemMessage(message, overlay);
    }
}

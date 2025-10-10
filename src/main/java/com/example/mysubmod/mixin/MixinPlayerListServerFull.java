package com.example.mysubmod.mixin;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.auth.AuthManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(value = PlayerList.class, priority = 900)
public abstract class MixinPlayerListServerFull {

    @Shadow
    public abstract int getMaxPlayers();

    @Shadow
    public abstract java.util.List<net.minecraft.server.level.ServerPlayer> getPlayers();

    /**
     * Intercept canPlayerLogin to allow protected accounts to bypass server full check
     * ONLY if there is at least one FREE_PLAYER to kick
     */
    @Inject(
        method = "canPlayerLogin",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onCanPlayerLogin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
        String playerName = profile.getName();

        // Check if this is a temporary name (queue candidate)
        String actualAccountName = playerName;
        if (playerName.startsWith("_Q_")) {
            com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();
            String originalName = parkingLobby.getOriginalAccountName(playerName);
            if (originalName != null) {
                actualAccountName = originalName;
                MySubMod.LOGGER.info("MIXIN PlayerList: Detected temporary name {} -> actual account: {}", playerName, actualAccountName);
            }
        }

        // Check account type (use actual account name)
        AuthManager authManager = AuthManager.getInstance();
        AuthManager.AccountType accountType = authManager.getAccountType(actualAccountName);
        boolean isProtectedAccount = (accountType == AuthManager.AccountType.ADMIN ||
                                      accountType == AuthManager.AccountType.PROTECTED_PLAYER);

        int maxPlayers = getMaxPlayers();
        com.example.mysubmod.auth.ParkingLobbyManager parkingLobby = com.example.mysubmod.auth.ParkingLobbyManager.getInstance();

        // ALWAYS count REAL players (exclude queue candidates, authenticated admins, and unauthenticated players)
        // This ensures unauthenticated players don't count towards server capacity
        int realPlayerCount = 0;
        int queueCandidateCount = 0;
        int adminCount = 0;
        int unauthenticatedCount = 0;
        for (net.minecraft.server.level.ServerPlayer player : getPlayers()) {
            String name = player.getName().getString();
            if (parkingLobby.isTemporaryQueueName(name)) {
                queueCandidateCount++;
            } else if (parkingLobby.isInParkingLobby(player.getUUID())) {
                // Player in parking lobby (not authenticated) - don't count as real player
                unauthenticatedCount++;
            } else {
                // Check if this is an authenticated admin (authenticated admins don't count towards max_players for ANYONE)
                AuthManager.AccountType type = authManager.getAccountType(name);
                boolean isAuthenticatedAdmin = (type == AuthManager.AccountType.ADMIN &&
                                                com.example.mysubmod.auth.AdminAuthManager.getInstance().isAuthenticated(player));

                if (isAuthenticatedAdmin) {
                    adminCount++;
                } else {
                    realPlayerCount++;
                }
            }
        }

        // If realPlayerCount < maxPlayers, allow connection for EVERYONE (admins and unauthenticated don't count)
        if (realPlayerCount < maxPlayers) {
            MySubMod.LOGGER.info("MIXIN PlayerList: Server has space ({}/{} real players, {} admins, {} unauthenticated, {} queue candidates) - allowing {} to connect",
                realPlayerCount, maxPlayers, adminCount, unauthenticatedCount, queueCandidateCount, actualAccountName);
            cir.setReturnValue(null);
            return;
        }

        // Server is truly full (realPlayerCount >= maxPlayers)
        // Only protected accounts can bypass by kicking FREE_PLAYER
        if (isProtectedAccount) {
            // Check if someone is already authenticating on this account (queue candidate scenario)
            if (parkingLobby.isAccountBeingAuthenticated(actualAccountName) && parkingLobby.hasQueueRoom(actualAccountName)) {
                MySubMod.LOGGER.info("MIXIN PlayerList: Server full ({}/{} real players, {} admins, {} unauthenticated, {} queue candidates) but allowing queue candidate for {} (someone authenticating)",
                    realPlayerCount, maxPlayers, adminCount, unauthenticatedCount, queueCandidateCount, actualAccountName);
                // Allow connection for password verification and queue
                cir.setReturnValue(null);
                return;
            }

            // Check if there is at least one FREE_PLAYER online to kick (exclude queue candidates, admins, and unauthenticated)
            boolean hasFreePlayer = false;
            for (net.minecraft.server.level.ServerPlayer player : getPlayers()) {
                String name = player.getName().getString();
                // Skip queue candidates - they are NOT real players and should never be considered for kicking
                if (parkingLobby.isTemporaryQueueName(name)) {
                    continue;
                }

                // Skip unauthenticated players - they are NOT real players and should never be considered for kicking
                if (parkingLobby.isInParkingLobby(player.getUUID())) {
                    continue;
                }

                // Skip authenticated admins - they don't count and can't be kicked
                AuthManager.AccountType type = authManager.getAccountType(name);
                boolean isAuthenticatedAdmin = (type == AuthManager.AccountType.ADMIN &&
                                                com.example.mysubmod.auth.AdminAuthManager.getInstance().isAuthenticated(player));
                if (isAuthenticatedAdmin) {
                    continue;
                }

                if (type == AuthManager.AccountType.FREE_PLAYER) {
                    hasFreePlayer = true;
                    break;
                }
            }

            if (hasFreePlayer) {
                MySubMod.LOGGER.info("MIXIN PlayerList: Server full ({}/{} real players, {} admins, {} unauthenticated, {} queue candidates) but allowing protected account {} to bypass limit (will kick FREE player)",
                    realPlayerCount, maxPlayers, adminCount, unauthenticatedCount, queueCandidateCount, actualAccountName);

                // Return null to allow connection (null = no error, can join)
                cir.setReturnValue(null);
            } else {
                MySubMod.LOGGER.warn("MIXIN PlayerList: Server full ({}/{} real players, {} admins, {} unauthenticated, {} queue candidates) with only protected accounts - denying protected account {}",
                    realPlayerCount, maxPlayers, adminCount, unauthenticatedCount, queueCandidateCount, actualAccountName);
                // Don't set return value - let vanilla handle it (will show "server full" message)
            }
        } else {
            // FREE_PLAYER and server is truly full - deny
            MySubMod.LOGGER.info("MIXIN PlayerList: Server full ({}/{} real players, {} admins, {} unauthenticated, {} queue candidates) - denying free player {}",
                realPlayerCount, maxPlayers, adminCount, unauthenticatedCount, queueCandidateCount, actualAccountName);
            // Don't set return value - let vanilla handle it (will show "server full" message)
        }
    }
}

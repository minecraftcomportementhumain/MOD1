package com.example.mysubmod.mixin;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.auth.AdminAuthManager;
import com.example.mysubmod.auth.AuthManager;
import com.example.mysubmod.auth.ParkingLobbyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/**
 * Mixin to override player count to exclude unauthenticated players AND authenticated admins
 * Only counts: FREE_PLAYER + authenticated PROTECTED_PLAYER
 * Admins don't count towards the server limit
 */
@Mixin(value = PlayerList.class, priority = 1001)
public abstract class MixinPlayerListCount {

    @Shadow
    public abstract List<ServerPlayer> getPlayers();

    /**
     * @author MySubMod
     * @reason Filter unauthenticated players and admins from player count
     */
    @Overwrite
    public int getPlayerCount() {
        ParkingLobbyManager parkingLobby = ParkingLobbyManager.getInstance();
        AuthManager authManager = AuthManager.getInstance();
        AdminAuthManager adminAuthManager = AdminAuthManager.getInstance();

        int count = 0;
        for (ServerPlayer player : getPlayers()) {
            String playerName = player.getName().getString();

            // Skip queue candidates (temporary names)
            if (parkingLobby.isTemporaryQueueName(playerName)) {
                continue;
            }

            // Skip players in parking lobby (unauthenticated)
            if (parkingLobby.isInParkingLobby(player.getUUID())) {
                continue;
            }

            // Skip authenticated admins - they don't count towards server limit
            AuthManager.AccountType accountType = authManager.getAccountType(playerName);
            if (accountType == AuthManager.AccountType.ADMIN && adminAuthManager.isAuthenticated(player)) {
                continue;
            }

            // Count this player (FREE_PLAYER or authenticated PROTECTED_PLAYER)
            count++;
        }

        return count;
    }
}

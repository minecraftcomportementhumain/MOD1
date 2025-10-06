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

@Mixin(PlayerList.class)
public abstract class MixinPlayerListServerFull {

    @Shadow
    public abstract int getMaxPlayers();

    @Shadow
    public abstract int getPlayerCount();

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

        // Check if this is a protected account
        AuthManager authManager = AuthManager.getInstance();
        AuthManager.AccountType accountType = authManager.getAccountType(playerName);
        boolean isProtectedAccount = (accountType == AuthManager.AccountType.ADMIN ||
                                      accountType == AuthManager.AccountType.PROTECTED_PLAYER);

        if (isProtectedAccount) {
            int currentPlayers = getPlayerCount();
            int maxPlayers = getMaxPlayers();

            if (currentPlayers >= maxPlayers) {
                // Check if there is at least one FREE_PLAYER online to kick
                boolean hasFreePlayer = false;
                for (net.minecraft.server.level.ServerPlayer player : getPlayers()) {
                    AuthManager.AccountType type = authManager.getAccountType(player.getName().getString());
                    if (type == AuthManager.AccountType.FREE_PLAYER) {
                        hasFreePlayer = true;
                        break;
                    }
                }

                if (hasFreePlayer) {
                    MySubMod.LOGGER.info("MIXIN PlayerList: Server full ({}/{}) but allowing protected account {} to bypass limit (will kick FREE player)",
                        currentPlayers, maxPlayers, playerName);

                    // Return null to allow connection (null = no error, can join)
                    cir.setReturnValue(null);
                } else {
                    MySubMod.LOGGER.warn("MIXIN PlayerList: Server full ({}/{}) with only protected accounts - denying protected account {}",
                        currentPlayers, maxPlayers, playerName);
                    // Don't set return value - let vanilla handle it (will show "server full" message)
                }
            }
        }
    }
}

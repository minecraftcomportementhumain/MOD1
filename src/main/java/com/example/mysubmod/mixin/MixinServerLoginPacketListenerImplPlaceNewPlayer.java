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

        AdminAuthManager authManager = AdminAuthManager.getInstance();

        // Get the player list to check for existing connection
        PlayerList playerList = this.server.getPlayerList();

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

            if (authManager.isAdminAccount(playerName)) {
                if (authManager.isAuthenticated(existingPlayer)) {
                    // Deny new connection - keep authenticated admin
                    MySubMod.LOGGER.warn("MIXIN: Denying connection for {} - admin already authenticated", playerName);
                    this.connection.send(new ClientboundLoginDisconnectPacket(
                        Component.literal("§c§lConnexion refusée\n\n§eUn administrateur authentifié utilise déjà ce compte.")
                    ));
                    this.connection.disconnect(Component.literal("Authenticated admin already connected"));
                    ci.cancel();
                } else if (authManager.isProtectedDuringAuth(existingPlayer)) {
                    // Admin is currently authenticating (30s protection) - deny new connection
                    MySubMod.LOGGER.warn("MIXIN: Denying connection for {} - admin is authenticating (protected)", playerName);
                    this.connection.send(new ClientboundLoginDisconnectPacket(
                        Component.literal("§c§lConnexion refusée\n\n§eUn administrateur est en cours d'authentification sur ce compte.\n§7Veuillez patienter 30 secondes.")
                    ));
                    this.connection.disconnect(Component.literal("Admin authenticating"));
                    ci.cancel();
                } else {
                    // Allow vanilla to kick old unauthenticated admin
                    MySubMod.LOGGER.info("MIXIN: Allowing connection for {} - will kick unauthenticated admin", playerName);
                    // Don't cancel - let vanilla handle the kick
                }
            } else {
                // Non-admin - deny new connection
                MySubMod.LOGGER.warn("MIXIN: Denying connection for {} - player already connected", playerName);
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

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
            com.example.mysubmod.auth.AdminAuthManager authManager = com.example.mysubmod.auth.AdminAuthManager.getInstance();
            String playerName = player.getName().getString();

            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SubModeChangePacket(SubModeManager.getInstance().getCurrentMode())
            );

            // Check if player is an admin account (including ops) that needs authentication
            boolean isAdminAccount = authManager.isAdminAccount(playerName);
            boolean isOp = player.hasPermissions(2);

            MySubMod.LOGGER.info("Player {} (UUID: {}) login check: isAdminAccount={}, isOp={}", playerName, player.getUUID(), isAdminAccount, isOp);

            // Check if this player needs authentication (admin list OR ops with password set)
            if (isAdminAccount || isOp) {
                // Only require authentication if they have a password set
                if (isAdminAccount) {
                    MySubMod.LOGGER.info("Sending auth request to player {}", playerName);
                    String ipAddress = player.getIpAddress();

                    // Check if IP is blacklisted
                    if (authManager.isIPBlacklisted(ipAddress)) {
                        long remainingTime = authManager.getRemainingIPBlacklistTime(ipAddress);
                        long minutes = remainingTime / 60000;

                        player.getServer().execute(() -> {
                            player.connection.disconnect(net.minecraft.network.chat.Component.literal(
                                "§4§lIP Blacklistée\n\n§cTrop de tentatives de connexion depuis cette IP.\n§7Temps restant: §e" + minutes + " minute(s)"
                            ));
                        });
                        return;
                    }

                    // Check if account is blacklisted
                    if (authManager.isBlacklisted(playerName)) {
                        // Blacklisted - kick player with remaining time
                        long remainingTime = authManager.getRemainingBlacklistTime(playerName);
                        long minutes = remainingTime / 60000;

                        player.getServer().execute(() -> {
                            player.connection.disconnect(net.minecraft.network.chat.Component.literal(
                                "§4§lCompte Blacklisté\n\n§cTrop de tentatives de connexion échouées.\n§7Temps restant: §e" + minutes + " minute(s)"
                            ));
                        });
                        return;
                    }

                    // Not blacklisted - send authentication request
                    authManager.startAuthenticationProtection(player); // Start 30-second protection
                    NetworkHandler.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new com.example.mysubmod.auth.AdminAuthRequestPacket(3) // 3 attempts per session
                    );
                }
            }

            // Send admin status (will be false for unauthenticated admins)
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new AdminStatusPacket(SubModeManager.getInstance().isAdmin(player))
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Clear authentication state when player disconnects
            com.example.mysubmod.auth.AdminAuthManager.getInstance().handleDisconnect(player);
        }
    }
}
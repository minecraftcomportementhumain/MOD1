package com.example.mysubmod.auth;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Blocks all player actions while in parking lobby
 */
@Mod.EventBusSubscriber
public class ParkingLobbyEventHandler {

    /**
     * Block movement
     */
    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (ParkingLobbyManager.getInstance().isInParkingLobby(player.getUUID())) {
                // Freeze position
                player.setDeltaMovement(0, player.getDeltaMovement().y, 0);

                // Reset head rotation to prevent looking around
                player.setXRot(0);
                player.setYRot(0);
            }
        }
    }

    /**
     * Block chat
     */
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (ParkingLobbyManager.getInstance().isInParkingLobby(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * Block interactions (right-click)
     */
    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            if (ParkingLobbyManager.getInstance().isInParkingLobby(serverPlayer.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Block attacks
     */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            if (ParkingLobbyManager.getInstance().isInParkingLobby(player.getUUID())) {
                event.setCanceled(true);
            }
        }
    }
}

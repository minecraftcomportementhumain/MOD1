package com.example.mysubmod.submodes.waitingroom;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WaitingRoomEventHandler {

    @SubscribeEvent
    public static void onPlayerAttack(LivingAttackEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.WAITING_ROOM) {
            return;
        }

        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            if (WaitingRoomManager.getInstance().isPlayerInWaitingRoom(attacker)) {
                event.setCanceled(true);
                attacker.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en salle d'attente"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.WAITING_ROOM) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (WaitingRoomManager.getInstance().isPlayerInWaitingRoom(player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en salle d'attente"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.WAITING_ROOM) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (WaitingRoomManager.getInstance().isPlayerInWaitingRoom(player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser d'objets en salle d'attente"));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.WAITING_ROOM) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer player) {
            if (WaitingRoomManager.getInstance().isPlayerInWaitingRoom(player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en salle d'attente"));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.WAITING_ROOM) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (WaitingRoomManager.getInstance().isPlayerInWaitingRoom(player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en salle d'attente"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerCrafting(PlayerEvent.ItemCraftedEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.WAITING_ROOM) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (WaitingRoomManager.getInstance().isPlayerInWaitingRoom(player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en salle d'attente"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDropItem(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.WAITING_ROOM) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer player) {
            if (WaitingRoomManager.getInstance().isPlayerInWaitingRoom(player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en salle d'attente"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerPickupItem(PlayerEvent.ItemPickupEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.WAITING_ROOM) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (WaitingRoomManager.getInstance().isPlayerInWaitingRoom(player)) {
                // Remove items from inventory immediately since pickup can't be canceled
                player.getInventory().removeItem(event.getStack());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() == SubMode.WAITING_ROOM) {
            if (event.getEntity() instanceof ServerPlayer player) {
                WaitingRoomManager.getInstance().teleportPlayerToWaitingRoom(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WaitingRoomManager.getInstance().restorePlayerInventory(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() == SubMode.WAITING_ROOM) {
            WaitingRoomManager.getInstance().deactivate(event.getServer());
        }
    }

    public static void checkPlayerBoundaries(ServerPlayer player) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.WAITING_ROOM) {
            return;
        }

        if (!WaitingRoomManager.getInstance().isPlayerInWaitingRoom(player)) {
            return;
        }

        BlockPos center = WaitingRoomManager.getInstance().getPlatformCenter();
        int platformSize = WaitingRoomManager.getInstance().getPlatformSize();
        Vec3 playerPos = player.position();

        double distanceX = Math.abs(playerPos.x - center.getX());
        double distanceZ = Math.abs(playerPos.z - center.getZ());

        if (distanceX > platformSize/2.0 - 1 || distanceZ > platformSize/2.0 - 1) {
            Vec3 teleportPos = new Vec3(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);
            player.teleportTo(teleportPos.x, teleportPos.y, teleportPos.z);
            player.sendSystemMessage(Component.literal("§eVous ne pouvez pas quitter la plateforme de la salle d'attente"));
        }
    }
}
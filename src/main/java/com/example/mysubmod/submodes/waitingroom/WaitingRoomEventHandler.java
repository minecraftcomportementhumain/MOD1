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
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
    private static boolean hologramsCleanedUp = false;

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
                // Clean up orphaned holograms on first player join (when entities are actually loaded)
                if (!hologramsCleanedUp) {
                    player.server.execute(() -> {
                        cleanupOrphanedHolograms(player.serverLevel());
                        hologramsCleanedUp = true;
                    });
                }

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
        // Reset the cleanup flag for next server start
        hologramsCleanedUp = false;

        if (SubModeManager.getInstance().getCurrentMode() == SubMode.WAITING_ROOM) {
            WaitingRoomManager.getInstance().deactivate(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Only active in waiting room mode
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.WAITING_ROOM) {
            return;
        }

        // Block hostile mobs near the waiting room platform
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster) {
            BlockPos spawnPos = event.getEntity().blockPosition();

            // Check if spawn is near the waiting room platform
            if (isNearWaitingRoomPlatform(spawnPos)) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean isNearWaitingRoomPlatform(BlockPos pos) {
        WaitingRoomManager manager = WaitingRoomManager.getInstance();
        BlockPos platformCenter = manager.getPlatformCenter();
        int platformSize = manager.getPlatformSize();

        // Check if within a larger radius around the platform for safety
        // Platform is 20x20, so we add extra buffer (30 block radius total)
        int safetyRadius = platformSize + 10;

        return isWithinRadius(pos, platformCenter, safetyRadius);
    }

    private static boolean isWithinRadius(BlockPos pos1, BlockPos pos2, int radius) {
        if (pos2 == null) return false;
        double distance = Math.sqrt(Math.pow(pos1.getX() - pos2.getX(), 2) +
                                  Math.pow(pos1.getZ() - pos2.getZ(), 2));
        return distance <= radius;
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

    private static void cleanupOrphanedHolograms(net.minecraft.server.level.ServerLevel level) {
        int removed = 0;

        // The central square is at (0, 100, 0) where SubMode1 holograms spawn
        BlockPos centralSquare = new BlockPos(0, 100, 0);

        MySubMod.LOGGER.info("Cleaning up orphaned holograms at central square (first player connected)...");

        // Create a bounding box around the central square area
        net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(
            centralSquare.getX() - 15, 100, centralSquare.getZ() - 15,
            centralSquare.getX() + 15, 105, centralSquare.getZ() + 15
        );

        // Get all armor stands in this area
        java.util.List<net.minecraft.world.entity.decoration.ArmorStand> armorStands = level.getEntitiesOfClass(
            net.minecraft.world.entity.decoration.ArmorStand.class,
            searchBox,
            armorStand -> armorStand.isInvisible() && armorStand.isCustomNameVisible()
        );

        MySubMod.LOGGER.info("Found {} armor stands in search area", armorStands.size());

        // Remove all found holograms
        for (net.minecraft.world.entity.decoration.ArmorStand armorStand : armorStands) {
            MySubMod.LOGGER.info("Removing orphaned hologram at {} with name: {}",
                armorStand.blockPosition(),
                armorStand.getCustomName() != null ? armorStand.getCustomName().getString() : "no name");
            armorStand.discard();
            removed++;
        }

        if (removed > 0) {
            MySubMod.LOGGER.info("Cleaned up {} orphaned hologram entities", removed);
        } else {
            MySubMod.LOGGER.info("No orphaned holograms found");
        }
    }
}
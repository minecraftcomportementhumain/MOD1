package com.example.mysubmod.submodes.submode1;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.items.ModItems;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SubMode1EventHandler {
    private static int positionLogTicks = 0;
    private static final int POSITION_LOG_INTERVAL = 100; // Log every 5 seconds (100 ticks)

    @SubscribeEvent
    public static void onPlayerAttack(LivingAttackEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            if (SubMode1Manager.getInstance().isPlayerAlive(attacker.getUUID())) {
                event.setCanceled(true);
                attacker.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en sous-mode 1"));
            } else if (SubMode1Manager.getInstance().isPlayerSpectator(attacker.getUUID())) {
                event.setCanceled(true);
                attacker.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (SubMode1Manager.getInstance().isPlayerAlive(player.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en sous-mode 1"));
            } else if (SubMode1Manager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer player) {
            if (SubMode1Manager.getInstance().isPlayerAlive(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en sous-mode 1"));
            } else if (SubMode1Manager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (SubMode1Manager.getInstance().isPlayerAlive(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en sous-mode 1"));
            } else if (SubMode1Manager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerCrafting(PlayerEvent.ItemCraftedEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (SubMode1Manager.getInstance().isPlayerAlive(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en sous-mode 1"));
            } else if (SubMode1Manager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerPickupItem(PlayerEvent.ItemPickupEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack stack = event.getStack();

            // Only allow candy pickup for alive players
            if (SubMode1Manager.getInstance().isPlayerAlive(player.getUUID())) {
                if (stack.is(ModItems.CANDY.get())) {
                    // Log candy pickup
                    if (SubMode1Manager.getInstance().getDataLogger() != null) {
                        BlockPos pos = new BlockPos((int)player.getX(), (int)player.getY(), (int)player.getZ());
                        SubMode1Manager.getInstance().getDataLogger().logCandyPickup(player, pos);
                    }

                    // Notify candy manager
                    ItemEntity itemEntity = event.getOriginalEntity();
                    if (itemEntity != null) {
                        SubMode1CandyManager.getInstance().onCandyPickup(itemEntity);
                    }
                } else {
                    // Remove non-candy items from inventory immediately
                    player.getInventory().removeItem(stack);
                }
            } else {
                // Remove items from spectator inventory
                player.getInventory().removeItem(stack);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() == SubMode.SUB_MODE_1) {
            if (event.getEntity() instanceof ServerPlayer player) {
                // New players joining during SubMode1 go to spectator
                SubMode1Manager.getInstance().teleportToSpectator(player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (!SubMode1Manager.getInstance().isGameActive()) {
            return;
        }

        // Keep permanent daylight
        net.minecraft.server.level.ServerLevel overworld = event.getServer().getLevel(net.minecraft.server.level.ServerLevel.OVERWORLD);
        if (overworld != null) {
            long currentTime = overworld.getDayTime() % 24000;
            if (currentTime > 12000) { // If it's night (after 12000 ticks)
                overworld.setDayTime(6000); // Reset to noon
            }
        }

        positionLogTicks++;
        if (positionLogTicks >= POSITION_LOG_INTERVAL) {
            positionLogTicks = 0;

            // Log positions of all alive players and check spectator boundaries
            if (SubMode1Manager.getInstance().getDataLogger() != null) {
                for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                    if (SubMode1Manager.getInstance().isPlayerAlive(player.getUUID())) {
                        SubMode1Manager.getInstance().getDataLogger().logPlayerPosition(player);
                    }
                }
            }

            // Check spectator boundaries
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (SubMode1Manager.getInstance().isPlayerSpectator(player.getUUID())) {
                    checkSpectatorBoundaries(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (SubMode1Manager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser d'objets en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDropItem(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer player) {
            if (SubMode1Manager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        // Only check hostile mobs (not players or item entities)
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster) {
            BlockPos spawnPos = event.getEntity().blockPosition();

            // Check if spawn is near any island
            if (isNearIsland(spawnPos)) {
                event.setCanceled(true);
                MySubMod.LOGGER.debug("Blocked hostile mob spawn at {} near islands", spawnPos);
            }
        }
    }

    private static boolean isNearIsland(BlockPos pos) {
        SubMode1Manager manager = SubMode1Manager.getInstance();

        // Check if near small island (-100, 100, -100)
        if (isWithinRadius(pos, manager.getSmallIslandCenter(), 25)) {
            return true;
        }

        // Check if near medium island (0, 100, -100)
        if (isWithinRadius(pos, manager.getMediumIslandCenter(), 30)) {
            return true;
        }

        // Check if near large island (100, 100, -100)
        if (isWithinRadius(pos, manager.getLargeIslandCenter(), 35)) {
            return true;
        }

        // Check if near spectator platform (0, 150, 0)
        BlockPos spectatorCenter = new BlockPos(0, 150, 0);
        if (isWithinRadius(pos, spectatorCenter, 20)) {
            return true;
        }

        return false;
    }

    private static boolean isWithinRadius(BlockPos pos1, BlockPos pos2, int radius) {
        double distance = Math.sqrt(Math.pow(pos1.getX() - pos2.getX(), 2) +
                                  Math.pow(pos1.getZ() - pos2.getZ(), 2));
        return distance <= radius;
    }

    public static void checkSpectatorBoundaries(ServerPlayer player) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (!SubMode1Manager.getInstance().isPlayerSpectator(player.getUUID())) {
            return;
        }

        // Spectator platform center at (0, 150, 0) with size 21x21 (-10 to +10)
        BlockPos spectatorCenter = new BlockPos(0, 150, 0);
        int platformSize = 21;
        Vec3 playerPos = player.position();

        double distanceX = Math.abs(playerPos.x - spectatorCenter.getX());
        double distanceZ = Math.abs(playerPos.z - spectatorCenter.getZ());

        // Check if player is outside platform bounds or below platform
        if (distanceX > platformSize/2.0 - 1 || distanceZ > platformSize/2.0 - 1 || playerPos.y < spectatorCenter.getY() - 5) {
            Vec3 teleportPos = new Vec3(spectatorCenter.getX() + 0.5, spectatorCenter.getY() + 1, spectatorCenter.getZ() + 0.5);
            player.teleportTo(teleportPos.x, teleportPos.y, teleportPos.z);
            player.sendSystemMessage(Component.literal("§eVous ne pouvez pas quitter la plateforme spectateur"));
        }
    }
}
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
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SubMode1EventHandler {
    private static int positionLogTicks = 0;
    private static final int POSITION_LOG_INTERVAL = 100; // Log every 5 seconds (100 ticks)
    private static int candyCountUpdateTicks = 0;
    private static final int CANDY_COUNT_UPDATE_INTERVAL = 40; // Update every 2 seconds (40 ticks)

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

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
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

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer player) {
            net.minecraft.world.level.block.Block block = event.getState().getBlock();

            // Log ALL block break attempts
            MySubMod.LOGGER.info("BlockBreak attempt by {}: {} at {} - isAdmin: {}, isAlive: {}, isSpectator: {}",
                player.getName().getString(),
                block.getClass().getSimpleName(),
                event.getPos(),
                SubModeManager.getInstance().isAdmin(player),
                SubMode1Manager.getInstance().isPlayerAlive(player.getUUID()),
                SubMode1Manager.getInstance().isPlayerSpectator(player.getUUID()));

            // ALWAYS prevent sign breaking for non-admins (to preserve text) - even during selection phase
            if (block instanceof net.minecraft.world.level.block.SignBlock ||
                block instanceof net.minecraft.world.level.block.StandingSignBlock ||
                block instanceof net.minecraft.world.level.block.WallSignBlock) {
                if (!SubModeManager.getInstance().isAdmin(player)) {
                    event.setCanceled(true);
                    MySubMod.LOGGER.info("BLOCKED sign break for non-admin player: {}", player.getName().getString());
                    return;
                } else {
                    MySubMod.LOGGER.info("ALLOWED sign break for admin player: {}", player.getName().getString());
                    return; // Allow admin to break signs
                }
            }

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
    public static void onEntityItemPickup(EntityItemPickupEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack stack = event.getItem().getItem();

            // Only allow candy pickup for alive players
            if (SubMode1Manager.getInstance().isPlayerAlive(player.getUUID())) {
                if (stack.is(ModItems.CANDY.get())) {
                    // Log candy pickup
                    if (SubMode1Manager.getInstance().getDataLogger() != null) {
                        BlockPos pos = new BlockPos((int)player.getX(), (int)player.getY(), (int)player.getZ());
                        SubMode1Manager.getInstance().getDataLogger().logCandyPickup(player, pos);
                    }

                    // Notify candy manager
                    ItemEntity itemEntity = event.getItem();
                    if (itemEntity != null) {
                        SubMode1CandyManager.getInstance().onCandyPickup(itemEntity);
                    }
                    // Allow candy pickup - do not cancel event
                } else {
                    // Cancel pickup of non-candy items
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cVous ne pouvez ramasser que des bonbons en sous-mode 1"));
                }
            } else {
                // Spectators cannot pick up any items
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas ramasser d'objets en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (SubModeManager.getInstance().getCurrentMode() == SubMode.SUB_MODE_1) {
                // New players joining during SubMode1 go to spectator
                SubMode1Manager.getInstance().teleportToSpectator(player);
            } else {
                // Player joining when SubMode1 is NOT active - clear their HUD and timer
                // Send empty candy counts to deactivate candy HUD
                com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.example.mysubmod.submodes.submode1.network.CandyCountUpdatePacket(new java.util.HashMap<>())
                );

                // Send -1 timer to deactivate game timer
                com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.example.mysubmod.submodes.submode1.network.GameTimerPacket(-1)
                );

                MySubMod.LOGGER.info("Cleared SubMode1 HUD for reconnecting player: {}", player.getName().getString());
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

        // Disable sprinting for all players in SubMode1 by setting sprint speed to walk speed
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            // Set movement speed attribute modifier to prevent sprint speed boost
            net.minecraft.world.entity.ai.attributes.AttributeInstance movementSpeed =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);

            if (movementSpeed != null) {
                // Remove any existing sprint modifier
                java.util.UUID sprintModifierUUID = java.util.UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
                movementSpeed.removeModifier(sprintModifierUUID);

                // Add a modifier that cancels sprint speed (sprint normally adds 30% speed)
                // By adding -0.03 modifier when sprinting, we cancel the boost
                if (player.isSprinting()) {
                    net.minecraft.world.entity.ai.attributes.AttributeModifier noSprintModifier =
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            sprintModifierUUID,
                            "No sprint boost",
                            -0.003, // Cancel 30% sprint boost (base speed is 0.1, so 0.1 * 0.3 = 0.03)
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION
                        );

                    if (movementSpeed.getModifier(sprintModifierUUID) == null) {
                        movementSpeed.addTransientModifier(noSprintModifier);
                    }
                }
            }
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

        // Update candy count HUD for all players
        candyCountUpdateTicks++;
        if (candyCountUpdateTicks >= CANDY_COUNT_UPDATE_INTERVAL) {
            candyCountUpdateTicks = 0;

            // Get candy counts from manager
            java.util.Map<com.example.mysubmod.submodes.submode1.islands.IslandType, Integer> candyCounts =
                SubMode1CandyManager.getInstance().getAvailableCandiesPerIsland(event.getServer());

            // Send to all players
            com.example.mysubmod.submodes.submode1.network.CandyCountUpdatePacket packet =
                new com.example.mysubmod.submodes.submode1.network.CandyCountUpdatePacket(candyCounts);
            com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.ALL.noArg(), packet);
        }

        // Note: Dandelion cleanup is done once at start of selection phase in SubMode1Manager
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
            } else if (SubMode1Manager.getInstance().isPlayerAlive(player.getUUID())) {
                // Check if trying to drop a candy
                ItemStack droppedItem = event.getEntity().getItem();
                if (droppedItem.is(ModItems.CANDY.get())) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter les bonbons en sous-mode 1"));
                }
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_1) {
            return;
        }

        // Prevent ALL items (except candies from our spawn system) from spawning on islands and paths
        if (event.getEntity() instanceof ItemEntity itemEntity) {
            BlockPos itemPos = itemEntity.blockPosition();

            // Only block items on islands and paths
            if (SubMode1Manager.getInstance().isNearIslandOrPath(itemPos)) {
                // Allow candies from our spawn system (they have glowing tag and are candy items)
                boolean isCandy = itemEntity.getItem().is(ModItems.CANDY.get());
                boolean hasGlowingTag = itemEntity.hasGlowingTag();

                if (!isCandy || !hasGlowingTag) {
                    event.setCanceled(true);
                    MySubMod.LOGGER.debug("Prevented item {} from spawning at {}",
                        itemEntity.getItem().getItem().getDescriptionId(), itemPos);
                }
            }
        }

        // Block hostile mobs near islands
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

        // Check if near small island
        if (isWithinRadius(pos, manager.getSmallIslandCenter(), 35)) {
            return true;
        }

        // Check if near medium island
        if (isWithinRadius(pos, manager.getMediumIslandCenter(), 50)) {
            return true;
        }

        // Check if near large island
        if (isWithinRadius(pos, manager.getLargeIslandCenter(), 65)) {
            return true;
        }

        // Check if near extra large island
        if (isWithinRadius(pos, manager.getExtraLargeIslandCenter(), 80)) {
            return true;
        }

        // Check if near central square
        if (isWithinRadius(pos, manager.getCentralSquare(), 15)) {
            return true;
        }

        // Check if near spectator platform
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
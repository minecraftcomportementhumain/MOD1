package com.example.mysubmod.submodes.submode2;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.items.ModItems;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode1.network.SubMode1CandyCountUpdatePacket;
import com.example.mysubmod.submodes.submode2.network.CandyCountUpdatePacket;
import com.example.mysubmod.submodes.submodeParent.CandyManager;
import com.example.mysubmod.submodes.submodeParent.EventHandler;
import com.example.mysubmod.submodes.submodeParent.SubModeParentManager;
import com.example.mysubmod.submodes.submodeParent.islands.IslandType;
import com.example.mysubmod.util.PlayerFilterUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SubMode2EventHandler extends EventHandler {

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if(SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_2){
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }

            // Allow candy usage even when targeting blocks
            ItemStack heldItem = player.getItemInHand(event.getHand());
            if (heldItem.is(ModItems.CANDY_BLUE.get())) {
                return; // Allow blue candy usage
            }
            if (heldItem.is(ModItems.CANDY_RED.get())) {
                return; // Allow red candy usage
            }

            if (SubModeParentManager.getInstance().isPlayerAlive(player.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en sous-mode 2"));
            } else if (SubModeParentManager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onEntityItemPickup(EntityItemPickupEvent event) {
        if(SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_2){
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(player)) {
                event.setCanceled(true);
                return;
            }

            ItemStack stack = event.getItem().getItem();

            // Only allow candy pickup for alive players
            if (SubModeParentManager.getInstance().isPlayerAlive(player.getUUID())) {
                if (stack.is(ModItems.CANDY_BLUE.get()) || stack.is(ModItems.CANDY_RED.get())) {
                    // Increment candy count for player
                    SubModeParentManager.getInstance().incrementCandyCount(player.getUUID(), stack.getCount());

                    // Log candy pickup with resource type
                    if (SubModeParentManager.getInstance().getDataLogger() != null) {
                        BlockPos pos = new BlockPos((int)player.getX(), (int)player.getY(), (int)player.getZ());
                        ResourceType resourceType = SubMode2CandyManager.getResourceTypeFromCandy(stack);
                        if (resourceType != null) {
                            SubMode2Manager.getInstance().getDataLogger().logCandyPickup(player, pos, resourceType);
                        }
                    }

                    // Notify candy manager
                    ItemEntity itemEntity = event.getItem();
                    if (itemEntity != null) {
                        SubMode2CandyManager.getInstance().onCandyPickup(itemEntity);
                    }
                    // Allow candy pickup - do not cancel event
                } else {
                    // Cancel pickup of non-candy items
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cVous ne pouvez ramasser que des bonbons en sous-mode 2"));
                }
            } else {
                // Spectators cannot pick up any items
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas ramasser d'objets en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDropItem(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_2) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer player) {
            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(player)) {
                event.setCanceled(true);
                return;
            }
            if (SubMode2Manager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en tant que spectateur"));
            } else if (SubMode2Manager.getInstance().isPlayerAlive(player.getUUID())) {
                // Check if trying to drop a candy
                ItemStack droppedItem = event.getEntity().getItem();
                if (droppedItem.is(ModItems.CANDY_BLUE.get()) || droppedItem.is(ModItems.CANDY_RED.get())) {
                    // Cancel the drop event
                    event.setCanceled(true);

                    // Return candy to player's inventory
                    if (!player.getInventory().add(droppedItem.copy())) {
                        // If inventory is full, drop it anyway (shouldn't happen in this case)
                        player.drop(droppedItem.copy(), false);
                    }

                    player.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter les bonbons en sous-mode 2"));
                }
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_2) {
            return;
        }

        // Prevent ALL items (except candies from our spawn system) from spawning on islands and paths
        if (event.getEntity() instanceof ItemEntity itemEntity) {
            BlockPos itemPos = itemEntity.blockPosition();

            // Only block items on islands and paths
            if (SubMode2Manager.getInstance().isNearIslandOrPath(itemPos)) {
                // Allow only candies from our spawn system (blue and red)
                boolean isCandy = itemEntity.getItem().is(ModItems.CANDY_BLUE.get()) ||
                                  itemEntity.getItem().is(ModItems.CANDY_RED.get());

                if (!isCandy) {
                    event.setCanceled(true);
                }
            }
        }

        // Block hostile mobs near islands
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster) {
            BlockPos spawnPos = event.getEntity().blockPosition();

            // Check if spawn is near any island
            if (isNearIsland(spawnPos)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if(SubModeManager.getInstance().getCurrentMode() != SubMode.SUB_MODE_2){
            return;
        }

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Disable sprinting for all players in SubMode by setting sprint speed to walk speed
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(event.getServer())) {
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

        // Keep permanent daylight DURING ENTIRE SUBMODE (not just active game)
        net.minecraft.server.level.ServerLevel overworld = event.getServer().getLevel(net.minecraft.server.level.ServerLevel.OVERWORLD);
        if (overworld != null) {
            long currentTime = overworld.getDayTime() % 24000;
            if (currentTime > 12000) { // If it's night (after 12000 ticks)
                overworld.setDayTime(6000); // Reset to noon
            }
        }

        if (!SubModeParentManager.getInstance().isGameActive()) {
            return;
        }

        positionLogTicks++;
        if (positionLogTicks >= POSITION_LOG_INTERVAL) {
            positionLogTicks = 0;

            // Log positions of all alive players and check spectator boundaries
            if (SubModeParentManager.getInstance().getDataLogger() != null) {
                for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(event.getServer())) {
                    if (SubModeParentManager.getInstance().isPlayerAlive(player.getUUID())) {
                        SubModeParentManager.getInstance().getDataLogger().logPlayerPosition(player);
                    }
                }
            }

            // Check spectator boundaries
            for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(event.getServer())) {
                if (SubModeParentManager.getInstance().isPlayerSpectator(player.getUUID())) {
                    checkSpectatorBoundaries(player);
                }
            }
        }

        // Update candy count HUD for all players
        candyCountUpdateTicks++;
        if (candyCountUpdateTicks >= CANDY_COUNT_UPDATE_INTERVAL) {
            candyCountUpdateTicks = 0;

            // Get candy counts from manager

            Map<IslandType, Map<ResourceType, Integer>> candyCounts =
                    SubMode2CandyManager.getResourcesPerIsland(event.getServer());

            // Send to authenticated players only
            CandyCountUpdatePacket packet =
                    new CandyCountUpdatePacket(candyCounts);
            for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(event.getServer())) {
                com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }

        // Note: Dandelion cleanup is done once at start of selection phase in SubModeManager
    }
}
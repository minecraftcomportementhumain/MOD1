package com.example.mysubmod.submodes.submodeParent;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.items.ModItems;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode1.network.SubMode1CandyCountUpdatePacket;
import com.example.mysubmod.submodes.submodeParent.islands.IslandType;
import com.example.mysubmod.submodes.submodeParent.network.GameTimerPacket;
import com.example.mysubmod.util.PlayerFilterUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EventHandler {
    protected static int positionLogTicks = 0;
    protected static final int POSITION_LOG_INTERVAL = 100; // Log every 5 seconds (100 ticks)
    protected static int candyCountUpdateTicks = 0;
    protected static final int CANDY_COUNT_UPDATE_INTERVAL = 40; // Update every 2 seconds (40 ticks)

    @SubscribeEvent
    public static void onPlayerAttack(LivingAttackEvent event) {

        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(attacker)) {
                event.setCanceled(true);
                return;
            }
            if (SubModeParentManager.getInstance().isPlayerAlive(attacker.getUUID())) {
                event.setCanceled(true);
                attacker.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en sous-mode "));
            } else if (SubModeParentManager.getInstance().isPlayerSpectator(attacker.getUUID())) {
                event.setCanceled(true);
                attacker.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {

        if (event.getEntity() instanceof ServerPlayer player) {
            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }

            // Allow candy usage even when targeting blocks
            ItemStack heldItem = player.getItemInHand(event.getHand());
            if (heldItem.is(ModItems.CANDY.get())) {
                return; // Allow candy usage
            }

            if (SubModeParentManager.getInstance().isPlayerAlive(player.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en sous-mode"));
            } else if (SubModeParentManager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {

        if (event.getPlayer() instanceof ServerPlayer player) {
            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(player)) {
                event.setCanceled(true);
                return;
            }

            Block block = event.getState().getBlock();

            // Log ALL block break attempts
            MySubMod.LOGGER.info("BlockBreak attempt by {}: {} at {} - isAdmin: {}, isAlive: {}, isSpectator: {}",
                player.getName().getString(),
                block.getClass().getSimpleName(),
                event.getPos(),
                SubModeManager.getInstance().isAdmin(player),
                SubModeParentManager.getInstance().isPlayerAlive(player.getUUID()),
                SubModeParentManager.getInstance().isPlayerSpectator(player.getUUID()));

            // ALWAYS prevent sign breaking for non-admins (to preserve text) - even during selection phase
            if (block instanceof SignBlock ||
                block instanceof StandingSignBlock ||
                block instanceof WallSignBlock) {
                if (!SubModeManager.getInstance().isAdmin(player)) {
                    event.setCanceled(true);
                    MySubMod.LOGGER.info("BLOCKED sign break for non-admin player: {}", player.getName().getString());
                    return;
                } else {
                    MySubMod.LOGGER.info("ALLOWED sign break for admin player: {}", player.getName().getString());
                    return; // Allow admin to break signs
                }
            }

            if (SubModeParentManager.getInstance().isPlayerAlive(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en sous-mode"));
            } else if (SubModeParentManager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {

        if (event.getEntity() instanceof ServerPlayer player) {
            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(player)) {
                event.setCanceled(true);
                return;
            }
            if (SubModeParentManager.getInstance().isPlayerAlive(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en sous-mode"));
            } else if (SubModeParentManager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerCrafting(PlayerEvent.ItemCraftedEvent event) {

        if (event.getEntity() instanceof ServerPlayer player) {
            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(player)) {
                event.setCanceled(true);
                return;
            }
            if (SubModeParentManager.getInstance().isPlayerAlive(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en sous-mode"));
            } else if (SubModeParentManager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onEntityItemPickup(EntityItemPickupEvent event) {

        if (event.getEntity() instanceof ServerPlayer player) {
            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(player)) {
                event.setCanceled(true);
                return;
            }

            ItemStack stack = event.getItem().getItem();

            // Only allow candy pickup for alive players
            if (SubModeParentManager.getInstance().isPlayerAlive(player.getUUID())) {
                if (stack.is(ModItems.CANDY.get())) {
                    // Increment candy count for player
                    SubModeParentManager.getInstance().incrementCandyCount(player.getUUID(), stack.getCount());

                    // Log candy pickup
                    if (SubModeParentManager.getInstance().getDataLogger() != null) {
                        BlockPos pos = new BlockPos((int)player.getX(), (int)player.getY(), (int)player.getZ());
                        SubModeParentManager.getInstance().getDataLogger().logCandyPickup(player, pos);
                    }

                    // Notify candy manager
                    ItemEntity itemEntity = event.getItem();
                    if (itemEntity != null) {
                        CandyManager.getInstance().onCandyPickup(itemEntity);
                    }
                    // Allow candy pickup - do not cancel event
                } else {
                    // Cancel pickup of non-candy items
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cVous ne pouvez ramasser que des bonbons en sous-mode"));
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
            MySubMod.LOGGER.info("DEBUG: EventHandler.onPlayerJoin called for {}", player.getName().getString());

            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(player)) {
                MySubMod.LOGGER.info("DEBUG: Player {} is restricted, skipping SubMode processing", player.getName().getString());
                return;
            }

            MySubMod.LOGGER.info("DEBUG: Player {} is NOT restricted, proceeding with SubMode processing", player.getName().getString());

            if (SubModeManager.getInstance().getCurrentMode() == SubMode.SUB_MODE_1) {
                SubModeParentManager manager = SubModeParentManager.getInstance();

                // Check if player was disconnected during the game
                if (manager.wasPlayerDisconnected(player.getName().getString())) {
                    // Reconnecting player - restore their state
                    manager.handlePlayerReconnection(player);
                } else {
                    // New players joining - check if we're in file selection phase
                    if (manager.isFileSelectionPhase()) {
                        // During file selection phase, treat new non-admin players as participants
                        if (!SubModeManager.getInstance().isAdmin(player)) {
                            manager.addPlayerToSelectionPhase(player);
                            player.sendSystemMessage(Component.literal("§eVous rejoignez le jeu. En attente de la sélection de fichier par l'admin..."));
                        } else {
                            // Admins go to spectator
                            manager.teleportToSpectator(player);
                        }
                    } else {
                        // After file selection, new players go to spectator
                        manager.teleportToSpectator(player);
                    }
                }
            } else {
                // Player joining when SubMode is NOT active - clear their HUD and timer
                // Send empty candy counts to deactivate candy HUD
                NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new SubMode1CandyCountUpdatePacket(new java.util.HashMap<>())
                );

                // Send -1 timer to deactivate game timer
                com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new GameTimerPacket(-1)
                );

                MySubMod.LOGGER.info("Cleared SubMode HUD for reconnecting player: {}", player.getName().getString());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MySubMod.LOGGER.info("DEBUG: SubMode1EventHandler.onPlayerLogout called for {}",
                player.getName().getString());

            // Skip temporary queue candidate accounts (but don't return - they're already filtered in handlePlayerDisconnection)
            if (SubModeManager.getInstance().getCurrentMode() == SubMode.SUB_MODE_1) {
                SubModeParentManager manager = SubModeParentManager.getInstance();

                boolean isAlive = manager.isPlayerAlive(player.getUUID());
                boolean inSelectionPhase = manager.isInSelectionPhase(player.getUUID());
                MySubMod.LOGGER.info("DEBUG: Player {} isAlive: {}, inSelectionPhase: {}",
                    player.getName().getString(), isAlive, inSelectionPhase);

                // Track disconnection time if player was alive OR in selection phase
                if (isAlive || inSelectionPhase) {
                    manager.handlePlayerDisconnection(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
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
            java.util.Map<IslandType, Integer> candyCounts =
                    CandyManager.getInstance().getAvailableCandiesPerIsland(event.getServer());

            // Send to authenticated players only
            SubMode1CandyCountUpdatePacket packet =
                new SubMode1CandyCountUpdatePacket(candyCounts);
            for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(event.getServer())) {
                com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }

        // Note: Dandelion cleanup is done once at start of selection phase in SubModeManager
    }

    @SubscribeEvent
    public static void onPlayerInteractItem(PlayerInteractEvent.RightClickItem event) {

        if (event.getEntity() instanceof ServerPlayer player) {
            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }
            if (SubModeParentManager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser d'objets en tant que spectateur"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDropItem(net.minecraftforge.event.entity.item.ItemTossEvent event) {

        if (event.getPlayer() instanceof ServerPlayer player) {
            // Skip temporary queue candidate accounts
            if (PlayerFilterUtil.isRestrictedPlayer(player)) {
                event.setCanceled(true);
                return;
            }
            if (SubModeParentManager.getInstance().isPlayerSpectator(player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en tant que spectateur"));
            } else if (SubModeParentManager.getInstance().isPlayerAlive(player.getUUID())) {
                // Check if trying to drop a candy
                ItemStack droppedItem = event.getEntity().getItem();
                if (droppedItem.is(ModItems.CANDY.get())) {
                    // Cancel the drop event
                    event.setCanceled(true);

                    // Return candy to player's inventory
                    if (!player.getInventory().add(droppedItem.copy())) {
                        // If inventory is full, drop it anyway (shouldn't happen in this case)
                        player.drop(droppedItem.copy(), false);
                    }

                    player.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter les bonbons en sous-mode"));
                }
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {

        // Prevent ALL items (except candies from our spawn system) from spawning on islands and paths
        if (event.getEntity() instanceof ItemEntity itemEntity) {
            BlockPos itemPos = itemEntity.blockPosition();

            // Only block items on islands and paths
            if (SubModeParentManager.getInstance().isNearIslandOrPath(itemPos)) {
                // Allow only candies from our spawn system
                boolean isCandy = itemEntity.getItem().is(ModItems.CANDY.get());

                if (!isCandy) {
                    event.setCanceled(true);
                }
            }
        }

        // Block hostile mobs near islands
        if (event.getEntity() instanceof Monster) {
            BlockPos spawnPos = event.getEntity().blockPosition();

            // Check if spawn is near any island
            if (isNearIsland(spawnPos)) {
                event.setCanceled(true);
            }
        }
    }


    protected static boolean isNearIsland(BlockPos pos) {
        SubModeParentManager manager = SubModeParentManager.getInstance();

        // Check if within small island (60x60, half = 30, +5 buffer = 35)
        if (isWithinSquare(pos, manager.getSmallIslandCenter(), 35)) {
            return true;
        }

        // Check if within medium island (90x90, half = 45, +5 buffer = 50)
        if (isWithinSquare(pos, manager.getMediumIslandCenter(), 50)) {
            return true;
        }

        // Check if within large island (120x120, half = 60, +5 buffer = 65)
        if (isWithinSquare(pos, manager.getLargeIslandCenter(), 65)) {
            return true;
        }

        // Check if within extra large island (150x150, half = 75, +5 buffer = 80)
        if (isWithinSquare(pos, manager.getExtraLargeIslandCenter(), 80)) {
            return true;
        }

        // Check if within central square (20x20, half = 10, +5 buffer = 15)
        if (isWithinSquare(pos, manager.getCentralSquare(), 15)) {
            return true;
        }

        // Check if within spectator platform (30x30, half = 15, +5 buffer = 20)
        BlockPos spectatorCenter = new BlockPos(0, 150, 0);
        if (isWithinSquare(pos, spectatorCenter, 20)) {
            return true;
        }

        return false;
    }

    private static boolean isWithinSquare(BlockPos pos, BlockPos center, int halfSize) {
        if (center == null) return false;
        int dx = Math.abs(pos.getX() - center.getX());
        int dz = Math.abs(pos.getZ() - center.getZ());
        return dx <= halfSize && dz <= halfSize;
    }

    public static void checkSpectatorBoundaries(ServerPlayer player) {

        if (!SubModeParentManager.getInstance().isPlayerSpectator(player.getUUID())) {
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
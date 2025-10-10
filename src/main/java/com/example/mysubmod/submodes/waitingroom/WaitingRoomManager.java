package com.example.mysubmod.submodes.waitingroom;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.util.PlayerFilterUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WaitingRoomManager {
    private static WaitingRoomManager instance;
    private final Map<UUID, List<ItemStack>> storedInventories = new ConcurrentHashMap<>();
    private final Set<UUID> waitingRoomPlayers = ConcurrentHashMap.newKeySet();
    private Timer reminderTimer;

    private static final BlockPos PLATFORM_CENTER = new BlockPos(0, 100, 0);
    private static final int PLATFORM_SIZE = 20;
    private static final int PLATFORM_HEIGHT = 3;

    private WaitingRoomManager() {}

    public static WaitingRoomManager getInstance() {
        if (instance == null) {
            instance = new WaitingRoomManager();
        }
        return instance;
    }

    public void activate(MinecraftServer server) {
        MySubMod.LOGGER.info("Activating waiting room mode");

        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            generatePlatform(overworld);
            teleportAllPlayersToWaitingRoom(server);
            startReminderMessages(server);
        }
    }

    public void deactivate(MinecraftServer server) {
        MySubMod.LOGGER.info("Deactivating waiting room mode");

        // Close all open screens for all authenticated players
        try {
            for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
                player.closeContainer();
            }
            MySubMod.LOGGER.info("Closed all open screens for players");
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error closing player screens", e);
        }

        stopReminderMessages();
        restoreAllInventories(server);
        clearPlatform(server.getLevel(ServerLevel.OVERWORLD));
        waitingRoomPlayers.clear();
    }

    private void generatePlatform(ServerLevel level) {
        BlockPos center = PLATFORM_CENTER;

        // Create platform base (stone)
        for (int x = -PLATFORM_SIZE/2; x <= PLATFORM_SIZE/2; x++) {
            for (int z = -PLATFORM_SIZE/2; z <= PLATFORM_SIZE/2; z++) {
                BlockPos pos = center.offset(x, -1, z);
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            }
        }

        // Create platform surface (stone bricks)
        for (int x = -PLATFORM_SIZE/2; x <= PLATFORM_SIZE/2; x++) {
            for (int z = -PLATFORM_SIZE/2; z <= PLATFORM_SIZE/2; z++) {
                BlockPos pos = center.offset(x, 0, z);
                level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }

        // Create barriers around the platform
        for (int x = -PLATFORM_SIZE/2; x <= PLATFORM_SIZE/2; x++) {
            for (int y = 1; y <= PLATFORM_HEIGHT; y++) {
                // North and South walls
                level.setBlock(center.offset(x, y, -PLATFORM_SIZE/2), Blocks.BARRIER.defaultBlockState(), 3);
                level.setBlock(center.offset(x, y, PLATFORM_SIZE/2), Blocks.BARRIER.defaultBlockState(), 3);
            }
        }

        for (int z = -PLATFORM_SIZE/2; z <= PLATFORM_SIZE/2; z++) {
            for (int y = 1; y <= PLATFORM_HEIGHT; y++) {
                // East and West walls
                level.setBlock(center.offset(-PLATFORM_SIZE/2, y, z), Blocks.BARRIER.defaultBlockState(), 3);
                level.setBlock(center.offset(PLATFORM_SIZE/2, y, z), Blocks.BARRIER.defaultBlockState(), 3);
            }
        }

        MySubMod.LOGGER.info("Waiting room platform generated at {}", center);
    }

    private void clearPlatform(ServerLevel level) {
        if (level == null) return;

        BlockPos center = PLATFORM_CENTER;

        // Clear the entire platform area
        for (int x = -PLATFORM_SIZE/2; x <= PLATFORM_SIZE/2; x++) {
            for (int z = -PLATFORM_SIZE/2; z <= PLATFORM_SIZE/2; z++) {
                for (int y = -2; y <= PLATFORM_HEIGHT + 1; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        MySubMod.LOGGER.info("Waiting room platform cleared");
    }

    private void teleportAllPlayersToWaitingRoom(MinecraftServer server) {
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
            teleportPlayerToWaitingRoom(player);
        }
    }

    public void teleportPlayerToWaitingRoom(ServerPlayer player) {
        storePlayerInventory(player);
        clearPlayerInventory(player);

        ServerLevel overworld = player.server.getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            Vec3 spawnPos = new Vec3(PLATFORM_CENTER.getX() + 0.5, PLATFORM_CENTER.getY() + 1, PLATFORM_CENTER.getZ() + 0.5);
            player.teleportTo(overworld, spawnPos.x, spawnPos.y, spawnPos.z, 0.0f, 0.0f);
            waitingRoomPlayers.add(player.getUUID());

            player.sendSystemMessage(Component.literal("§eVous avez été téléporté vers la salle d'attente"));
        }
    }

    private void storePlayerInventory(ServerPlayer player) {
        List<ItemStack> inventory = new ArrayList<>();

        // Store main inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            inventory.add(player.getInventory().getItem(i).copy());
        }

        storedInventories.put(player.getUUID(), inventory);
    }

    private void clearPlayerInventory(ServerPlayer player) {
        player.getInventory().clearContent();
    }

    private void restoreAllInventories(MinecraftServer server) {
        for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
            restorePlayerInventory(player);
        }
    }

    public void restorePlayerInventory(ServerPlayer player) {
        List<ItemStack> inventory = storedInventories.remove(player.getUUID());
        if (inventory != null) {
            for (int i = 0; i < Math.min(inventory.size(), player.getInventory().getContainerSize()); i++) {
                player.getInventory().setItem(i, inventory.get(i));
            }
            player.sendSystemMessage(Component.literal("§aVotre inventaire a été restauré"));
        }
        waitingRoomPlayers.remove(player.getUUID());
    }

    private void startReminderMessages(MinecraftServer server) {
        reminderTimer = new Timer();
        reminderTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Component message = Component.literal("§e[Salle d'attente] Veuillez attendre qu'un administrateur lance un jeu");
                for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
                    player.sendSystemMessage(message);
                }
            }
        }, 60000, 60000); // 1 minute initial delay, then every minute
    }

    private void stopReminderMessages() {
        if (reminderTimer != null) {
            reminderTimer.cancel();
            reminderTimer = null;
        }
    }

    public boolean isPlayerInWaitingRoom(ServerPlayer player) {
        return waitingRoomPlayers.contains(player.getUUID());
    }

    public BlockPos getPlatformCenter() {
        return PLATFORM_CENTER;
    }

    public int getPlatformSize() {
        return PLATFORM_SIZE;
    }
}
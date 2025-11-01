package com.example.mysubmod.submodes.submodeParent.islands;

import com.example.mysubmod.MySubMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

public class IslandGenerator {

    public void generateIsland(ServerLevel level, BlockPos center, IslandType type) {
        MySubMod.LOGGER.info("Generating square {} at {}", type.getDisplayName(), center);

        int size = type.getRadius(); // Now represents half-size of square
        Random random = new Random();

        // Generate square island base (dirt/grass)
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                // Add some randomness to the edges for natural look
                boolean isEdge = (Math.abs(x) == size || Math.abs(z) == size);
                if (isEdge && random.nextFloat() > 0.8f) {
                    continue; // Skip some edge blocks randomly
                }

                BlockPos pos = center.offset(x, -1, z);
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);

                // Add grass on top
                pos = center.offset(x, 0, z);
                level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);

                // Add some height variation (less near edges)
                int distanceFromEdge = Math.min(Math.min(size - Math.abs(x), size - Math.abs(z)), size);
                if (random.nextFloat() > 0.8f && distanceFromEdge >= 3) {
                    pos = center.offset(x, 1, z);
                    level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);

                    if (random.nextFloat() > 0.9f && distanceFromEdge >= 5) {
                        pos = center.offset(x, 2, z);
                        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    }
                }
            }
        }

        // Add some decorative elements
        addDecorations(level, center, size, random);

        // Clear area above island to ensure no obstructions
        clearAboveIsland(level, center, size);

        MySubMod.LOGGER.info("Square island generation completed for {}", type.getDisplayName());
    }

    private void addDecorations(ServerLevel level, BlockPos center, int size, Random random) {
        // Add some trees (avoid edges)
        int treeCount = size / 5;
        for (int i = 0; i < treeCount; i++) {
            int x = random.nextInt((size - 3) * 2) - (size - 3); // Keep away from edges
            int z = random.nextInt((size - 3) * 2) - (size - 3);

            BlockPos treePos = center.offset(x, 1, z);
            generateSimpleTree(level, treePos, random);
        }

        // Add some flowers and tall grass (within square bounds)
        for (int x = -size + 2; x <= size - 2; x++) { // Stay inside edges
            for (int z = -size + 2; z <= size - 2; z++) {
                if (random.nextFloat() > 0.95f) {
                    BlockPos decorPos = center.offset(x, 1, z);
                    if (level.getBlockState(decorPos).isAir()) {
                        if (random.nextBoolean()) {
                            level.setBlock(decorPos, Blocks.DANDELION.defaultBlockState(), 3);
                        } else {
                            level.setBlock(decorPos, Blocks.TALL_GRASS.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    private void generateSimpleTree(ServerLevel level, BlockPos base, Random random) {
        int height = 3 + random.nextInt(3);

        // Generate trunk
        for (int y = 0; y < height; y++) {
            level.setBlock(base.offset(0, y, 0), Blocks.OAK_LOG.defaultBlockState(), 3);
        }

        // Generate leaves
        BlockPos leafBase = base.offset(0, height - 1, 0);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 2; y++) {
                    if (Math.abs(x) + Math.abs(z) + y <= 3 && random.nextFloat() > 0.2f) {
                        BlockPos leafPos = leafBase.offset(x, y, z);
                        if (level.getBlockState(leafPos).isAir()) {
                            level.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    private void clearAboveIsland(ServerLevel level, BlockPos center, int size) {
        for (int x = -size - 5; x <= size + 5; x++) {
            for (int z = -size - 5; z <= size + 5; z++) {
                for (int y = 1; y <= 20; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !state.is(Blocks.OAK_LOG) && !state.is(Blocks.OAK_LEAVES) &&
                        !state.is(Blocks.DANDELION) && !state.is(Blocks.TALL_GRASS)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    public static void clearIsland(ServerLevel level, BlockPos center, IslandType type) {
        if (level == null) return;

        int size = type.getRadius() + 10; // Clear a bit more to be sure

        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                for (int y = -5; y <= 25; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        MySubMod.LOGGER.info("Cleared square island area for {}", type.getDisplayName());
    }
}
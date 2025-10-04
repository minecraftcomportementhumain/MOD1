package com.example.mysubmod;

import com.example.mysubmod.items.ModItems;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.SubModeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("mysubmod")
public class MySubMod {
    public static final String MOD_ID = "mysubmod";
    public static final Logger LOGGER = LogManager.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public MySubMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.init();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Starting MySubMod server-side functionality");

        // Force deactivate all sub-modes first to clean up any leftover state
        SubModeManager.getInstance().forceDeactivateAllSubModes(event.getServer());

        // Then start the waiting room
        SubModeManager.getInstance().startWaitingRoom();

        // Activate waiting room and clean up holograms after server is fully loaded
        event.getServer().execute(() -> {
            net.minecraft.server.level.ServerLevel overworld = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (overworld != null) {
                // Clean up BEFORE activating waiting room
                cleanupOrphanedHolograms(overworld);
            }
            com.example.mysubmod.submodes.waitingroom.WaitingRoomManager.getInstance().activate(event.getServer());
        });
    }

    private void cleanupOrphanedHolograms(net.minecraft.server.level.ServerLevel level) {
        // The central square is at (0, 100, 0) where SubMode1 holograms spawn
        net.minecraft.core.BlockPos centralSquare = new net.minecraft.core.BlockPos(0, 100, 0);
        net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(centralSquare);

        LOGGER.info("Force loading chunk at {} for hologram cleanup...", chunkPos);

        // Force load the chunk with a loading ticket to ensure entities are loaded
        level.setChunkForced(chunkPos.x, chunkPos.z, true);

        // Wait a bit for entities to actually load
        try {
            Thread.sleep(500); // 500ms delay to let entities load
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while waiting for chunk to load", e);
        }

        int removed = 0;
        LOGGER.info("Scanning for orphaned holograms at central square (0, 100, 0)...");

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

        LOGGER.info("Found {} armor stands in search area", armorStands.size());

        // Remove all found holograms
        for (net.minecraft.world.entity.decoration.ArmorStand armorStand : armorStands) {
            LOGGER.info("Found orphaned hologram at {} with name: {}",
                armorStand.blockPosition(),
                armorStand.getCustomName() != null ? armorStand.getCustomName().getString() : "no name");
            armorStand.discard();
            removed++;
        }

        // Unforce the chunk
        level.setChunkForced(chunkPos.x, chunkPos.z, false);

        if (removed > 0) {
            LOGGER.info("Cleaned up {} orphaned hologram entities from previous session", removed);
        } else {
            LOGGER.info("No orphaned holograms found at central square");
        }
    }
}
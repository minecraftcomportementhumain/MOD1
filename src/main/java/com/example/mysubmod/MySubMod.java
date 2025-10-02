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

        // Activate waiting room after a short delay to ensure server is fully loaded
        event.getServer().execute(() -> {
            com.example.mysubmod.submodes.waitingroom.WaitingRoomManager.getInstance().activate(event.getServer());
        });
    }
}
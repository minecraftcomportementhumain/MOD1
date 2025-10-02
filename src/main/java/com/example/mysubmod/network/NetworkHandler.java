package com.example.mysubmod.network;

import com.example.mysubmod.MySubMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(MySubMod.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void init() {
        INSTANCE.registerMessage(
            packetId++,
            SubModeChangePacket.class,
            SubModeChangePacket::encode,
            SubModeChangePacket::decode,
            SubModeChangePacket::handle
        );

        INSTANCE.registerMessage(
            packetId++,
            SubModeRequestPacket.class,
            SubModeRequestPacket::encode,
            SubModeRequestPacket::decode,
            SubModeRequestPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            packetId++,
            AdminStatusPacket.class,
            AdminStatusPacket::encode,
            AdminStatusPacket::decode,
            AdminStatusPacket::handle
        );

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.submodes.submode1.network.IslandSelectionPacket.class,
            com.example.mysubmod.submodes.submode1.network.IslandSelectionPacket::encode,
            com.example.mysubmod.submodes.submode1.network.IslandSelectionPacket::decode,
            com.example.mysubmod.submodes.submode1.network.IslandSelectionPacket::handle
        );

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.submodes.submode1.network.IslandChoicePacket.class,
            com.example.mysubmod.submodes.submode1.network.IslandChoicePacket::encode,
            com.example.mysubmod.submodes.submode1.network.IslandChoicePacket::decode,
            com.example.mysubmod.submodes.submode1.network.IslandChoicePacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.submodes.submode1.network.GameTimerPacket.class,
            com.example.mysubmod.submodes.submode1.network.GameTimerPacket::encode,
            com.example.mysubmod.submodes.submode1.network.GameTimerPacket::decode,
            com.example.mysubmod.submodes.submode1.network.GameTimerPacket::handle
        );

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.submodes.submode1.network.CandyFileListPacket.class,
            com.example.mysubmod.submodes.submode1.network.CandyFileListPacket::encode,
            com.example.mysubmod.submodes.submode1.network.CandyFileListPacket::decode,
            com.example.mysubmod.submodes.submode1.network.CandyFileListPacket::handle
        );

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.submodes.submode1.network.CandyFileSelectionPacket.class,
            com.example.mysubmod.submodes.submode1.network.CandyFileSelectionPacket::encode,
            com.example.mysubmod.submodes.submode1.network.CandyFileSelectionPacket::decode,
            com.example.mysubmod.submodes.submode1.network.CandyFileSelectionPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.submodes.submode1.network.CandyFileUploadPacket.class,
            com.example.mysubmod.submodes.submode1.network.CandyFileUploadPacket::encode,
            com.example.mysubmod.submodes.submode1.network.CandyFileUploadPacket::decode,
            com.example.mysubmod.submodes.submode1.network.CandyFileUploadPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.submodes.submode1.network.CandyFileDeletePacket.class,
            com.example.mysubmod.submodes.submode1.network.CandyFileDeletePacket::encode,
            com.example.mysubmod.submodes.submode1.network.CandyFileDeletePacket::decode,
            com.example.mysubmod.submodes.submode1.network.CandyFileDeletePacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.submodes.submode1.network.CandyCountUpdatePacket.class,
            com.example.mysubmod.submodes.submode1.network.CandyCountUpdatePacket::encode,
            com.example.mysubmod.submodes.submode1.network.CandyCountUpdatePacket::new,
            com.example.mysubmod.submodes.submode1.network.CandyCountUpdatePacket::handle
        );
    }
}
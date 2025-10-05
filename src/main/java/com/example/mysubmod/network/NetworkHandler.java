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
            SubModeControlScreenRequestPacket.class,
            SubModeControlScreenRequestPacket::encode,
            SubModeControlScreenRequestPacket::decode,
            SubModeControlScreenRequestPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            packetId++,
            SubModeControlScreenPacket.class,
            SubModeControlScreenPacket::encode,
            SubModeControlScreenPacket::decode,
            SubModeControlScreenPacket::handle
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

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.submodes.submode1.network.CandyFileListRequestPacket.class,
            com.example.mysubmod.submodes.submode1.network.CandyFileListRequestPacket::toBytes,
            com.example.mysubmod.submodes.submode1.network.CandyFileListRequestPacket::new,
            com.example.mysubmod.submodes.submode1.network.CandyFileListRequestPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // Log management packets
        INSTANCE.registerMessage(
            packetId++,
            LogListRequestPacket.class,
            LogListRequestPacket::toBytes,
            LogListRequestPacket::new,
            LogListRequestPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            packetId++,
            LogListPacket.class,
            LogListPacket::toBytes,
            LogListPacket::new,
            LogListPacket::handle
        );

        INSTANCE.registerMessage(
            packetId++,
            LogDownloadPacket.class,
            LogDownloadPacket::toBytes,
            LogDownloadPacket::new,
            LogDownloadPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            packetId++,
            LogDeletePacket.class,
            LogDeletePacket::toBytes,
            LogDeletePacket::new,
            LogDeletePacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            packetId++,
            LogDataPacket.class,
            LogDataPacket::toBytes,
            LogDataPacket::new,
            LogDataPacket::handle
        );

        // Admin authentication packets
        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.auth.AdminAuthRequestPacket.class,
            com.example.mysubmod.auth.AdminAuthRequestPacket::encode,
            com.example.mysubmod.auth.AdminAuthRequestPacket::decode,
            com.example.mysubmod.auth.AdminAuthRequestPacket::handle
        );

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.auth.AdminAuthPacket.class,
            com.example.mysubmod.auth.AdminAuthPacket::encode,
            com.example.mysubmod.auth.AdminAuthPacket::decode,
            com.example.mysubmod.auth.AdminAuthPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.auth.AdminAuthResponsePacket.class,
            com.example.mysubmod.auth.AdminAuthResponsePacket::encode,
            com.example.mysubmod.auth.AdminAuthResponsePacket::decode,
            com.example.mysubmod.auth.AdminAuthResponsePacket::handle
        );

        INSTANCE.registerMessage(
            packetId++,
            com.example.mysubmod.submodes.submode1.network.GameEndPacket.class,
            com.example.mysubmod.submodes.submode1.network.GameEndPacket::encode,
            com.example.mysubmod.submodes.submode1.network.GameEndPacket::decode,
            com.example.mysubmod.submodes.submode1.network.GameEndPacket::handle
        );
    }

    /**
     * Helper method to send packet to a specific player
     */
    public static void sendToPlayer(Object packet, net.minecraft.server.level.ServerPlayer player) {
        INSTANCE.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
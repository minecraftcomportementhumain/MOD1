package com.example.mysubmod.network;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submode1.network.SubMode1CandyCountUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
                com.example.mysubmod.submodes.submodeParent.network.IslandSelectionPacket.class,
                com.example.mysubmod.submodes.submodeParent.network.IslandSelectionPacket::encode,
                com.example.mysubmod.submodes.submodeParent.network.IslandSelectionPacket::decode,
                com.example.mysubmod.submodes.submodeParent.network.IslandSelectionPacket::handle
        );

        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.submodes.submodeParent.network.IslandChoicePacket.class,
                com.example.mysubmod.submodes.submodeParent.network.IslandChoicePacket::encode,
                com.example.mysubmod.submodes.submodeParent.network.IslandChoicePacket::decode,
                com.example.mysubmod.submodes.submodeParent.network.IslandChoicePacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.submodes.submodeParent.network.GameTimerPacket.class,
                com.example.mysubmod.submodes.submodeParent.network.GameTimerPacket::encode,
                com.example.mysubmod.submodes.submodeParent.network.GameTimerPacket::decode,
                com.example.mysubmod.submodes.submodeParent.network.GameTimerPacket::handle
        );

        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.submodes.submodeParent.network.FileListPacket.class,
                com.example.mysubmod.submodes.submodeParent.network.FileListPacket::encode,
                com.example.mysubmod.submodes.submodeParent.network.FileListPacket::decode,
                com.example.mysubmod.submodes.submodeParent.network.FileListPacket::handle
        );

        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.submodes.submodeParent.network.FileSelectionPacket.class,
                com.example.mysubmod.submodes.submodeParent.network.FileSelectionPacket::encode,
                com.example.mysubmod.submodes.submodeParent.network.FileSelectionPacket::decode,
                com.example.mysubmod.submodes.submodeParent.network.FileSelectionPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.submodes.submodeParent.network.FileUploadPacket.class,
                com.example.mysubmod.submodes.submode1.network.CandyFileUploadPacket::encode,
                com.example.mysubmod.submodes.submode1.network.CandyFileUploadPacket::decode,
                com.example.mysubmod.submodes.submode1.network.CandyFileUploadPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.submodes.submodeParent.network.FileDeletePacket.class,
                com.example.mysubmod.submodes.submodeParent.network.FileDeletePacket::encode,
                com.example.mysubmod.submodes.submodeParent.network.FileDeletePacket::decode,
                com.example.mysubmod.submodes.submodeParent.network.FileDeletePacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
                packetId++,
                SubMode1CandyCountUpdatePacket.class,
                SubMode1CandyCountUpdatePacket::encode,
                SubMode1CandyCountUpdatePacket::new,
                SubMode1CandyCountUpdatePacket::handle
        );

        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.submodes.submodeParent.network.FileListRequestPacket.class,
                com.example.mysubmod.submodes.submodeParent.network.FileListRequestPacket::toBytes,
                com.example.mysubmod.submodes.submodeParent.network.FileListRequestPacket::new,
                com.example.mysubmod.submodes.submodeParent.network.FileListRequestPacket::handle,
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
                com.example.mysubmod.submodes.submodeParent.network.GameEndPacket.class,
                com.example.mysubmod.submodes.submodeParent.network.GameEndPacket::encode,
                com.example.mysubmod.submodes.submodeParent.network.GameEndPacket::decode,
                com.example.mysubmod.submodes.submodeParent.network.GameEndPacket::handle
        );

        // SubMode2 packets
//        INSTANCE.registerMessage(
//            packetId++,
//            com.example.mysubmod.submodes.submode2.network.IslandSelectionPacket.class,
//            com.example.mysubmod.submodes.submode2.network.IslandSelectionPacket::encode,
//            com.example.mysubmod.submodes.submode2.network.IslandSelectionPacket::decode,
//            com.example.mysubmod.submodes.submode2.network.IslandSelectionPacket::handle
//        );


//        INSTANCE.registerMessage(
//            packetId++,
//            com.example.mysubmod.submodes.submode2.network.GameTimerPacket.class,
//            com.example.mysubmod.submodes.submode2.network.GameTimerPacket::encode,
//            com.example.mysubmod.submodes.submode2.network.GameTimerPacket::decode,
//            com.example.mysubmod.submodes.submode2.network.GameTimerPacket::handle
//        );

//        INSTANCE.registerMessage(
//            packetId++,
//            com.example.mysubmod.submodes.submode2.network.CandyFileListPacket.class,
//            com.example.mysubmod.submodes.submode2.network.CandyFileListPacket::encode,
//            com.example.mysubmod.submodes.submode2.network.CandyFileListPacket::decode,
//            com.example.mysubmod.submodes.submode2.network.CandyFileListPacket::handle
//        );

//        INSTANCE.registerMessage(
//            packetId++,
//            com.example.mysubmod.submodes.submode2.network.CandyFileSelectionPacket.class,
//            com.example.mysubmod.submodes.submode2.network.CandyFileSelectionPacket::encode,
//            com.example.mysubmod.submodes.submode2.network.CandyFileSelectionPacket::decode,
//            com.example.mysubmod.submodes.submode2.network.CandyFileSelectionPacket::handle,
//            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
//        );

        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.submodes.submodeParent.network.FileUploadPacket.class,
                com.example.mysubmod.submodes.submode2.network.CandyFileUploadPacket::encode,
                com.example.mysubmod.submodes.submode2.network.CandyFileUploadPacket::decode,
                com.example.mysubmod.submodes.submode2.network.CandyFileUploadPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );


        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.submodes.submode2.network.CandyCountUpdatePacket.class,
                com.example.mysubmod.submodes.submode2.network.CandyCountUpdatePacket::encode,
                com.example.mysubmod.submodes.submode2.network.CandyCountUpdatePacket::new,
                com.example.mysubmod.submodes.submode2.network.CandyCountUpdatePacket::handle
        );

//        INSTANCE.registerMessage(
//            packetId++,
//            com.example.mysubmod.submodes.submode2.network.CandyFileListRequestPacket.class,
//            com.example.mysubmod.submodes.submode2.network.CandyFileListRequestPacket::toBytes,
//            com.example.mysubmod.submodes.submode2.network.CandyFileListRequestPacket::new,
//            com.example.mysubmod.submodes.submode2.network.CandyFileListRequestPacket::handle,
//            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
//        );

//        INSTANCE.registerMessage(
//            packetId++,
//            com.example.mysubmod.submodes.submode2.network.GameEndPacket.class,
//            com.example.mysubmod.submodes.submode2.network.GameEndPacket::encode,
//            com.example.mysubmod.submodes.submode2.network.GameEndPacket::decode,
//            com.example.mysubmod.submodes.submode2.network.GameEndPacket::handle
//        );

        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.submodes.submode2.network.PenaltySyncPacket.class,
                com.example.mysubmod.submodes.submode2.network.PenaltySyncPacket::encode,
                com.example.mysubmod.submodes.submode2.network.PenaltySyncPacket::decode,
                com.example.mysubmod.submodes.submode2.network.PenaltySyncPacket::handle
        );

        // SubMode2 log management packet
//        INSTANCE.registerMessage(
//            packetId++,
//            SubMode2LogListRequestPacket.class,
//            SubMode2LogListRequestPacket::toBytes,
//            SubMode2LogListRequestPacket::new,
//            SubMode2LogListRequestPacket::handle,
//            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
//        );

        // Queue token packets
        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.auth.QueueTokenPacket.class,
                com.example.mysubmod.auth.QueueTokenPacket::encode,
                com.example.mysubmod.auth.QueueTokenPacket::decode,
                com.example.mysubmod.auth.QueueTokenPacket::handle
        );

        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.auth.QueueTokenRequestPacket.class,
                com.example.mysubmod.auth.QueueTokenRequestPacket::encode,
                com.example.mysubmod.auth.QueueTokenRequestPacket::decode,
                com.example.mysubmod.auth.QueueTokenRequestPacket::handle
        );

        INSTANCE.registerMessage(
                packetId++,
                com.example.mysubmod.auth.QueueTokenVerifyPacket.class,
                com.example.mysubmod.auth.QueueTokenVerifyPacket::encode,
                com.example.mysubmod.auth.QueueTokenVerifyPacket::decode,
                com.example.mysubmod.auth.QueueTokenVerifyPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    /**
     * Helper method to send packet to a specific player
     */
    public static void sendToPlayer(Object packet, ServerPlayer player) {
        INSTANCE.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

}
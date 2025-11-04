package com.example.mysubmod.submodes.submodeParent.network;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submodeParent.data.SpawnFileManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FileDeletePacket {
    private final String filename;

    public FileDeletePacket(String filename) {
        this.filename = filename;
    }

    public static void encode(FileDeletePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.filename);
    }

    public static FileDeletePacket decode(FriendlyByteBuf buf) {
        return new FileDeletePacket(buf.readUtf());
    }

    public static void handle(FileDeletePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && SubModeManager.getInstance().isAdmin(player)) {
                boolean success = SpawnFileManager.getInstance().deleteFile(packet.filename);
                if (success) {
                    MySubMod.LOGGER.info("Admin {} deleted candy spawn file: {}",
                        player.getName().getString(), packet.filename);
                } else {
                    MySubMod.LOGGER.warn("Failed to delete candy spawn file: {} by {}",
                        packet.filename, player.getName().getString());
                }
            } else {
                MySubMod.LOGGER.warn("Non-admin player {} attempted to delete candy spawn file",
                    player != null ? player.getName().getString() : "unknown");
            }
        });
        context.setPacketHandled(true);
    }
}
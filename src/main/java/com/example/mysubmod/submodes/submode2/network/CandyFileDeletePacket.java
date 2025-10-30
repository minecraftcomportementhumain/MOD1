package com.example.mysubmod.submodes.submode2.network;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode2.data.CandySpawnFileManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CandyFileDeletePacket {
    private final String filename;

    public CandyFileDeletePacket(String filename) {
        this.filename = filename;
    }

    public static void encode(CandyFileDeletePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.filename);
    }

    public static CandyFileDeletePacket decode(FriendlyByteBuf buf) {
        return new CandyFileDeletePacket(buf.readUtf());
    }

    public static void handle(CandyFileDeletePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && SubModeManager.getInstance().isAdmin(player)) {
                boolean success = CandySpawnFileManager.getInstance().deleteFile(packet.filename);
                if (success) {
                    MySubMod.LOGGER.info("Admin {} deleted SubMode2 candy spawn file: {}",
                        player.getName().getString(), packet.filename);
                } else {
                    MySubMod.LOGGER.warn("Failed to delete SubMode2 candy spawn file: {} by {}",
                        packet.filename, player.getName().getString());
                }
            } else {
                MySubMod.LOGGER.warn("Non-admin player {} attempted to delete SubMode2 candy spawn file",
                    player != null ? player.getName().getString() : "unknown");
            }
        });
        context.setPacketHandled(true);
    }
}

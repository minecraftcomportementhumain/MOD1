package com.example.mysubmod.submodes.submode1.network;

import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode1.SubMode1Manager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CandyFileSelectionPacket {
    private final String selectedFile;

    public CandyFileSelectionPacket(String selectedFile) {
        this.selectedFile = selectedFile;
    }

    public static void encode(CandyFileSelectionPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.selectedFile);
    }

    public static CandyFileSelectionPacket decode(FriendlyByteBuf buf) {
        return new CandyFileSelectionPacket(buf.readUtf());
    }

    public static void handle(CandyFileSelectionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // Verify admin permissions on server side
                if (SubModeManager.getInstance().isPlayerAdmin(player)) {
                    SubMode1Manager.getInstance().setCandySpawnFile(packet.selectedFile);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String getSelectedFile() {
        return selectedFile;
    }
}
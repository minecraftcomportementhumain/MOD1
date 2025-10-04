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
                if (SubModeManager.getInstance().isAdmin(player)) {
                    // Check if a game is already active or selection phase is active
                    if (SubMode1Manager.getInstance().isGameActive() || SubMode1Manager.getInstance().isSelectionPhase()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cImpossible de sélectionner un fichier - Une partie est déjà en cours!"));
                        return;
                    }

                    SubMode1Manager.getInstance().setCandySpawnFile(packet.selectedFile);
                    // Start island selection after file is set
                    SubMode1Manager.getInstance().startIslandSelection(player.getServer());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String getSelectedFile() {
        return selectedFile;
    }
}
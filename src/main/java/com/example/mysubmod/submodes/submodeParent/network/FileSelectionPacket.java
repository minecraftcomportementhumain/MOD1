package com.example.mysubmod.submodes.submodeParent.network;

import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submodeParent.SubModeParentManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FileSelectionPacket {
    private final String selectedFile;

    public FileSelectionPacket(String selectedFile) {
        this.selectedFile = selectedFile;
    }

    public static void encode(FileSelectionPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.selectedFile);
    }

    public static FileSelectionPacket decode(FriendlyByteBuf buf) {
        return new FileSelectionPacket(buf.readUtf());
    }

    public static void handle(FileSelectionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // Verify admin permissions on server side
                if (SubModeManager.getInstance().isAdmin(player)) {
                    // Check if a game is already active or selection phase is active
                    if (SubModeParentManager.getInstance().isGameActive() || SubModeParentManager.getInstance().isSelectionPhase()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cImpossible de sélectionner un fichier - Une partie est déjà en cours!"));
                        return;
                    }

                    SubModeParentManager.getInstance().setCandySpawnFile(packet.selectedFile);
                    // Start island selection after file is set
                    SubModeParentManager.getInstance().startIslandSelection(player.getServer());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String getSelectedFile() {
        return selectedFile;
    }
}
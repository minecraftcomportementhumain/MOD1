package com.example.mysubmod.submodes.submodeParent.network;

import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submodeParent.SubModeParentManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FileListRequestPacket {
    public FileListRequestPacket() {
    }

    public FileListRequestPacket(FriendlyByteBuf buf) {
        // No data to read
    }

    public void toBytes(FriendlyByteBuf buf) {
        // No data to write
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && SubModeManager.getInstance().isAdmin(player)) {
                // Check if a game is already active or selection phase is active
                if (SubModeParentManager.getInstance().isGameActive() || SubModeParentManager.getInstance().isSelectionPhase()) {
                    player.sendSystemMessage(Component.literal(
                        "§cImpossible de rafraîchir - Une partie est déjà en cours!"));
                    return;
                }
                SubModeParentManager.getInstance().sendCandyFileListToPlayer(player);
            }
        });
        return true;
    }
}

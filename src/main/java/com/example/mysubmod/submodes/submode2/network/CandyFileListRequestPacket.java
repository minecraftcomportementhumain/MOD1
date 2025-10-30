package com.example.mysubmod.submodes.submode2.network;

import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode2.SubMode2Manager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CandyFileListRequestPacket {
    public CandyFileListRequestPacket() {
    }

    public CandyFileListRequestPacket(FriendlyByteBuf buf) {
        // No data to read
    }

    public void toBytes(FriendlyByteBuf buf) {
        // No data to write
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = context.getSender();
            if (player != null && SubModeManager.getInstance().isAdmin(player)) {
                // Check if a game is already active or selection phase is active
                if (SubMode2Manager.getInstance().isGameActive() || SubMode2Manager.getInstance().isSelectionPhase()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§cImpossible de rafraîchir - Une partie est déjà en cours!"));
                    return;
                }
                SubMode2Manager.getInstance().sendCandyFileListToPlayer(player);
            }
        });
        return true;
    }
}

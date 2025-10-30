package com.example.mysubmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SubMode2LogListRequestPacket {
    public SubMode2LogListRequestPacket() {
    }

    public SubMode2LogListRequestPacket(FriendlyByteBuf buf) {
        // No data to read
    }

    public void toBytes(FriendlyByteBuf buf) {
        // No data to write
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = context.getSender();
            if (player != null && com.example.mysubmod.submodes.SubModeManager.getInstance().isAdmin(player)) {
                // Send SubMode2 logs (mode number = 2)
                com.example.mysubmod.server.LogManager.sendLogList(player, 2);
            }
        });
        return true;
    }
}

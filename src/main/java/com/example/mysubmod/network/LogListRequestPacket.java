package com.example.mysubmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LogListRequestPacket {
    public LogListRequestPacket() {
    }

    public LogListRequestPacket(FriendlyByteBuf buf) {
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
                com.example.mysubmod.server.LogManager.sendLogList(player);
            }
        });
        return true;
    }
}

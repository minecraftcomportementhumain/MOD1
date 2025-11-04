package com.example.mysubmod.network;

import com.example.mysubmod.server.LogManager;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
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
            ServerPlayer player = context.getSender();
            if (player != null && SubModeManager.getInstance().isAdmin(player)) {
                LogManager.sendLogList(player);
            }
        });
        return true;
    }
}

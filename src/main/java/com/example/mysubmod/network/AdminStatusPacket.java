package com.example.mysubmod.network;

import com.example.mysubmod.client.ClientSubModeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AdminStatusPacket {
    private final boolean isAdmin;

    public AdminStatusPacket(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public static void encode(AdminStatusPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.isAdmin);
    }

    public static AdminStatusPacket decode(FriendlyByteBuf buf) {
        return new AdminStatusPacket(buf.readBoolean());
    }

    public static void handle(AdminStatusPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientSubModeManager.setIsAdmin(packet.isAdmin);
        });
        ctx.get().setPacketHandled(true);
    }
}
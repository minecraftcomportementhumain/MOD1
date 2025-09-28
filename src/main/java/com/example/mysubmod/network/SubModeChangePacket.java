package com.example.mysubmod.network;

import com.example.mysubmod.client.ClientSubModeManager;
import com.example.mysubmod.submodes.SubMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SubModeChangePacket {
    private final SubMode newMode;

    public SubModeChangePacket(SubMode newMode) {
        this.newMode = newMode;
    }

    public static void encode(SubModeChangePacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.newMode);
    }

    public static SubModeChangePacket decode(FriendlyByteBuf buf) {
        return new SubModeChangePacket(buf.readEnum(SubMode.class));
    }

    public static void handle(SubModeChangePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientSubModeManager.setCurrentMode(packet.newMode);
        });
        ctx.get().setPacketHandled(true);
    }
}
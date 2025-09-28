package com.example.mysubmod.network;

import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SubModeRequestPacket {
    private final SubMode requestedMode;

    public SubModeRequestPacket(SubMode requestedMode) {
        this.requestedMode = requestedMode;
    }

    public static void encode(SubModeRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.requestedMode);
    }

    public static SubModeRequestPacket decode(FriendlyByteBuf buf) {
        return new SubModeRequestPacket(buf.readEnum(SubMode.class));
    }

    public static void handle(SubModeRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                SubModeManager.getInstance().changeSubMode(packet.requestedMode, player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
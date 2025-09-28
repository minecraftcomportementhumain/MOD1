package com.example.mysubmod.submodes.submode1.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class IslandSelectionPacket {

    public IslandSelectionPacket() {}

    public static void encode(IslandSelectionPacket packet, FriendlyByteBuf buf) {
        // No data to encode
    }

    public static IslandSelectionPacket decode(FriendlyByteBuf buf) {
        return new IslandSelectionPacket();
    }

    public static void handle(IslandSelectionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openIslandSelectionScreen());
        });
        ctx.get().setPacketHandled(true);
    }
}
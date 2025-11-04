package com.example.mysubmod.submodes.submodeParent.network;

import com.example.mysubmod.submodes.submodeParent.network.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class IslandSelectionPacket {
    private final int timeLeftSeconds;

    public IslandSelectionPacket(int timeLeftSeconds) {
        this.timeLeftSeconds = timeLeftSeconds;
    }

    public static void encode(IslandSelectionPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.timeLeftSeconds);
    }

    public static IslandSelectionPacket decode(FriendlyByteBuf buf) {
        return new IslandSelectionPacket(buf.readInt());
    }

    public static void handle(IslandSelectionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openIslandSelectionScreen(packet.timeLeftSeconds));
        });
        ctx.get().setPacketHandled(true);
    }
}
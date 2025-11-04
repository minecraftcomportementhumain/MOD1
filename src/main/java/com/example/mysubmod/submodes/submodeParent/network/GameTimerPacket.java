package com.example.mysubmod.submodes.submodeParent.network;

import com.example.mysubmod.submodes.submodeParent.network.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class GameTimerPacket {
    private final int secondsLeft;

    public GameTimerPacket(int secondsLeft) {
        this.secondsLeft = secondsLeft;
    }

    public static void encode(GameTimerPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.secondsLeft);
    }

    public static GameTimerPacket decode(FriendlyByteBuf buf) {
        return new GameTimerPacket(buf.readInt());
    }

    public static void handle(GameTimerPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.updateGameTimer(packet.secondsLeft));
        });
        ctx.get().setPacketHandled(true);
    }
}
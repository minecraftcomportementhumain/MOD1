package com.example.mysubmod.submodes.submode2.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from server to client to notify game has ended
 */
public class GameEndPacket {

    public GameEndPacket() {
    }

    public static void encode(GameEndPacket packet, FriendlyByteBuf buf) {
        // No data to encode
    }

    public static GameEndPacket decode(FriendlyByteBuf buf) {
        return new GameEndPacket();
    }

    public static void handle(GameEndPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Only execute on client side
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleGameEnd());
        });
        ctx.get().setPacketHandled(true);
    }

    // Client-only handler class
    public static class ClientPacketHandler {
        public static void handleGameEnd() {
            com.example.mysubmod.submodes.submode2.client.ClientGameTimer.markGameAsEnded();
            // Also deactivate the timer to stop displaying it
            com.example.mysubmod.submodes.submode2.client.ClientGameTimer.deactivate();
            // Deactivate penalty timer HUD
            com.example.mysubmod.submodes.submode2.client.PenaltyTimerHUD.deactivate();
        }
    }
}

package com.example.mysubmod.auth;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from server to client to request authentication (open password screen)
 */
public class AdminAuthRequestPacket {
    private final int remainingAttempts;

    public AdminAuthRequestPacket(int remainingAttempts) {
        this.remainingAttempts = remainingAttempts;
    }

    public static void encode(AdminAuthRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.remainingAttempts);
    }

    public static AdminAuthRequestPacket decode(FriendlyByteBuf buf) {
        return new AdminAuthRequestPacket(buf.readInt());
    }

    public static void handle(AdminAuthRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Only execute on client side
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleAuthRequest(packet));
        });
        ctx.get().setPacketHandled(true);
    }

    // Client-only handler class
    public static class ClientPacketHandler {
        public static void handleAuthRequest(AdminAuthRequestPacket packet) {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            minecraft.execute(() -> {
                minecraft.setScreen(new AdminPasswordScreen(packet.remainingAttempts));
            });
        }
    }
}

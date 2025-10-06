package com.example.mysubmod.auth;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from server to client with authentication result
 */
public class AdminAuthResponsePacket {
    private final boolean success;
    private final int remainingAttempts;
    private final String message;

    public AdminAuthResponsePacket(boolean success, int remainingAttempts, String message) {
        this.success = success;
        this.remainingAttempts = remainingAttempts;
        this.message = message;
    }

    public static void encode(AdminAuthResponsePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.success);
        buf.writeInt(packet.remainingAttempts);
        buf.writeUtf(packet.message, 200);
    }

    public static AdminAuthResponsePacket decode(FriendlyByteBuf buf) {
        return new AdminAuthResponsePacket(
            buf.readBoolean(),
            buf.readInt(),
            buf.readUtf(200)
        );
    }

    public static void handle(AdminAuthResponsePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Only execute on client side
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleAuthResponse(packet));
        });
        ctx.get().setPacketHandled(true);
    }

    // Client-only handler class
    public static class ClientPacketHandler {
        public static void handleAuthResponse(AdminAuthResponsePacket packet) {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();

            if (packet.success) {
                // Success - close the password screen
                minecraft.execute(() -> minecraft.setScreen(null));
            } else {
                // Failure - update screen with remaining attempts or show error
                minecraft.execute(() -> {
                    if (minecraft.screen instanceof AuthPasswordScreen authScreen) {
                        // Update unified auth screen
                        authScreen.setRemainingAttempts(packet.remainingAttempts);
                        authScreen.showError(packet.message);
                    } else if (minecraft.screen instanceof AdminPasswordScreen passwordScreen) {
                        // Legacy support for old admin screen
                        passwordScreen.setRemainingAttempts(packet.remainingAttempts);
                        passwordScreen.showError(packet.message);
                    }
                });
            }
        }
    }
}

package com.example.mysubmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from server to client to open SubModeControlScreen with player count
 */
public class SubModeControlScreenPacket {
    private final int nonAdminPlayerCount;

    public SubModeControlScreenPacket(int nonAdminPlayerCount) {
        this.nonAdminPlayerCount = nonAdminPlayerCount;
    }

    public static void encode(SubModeControlScreenPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.nonAdminPlayerCount);
    }

    public static SubModeControlScreenPacket decode(FriendlyByteBuf buf) {
        return new SubModeControlScreenPacket(buf.readInt());
    }

    public static void handle(SubModeControlScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Only execute on client side
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSubModeControlScreen(packet));
        });
        ctx.get().setPacketHandled(true);
    }

    // Client-only handler class
    public static class ClientPacketHandler {
        public static void handleSubModeControlScreen(SubModeControlScreenPacket packet) {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            minecraft.execute(() -> {
                minecraft.setScreen(new com.example.mysubmod.client.gui.SubModeControlScreen(packet.nonAdminPlayerCount));
            });
        }
    }
}

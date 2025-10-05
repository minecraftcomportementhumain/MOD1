package com.example.mysubmod.network;

import com.example.mysubmod.submodes.SubModeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to request opening SubModeControlScreen
 */
public class SubModeControlScreenRequestPacket {

    public SubModeControlScreenRequestPacket() {
    }

    public static void encode(SubModeControlScreenRequestPacket packet, FriendlyByteBuf buf) {
        // No data to encode
    }

    public static SubModeControlScreenRequestPacket decode(FriendlyByteBuf buf) {
        return new SubModeControlScreenRequestPacket();
    }

    public static void handle(SubModeControlScreenRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // Count non-admin players
                int nonAdminCount = 0;
                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    if (!SubModeManager.getInstance().isAdmin(p)) {
                        nonAdminCount++;
                    }
                }

                // Send response with player count
                NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SubModeControlScreenPacket(nonAdminCount)
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

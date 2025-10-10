package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from server to client with queue token
 * Client stores this token and sends it back when reconnecting
 */
public class QueueTokenPacket {
    private final String accountName;
    private final String token;
    private final long monopolyStartMs;
    private final long monopolyEndMs;

    public QueueTokenPacket(String accountName, String token, long monopolyStartMs, long monopolyEndMs) {
        this.accountName = accountName;
        this.token = token;
        this.monopolyStartMs = monopolyStartMs;
        this.monopolyEndMs = monopolyEndMs;
    }

    public static void encode(QueueTokenPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.accountName, 50);
        buf.writeUtf(packet.token, 10);
        buf.writeLong(packet.monopolyStartMs);
        buf.writeLong(packet.monopolyEndMs);
    }

    public static QueueTokenPacket decode(FriendlyByteBuf buf) {
        return new QueueTokenPacket(
            buf.readUtf(50),
            buf.readUtf(10),
            buf.readLong(),
            buf.readLong()
        );
    }

    public static void handle(QueueTokenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Check if this is a token clear request (empty token)
            if (packet.token.isEmpty()) {
                QueueTokenStorage.removeToken(packet.accountName);
                MySubMod.LOGGER.info("Cleared queue token for {}", packet.accountName);
            } else {
                // Store token on client side
                QueueTokenStorage.storeToken(packet.accountName, packet.token, packet.monopolyStartMs, packet.monopolyEndMs);
                MySubMod.LOGGER.info("Received and stored queue token for {}: {}", packet.accountName, packet.token);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

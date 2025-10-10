package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from server to client requesting the stored queue token
 * Client automatically responds with QueueTokenVerifyPacket
 */
public class QueueTokenRequestPacket {
    private final String accountName;

    public QueueTokenRequestPacket(String accountName) {
        this.accountName = accountName;
    }

    public static void encode(QueueTokenRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.accountName, 50);
    }

    public static QueueTokenRequestPacket decode(FriendlyByteBuf buf) {
        return new QueueTokenRequestPacket(buf.readUtf(50));
    }

    public static void handle(QueueTokenRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Client-side: Check if we have a token for this account
            String token = QueueTokenStorage.getToken(packet.accountName);

            if (token != null) {
                MySubMod.LOGGER.info("Server requested token for {} - sending token: {}", packet.accountName, token);
                // Send token to server
                NetworkHandler.INSTANCE.sendToServer(new QueueTokenVerifyPacket(packet.accountName, token));
            } else {
                MySubMod.LOGGER.warn("Server requested token for {} but no token found in storage", packet.accountName);
                // Send empty token to trigger rejection
                NetworkHandler.INSTANCE.sendToServer(new QueueTokenVerifyPacket(packet.accountName, ""));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

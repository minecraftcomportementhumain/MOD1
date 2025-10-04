package com.example.mysubmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet to send log file data from server to client
 */
public class LogDataPacket {
    private final String fileName;
    private final byte[] data;

    public LogDataPacket(String fileName, byte[] data) {
        this.fileName = fileName;
        this.data = data;
    }

    public LogDataPacket(FriendlyByteBuf buf) {
        this.fileName = buf.readUtf();
        int dataLength = buf.readInt();
        this.data = new byte[dataLength];
        buf.readBytes(this.data);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(fileName);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getData() {
        return data;
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // Client-side handling
            com.example.mysubmod.client.LogPacketHandler.handleLogData(fileName, data);
        });
        return true;
    }
}

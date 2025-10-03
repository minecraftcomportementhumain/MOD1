package com.example.mysubmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LogListPacket {
    private final List<String> logFolders;

    public LogListPacket(List<String> logFolders) {
        this.logFolders = logFolders;
    }

    public LogListPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        this.logFolders = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            this.logFolders.add(buf.readUtf());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(logFolders.size());
        for (String folder : logFolders) {
            buf.writeUtf(folder);
        }
    }

    public List<String> getLogFolders() {
        return logFolders;
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            com.example.mysubmod.client.LogPacketHandler.handleLogListPacket(this);
        });
        return true;
    }
}

package com.example.mysubmod.submodes.submode1.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CandyFileListPacket {
    private final List<String> availableFiles;

    public CandyFileListPacket(List<String> availableFiles) {
        this.availableFiles = new ArrayList<>(availableFiles);
    }

    public static void encode(CandyFileListPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.availableFiles.size());
        for (String filename : packet.availableFiles) {
            buf.writeUtf(filename);
        }
    }

    public static CandyFileListPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> files = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            files.add(buf.readUtf());
        }
        return new CandyFileListPacket(files);
    }

    public static void handle(CandyFileListPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handleCandyFileList(packet.availableFiles);
        });
        ctx.get().setPacketHandled(true);
    }

    public List<String> getAvailableFiles() {
        return availableFiles;
    }
}
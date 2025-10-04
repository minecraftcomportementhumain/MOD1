package com.example.mysubmod.submodes.submode1.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CandyFileListPacket {
    private final List<String> availableFiles;
    private final boolean openScreen; // Whether to open the screen automatically

    public CandyFileListPacket(List<String> availableFiles) {
        this(availableFiles, true); // Default to opening screen
    }

    public CandyFileListPacket(List<String> availableFiles, boolean openScreen) {
        this.availableFiles = new ArrayList<>(availableFiles);
        this.openScreen = openScreen;
    }

    public static void encode(CandyFileListPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.availableFiles.size());
        for (String filename : packet.availableFiles) {
            buf.writeUtf(filename);
        }
        buf.writeBoolean(packet.openScreen);
    }

    public static CandyFileListPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> files = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            files.add(buf.readUtf());
        }
        boolean openScreen = buf.readBoolean();
        return new CandyFileListPacket(files, openScreen);
    }

    public static void handle(CandyFileListPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handleCandyFileList(packet.availableFiles, packet.openScreen);
        });
        ctx.get().setPacketHandled(true);
    }

    public List<String> getAvailableFiles() {
        return availableFiles;
    }

    public boolean shouldOpenScreen() {
        return openScreen;
    }
}
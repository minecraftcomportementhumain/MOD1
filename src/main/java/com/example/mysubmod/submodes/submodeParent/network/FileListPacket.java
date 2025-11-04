package com.example.mysubmod.submodes.submodeParent.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class FileListPacket {
    private final List<String> availableFiles;
    private final boolean openScreen; // Whether to open the screen automatically

    public FileListPacket(List<String> availableFiles) {
        this(availableFiles, true); // Default to opening screen
    }

    public FileListPacket(List<String> availableFiles, boolean openScreen) {
        this.availableFiles = new ArrayList<>(availableFiles);
        this.openScreen = openScreen;
    }

    public static void encode(FileListPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.availableFiles.size());
        for (String filename : packet.availableFiles) {
            buf.writeUtf(filename);
        }
        buf.writeBoolean(packet.openScreen);
    }

    public static FileListPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> files = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            files.add(buf.readUtf());
        }
        boolean openScreen = buf.readBoolean();
        return new FileListPacket(files, openScreen);
    }

    public static void handle(FileListPacket packet, Supplier<NetworkEvent.Context> ctx) {
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
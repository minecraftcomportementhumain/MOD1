package com.example.mysubmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LogDownloadPacket {
    private final String folderName;
    private final boolean downloadAll;

    public LogDownloadPacket(String folderName, boolean downloadAll) {
        this.folderName = folderName;
        this.downloadAll = downloadAll;
    }

    public LogDownloadPacket(FriendlyByteBuf buf) {
        this.downloadAll = buf.readBoolean();
        this.folderName = buf.readBoolean() ? buf.readUtf() : null;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(downloadAll);
        buf.writeBoolean(folderName != null);
        if (folderName != null) {
            buf.writeUtf(folderName);
        }
    }

    public String getFolderName() {
        return folderName;
    }

    public boolean isDownloadAll() {
        return downloadAll;
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = context.getSender();
            if (player != null && com.example.mysubmod.submodes.SubModeManager.getInstance().isAdmin(player)) {
                com.example.mysubmod.server.LogManager.downloadLogs(player, folderName, downloadAll);
            }
        });
        return true;
    }
}

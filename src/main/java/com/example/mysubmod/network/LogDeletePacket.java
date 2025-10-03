package com.example.mysubmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LogDeletePacket {
    private final String folderName;
    private final boolean deleteAll;

    public LogDeletePacket(String folderName, boolean deleteAll) {
        this.folderName = folderName;
        this.deleteAll = deleteAll;
    }

    public LogDeletePacket(FriendlyByteBuf buf) {
        this.deleteAll = buf.readBoolean();
        this.folderName = buf.readBoolean() ? buf.readUtf() : null;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(deleteAll);
        buf.writeBoolean(folderName != null);
        if (folderName != null) {
            buf.writeUtf(folderName);
        }
    }

    public String getFolderName() {
        return folderName;
    }

    public boolean isDeleteAll() {
        return deleteAll;
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = context.getSender();
            if (player != null && com.example.mysubmod.submodes.SubModeManager.getInstance().isAdmin(player)) {
                com.example.mysubmod.server.LogManager.deleteLogs(player, folderName, deleteAll);
            }
        });
        return true;
    }
}

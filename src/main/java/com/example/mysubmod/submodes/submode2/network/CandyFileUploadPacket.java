package com.example.mysubmod.submodes.submode2.network;

import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode2.data.CandySpawnFileManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

public class CandyFileUploadPacket {
    private final UUID transferId;
    private final String filename;
    private final int totalChunks;
    private final int chunkIndex;
    private final byte[] chunkData;

    public CandyFileUploadPacket(UUID transferId, String filename, int totalChunks, int chunkIndex, byte[] chunkData) {
        this.transferId = transferId;
        this.filename = filename;
        this.totalChunks = totalChunks;
        this.chunkIndex = chunkIndex;
        this.chunkData = chunkData;
    }

    public static void encode(CandyFileUploadPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.transferId);
        buf.writeUtf(packet.filename);
        buf.writeInt(packet.totalChunks);
        buf.writeInt(packet.chunkIndex);
        buf.writeInt(packet.chunkData.length);
        buf.writeBytes(packet.chunkData);
    }

    public static CandyFileUploadPacket decode(FriendlyByteBuf buf) {
        UUID transferId = buf.readUUID();
        String filename = buf.readUtf();
        int totalChunks = buf.readInt();
        int chunkIndex = buf.readInt();
        int length = buf.readInt();
        byte[] chunkData = new byte[length];
        buf.readBytes(chunkData);
        return new CandyFileUploadPacket(transferId, filename, totalChunks, chunkIndex, chunkData);
    }

    public static void handle(CandyFileUploadPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // Verify admin permissions on server side
                if (SubModeManager.getInstance().isAdmin(player)) {
                    int success = CandySpawnFileManager.getInstance().handleChunk(packet);
                    if (success == 0) {
                        player.sendSystemMessage(Component.literal("§aFichier de spawn de bonbons téléchargé avec succès: " + packet.filename));
                    } else if(success == -1){
                        player.sendSystemMessage(Component.literal("§cErreur lors du téléchargement du fichier. Vérifiez le format."));
                    }
                } else {
                    player.sendSystemMessage(Component.literal("§cVous n'avez pas les permissions pour télécharger des fichiers."));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String intoString(byte[] byteArray){
        return new String(byteArray, StandardCharsets.UTF_8);
    }

    public UUID getTransferId() { return transferId; }
    public String getFilename() { return filename; }
    public int getTotalChunks() { return totalChunks; }
    public int getChunkIndex() { return chunkIndex; }
    public byte[] getChunkData() { return chunkData; }
}
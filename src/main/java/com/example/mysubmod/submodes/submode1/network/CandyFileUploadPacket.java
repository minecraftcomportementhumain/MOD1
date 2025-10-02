package com.example.mysubmod.submodes.submode1.network;

import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode1.data.CandySpawnFileManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CandyFileUploadPacket {
    private final String filename;
    private final String content;

    public CandyFileUploadPacket(String filename, String content) {
        this.filename = filename;
        this.content = content;
    }

    public static void encode(CandyFileUploadPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.filename);
        buf.writeUtf(packet.content);
    }

    public static CandyFileUploadPacket decode(FriendlyByteBuf buf) {
        return new CandyFileUploadPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(CandyFileUploadPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // Verify admin permissions on server side
                if (SubModeManager.getInstance().isPlayerAdmin(player)) {
                    boolean success = CandySpawnFileManager.getInstance().saveUploadedFile(
                        packet.filename, packet.content);

                    if (success) {
                        player.sendSystemMessage(Component.literal("§aFichier de spawn de bonbons téléchargé avec succès: " + packet.filename));
                    } else {
                        player.sendSystemMessage(Component.literal("§cErreur lors du téléchargement du fichier. Vérifiez le format."));
                    }
                } else {
                    player.sendSystemMessage(Component.literal("§cVous n'avez pas les permissions pour télécharger des fichiers."));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String getFilename() {
        return filename;
    }

    public String getContent() {
        return content;
    }
}
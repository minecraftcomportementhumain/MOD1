package com.example.mysubmod.submodes.submodeParent.network;

import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode2.data.SubMode2SpawnFileManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FileUploadPacket {
    protected final String filename;
    protected final String content;

    public FileUploadPacket(String filename, String content) {
        this.filename = filename;
        this.content = content;
    }

    public static void encode(FileUploadPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.filename);
        buf.writeUtf(packet.content);
    }

    public static FileUploadPacket decode(FriendlyByteBuf buf) {
        return new FileUploadPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(FileUploadPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // Verify admin permissions on server side
                if (SubModeManager.getInstance().isAdmin(player)) {
                    boolean success = SubMode2SpawnFileManager.getInstance().saveUploadedFile(
                            packet.filename, packet.content);

                    if (success) {
                        player.sendSystemMessage(Component.literal("§aFichier de spawn de bonbons SubMode2 téléchargé avec succès: " + packet.filename));
                    } else {
                        player.sendSystemMessage(Component.literal("§cErreur lors du téléchargement du fichier. Vérifiez le format (doit inclure le type A ou B)."));
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
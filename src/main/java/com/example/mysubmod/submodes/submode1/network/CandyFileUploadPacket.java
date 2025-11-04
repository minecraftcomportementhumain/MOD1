package com.example.mysubmod.submodes.submode1.network;

import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode1.data.SubMode1SpawnFileManager;
import com.example.mysubmod.submodes.submodeParent.network.FileUploadPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CandyFileUploadPacket extends FileUploadPacket {

    public CandyFileUploadPacket(String filename, String content) {
        super(filename, content);
    }

    //MUST CHANGE SPAWNFILEMANAGER
    public static void handle(FileUploadPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // Verify admin permissions on server side
                if (SubModeManager.getInstance().isAdmin(player)) {
                    boolean success = SubMode1SpawnFileManager.getInstance().saveUploadedFile(
                            packet.getFilename(), packet.getContent());

                    if (success) {
                        player.sendSystemMessage(Component.literal("§aFichier de spawn de bonbons SubMode2 téléchargé avec succès: " + packet.getFilename()));
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
}
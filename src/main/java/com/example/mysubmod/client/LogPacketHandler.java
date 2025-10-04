package com.example.mysubmod.client;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.client.gui.LogManagementScreen;
import com.example.mysubmod.network.LogListPacket;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class LogPacketHandler {
    public static void handleLogListPacket(LogListPacket packet) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new LogManagementScreen(packet.getLogFolders()));
        });
    }

    public static void handleLogData(String fileName, byte[] data) {
        Minecraft.getInstance().execute(() -> {
            try {
                // Save to client's Downloads folder
                String userHome = System.getProperty("user.home");
                File downloadsDir = new File(userHome, "Downloads");

                if (!downloadsDir.exists()) {
                    downloadsDir = new File(userHome); // Fallback to user home
                }

                File outputFile = new File(downloadsDir, fileName);

                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(data);
                }

                Minecraft.getInstance().player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§aLogs téléchargés dans: " + outputFile.getAbsolutePath()));
                MySubMod.LOGGER.info("Downloaded log file to: {}", outputFile.getAbsolutePath());

            } catch (IOException e) {
                MySubMod.LOGGER.error("Error saving downloaded log file", e);
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("§cErreur lors de la sauvegarde du fichier"));
                }
            }
        });
    }
}

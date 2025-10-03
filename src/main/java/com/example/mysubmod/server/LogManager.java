package com.example.mysubmod.server;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.network.LogListPacket;
import com.example.mysubmod.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LogManager {
    private static final String LOG_BASE_DIR = "mysubmod_data";

    public static void sendLogList(ServerPlayer player) {
        List<String> logFolders = getLogFolders();
        NetworkHandler.INSTANCE.send(
            PacketDistributor.PLAYER.with(() -> player),
            new LogListPacket(logFolders)
        );
    }

    public static List<String> getLogFolders() {
        List<String> folders = new ArrayList<>();
        File baseDir = new File(LOG_BASE_DIR);

        if (!baseDir.exists() || !baseDir.isDirectory()) {
            return folders;
        }

        File[] files = baseDir.listFiles();
        if (files == null) {
            return folders;
        }

        for (File file : files) {
            if (file.isDirectory() && file.getName().startsWith("submode1_game_")) {
                folders.add(file.getName());
            }
        }

        // Sort by name (which includes timestamp) - newest first
        folders.sort(Comparator.reverseOrder());

        return folders;
    }

    public static void downloadLogs(ServerPlayer player, String folderName, boolean downloadAll) {
        try {
            File baseDir = new File(LOG_BASE_DIR);
            if (!baseDir.exists()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cAucun dossier de logs trouvé"));
                return;
            }

            if (downloadAll) {
                downloadAllLogsAsZip(player, baseDir);
            } else if (folderName != null) {
                downloadSingleFolder(player, new File(baseDir, folderName));
            }
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error downloading logs", e);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cErreur lors du téléchargement des logs"));
        }
    }

    private static void downloadAllLogsAsZip(ServerPlayer player, File baseDir) throws IOException {
        // Get Windows Downloads folder
        String userHome = System.getProperty("user.home");
        File downloadsDir = new File(userHome, "Downloads");

        if (!downloadsDir.exists()) {
            downloadsDir = new File(userHome); // Fallback to user home
        }

        File outputZip = new File(downloadsDir, "all_logs.zip");

        try (FileOutputStream fos = new FileOutputStream(outputZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            File[] folders = baseDir.listFiles();
            if (folders != null) {
                for (File folder : folders) {
                    if (folder.isDirectory() && folder.getName().startsWith("submode1_game_")) {
                        addFolderToZip(folder, folder.getName(), zos);
                    }
                }
            }
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "§aTous les logs ont été téléchargés dans: " + outputZip.getAbsolutePath()));
        MySubMod.LOGGER.info("Created zip archive of all logs at: {}", outputZip.getAbsolutePath());
    }

    private static void downloadSingleFolder(ServerPlayer player, File folder) throws IOException {
        if (!folder.exists() || !folder.isDirectory()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cDossier non trouvé: " + folder.getName()));
            return;
        }

        // Get Windows Downloads folder
        String userHome = System.getProperty("user.home");
        File downloadsDir = new File(userHome, "Downloads");

        if (!downloadsDir.exists()) {
            downloadsDir = new File(userHome); // Fallback to user home
        }

        File outputZip = new File(downloadsDir, folder.getName() + ".zip");

        try (FileOutputStream fos = new FileOutputStream(outputZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            addFolderToZip(folder, folder.getName(), zos);
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "§aLogs téléchargés dans: " + outputZip.getAbsolutePath()));
        MySubMod.LOGGER.info("Created zip archive for {} at: {}", folder.getName(), outputZip.getAbsolutePath());
    }

    private static void addFolderToZip(File folder, String parentPath, ZipOutputStream zos) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                addFolderToZip(file, parentPath + "/" + file.getName(), zos);
            } else {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(parentPath + "/" + file.getName());
                    zos.putNextEntry(zipEntry);

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }

                    zos.closeEntry();
                }
            }
        }
    }

    public static void deleteLogs(ServerPlayer player, String folderName, boolean deleteAll) {
        try {
            File baseDir = new File(LOG_BASE_DIR);
            if (!baseDir.exists()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cAucun dossier de logs trouvé"));
                return;
            }

            if (deleteAll) {
                deleteAllLogs(player, baseDir);
            } else if (folderName != null) {
                deleteSingleFolder(player, new File(baseDir, folderName));
            }
        } catch (Exception e) {
            MySubMod.LOGGER.error("Error deleting logs", e);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cErreur lors de la suppression des logs"));
        }
    }

    private static void deleteAllLogs(ServerPlayer player, File baseDir) throws IOException {
        int deletedCount = 0;
        File[] folders = baseDir.listFiles();

        if (folders != null) {
            for (File folder : folders) {
                if (folder.isDirectory() && folder.getName().startsWith("submode1_game_")) {
                    deleteDirectory(folder);
                    deletedCount++;
                }
            }
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "§aTous les logs ont été supprimés (" + deletedCount + " dossier(s))"));
        MySubMod.LOGGER.info("Deleted all log folders: {} folders", deletedCount);
    }

    private static void deleteSingleFolder(ServerPlayer player, File folder) throws IOException {
        if (!folder.exists() || !folder.isDirectory()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cDossier non trouvé: " + folder.getName()));
            return;
        }

        deleteDirectory(folder);

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "§aDossier supprimé: " + folder.getName()));
        MySubMod.LOGGER.info("Deleted log folder: {}", folder.getName());
    }

    private static void deleteDirectory(File directory) throws IOException {
        Path path = directory.toPath();

        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

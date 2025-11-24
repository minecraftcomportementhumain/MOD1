package com.example.mysubmod.submodes.submode2.data;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submode2.network.CandyFileUploadPacket;
import com.example.mysubmod.submodes.submode2.ResourceType;
import net.minecraft.core.BlockPos;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gestion des fichiers de configuration de spawn pour le Sous-mode 2
 * Format: temps,nombre,x,y,z,type
 * où type = A ou B
 */
public class CandySpawnFileManager {
    private static final String CANDY_SPAWN_DIRECTORY = "candy_spawn_configs_submode2";
    private static CandySpawnFileManager instance;
    private static final Map<UUID, ConcurrentHashMap<Integer, byte[]>> pendingUploads = new ConcurrentHashMap<>();

    private CandySpawnFileManager() {}

    public static CandySpawnFileManager getInstance() {
        if (instance == null) {
            instance = new CandySpawnFileManager();
        }
        return instance;
    }

    public void ensureDirectoryExists() {
        Path configDir = Paths.get(CANDY_SPAWN_DIRECTORY);
        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
                createDefaultFile();
                MySubMod.LOGGER.info("Created SubMode2 candy spawn configs directory: {}", configDir.toAbsolutePath());
            } catch (IOException e) {
                MySubMod.LOGGER.error("Failed to create SubMode2 candy spawn configs directory", e);
            }
        }
    }

    private void createDefaultFile() {
        String defaultContent = """
            # Fichier de configuration d'apparition des bonbons - SOUS-MODE 2
            # Format: temps_en_secondes,nombre_bonbons,x,y,z,type
            # Temps: 0-900 secondes (15 minutes max)
            # Nombre de bonbons: 1-100 max
            # Coordonnées: doivent être sur une des 4 îles
            # Y (hauteur): 100-120 (îles à Y=100, +20 pour relief futur)
            # Type: A ou B (Bonbon Bleu ou Bonbon Rouge)
            # Îles: SMALL(0,-360), MEDIUM(360,0), LARGE(0,360), EXTRA_LARGE(-360,0)

            # Exemple: Mix de Type A et B sur différentes îles
            60,5,0,101,-360,A
            60,5,360,101,0,B
            120,3,0,101,360,A
            120,3,-360,101,0,B
            180,2,0,101,-360,B
            180,2,360,101,0,A
            240,4,0,101,360,B
            240,4,-360,101,0,A
            300,10,0,101,-360,A
            300,10,360,101,0,B
            360,8,0,101,360,A
            360,8,-360,101,0,B
            420,15,0,101,-360,B
            420,15,360,101,0,A
            480,12,0,101,360,B
            480,12,-360,101,0,A
            """;

        try {
            Path defaultFile = Paths.get(CANDY_SPAWN_DIRECTORY, "default.txt");
            Files.writeString(defaultFile, defaultContent);
            MySubMod.LOGGER.info("Created default SubMode2 candy spawn config file");
        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to create default SubMode2 candy spawn config", e);
        }
    }

    public List<String> getAvailableFiles() {
        try {
            Path configDir = Paths.get(CANDY_SPAWN_DIRECTORY);
            if (!Files.exists(configDir)) {
                ensureDirectoryExists();
            }

            return Files.list(configDir)
                .filter(path -> path.toString().endsWith(".txt"))
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to list SubMode2 candy spawn config files", e);
            return new ArrayList<>();
        }
    }

    public List<CandySpawnEntry> loadSpawnConfig(String filePathOrName) {
        List<CandySpawnEntry> entries = new ArrayList<>();

        Path configFile;
        if (filePathOrName.contains(File.separator) || filePathOrName.contains("/")) {
            configFile = Paths.get(filePathOrName);
        } else {
            configFile = Paths.get(CANDY_SPAWN_DIRECTORY, filePathOrName);
        }

        if (!Files.exists(configFile)) {
            MySubMod.LOGGER.error("SubMode2 candy spawn config file not found: {}", filePathOrName);
            return entries;
        }

        List<String> modifiedLines = new ArrayList<>();
        boolean hasInvalidLines = false;

        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmedLine = line.trim();

                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    modifiedLines.add(line);
                    continue;
                }

                try {
                    CandySpawnEntry entry = parseLine(trimmedLine);
                    if (entry != null) {
                        entries.add(entry);
                        modifiedLines.add(line);
                    }
                } catch (Exception e) {
                    MySubMod.LOGGER.warn("Invalid line {} in {}: {} ({})", lineNumber, filePathOrName, trimmedLine, e.getMessage());
                    modifiedLines.add("# INVALID: " + line + " # Error: " + e.getMessage());
                    hasInvalidLines = true;
                }
            }

            MySubMod.LOGGER.info("Loaded {} SubMode2 candy spawn entries from {}", entries.size(), filePathOrName);

            if (hasInvalidLines) {
                try {
                    Files.write(configFile, modifiedLines);
                    MySubMod.LOGGER.info("Commented out invalid lines in {}", filePathOrName);
                } catch (IOException writeEx) {
                    MySubMod.LOGGER.error("Failed to update file with commented lines: {}", filePathOrName, writeEx);
                }
            }

        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to read SubMode2 candy spawn config file: {}", filePathOrName, e);
        }

        return entries;
    }

    private CandySpawnEntry parseLine(String line) {
        String[] parts = line.split(",");
        if (parts.length != 6) {
            throw new IllegalArgumentException("Expected format: time,count,x,y,z,type (A or B)");
        }

        int timeSeconds = Integer.parseInt(parts[0].trim());
        int candyCount = Integer.parseInt(parts[1].trim());
        int x = Integer.parseInt(parts[2].trim());
        int y = Integer.parseInt(parts[3].trim());
        int z = Integer.parseInt(parts[4].trim());
        String typeStr = parts[5].trim();

        // Validate time range (0-900 seconds = 15 minutes)
        if (timeSeconds < 0 || timeSeconds > 900) {
            throw new IllegalArgumentException("Time must be between 0 and 900 seconds");
        }

        // Validate candy count (1-100)
        if (candyCount <= 0 || candyCount > 100) {
            throw new IllegalArgumentException("Candy count must be between 1 and 100");
        }

        // Validate Y coordinate (height)
        if (y < 100 || y > 120) {
            throw new IllegalArgumentException("Y coordinate must be between 100 and 120");
        }

        // Validate resource type
        ResourceType type = ResourceType.fromString(typeStr);
        if (type == null) {
            throw new IllegalArgumentException("Type must be A or B, got: " + typeStr);
        }

        BlockPos position = new BlockPos(x, y, z);

        // Validate that coordinates are on one of the 4 islands
        if (!isPositionOnIsland(position)) {
            throw new IllegalArgumentException(String.format(
                "Position (%d,%d,%d) is not on any island", x, y, z));
        }

        return new CandySpawnEntry(timeSeconds, candyCount, position, type);
    }

    private boolean isPositionOnIsland(BlockPos pos) {
        // Island centers and radii (half-sizes)
        int[][] islandData = {
            {0, -360, 30},      // SMALL
            {360, 0, 45},       // MEDIUM
            {0, 360, 60},       // LARGE
            {-360, 0, 75}       // EXTRA_LARGE
        };

        for (int[] island : islandData) {
            int centerX = island[0];
            int centerZ = island[1];
            int halfSize = island[2];

            int minX = centerX - halfSize;
            int maxX = centerX + halfSize;
            int minZ = centerZ - halfSize;
            int maxZ = centerZ + halfSize;

            if (pos.getX() >= minX && pos.getX() <= maxX &&
                pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                return true;
            }
        }

        return false;
    }

    public boolean saveUploadedFile(String filename, String content) {
        try {
            ensureDirectoryExists();
            Path configFile = Paths.get(CANDY_SPAWN_DIRECTORY, filename);

            // Validate content
            String[] lines = content.split("\\r?\\n");
            List<CandySpawnEntry> testEntries = new ArrayList<>();

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                try {
                    CandySpawnEntry entry = parseLine(line);
                    if (entry != null) {
                        testEntries.add(entry);
                    }
                } catch (Exception e) {
                    MySubMod.LOGGER.warn("Invalid line in uploaded SubMode2 file: {} ({})", line, e.getMessage());
                    return false;
                }
            }

            if (testEntries.isEmpty()) {
                MySubMod.LOGGER.warn("Uploaded SubMode2 file contains no valid entries");
                return false;
            }

            Files.writeString(configFile, content);
            MySubMod.LOGGER.info("Successfully saved uploaded SubMode2 candy spawn config: {}", filename);
            return true;

        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to save uploaded SubMode2 candy spawn config", e);
            return false;
        }
    }

    public int handleChunk(CandyFileUploadPacket packet) {

        // Store the chunk
        pendingUploads
                .computeIfAbsent(packet.getTransferId(), k -> new ConcurrentHashMap<>())
                .put(packet.getChunkIndex(), packet.getChunkData());
        Path configFile = Path.of("");
        // Check if all chunks are received
        if (pendingUploads.get(packet.getTransferId()).size() == packet.getTotalChunks()) {
            // Reassemble
            byte[] fullData = combineByteArrays(pendingUploads.get(packet.getTransferId()), packet.getTotalChunks());
            String fileContent = new String(fullData, StandardCharsets.UTF_8);
            try {
                ensureDirectoryExists();
                String filename = packet.getFilename();
                configFile = Paths.get(CANDY_SPAWN_DIRECTORY, filename);

                // Validate content
                String[] lines = fileContent.split("\\r?\\n");
                List<CandySpawnEntry> testEntries = new ArrayList<>();

                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    try {
                        CandySpawnEntry entry = parseLine(line);
                        if (entry != null) {
                            testEntries.add(entry);
                        }
                    } catch (Exception e) {
                        MySubMod.LOGGER.warn("Invalid line in uploaded SubMode2 file: {} ({})", line, e.getMessage());
                        return -1;
                    }
                }

                if (testEntries.isEmpty()) {
                    MySubMod.LOGGER.warn("Uploaded SubMode2 file contains no valid entries");
                    return -1;
                }

                Files.writeString(configFile, fileContent);
                MySubMod.LOGGER.info("Successfully saved uploaded SubMode2 candy spawn config: {}", filename);
                // Clean up storage
                pendingUploads.remove(packet.getTransferId());
                return 0;

            } catch (IOException e) {
                MySubMod.LOGGER.error("Failed to save uploaded SubMode2 candy spawn config", e);
                return -1;
            }
        }
        //Still waiting for more
        return 1;
    }

    private static byte[] combineByteArrays(Map<Integer, byte[]> chunks, int totalChunks) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (int i = 0; i < totalChunks; i++) {
            try {
                outputStream.write(chunks.get(i));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return outputStream.toByteArray();
    }

    public String getDefaultFile() {
        List<String> files = getAvailableFiles();
        if (files.isEmpty()) {
            ensureDirectoryExists();
            files = getAvailableFiles();
        }

        if (files.contains("default.txt")) {
            return "default.txt";
        }

        return files.isEmpty() ? null : files.get(0);
    }

    public boolean deleteFile(String filename) {
        try {
            if ("default.txt".equals(filename)) {
                MySubMod.LOGGER.warn("Cannot delete default.txt file");
                return false;
            }

            Path configFile = Paths.get(CANDY_SPAWN_DIRECTORY, filename);
            if (!Files.exists(configFile)) {
                MySubMod.LOGGER.warn("File to delete does not exist: {}", filename);
                return false;
            }

            Files.delete(configFile);
            MySubMod.LOGGER.info("Successfully deleted SubMode2 candy spawn config file: {}", filename);
            return true;

        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to delete SubMode2 candy spawn config file: {}", filename, e);
            return false;
        }
    }
}

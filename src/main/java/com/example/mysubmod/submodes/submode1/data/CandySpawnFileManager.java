package com.example.mysubmod.submodes.submode1.data;

import com.example.mysubmod.MySubMod;
import net.minecraft.core.BlockPos;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class CandySpawnFileManager {
    private static final String CANDY_SPAWN_DIRECTORY = "candy_spawn_configs";
    private static CandySpawnFileManager instance;

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
                MySubMod.LOGGER.info("Created candy spawn configs directory: {}", configDir.toAbsolutePath());
            } catch (IOException e) {
                MySubMod.LOGGER.error("Failed to create candy spawn configs directory", e);
            }
        }
    }

    private void createDefaultFile() {
        String defaultContent = """
            # Fichier de configuration d'apparition des bonbons
            # Format: temps_en_secondes,nombre_bonbons,x,y,z
            # Temps: 0-900 secondes (15 minutes max)
            # Nombre de bonbons: 1-100 max
            # Coordonnées: doivent être sur une des 4 îles
            # Y (hauteur): 100-120 (îles à Y=100, +20 pour relief futur)
            # Îles: SMALL(0,-360), MEDIUM(360,0), LARGE(0,360), EXTRA_LARGE(-360,0)

            # Exemple: Apparition progressive sur différentes îles
            60,5,0,101,-360
            120,3,360,101,0
            180,2,0,101,360
            240,4,-360,101,0
            300,10,0,101,-360
            360,8,360,101,0
            420,15,0,101,360
            480,12,-360,101,0
            540,20,0,101,-360
            600,18,360,101,0
            660,25,0,101,360
            720,22,-360,101,0
            780,30,0,101,-360
            840,28,360,101,0
            """;

        try {
            Path defaultFile = Paths.get(CANDY_SPAWN_DIRECTORY, "default.txt");
            Files.writeString(defaultFile, defaultContent);
            MySubMod.LOGGER.info("Created default candy spawn config file");
        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to create default candy spawn config", e);
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
            MySubMod.LOGGER.error("Failed to list candy spawn config files", e);
            return new ArrayList<>();
        }
    }

    public List<CandySpawnEntry> loadSpawnConfig(String filename) {
        List<CandySpawnEntry> entries = new ArrayList<>();
        Path configFile = Paths.get(CANDY_SPAWN_DIRECTORY, filename);

        if (!Files.exists(configFile)) {
            MySubMod.LOGGER.error("Candy spawn config file not found: {}", filename);
            return entries;
        }

        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                try {
                    CandySpawnEntry entry = parseLine(line);
                    if (entry != null) {
                        entries.add(entry);
                    }
                } catch (Exception e) {
                    MySubMod.LOGGER.warn("Invalid line {} in {}: {} ({})", lineNumber, filename, line, e.getMessage());
                }
            }

            MySubMod.LOGGER.info("Loaded {} candy spawn entries from {}", entries.size(), filename);

        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to read candy spawn config file: {}", filename, e);
        }

        return entries;
    }

    private CandySpawnEntry parseLine(String line) {
        String[] parts = line.split(",");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Expected format: time,count,x,y,z");
        }

        int timeSeconds = Integer.parseInt(parts[0].trim());
        int candyCount = Integer.parseInt(parts[1].trim());
        int x = Integer.parseInt(parts[2].trim());
        int y = Integer.parseInt(parts[3].trim());
        int z = Integer.parseInt(parts[4].trim());

        // Validate time range (0-900 seconds = 15 minutes)
        if (timeSeconds < 0 || timeSeconds > 900) {
            throw new IllegalArgumentException("Time must be between 0 and 900 seconds");
        }

        // Validate candy count (1-100)
        if (candyCount <= 0 || candyCount > 100) {
            throw new IllegalArgumentException("Candy count must be between 1 and 100");
        }

        // Validate Y coordinate (height)
        // Islands are at Y=100, nothing below is valid, allow up to +20 for future terrain
        if (y < 100 || y > 120) {
            throw new IllegalArgumentException("Y coordinate must be between 100 and 120");
        }

        net.minecraft.core.BlockPos position = new net.minecraft.core.BlockPos(x, y, z);

        // Validate that coordinates are on one of the 4 islands
        if (!isPositionOnIsland(position)) {
            throw new IllegalArgumentException(String.format(
                "Position (%d,%d,%d) is not on any island", x, y, z));
        }

        return new CandySpawnEntry(timeSeconds, candyCount, position);
    }

    /**
     * Check if a position is on one of the 4 islands
     * Islands are square and at: SMALL(0,-360), MEDIUM(360,0), LARGE(0,360), EXTRA_LARGE(-360,0)
     */
    private boolean isPositionOnIsland(net.minecraft.core.BlockPos pos) {
        // Island centers and radii (half-sizes)
        // SMALL: 60x60 (radius 30), MEDIUM: 90x90 (radius 45), LARGE: 120x120 (radius 60), EXTRA_LARGE: 150x150 (radius 75)
        int[][] islandData = {
            {0, -360, 30},      // SMALL: center (0,-360), radius 30
            {360, 0, 45},       // MEDIUM: center (360,0), radius 45
            {0, 360, 60},       // LARGE: center (0,360), radius 60
            {-360, 0, 75}       // EXTRA_LARGE: center (-360,0), radius 75
        };

        for (int[] island : islandData) {
            int centerX = island[0];
            int centerZ = island[1];
            int halfSize = island[2];

            // Check if position is within square bounds
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

            // Validate the content by trying to parse it
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
                    MySubMod.LOGGER.warn("Invalid line in uploaded file: {} ({})", line, e.getMessage());
                    return false;
                }
            }

            if (testEntries.isEmpty()) {
                MySubMod.LOGGER.warn("Uploaded file contains no valid entries");
                return false;
            }

            Files.writeString(configFile, content);
            MySubMod.LOGGER.info("Successfully saved uploaded candy spawn config: {}", filename);
            return true;

        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to save uploaded candy spawn config", e);
            return false;
        }
    }

    public String getDefaultFile() {
        List<String> files = getAvailableFiles();
        if (files.isEmpty()) {
            ensureDirectoryExists();
            files = getAvailableFiles();
        }

        // Prefer default.txt if it exists
        if (files.contains("default.txt")) {
            return "default.txt";
        }

        // Otherwise return the first available file
        return files.isEmpty() ? null : files.get(0);
    }

    public boolean deleteFile(String filename) {
        try {
            // Don't allow deleting default.txt
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
            MySubMod.LOGGER.info("Successfully deleted candy spawn config file: {}", filename);
            return true;

        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to delete candy spawn config file: {}", filename, e);
            return false;
        }
    }
}
package com.example.mysubmod.submodes.submode1.data;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submode1.islands.IslandType;
import net.minecraft.server.MinecraftServer;

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
            # Format: temps_en_secondes,nombre_bonbons,île,numéro_spawn_point
            # Îles disponibles: SMALL, MEDIUM, LARGE, EXTRA_LARGE
            # Spawn points: SMALL(1), MEDIUM(1-2), LARGE(1-3), EXTRA_LARGE(1-4)
            # Temps: 0-900 secondes (15 minutes)

            # Exemple: Apparition progressive
            60,5,EXTRA_LARGE,1
            120,3,LARGE,2
            180,2,MEDIUM,1
            240,4,EXTRA_LARGE,2
            300,3,LARGE,1
            360,2,SMALL,1
            420,5,EXTRA_LARGE,3
            480,4,LARGE,3
            540,3,MEDIUM,2
            600,3,EXTRA_LARGE,4
            660,2,LARGE,1
            720,4,MEDIUM,1
            780,2,EXTRA_LARGE,2
            840,1,SMALL,1
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
        if (parts.length != 4) {
            throw new IllegalArgumentException("Expected format: time,count,island,spawnpoint");
        }

        int timeSeconds = Integer.parseInt(parts[0].trim());
        int candyCount = Integer.parseInt(parts[1].trim());
        String islandStr = parts[2].trim().toUpperCase();
        int spawnPointNumber = Integer.parseInt(parts[3].trim());

        // Validate time range (0-900 seconds = 15 minutes)
        if (timeSeconds < 0 || timeSeconds > 900) {
            throw new IllegalArgumentException("Time must be between 0 and 900 seconds");
        }

        // Validate candy count
        if (candyCount <= 0 || candyCount > 50) {
            throw new IllegalArgumentException("Candy count must be between 1 and 50");
        }

        // Parse island type
        IslandType island;
        switch (islandStr) {
            case "SMALL" -> island = IslandType.SMALL;
            case "MEDIUM" -> island = IslandType.MEDIUM;
            case "LARGE" -> island = IslandType.LARGE;
            case "EXTRA_LARGE" -> island = IslandType.EXTRA_LARGE;
            default -> throw new IllegalArgumentException("Invalid island type: " + islandStr);
        }

        // Validate spawn point number for the island type
        if (spawnPointNumber < 1 || spawnPointNumber > island.getSpawnPointCount()) {
            throw new IllegalArgumentException(String.format("Spawn point %d invalid for island %s (max: %d)",
                spawnPointNumber, island.name(), island.getSpawnPointCount()));
        }

        return new CandySpawnEntry(timeSeconds, candyCount, island, spawnPointNumber);
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
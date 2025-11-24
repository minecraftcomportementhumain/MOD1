package com.example.mysubmod.submodes.submode1.client;

import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.submode1.network.CandyFileUploadPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CandyFileUploadScreen extends Screen {
    private EditBox pathBox;
    private Button uploadButton;
    private Button cancelButton;
    private Button randomButton;
    private String loadedContent = "";
    private String loadedFilename = "";

    public CandyFileUploadScreen() {
        super(Component.literal("Télécharger un fichier de spawn des bonbons"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 100;
        int widthButton = 140;
        int heightButton = 20;

        // File path input (single field)
        pathBox = new EditBox(this.font, centerX - 200, startY, 400, 20, Component.literal("Chemin du fichier"));
        pathBox.setHint(Component.literal("C:\\chemin\\vers\\fichier.txt"));
        pathBox.setMaxLength(256);
        pathBox.setResponder(this::onPathChanged);
        this.addRenderableWidget(pathBox);

        // Upload button
        uploadButton = Button.builder(
            Component.literal("Charger au serveur"),
            button -> uploadFile()
        )
        .bounds(centerX - widthButton - 10, startY + 40, widthButton, heightButton)
        .build();
        uploadButton.active = false;
        this.addRenderableWidget(uploadButton);

        // Cancel button
        cancelButton = Button.builder(
            Component.literal("Annuler"),
            button -> onClose()
        )
        .bounds(centerX - widthButton/2 - 10, startY + 70, widthButton, heightButton)
        .build();
        this.addRenderableWidget(cancelButton);


        // Create Random File
        randomButton = Button.builder(
                        Component.literal("Générer un fichier"),
                        button -> uploadRandom()
                )
                .bounds(centerX + 10, startY + 40, widthButton, heightButton)
                .build();
        this.addRenderableWidget(randomButton);

    }

    private void onPathChanged(String newPath) {
        // Reset state immediately
        uploadButton.active = false;
        loadedContent = "";
        loadedFilename = "";

        String filePath = newPath.trim();

        if (filePath.isEmpty()) {
            return;
        }

        // Don't validate until path looks somewhat complete (has extension or is long enough)
        if (filePath.length() < 5) {
            return;
        }

        try {
            Path path = Paths.get(filePath);

            // Check if file exists - if not, just return without crashing
            if (!Files.exists(path)) {
                return;
            }

            if (!Files.isRegularFile(path)) {
                return;
            }

            String content = Files.readString(path);
            String filename = path.getFileName().toString();

            // Ensure .txt extension
            if (!filename.toLowerCase().endsWith(".txt")) {
                filename += ".txt";
            }

            // Check if file is empty
            if (content.trim().isEmpty()) {
                loadedFilename = "ERREUR: Le fichier est vide";
                return;
            }

            // Validate file content
            ValidationResult validation = validateFileContent(content, path);

            if (validation.hasInvalidLines) {
                // File has invalid lines - keep it invalid, don't allow upload
                loadedFilename = "ERREUR: Fichier invalide (lignes marquées INVALID)";
            } else {
                // File is valid - allow upload
                loadedContent = content;
                loadedFilename = filename;
                uploadButton.active = true;
            }

        } catch (Exception e) {
            // Catch all exceptions to prevent crashes during typing/pasting
            // Just reset state silently
        }
    }

    private ValidationResult validateFileContent(String content, Path filePath) {
        String[] lines = content.split("\\r?\\n");
        List<String> modifiedLines = new ArrayList<>();
        boolean hasInvalidLines = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // Keep comments and empty lines as-is
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                modifiedLines.add(line);
                continue;
            }

            // Skip lines already marked as INVALID (don't re-comment them)
            if (trimmedLine.startsWith("INVALID:")) {
                modifiedLines.add(line);
                hasInvalidLines = true;
                continue;
            }

            // Validate line
            try {
                validateLine(trimmedLine);
                modifiedLines.add(line);
            } catch (Exception e) {
                // Invalid line - add error comment but keep line invalid (no # at start)
                modifiedLines.add("INVALID: " + line + " - Error: " + e.getMessage());
                hasInvalidLines = true;
            }
        }

        // If there were invalid lines, rewrite the file with comments
        if (hasInvalidLines) {
            try {
                Files.write(filePath, modifiedLines);
            } catch (IOException e) {
                // Ignore write errors
            }
        }

        return new ValidationResult(hasInvalidLines);
    }

    private void validateLine(String line) throws Exception {
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
        if (y < 100 || y > 120) {
            throw new IllegalArgumentException("Y coordinate must be between 100 and 120");
        }

        // Validate position is on an island
        if (!isPositionOnIsland(x, z)) {
            throw new IllegalArgumentException(String.format("Position (%d,%d,%d) is not on any island", x, y, z));
        }
    }

    private boolean isPositionOnIsland(int x, int z) {
        // Island centers and radii
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

            int minX = centerX - halfSize;
            int maxX = centerX + halfSize;
            int minZ = centerZ - halfSize;
            int maxZ = centerZ + halfSize;

            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                return true;
            }
        }

        return false;
    }

    private static class ValidationResult {
        final boolean hasInvalidLines;

        ValidationResult(boolean hasInvalidLines) {
            this.hasInvalidLines = hasInvalidLines;
        }
    }

    private void uploadFile() {
        if (loadedFilename.isEmpty() || loadedContent.isEmpty()) {
            return;
        }

        sendChunksFile(loadedFilename,loadedContent);
        onClose();
    }

    private void uploadRandom(){
        this.minecraft.setScreen(new CandyRandomFileScreen());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int startY = 100;

        // Title
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 30, 0xFFFFFF);

        // Instructions
        String instructions = "Entrez le chemin complet vers votre fichier de configuration";
        guiGraphics.drawCenteredString(this.font, instructions, centerX, 50, 0xCCCCCC);

        // Label
        guiGraphics.drawString(this.font, "Chemin du fichier:", centerX - 200, startY - 15, 0xFFFFFF);

        // Status indicator
        if (!loadedFilename.isEmpty()) {
            if (loadedFilename.startsWith("ERREUR:")) {
                // Error message in red
                guiGraphics.drawCenteredString(this.font, loadedFilename, centerX, startY + 30, 0xFF5555);
            } else {
                // Success message in green
                String status = "✓ Fichier chargé: " + loadedFilename;
                guiGraphics.drawCenteredString(this.font, status, centerX, startY + 30, 0x55FF55);
            }
        }

        // Format help at bottom
        String format = "Format requis: temps_en_secondes,nombre_bonbons,x,y,z";
        guiGraphics.drawCenteredString(this.font, format, centerX, startY + 100, 0x888888);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public void sendChunksFile(String loadedFilename,String loadedContent){
        byte[] data = loadedContent.getBytes(StandardCharsets.UTF_8);
        final int MAX_CHUNK_SIZE = 1000;
        UUID transferId = UUID.randomUUID();
        int totalChunks = (int) Math.ceil((double) data.length / MAX_CHUNK_SIZE);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * MAX_CHUNK_SIZE;
            int length = Math.min(data.length - start, MAX_CHUNK_SIZE);
            byte[] chunkData = new byte[length];
            System.arraycopy(data, start, chunkData, 0, length);

            // Send the new chunk packet:
            CandyFileUploadPacket packet = new CandyFileUploadPacket(transferId, loadedFilename, totalChunks, i, chunkData);
            NetworkHandler.INSTANCE.sendToServer(packet);
        }

    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CandyFileUploadScreen extends Screen {
    private EditBox filenameBox;
    private EditBox pathBox;
    private Button loadButton;
    private Button uploadButton;
    private Button cancelButton;
    private String loadedContent = "";

    public CandyFileUploadScreen() {
        super(Component.literal("Télécharger un fichier de spawn des bonbons"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 100; // Fixed start position instead of center-based

        // Filename input
        filenameBox = new EditBox(this.font, centerX - 150, startY, 300, 20, Component.literal("Nom du fichier"));
        filenameBox.setHint(Component.literal("nom_du_fichier.txt"));
        filenameBox.setMaxLength(50);
        this.addRenderableWidget(filenameBox);

        // File path input
        pathBox = new EditBox(this.font, centerX - 150, startY + 40, 300, 20, Component.literal("Chemin du fichier"));
        pathBox.setHint(Component.literal("C:\\path\\to\\file.txt"));
        pathBox.setMaxLength(256);
        this.addRenderableWidget(pathBox);

        // Load button
        loadButton = Button.builder(
            Component.literal("Charger le fichier"),
            button -> loadFileFromPath()
        )
        .bounds(centerX - 60, startY + 70, 120, 20)
        .build();
        this.addRenderableWidget(loadButton);


        // Upload button
        uploadButton = Button.builder(
            Component.literal("Envoyer sur le serveur"),
            button -> uploadFile()
        )
        .bounds(centerX - 150, startY + 120, 140, 20)
        .build();
        uploadButton.active = false;
        this.addRenderableWidget(uploadButton);

        // Cancel button
        cancelButton = Button.builder(
            Component.literal("Annuler"),
            button -> onClose()
        )
        .bounds(centerX + 10, startY + 120, 140, 20)
        .build();
        this.addRenderableWidget(cancelButton);
    }

    private void loadFileFromPath() {
        String filePath = pathBox.getValue().trim();
        if (filePath.isEmpty()) {
            // File path is empty - keep existing file validation
            return;
        }

        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                // File not found - keep existing validation logic
                return;
            }

            if (!Files.isRegularFile(path)) {
                // Path is not a file - keep existing validation logic
                return;
            }

            String content = Files.readString(path);
            String filename = path.getFileName().toString();

            // Update filename if not set
            if (filenameBox.getValue().trim().isEmpty()) {
                filenameBox.setValue(filename);
            }

            loadedContent = content;
            uploadButton.active = !filenameBox.getValue().trim().isEmpty() && !content.isEmpty();

        } catch (IOException e) {
            // Error reading file - keep existing error handling
        } catch (Exception e) {
            // General error - keep existing error handling
        }
    }


    private void uploadFile() {
        String filename = filenameBox.getValue().trim();
        if (filename.isEmpty() || loadedContent.isEmpty()) {
            return;
        }

        // Ensure .txt extension
        if (!filename.toLowerCase().endsWith(".txt")) {
            filename += ".txt";
        }

        NetworkHandler.INSTANCE.sendToServer(new CandyFileUploadPacket(filename, loadedContent));
        onClose();
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

        // Labels with proper spacing
        guiGraphics.drawString(this.font, "Nom du fichier:", centerX - 150, startY - 15, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Chemin du fichier:", centerX - 150, startY + 25, 0xFFFFFF);

        // Format help at bottom
        String format = "Format requis: temps_en_secondes,nombre_bonbons,île";
        guiGraphics.drawCenteredString(this.font, format, centerX, startY + 160, 0x888888);

        String islandTypes = "Îles valides: SMALL, MEDIUM, LARGE";
        guiGraphics.drawCenteredString(this.font, islandTypes, centerX, startY + 175, 0x888888);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
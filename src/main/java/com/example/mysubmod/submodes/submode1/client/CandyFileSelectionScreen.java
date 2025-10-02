package com.example.mysubmod.submodes.submode1.client;

import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.submode1.network.CandyFileSelectionPacket;
import com.example.mysubmod.submodes.submode1.network.CandyFileDeletePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CandyFileSelectionScreen extends Screen {
    private final List<String> availableFiles;
    private String selectedFile;
    private CycleButton<String> fileSelector;
    private Button confirmButton;
    private Button deleteButton;

    public CandyFileSelectionScreen(List<String> availableFiles) {
        super(Component.literal("Sélection du fichier de spawn des bonbons"));
        this.availableFiles = new ArrayList<>(availableFiles);
        this.selectedFile = availableFiles.isEmpty() ? null : availableFiles.get(0); // Auto-select first file
    }

    @Override
    protected void init() {
        super.init();

        // Clear existing widgets to prevent duplicates on resize
        this.clearWidgets();

        // Safety check for window dimensions
        if (this.width <= 0 || this.height <= 0) {
            return;
        }

        int centerX = this.width / 2;
        int startY = 120; // Fixed start position
        int buttonWidth = Math.min(300, this.width - 40); // Ensure dropdown fits in window
        int buttonHeight = 25;

        // File selection dropdown
        fileSelector = CycleButton.<String>builder(filename -> Component.literal(filename))
            .withValues(availableFiles)
            .withInitialValue(selectedFile)
            .create(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight,
                Component.literal("Fichier sélectionné"),
                (button, value) -> selectFile(value));
        this.addRenderableWidget(fileSelector);

        // Delete button (positioned below dropdown)
        deleteButton = Button.builder(
            Component.literal("🗑 Supprimer ce fichier"),
            button -> deleteSelectedFile()
        )
        .bounds(centerX - buttonWidth / 2, startY + 40, buttonWidth, buttonHeight)
        .build();
        this.addRenderableWidget(deleteButton);
        updateDeleteButtonState();

        // Confirm button - positioned below delete button
        int confirmButtonY = startY + 80; // Fixed spacing
        confirmButton = Button.builder(
            Component.literal("✓ Confirmer la sélection"),
            button -> confirmSelection()
        )
        .bounds(centerX - buttonWidth / 2, confirmButtonY, buttonWidth, buttonHeight + 5)
        .build();

        this.addRenderableWidget(confirmButton);

        // Update states
        updateDeleteButtonState();
    }

    private void selectFile(String filename) {
        this.selectedFile = filename;
        updateDeleteButtonState();
    }

    private void updateDeleteButtonState() {
        // Only enable delete button for non-default files
        if (deleteButton != null) {
            deleteButton.active = selectedFile != null && !"default.txt".equals(selectedFile);
        }
    }

    private void confirmSelection() {
        if (selectedFile != null) {
            NetworkHandler.INSTANCE.sendToServer(new CandyFileSelectionPacket(selectedFile));
            onClose();
        }
    }

    private void deleteSelectedFile() {
        if (selectedFile != null && !"default.txt".equals(selectedFile)) {
            NetworkHandler.INSTANCE.sendToServer(new CandyFileDeletePacket(selectedFile));
            // Remove from local list and refresh UI
            availableFiles.remove(selectedFile);
            if (!availableFiles.isEmpty()) {
                selectedFile = availableFiles.get(0); // Select first remaining file
            } else {
                selectedFile = null;
            }
            // Refresh the screen
            this.minecraft.setScreen(new CandyFileSelectionScreen(availableFiles));
        }
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;

        // Title - larger and more prominent
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 30, 0xFFFFFF);

        // Instructions - better spaced
        String instructions = "Sélectionnez un fichier de configuration pour l'apparition des bonbons";
        guiGraphics.drawCenteredString(this.font, instructions, centerX, 50, 0xCCCCCC);

        // File count info
        String fileCount = String.format("Fichiers disponibles: %d", availableFiles.size());
        guiGraphics.drawCenteredString(this.font, fileCount, centerX, 75, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
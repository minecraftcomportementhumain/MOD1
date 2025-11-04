package com.example.mysubmod.submodes.submodeParent.client;

import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.submodeParent.network.FileDeletePacket;
import com.example.mysubmod.submodes.submodeParent.network.FileListRequestPacket;
import com.example.mysubmod.submodes.submodeParent.network.FileSelectionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class FileSelectionScreen extends Screen {
    private final List<String> availableFiles;
    private CandyFileList fileList;
    private Button confirmButton;
    private Button deleteSelectedButton;
    private Button refreshButton;

    public FileSelectionScreen(List<String> availableFiles) {
        super(Component.literal("Sélection du fichier de spawn des bonbons"));
        this.availableFiles = new ArrayList<>(availableFiles);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int listWidth = Math.min(400, this.width - 40);
        int listHeight = this.height - 160;
        int listX = centerX - listWidth / 2;
        int listY = 60;

        // Create file list centered
        fileList = new CandyFileList(this.minecraft, listWidth, listHeight, listY, listY + listHeight, 25, availableFiles, listX);
        this.addWidget(fileList);

        int buttonY = listY + listHeight + 10;
        int buttonHeight = 20;
        int buttonSpacing = 5;

        // Row 1: Confirm and Delete Selected
        confirmButton = Button.builder(
            Component.literal("✓ Confirmer et lancer la partie"),
            button -> confirmSelection()
        ).bounds(listX, buttonY, (listWidth - buttonSpacing) / 2, buttonHeight).build();
        this.addRenderableWidget(confirmButton);

        deleteSelectedButton = Button.builder(
            Component.literal("🗑 Supprimer Sélectionné"),
            button -> deleteSelectedFile()
        ).bounds(listX + (listWidth + buttonSpacing) / 2, buttonY, (listWidth - buttonSpacing) / 2, buttonHeight).build();
        this.addRenderableWidget(deleteSelectedButton);

        // Row 2: Refresh and Close
        buttonY += buttonHeight + buttonSpacing;
        refreshButton = Button.builder(
            Component.literal("🔄 Actualiser"),
            button -> refreshFileList()
        ).bounds(listX, buttonY, (listWidth - buttonSpacing) / 2, buttonHeight).build();
        this.addRenderableWidget(refreshButton);

        Button closeButton = Button.builder(
            Component.literal("Fermer"),
            button -> this.onClose()
        ).bounds(listX + (listWidth + buttonSpacing) / 2, buttonY, (listWidth - buttonSpacing) / 2, buttonHeight).build();
        this.addRenderableWidget(closeButton);

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = fileList != null && fileList.getSelected() != null;
        boolean hasFiles = !availableFiles.isEmpty();

        if (confirmButton != null) confirmButton.active = hasSelection;

        // Can't delete default.txt
        if (deleteSelectedButton != null) {
            CandyFileList.CandyFileEntry selected = fileList != null ? fileList.getSelected() : null;
            deleteSelectedButton.active = hasSelection && selected != null && !"default.txt".equals(selected.getFileName());
        }
    }

    private void confirmSelection() {
        CandyFileList.CandyFileEntry selected = fileList.getSelected();
        if (selected != null) {
            NetworkHandler.INSTANCE.sendToServer(new FileSelectionPacket(selected.getFileName()));
            this.minecraft.player.sendSystemMessage(Component.literal("§aFichier sélectionné: " + selected.getFileName()));
            this.onClose();
        }
    }

    private void deleteSelectedFile() {
        CandyFileList.CandyFileEntry selected = fileList.getSelected();
        if (selected != null && !"default.txt".equals(selected.getFileName())) {
            NetworkHandler.INSTANCE.sendToServer(new FileDeletePacket(selected.getFileName()));
            this.minecraft.player.sendSystemMessage(Component.literal("§cSuppression de " + selected.getFileName() + "..."));
            availableFiles.remove(selected.getFileName());
            fileList.children().remove(selected);
            updateButtonStates();
        }
    }

    private void refreshFileList() {
        NetworkHandler.INSTANCE.sendToServer(new FileListRequestPacket());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        if (fileList != null) {
            fileList.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 20, 0xFFFFFF);

        String countText = availableFiles.size() + " fichier(s) disponible(s)";
        guiGraphics.drawCenteredString(this.font, Component.literal(countText), centerX, 40, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (fileList != null && fileList.mouseClicked(mouseX, mouseY, button)) {
            updateButtonStates();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Inner class for the scrollable list
    public static class CandyFileList extends ObjectSelectionList<CandyFileList.CandyFileEntry> {
        private final int leftPos;

        public CandyFileList(net.minecraft.client.Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight, List<String> files, int leftPos) {
            super(minecraft, width, height, top, bottom, itemHeight);
            this.leftPos = leftPos;
            for (String file : files) {
                this.addEntry(new CandyFileEntry(file));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 10;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.leftPos + this.width - 6;
        }

        @Override
        public int getRowLeft() {
            return this.leftPos;
        }

        public static class CandyFileEntry extends Entry<CandyFileEntry> {
            private final String fileName;
            private final Component displayName;

            public CandyFileEntry(String fileName) {
                this.fileName = fileName;
                // Add icon for default file
                String icon = "default.txt".equals(fileName) ? "📄" : "📁";
                this.displayName = Component.literal(icon + " " + fileName);
            }

            public String getFileName() {
                return fileName;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                             int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
                // Draw selection background
                if (isMouseOver) {
                    guiGraphics.fill(left, top, left + width, top + height, 0x80FFFFFF);
                }

                guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    displayName, left + 5, top + 5, 0xFFFFFF);
            }

            @Override
            public Component getNarration() {
                return displayName;
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    return true;
                }
                return false;
            }
        }
    }
}
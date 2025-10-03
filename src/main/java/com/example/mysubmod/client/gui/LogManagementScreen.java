package com.example.mysubmod.client.gui;

import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.network.LogDownloadPacket;
import com.example.mysubmod.network.LogDeletePacket;
import com.example.mysubmod.network.LogListRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class LogManagementScreen extends Screen {
    private final List<String> logFolders;
    private LogFolderList folderList;
    private Button downloadAllButton;
    private Button deleteAllButton;
    private Button downloadSelectedButton;
    private Button deleteSelectedButton;
    private Button refreshButton;

    public LogManagementScreen(List<String> logFolders) {
        super(Component.literal("Gestion des Logs"));
        this.logFolders = new ArrayList<>(logFolders);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int listWidth = Math.min(400, this.width - 40);
        int listHeight = this.height - 160;
        int listX = centerX - listWidth / 2;
        int listY = 60;

        // Create folder list
        folderList = new LogFolderList(this.minecraft, listWidth, listHeight, listY, listY + listHeight, 25, logFolders);
        this.addWidget(folderList);

        int buttonY = listY + listHeight + 10;
        int buttonWidth = 95;
        int buttonHeight = 20;
        int buttonSpacing = 5;

        // Row 1: Download All and Delete All
        downloadAllButton = Button.builder(
            Component.literal("⬇ Télécharger Tous"),
            button -> downloadAllLogs()
        ).bounds(listX, buttonY, (listWidth - buttonSpacing) / 2, buttonHeight).build();
        this.addRenderableWidget(downloadAllButton);

        deleteAllButton = Button.builder(
            Component.literal("🗑 Supprimer Tous"),
            button -> deleteAllLogs()
        ).bounds(listX + (listWidth + buttonSpacing) / 2, buttonY, (listWidth - buttonSpacing) / 2, buttonHeight).build();
        this.addRenderableWidget(deleteAllButton);

        // Row 2: Download Selected and Delete Selected
        buttonY += buttonHeight + buttonSpacing;
        downloadSelectedButton = Button.builder(
            Component.literal("⬇ Télécharger Sélectionné"),
            button -> downloadSelectedLog()
        ).bounds(listX, buttonY, (listWidth - buttonSpacing) / 2, buttonHeight).build();
        this.addRenderableWidget(downloadSelectedButton);

        deleteSelectedButton = Button.builder(
            Component.literal("🗑 Supprimer Sélectionné"),
            button -> deleteSelectedLog()
        ).bounds(listX + (listWidth + buttonSpacing) / 2, buttonY, (listWidth - buttonSpacing) / 2, buttonHeight).build();
        this.addRenderableWidget(deleteSelectedButton);

        // Row 3: Refresh and Close
        buttonY += buttonHeight + buttonSpacing + 5;
        refreshButton = Button.builder(
            Component.literal("🔄 Actualiser"),
            button -> refreshLogList()
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
        boolean hasSelection = folderList != null && folderList.getSelected() != null;
        boolean hasLogs = !logFolders.isEmpty();

        if (downloadSelectedButton != null) downloadSelectedButton.active = hasSelection;
        if (deleteSelectedButton != null) deleteSelectedButton.active = hasSelection;
        if (downloadAllButton != null) downloadAllButton.active = hasLogs;
        if (deleteAllButton != null) deleteAllButton.active = hasLogs;
    }

    private void downloadAllLogs() {
        NetworkHandler.INSTANCE.sendToServer(new LogDownloadPacket(null, true));
        this.minecraft.player.sendSystemMessage(Component.literal("§aTéléchargement de tous les logs en cours..."));
    }

    private void deleteAllLogs() {
        NetworkHandler.INSTANCE.sendToServer(new LogDeletePacket(null, true));
        this.minecraft.player.sendSystemMessage(Component.literal("§cSuppression de tous les logs..."));
        this.onClose();
    }

    private void downloadSelectedLog() {
        LogFolderList.LogFolderEntry selected = folderList.getSelected();
        if (selected != null) {
            NetworkHandler.INSTANCE.sendToServer(new LogDownloadPacket(selected.getFolderName(), false));
            this.minecraft.player.sendSystemMessage(Component.literal("§aTéléchargement de " + selected.getFolderName() + "..."));
        }
    }

    private void deleteSelectedLog() {
        LogFolderList.LogFolderEntry selected = folderList.getSelected();
        if (selected != null) {
            NetworkHandler.INSTANCE.sendToServer(new LogDeletePacket(selected.getFolderName(), false));
            this.minecraft.player.sendSystemMessage(Component.literal("§cSuppression de " + selected.getFolderName() + "..."));
            logFolders.remove(selected.getFolderName());
            folderList.children().remove(selected);
            updateButtonStates();
        }
    }

    private void refreshLogList() {
        NetworkHandler.INSTANCE.sendToServer(new LogListRequestPacket());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        if (folderList != null) {
            folderList.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 20, 0xFFFFFF);

        String countText = logFolders.size() + " dossier(s) de logs disponible(s)";
        guiGraphics.drawCenteredString(this.font, Component.literal(countText), centerX, 40, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (folderList != null && folderList.mouseClicked(mouseX, mouseY, button)) {
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
    public static class LogFolderList extends ObjectSelectionList<LogFolderList.LogFolderEntry> {
        public LogFolderList(net.minecraft.client.Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight, List<String> folders) {
            super(minecraft, width, height, top, bottom, itemHeight);
            for (String folder : folders) {
                this.addEntry(new LogFolderEntry(folder));
            }
        }

        @Override
        public int getRowWidth() {
            return 396;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width - 6;
        }

        public static class LogFolderEntry extends Entry<LogFolderEntry> {
            private final String folderName;
            private final Component displayName;

            public LogFolderEntry(String folderName) {
                this.folderName = folderName;
                this.displayName = Component.literal("📁 " + folderName);
            }

            public String getFolderName() {
                return folderName;
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

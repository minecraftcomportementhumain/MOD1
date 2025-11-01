package com.example.mysubmod.client.gui;

import com.example.mysubmod.client.ClientSubModeManager;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.network.SubModeRequestPacket;
import com.example.mysubmod.network.LogListRequestPacket;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.submodeParent.client.FileUploadScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SubModeControlScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 25;

    private final int playerCount;

    public SubModeControlScreen(int playerCount) {
        super(Component.literal("Contrôle des Sous-modes"));
        this.playerCount = playerCount;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 20; // Déplacé vers le bas pour faire de la place pour le compteur

        addRenderableWidget(Button.builder(
            Component.literal("Salle d'attente"),
            button -> sendSubModeRequest(SubMode.WAITING_ROOM)
        ).bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // Sous-mode 1 with upload and log buttons
        addRenderableWidget(Button.builder(
            Component.literal("Sous-mode 1"),
            button -> sendSubModeRequest(SubMode.SUB_MODE_1)
        ).bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING, BUTTON_WIDTH - 80, BUTTON_HEIGHT).build());

        // Buttons next to Sous-mode 1 (only for admins)
        if (ClientSubModeManager.isAdmin()) {
            // Log management button
            addRenderableWidget(Button.builder(
                Component.literal("📊"),
                button -> openLogManagementScreen(SubMode.SUB_MODE_1)
            ).bounds(centerX + BUTTON_WIDTH / 2 - 55, startY + BUTTON_SPACING, 25, BUTTON_HEIGHT)
            .tooltip(Tooltip.create(Component.literal("Gestion des logs")))
            .build());

            // File upload button
            addRenderableWidget(Button.builder(
                Component.literal("📁"),
                button -> openCandyFileUploadScreen(SubMode.SUB_MODE_1)
            ).bounds(centerX + BUTTON_WIDTH / 2 - 25, startY + BUTTON_SPACING, 25, BUTTON_HEIGHT)
            .tooltip(Tooltip.create(Component.literal("Charger un fichier de spawn de bonbons depuis le disque")))
            .build());
        }

        // Sous-mode 2 with upload and log buttons
        addRenderableWidget(Button.builder(
            Component.literal("Sous-mode 2"),
            button -> sendSubModeRequest(SubMode.SUB_MODE_2)
        ).bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 2, BUTTON_WIDTH - 80, BUTTON_HEIGHT).build());

        // Buttons next to Sous-mode 2 (only for admins)
        if (ClientSubModeManager.isAdmin()) {
            // Log management button for SubMode2
            addRenderableWidget(Button.builder(
                Component.literal("📊"),
                button -> openLogManagementScreen(SubMode.SUB_MODE_2)
            ).bounds(centerX + BUTTON_WIDTH / 2 - 55, startY + BUTTON_SPACING * 2, 25, BUTTON_HEIGHT)
            .tooltip(Tooltip.create(Component.literal("Gestion des logs SubMode2")))
            .build());

            // File upload button for SubMode2
            addRenderableWidget(Button.builder(
                Component.literal("📁"),
                button -> openCandyFileUploadScreen(SubMode.SUB_MODE_2)
            ).bounds(centerX + BUTTON_WIDTH / 2 - 25, startY + BUTTON_SPACING * 2, 25, BUTTON_HEIGHT)
            .tooltip(Tooltip.create(Component.literal("Charger un fichier de spawn de bonbons SubMode2")))
            .build());
        }

        addRenderableWidget(Button.builder(
            Component.literal("Fermer"),
            button -> this.onClose()
        ).bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 3 + 10, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void sendSubModeRequest(SubMode mode) {
        if (ClientSubModeManager.isAdmin()) {
            NetworkHandler.INSTANCE.sendToServer(new SubModeRequestPacket(mode));
            this.onClose();
        }
    }

    private void openCandyFileUploadScreen(SubMode submode) {
        this.minecraft.setScreen(new FileUploadScreen(submode));
    }

    private void openLogManagementScreen(SubMode submode) {
        // Request log list from server (SubMode1)
        NetworkHandler.INSTANCE.sendToServer(new LogListRequestPacket());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int titleY = this.height / 2 - 80;

        if (!ClientSubModeManager.isAdmin()) {
            String adminText = "Seuls les administrateurs peuvent changer de mode";
            guiGraphics.drawCenteredString(this.font, Component.literal(adminText), centerX, titleY - 20, 0xFF5555);
        }

        guiGraphics.drawCenteredString(this.font, this.title, centerX, titleY, 0xFFFFFF);

        String currentModeText = "Mode actuel: " + ClientSubModeManager.getCurrentMode().getDisplayName();
        guiGraphics.drawCenteredString(this.font, Component.literal(currentModeText), centerX, titleY + 20, 0xAAAAAA);

        // Display player count (same as TAB - excludes unauthenticated and admins)
        String playerCountText = "Joueurs connectés: " + playerCount;
        guiGraphics.drawCenteredString(this.font, Component.literal(playerCountText), centerX, titleY + 35, 0x00FF00);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
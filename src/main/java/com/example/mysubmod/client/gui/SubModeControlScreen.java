package com.example.mysubmod.client.gui;

import com.example.mysubmod.client.ClientSubModeManager;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.network.SubModeRequestPacket;
import com.example.mysubmod.submodes.SubMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SubModeControlScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 25;

    public SubModeControlScreen() {
        super(Component.literal("Contrôle des Sous-modes"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 40;

        addRenderableWidget(Button.builder(
            Component.literal("Salle d'attente"),
            button -> sendSubModeRequest(SubMode.WAITING_ROOM)
        ).bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
            Component.literal("Sous-mode 1"),
            button -> sendSubModeRequest(SubMode.SUB_MODE_1)
        ).bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
            Component.literal("Sous-mode 2"),
            button -> sendSubModeRequest(SubMode.SUB_MODE_2)
        ).bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 2, BUTTON_WIDTH, BUTTON_HEIGHT).build());

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

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
package com.example.mysubmod.submodes.submode1.client;

import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.submode1.islands.IslandType;
import com.example.mysubmod.submodes.submode1.network.IslandChoicePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IslandSelectionScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 25;

    private int timeLeft;
    private int tickCounter = 0;

    public IslandSelectionScreen(int initialTimeLeft) {
        super(Component.literal("Sélection d'Île"));
        this.timeLeft = initialTimeLeft;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 50;

        addRenderableWidget(Button.builder(
            Component.literal(IslandType.SMALL.getDisplayName()),
            button -> selectIsland(IslandType.SMALL)
        ).bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
            Component.literal(IslandType.MEDIUM.getDisplayName()),
            button -> selectIsland(IslandType.MEDIUM)
        ).bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
            Component.literal(IslandType.LARGE.getDisplayName()),
            button -> selectIsland(IslandType.LARGE)
        ).bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 2, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
            Component.literal(IslandType.EXTRA_LARGE.getDisplayName()),
            button -> selectIsland(IslandType.EXTRA_LARGE)
        ).bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 3, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void selectIsland(IslandType island) {
        NetworkHandler.INSTANCE.sendToServer(new IslandChoicePacket(island));
        this.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        tickCounter++;

        if (tickCounter >= 20) { // Every second
            tickCounter = 0;
            timeLeft--;

            if (timeLeft <= 0) {
                this.onClose(); // Auto-close when time runs out
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int titleY = this.height / 2 - 80;

        guiGraphics.drawCenteredString(this.font, this.title, centerX, titleY, 0xFFFFFF);

        String timeText = "Temps restant: " + timeLeft + "s";
        int timeColor = timeLeft <= 10 ? 0xFF5555 : 0xFFFF55;
        guiGraphics.drawCenteredString(this.font, Component.literal(timeText), centerX, titleY+10, timeColor);

        String instructionText = "Choisissez votre île de départ";
        guiGraphics.drawCenteredString(this.font, Component.literal(instructionText), centerX, titleY + 20, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Prevent closing with Escape
    }
}
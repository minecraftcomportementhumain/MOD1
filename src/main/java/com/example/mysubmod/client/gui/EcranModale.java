package com.example.mysubmod.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Fenêtre modale générique affichant un message avec un bouton OK.
 * Revient à l'écran précédent à la fermeture.
 */
@OnlyIn(Dist.CLIENT)
public class EcranModale extends Screen {
    private final List<String> lignes;
    private final Screen ecranPrecedent;

    public EcranModale(String titre, List<String> lignes, Screen ecranPrecedent) {
        super(Component.literal(titre));
        this.lignes = lignes;
        this.ecranPrecedent = ecranPrecedent;
    }

    public EcranModale(String titre, String ligne, Screen ecranPrecedent) {
        this(titre, List.of(ligne), ecranPrecedent);
    }

    @Override
    protected void init() {
        super.init();
        int centreX = this.width / 2;
        int centreY = this.height / 2;

        addRenderableWidget(Button.builder(
            Component.literal("OK"),
            bouton -> this.onClose()
        ).bounds(centreX - 40, centreY + 20 + lignes.size() * 12, 80, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(ecranPrecedent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        int centreX = this.width / 2;
        int centreY = this.height / 2;

        int largeurModal = Math.min(380, this.width - 40);
        int hauteurModal = 70 + lignes.size() * 12;
        int x0 = centreX - largeurModal / 2;
        int y0 = centreY - 30;

        guiGraphics.fill(x0, y0, x0 + largeurModal, y0 + hauteurModal + 10, 0xFF202020);

        guiGraphics.drawCenteredString(this.font, "§l" + this.title.getString(), centreX, y0 + 10, 0xFFFFFF);
        int yLigne = y0 + 30;
        for (String ligne : lignes) {
            guiGraphics.drawCenteredString(this.font, ligne, centreX, yLigne, 0xDDDDDD);
            yLigne += 12;
        }

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

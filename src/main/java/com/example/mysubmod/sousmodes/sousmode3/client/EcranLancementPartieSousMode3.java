package com.example.mysubmod.sousmodes.sousmode3.client;

import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetLancerPartieSousMode3;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Menu admin N des parties sur carte : n'affiche que le bouton de lancement.
 * Sous-mode 3 : décompte de 10 secondes puis téléportation au point d'apparition.
 * Sous-modes 1 et 2 sur carte : lance la phase de sélection de la zone de départ.
 */
@OnlyIn(Dist.CLIENT)
public class EcranLancementPartieSousMode3 extends Screen {
    private static final int LARGEUR_BOUTON = 200;
    private static final int HAUTEUR_BOUTON = 20;

    public EcranLancementPartieSousMode3() {
        super(Component.literal(com.example.mysubmod.client.GestionnaireSubModeClient
            .obtenirModeActuel().obtenirNomAffichage()));
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int centreY = this.height / 2;

        addRenderableWidget(Button.builder(
            Component.literal("§aLancer la partie"),
            bouton -> {
                GestionnaireReseau.INSTANCE.sendToServer(new PaquetLancerPartieSousMode3());
                this.onClose();
            }
        ).bounds(centreX - LARGEUR_BOUTON / 2, centreY - 10, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());

        addRenderableWidget(Button.builder(
            Component.literal("Fermer"),
            bouton -> this.onClose()
        ).bounds(centreX - LARGEUR_BOUTON / 2, centreY + 20, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        int centreX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centreX, this.height / 2 - 50, 0xFFFFFF);
        String sousTitre = com.example.mysubmod.client.GestionnaireSubModeClient.obtenirModeActuel()
            == com.example.mysubmod.sousmodes.SousMode.SOUS_MODE_3
            ? "§7Un décompte de 10 secondes précède la téléportation des joueurs"
            : "§7Les joueurs choisiront ensuite leur zone de départ (30 secondes)";
        guiGraphics.drawCenteredString(this.font, sousTitre, centreX, this.height / 2 - 35, 0xAAAAAA);

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.example.mysubmod.cartes.client;

import com.example.mysubmod.cartes.reseau.PaquetDemandeEditeurCarte;
import com.example.mysubmod.cartes.reseau.PaquetDemandeListeCartes;
import com.example.mysubmod.cartes.reseau.PaquetListeCartes;
import com.example.mysubmod.reseau.GestionnaireReseau;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Menu « Cartes et parcelles » (accessible depuis le menu M des admins) :
 * - Liste des cartes (sélection / suppression)
 * - Outil de création de carte
 */
@OnlyIn(Dist.CLIENT)
public class EcranCartesEtParcelles extends Screen {
    private static final int LARGEUR_BOUTON = 200;
    private static final int HAUTEUR_BOUTON = 20;
    private static final int ESPACEMENT_BOUTON = 25;

    private final String carteActive;

    public EcranCartesEtParcelles(String carteActive) {
        super(Component.literal("Cartes et parcelles"));
        this.carteActive = carteActive;
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int debutY = this.height / 2 - 30;

        addRenderableWidget(Button.builder(
            Component.literal("Liste des cartes"),
            bouton -> GestionnaireReseau.INSTANCE.sendToServer(
                new PaquetDemandeListeCartes(PaquetListeCartes.BUT_LISTE_SELECTION))
        ).bounds(centreX - LARGEUR_BOUTON / 2, debutY, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());

        addRenderableWidget(Button.builder(
            Component.literal("Outil de création de carte"),
            bouton -> GestionnaireReseau.INSTANCE.sendToServer(new PaquetDemandeEditeurCarte())
        ).bounds(centreX - LARGEUR_BOUTON / 2, debutY + ESPACEMENT_BOUTON, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());

        addRenderableWidget(Button.builder(
            Component.literal("Fermer"),
            bouton -> this.onClose()
        ).bounds(centreX - LARGEUR_BOUTON / 2, debutY + ESPACEMENT_BOUTON * 2 + 10, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        int centreX = this.width / 2;
        int titreY = this.height / 2 - 70;

        guiGraphics.drawCenteredString(this.font, this.title, centreX, titreY, 0xFFFFFF);

        String texteCarte = carteActive == null || carteActive.isEmpty()
            ? "§7Carte active : §cAucune"
            : "§7Carte active : §a" + carteActive;
        guiGraphics.drawCenteredString(this.font, Component.literal(texteCarte), centreX, titreY + 15, 0xFFFFFF);

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

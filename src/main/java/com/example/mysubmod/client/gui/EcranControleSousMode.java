package com.example.mysubmod.client.gui;

import com.example.mysubmod.client.GestionnaireSubModeClient;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.reseau.PaquetDemandeSousMode;
import com.example.mysubmod.sousmodes.SousMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class EcranControleSousMode extends Screen {
    private static final int LARGEUR_BOUTON = 200;
    private static final int HAUTEUR_BOUTON = 20;
    private static final int ESPACEMENT_BOUTON = 25;

    private final int nombreJoueurs;
    private final String carteActive;

    public EcranControleSousMode(int nombreJoueurs, String carteActive) {
        super(Component.literal("Contrôle des Sous-modes"));
        this.nombreJoueurs = nombreJoueurs;
        this.carteActive = carteActive != null ? carteActive : "";
    }

    private boolean aCarteActive() {
        return !carteActive.isEmpty();
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int débutY = this.height / 2 - 45;

        addRenderableWidget(Button.builder(
            Component.literal("Salle d'attente"),
            bouton -> envoyerRequêteSousMode(SousMode.SALLE_ATTENTE)
        ).bounds(centreX - LARGEUR_BOUTON / 2, débutY, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());

        // Sous-mode 3 avec bouton de logs (les conditions de partie se règlent au menu N,
        // les bonbons proviennent de la carte sélectionnée)
        addRenderableWidget(Button.builder(
            Component.literal("Sous-mode 3"),
            bouton -> envoyerRequêteSousMode(SousMode.SOUS_MODE_3)
        ).bounds(centreX - LARGEUR_BOUTON / 2, débutY + ESPACEMENT_BOUTON, LARGEUR_BOUTON - 80, HAUTEUR_BOUTON).build());

        if (GestionnaireSubModeClient.estAdmin()) {
            addRenderableWidget(Button.builder(
                Component.literal("📊"),
                bouton -> ouvrirÉcranGestionLogsSousMode3()
            ).bounds(centreX + LARGEUR_BOUTON / 2 - 55, débutY + ESPACEMENT_BOUTON, 25, HAUTEUR_BOUTON)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Gestion des journaux Sous-mode 3")))
            .build());
        }

        // Cartes et parcelles (admins) : liste des cartes + outil de création de carte
        if (GestionnaireSubModeClient.estAdmin()) {
            addRenderableWidget(Button.builder(
                Component.literal("Cartes et parcelles"),
                bouton -> this.minecraft.setScreen(
                    new com.example.mysubmod.cartes.client.EcranCartesEtParcelles(carteActive))
            ).bounds(centreX - LARGEUR_BOUTON / 2, débutY + ESPACEMENT_BOUTON * 2, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());
        }

        addRenderableWidget(Button.builder(
            Component.literal("Fermer"),
            bouton -> this.onClose()
        ).bounds(centreX - LARGEUR_BOUTON / 2, débutY + ESPACEMENT_BOUTON * 3 + 10, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());
    }

    private void envoyerRequêteSousMode(SousMode mode) {
        if (!GestionnaireSubModeClient.estAdmin()) {
            return;
        }

        // Le Sous-mode 3 exige une carte sélectionnée
        if (mode == SousMode.SOUS_MODE_3 && !aCarteActive()) {
            this.minecraft.setScreen(new EcranModale("Aucune carte sélectionnée",
                List.of("Aucune carte sélectionnée.", "Veuillez sélectionner une carte pour lancer le Sous-mode 3."),
                this));
            return;
        }

        GestionnaireReseau.INSTANCE.sendToServer(new PaquetDemandeSousMode(mode));
        this.onClose();
    }

    private void ouvrirÉcranGestionLogsSousMode3() {
        // Demander la liste des logs au serveur (Sous-mode 3)
        GestionnaireReseau.INSTANCE.sendToServer(
            new com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetDemandeListeLogsSousMode3());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        int centreX = this.width / 2;
        int titreY = this.height / 2 - 110;

        if (!GestionnaireSubModeClient.estAdmin()) {
            String texteAdmin = "Seuls les administrateurs peuvent changer de mode";
            guiGraphics.drawCenteredString(this.font, Component.literal(texteAdmin), centreX, titreY - 15, 0xFF5555);
        }

        guiGraphics.drawCenteredString(this.font, this.title, centreX, titreY, 0xFFFFFF);

        String texteModeActuel = "Mode actuel: " + GestionnaireSubModeClient.obtenirModeActuel().obtenirNomAffichage();
        guiGraphics.drawCenteredString(this.font, Component.literal(texteModeActuel), centreX, titreY + 15, 0xAAAAAA);

        // Afficher le nombre de joueurs (même que TAB - exclut non authentifiés et admins)
        String texteNombreJoueurs = "Joueurs connectés: " + nombreJoueurs;
        guiGraphics.drawCenteredString(this.font, Component.literal(texteNombreJoueurs), centreX, titreY + 28, 0x00FF00);

        // Carte active du système de cartes
        String texteCarte = aCarteActive()
            ? "Carte active : §a" + carteActive
            : "Carte active : §cAucune";
        guiGraphics.drawCenteredString(this.font, Component.literal(texteCarte), centreX, titreY + 41, 0xFFFFFF);

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

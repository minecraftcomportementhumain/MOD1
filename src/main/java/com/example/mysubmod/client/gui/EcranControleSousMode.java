package com.example.mysubmod.client.gui;

import com.example.mysubmod.client.GestionnaireSubModeClient;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.reseau.PaquetDemandeSousMode;
import com.example.mysubmod.reseau.PaquetDemandeListeLogs;
import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.sousmode1.client.EcranTeleversementFichierBonbons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class EcranControleSousMode extends Screen {
    private static final int LARGEUR_BOUTON = 200;
    private static final int HAUTEUR_BOUTON = 20;
    private static final int ESPACEMENT_BOUTON = 25;

    private final int nombreJoueurs;

    public EcranControleSousMode(int nombreJoueurs) {
        super(Component.literal("Contrôle des Sous-modes"));
        this.nombreJoueurs = nombreJoueurs;
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int débutY = this.height / 2 - 20; // Déplacé vers le bas pour faire de la place pour le compteur

        addRenderableWidget(Button.builder(
            Component.literal("Salle d'attente"),
            bouton -> envoyerRequêteSousMode(SousMode.SALLE_ATTENTE)
        ).bounds(centreX - LARGEUR_BOUTON / 2, débutY, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());

        // Sous-mode 1 avec boutons d'upload et de logs
        addRenderableWidget(Button.builder(
            Component.literal("Sous-mode 1"),
            bouton -> envoyerRequêteSousMode(SousMode.SOUS_MODE_1)
        ).bounds(centreX - LARGEUR_BOUTON / 2, débutY + ESPACEMENT_BOUTON, LARGEUR_BOUTON - 80, HAUTEUR_BOUTON).build());

        // Boutons à côté du Sous-mode 1 (seulement pour les admins)
        if (GestionnaireSubModeClient.estAdmin()) {
            // Bouton de gestion des logs
            addRenderableWidget(Button.builder(
                Component.literal("📊"),
                bouton -> ouvrirÉcranGestionLogs()
            ).bounds(centreX + LARGEUR_BOUTON / 2 - 55, débutY + ESPACEMENT_BOUTON, 25, HAUTEUR_BOUTON)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Gestion des journaux Sous-mode 1")))
            .build());

            // Bouton d'upload de fichier
            addRenderableWidget(Button.builder(
                Component.literal("📁"),
                bouton -> ouvrirÉcranUploadFichierBonbons()
            ).bounds(centreX + LARGEUR_BOUTON / 2 - 25, débutY + ESPACEMENT_BOUTON, 25, HAUTEUR_BOUTON)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Charger un fichier d'apparition de bonbons depuis le disque")))
            .build());
        }

        // Sous-mode 2 avec boutons d'upload et de logs
        addRenderableWidget(Button.builder(
            Component.literal("Sous-mode 2"),
            bouton -> envoyerRequêteSousMode(SousMode.SOUS_MODE_2)
        ).bounds(centreX - LARGEUR_BOUTON / 2, débutY + ESPACEMENT_BOUTON * 2, LARGEUR_BOUTON - 80, HAUTEUR_BOUTON).build());

        // Boutons à côté du Sous-mode 2 (seulement pour les admins)
        if (GestionnaireSubModeClient.estAdmin()) {
            // Bouton de gestion des logs pour Sous-mode 2
            addRenderableWidget(Button.builder(
                Component.literal("📊"),
                bouton -> ouvrirÉcranGestionLogsSousMode2()
            ).bounds(centreX + LARGEUR_BOUTON / 2 - 55, débutY + ESPACEMENT_BOUTON * 2, 25, HAUTEUR_BOUTON)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Gestion des journaux Sous-mode 2")))
            .build());

            // Bouton d'upload de fichier pour Sous-mode 2
            addRenderableWidget(Button.builder(
                Component.literal("📁"),
                bouton -> ouvrirÉcranUploadFichierBonbonsSousMode2()
            ).bounds(centreX + LARGEUR_BOUTON / 2 - 25, débutY + ESPACEMENT_BOUTON * 2, 25, HAUTEUR_BOUTON)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Charger un fichier d'apparition de bonbons Sous-mode 2")))
            .build());
        }

        addRenderableWidget(Button.builder(
            Component.literal("Fermer"),
            bouton -> this.onClose()
        ).bounds(centreX - LARGEUR_BOUTON / 2, débutY + ESPACEMENT_BOUTON * 3 + 10, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());
    }

    private void envoyerRequêteSousMode(SousMode mode) {
        if (GestionnaireSubModeClient.estAdmin()) {
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetDemandeSousMode(mode));
            this.onClose();
        }
    }

    private void ouvrirÉcranUploadFichierBonbons() {
        this.minecraft.setScreen(new EcranTeleversementFichierBonbons());
    }

    private void ouvrirÉcranUploadFichierBonbonsSousMode2() {
        this.minecraft.setScreen(new com.example.mysubmod.sousmodes.sousmode2.client.EcranTeleversementFichierBonbons());
    }

    private void ouvrirÉcranGestionLogs() {
        // Demander la liste des logs au serveur (Sous-mode 1)
        GestionnaireReseau.INSTANCE.sendToServer(new PaquetDemandeListeLogs());
    }

    private void ouvrirÉcranGestionLogsSousMode2() {
        // Demander la liste des logs au serveur (Sous-mode 2)
        GestionnaireReseau.INSTANCE.sendToServer(new com.example.mysubmod.reseau.PaquetDemandeListeLogsSousMode2());
    }


    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        int centreX = this.width / 2;
        int titreY = this.height / 2 - 80;

        if (!GestionnaireSubModeClient.estAdmin()) {
            String texteAdmin = "Seuls les administrateurs peuvent changer de mode";
            guiGraphics.drawCenteredString(this.font, Component.literal(texteAdmin), centreX, titreY - 20, 0xFF5555);
        }

        guiGraphics.drawCenteredString(this.font, this.title, centreX, titreY, 0xFFFFFF);

        String texteModeActuel = "Mode actuel: " + GestionnaireSubModeClient.obtenirModeActuel().obtenirNomAffichage();
        guiGraphics.drawCenteredString(this.font, Component.literal(texteModeActuel), centreX, titreY + 20, 0xAAAAAA);

        // Afficher le nombre de joueurs (même que TAB - exclut non authentifiés et admins)
        String texteNombreJoueurs = "Joueurs connectés: " + nombreJoueurs;
        guiGraphics.drawCenteredString(this.font, Component.literal(texteNombreJoueurs), centreX, titreY + 35, 0x00FF00);

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.example.mysubmod.client.gui;

import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.reseau.PaquetTelechargementLogs;
import com.example.mysubmod.reseau.PaquetSuppressionLogs;
import com.example.mysubmod.reseau.PaquetDemandeListeLogs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class EcranGestionLogs extends Screen {
    private final List<String> dossiersLog;
    private ListeDossiersLog listeDossiers;
    private Button boutonTéléchargerTout;
    private Button boutonSupprimerTout;
    private Button boutonTéléchargerSélectionné;
    private Button boutonSupprimerSélectionné;
    private Button boutonActualiser;

    public EcranGestionLogs(List<String> dossiersLog) {
        super(Component.literal("Gestion des Journaux"));
        this.dossiersLog = new ArrayList<>(dossiersLog);
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int largeurListe = Math.min(400, this.width - 40);
        int hauteurListe = this.height - 160;
        int xListe = centreX - largeurListe / 2;
        int yListe = 60;

        // Créer la liste de dossiers centrée
        listeDossiers = new ListeDossiersLog(this.minecraft, largeurListe, hauteurListe, yListe, yListe + hauteurListe, 25, dossiersLog, xListe);
        this.addWidget(listeDossiers);

        int yBouton = yListe + hauteurListe + 10;
        int largeurBouton = 95;
        int hauteurBouton = 20;
        int espacementBouton = 5;

        // Rangée 1: Télécharger Tous et Supprimer Tous
        boutonTéléchargerTout = Button.builder(
            Component.literal("⬇ Télécharger Tous"),
            bouton -> téléchargerTousLesLogs()
        ).bounds(xListe, yBouton, (largeurListe - espacementBouton) / 2, hauteurBouton).build();
        this.addRenderableWidget(boutonTéléchargerTout);

        boutonSupprimerTout = Button.builder(
            Component.literal("🗑 Supprimer Tous"),
            bouton -> supprimerTousLesLogs()
        ).bounds(xListe + (largeurListe + espacementBouton) / 2, yBouton, (largeurListe - espacementBouton) / 2, hauteurBouton).build();
        this.addRenderableWidget(boutonSupprimerTout);

        // Rangée 2: Télécharger Sélectionné et Supprimer Sélectionné
        yBouton += hauteurBouton + espacementBouton;
        boutonTéléchargerSélectionné = Button.builder(
            Component.literal("⬇ Télécharger Sélectionné"),
            bouton -> téléchargerLogSélectionné()
        ).bounds(xListe, yBouton, (largeurListe - espacementBouton) / 2, hauteurBouton).build();
        this.addRenderableWidget(boutonTéléchargerSélectionné);

        boutonSupprimerSélectionné = Button.builder(
            Component.literal("🗑 Supprimer Sélectionné"),
            bouton -> supprimerLogSélectionné()
        ).bounds(xListe + (largeurListe + espacementBouton) / 2, yBouton, (largeurListe - espacementBouton) / 2, hauteurBouton).build();
        this.addRenderableWidget(boutonSupprimerSélectionné);

        // Rangée 3: Actualiser et Fermer
        yBouton += hauteurBouton + espacementBouton + 5;
        boutonActualiser = Button.builder(
            Component.literal("🔄 Actualiser"),
            bouton -> actualiserListeLogs()
        ).bounds(xListe, yBouton, (largeurListe - espacementBouton) / 2, hauteurBouton).build();
        this.addRenderableWidget(boutonActualiser);

        Button boutonFermer = Button.builder(
            Component.literal("Fermer"),
            bouton -> this.onClose()
        ).bounds(xListe + (largeurListe + espacementBouton) / 2, yBouton, (largeurListe - espacementBouton) / 2, hauteurBouton).build();
        this.addRenderableWidget(boutonFermer);

        mettreÀJourÉtatsBoutons();
    }

    private void mettreÀJourÉtatsBoutons() {
        boolean aSélection = listeDossiers != null && listeDossiers.getSelected() != null;
        boolean aLogs = !dossiersLog.isEmpty();

        if (boutonTéléchargerSélectionné != null) boutonTéléchargerSélectionné.active = aSélection;
        if (boutonSupprimerSélectionné != null) boutonSupprimerSélectionné.active = aSélection;
        if (boutonTéléchargerTout != null) boutonTéléchargerTout.active = aLogs;
        if (boutonSupprimerTout != null) boutonSupprimerTout.active = aLogs;
    }

    private void téléchargerTousLesLogs() {
        GestionnaireReseau.INSTANCE.sendToServer(new PaquetTelechargementLogs(null, true));
        this.minecraft.player.sendSystemMessage(Component.literal("§aTéléchargement de tous les journaux en cours..."));
    }

    private void supprimerTousLesLogs() {
        GestionnaireReseau.INSTANCE.sendToServer(new PaquetSuppressionLogs(null, true));
        this.minecraft.player.sendSystemMessage(Component.literal("§cSuppression de tous les journaux..."));
        this.onClose();
    }

    private void téléchargerLogSélectionné() {
        ListeDossiersLog.EntréeDossierLog sélectionné = listeDossiers.getSelected();
        if (sélectionné != null) {
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetTelechargementLogs(sélectionné.getNomDossier(), false));
            this.minecraft.player.sendSystemMessage(Component.literal("§aTéléchargement de " + sélectionné.getNomDossier() + "..."));
        }
    }

    private void supprimerLogSélectionné() {
        ListeDossiersLog.EntréeDossierLog sélectionné = listeDossiers.getSelected();
        if (sélectionné != null) {
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetSuppressionLogs(sélectionné.getNomDossier(), false));
            this.minecraft.player.sendSystemMessage(Component.literal("§cSuppression de " + sélectionné.getNomDossier() + "..."));
            dossiersLog.remove(sélectionné.getNomDossier());
            listeDossiers.children().remove(sélectionné);
            mettreÀJourÉtatsBoutons();
        }
    }

    private void actualiserListeLogs() {
        GestionnaireReseau.INSTANCE.sendToServer(new PaquetDemandeListeLogs());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        if (listeDossiers != null) {
            listeDossiers.render(guiGraphics, sourisX, sourisY, tickPartiel);
        }

        int centreX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centreX, 20, 0xFFFFFF);

        String texteCompteur = dossiersLog.size() + " dossier(s) de journaux disponible(s)";
        guiGraphics.drawCenteredString(this.font, Component.literal(texteCompteur), centreX, 40, 0xAAAAAA);

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean mouseClicked(double sourisX, double sourisY, int bouton) {
        if (listeDossiers != null && listeDossiers.mouseClicked(sourisX, sourisY, bouton)) {
            mettreÀJourÉtatsBoutons();
            return true;
        }
        return super.mouseClicked(sourisX, sourisY, bouton);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Classe interne pour la liste déroulante
    public static class ListeDossiersLog extends ObjectSelectionList<ListeDossiersLog.EntréeDossierLog> {
        private final int positionGauche;

        public ListeDossiersLog(net.minecraft.client.Minecraft minecraft, int largeur, int hauteur, int haut, int bas, int hauteurÉlément, List<String> dossiers, int positionGauche) {
            super(minecraft, largeur, hauteur, haut, bas, hauteurÉlément);
            this.positionGauche = positionGauche;
            for (String dossier : dossiers) {
                this.addEntry(new EntréeDossierLog(dossier));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 10;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.positionGauche + this.width - 6;
        }

        @Override
        public int getRowLeft() {
            return this.positionGauche;
        }

        public static class EntréeDossierLog extends Entry<EntréeDossierLog> {
            private final String nomDossier;
            private final Component nomAffichage;

            public EntréeDossierLog(String nomDossier) {
                this.nomDossier = nomDossier;
                this.nomAffichage = Component.literal("📁 " + nomDossier);
            }

            public String getNomDossier() {
                return nomDossier;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int haut, int gauche, int largeur, int hauteur,
                             int sourisX, int sourisY, boolean sourisDessus, float tickPartiel) {
                // Dessiner l'arrière-plan de sélection
                if (sourisDessus) {
                    guiGraphics.fill(gauche, haut, gauche + largeur, haut + hauteur, 0x80FFFFFF);
                }

                guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    nomAffichage, gauche + 5, haut + 5, 0xFFFFFF);
            }

            @Override
            public Component getNarration() {
                return nomAffichage;
            }

            @Override
            public boolean mouseClicked(double sourisX, double sourisY, int bouton) {
                if (bouton == 0) {
                    return true;
                }
                return false;
            }
        }
    }
}

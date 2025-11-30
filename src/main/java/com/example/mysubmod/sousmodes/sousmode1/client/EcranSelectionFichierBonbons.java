package com.example.mysubmod.sousmodes.sousmode1.client;

import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSelectionFichierBonbons;
import com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSuppressionFichierBonbons;
import com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetDemandeListeFichiersBonbons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class EcranSelectionFichierBonbons extends Screen {
    private final List<String> fichiersDisponibles;
    private ListeFichiersBonbons listeFichiers;
    private Button boutonConfirmer;
    private Button boutonSupprimerSelection;
    private Button boutonActualiser;

    public EcranSelectionFichierBonbons(List<String> fichiersDisponibles) {
        super(Component.literal("Sélection du fichier d'apparition des bonbons"));
        this.fichiersDisponibles = new ArrayList<>(fichiersDisponibles);
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int largeurListe = this.width;
        int hauteurListe = this.height - 160;
        int listeX = centreX - largeurListe / 2;
        int listeY = 60;

        // Créer la liste de fichiers centrée
        listeFichiers = new ListeFichiersBonbons(this.minecraft, largeurListe, hauteurListe, listeY, listeY + hauteurListe, 25, fichiersDisponibles, listeX);
        this.addWidget(listeFichiers);

        int boutonY = listeY + hauteurListe + 10;
        int hauteurBouton = 20;
        int espacementBoutons = 5;

        // Rangée 1: Confirmer et Supprimer la sélection
        boutonConfirmer = Button.builder(
            Component.literal("✓ Confirmer et lancer la partie"),
            button -> confirmerSelection()
        ).bounds(listeX, boutonY, (largeurListe - espacementBoutons) / 2, hauteurBouton).build();
        this.addRenderableWidget(boutonConfirmer);

        boutonSupprimerSelection = Button.builder(
            Component.literal("🗑 Supprimer Sélectionné"),
            button -> supprimerFichierSelectionne()
        ).bounds(listeX + (largeurListe + espacementBoutons) / 2, boutonY, (largeurListe - espacementBoutons) / 2, hauteurBouton).build();
        this.addRenderableWidget(boutonSupprimerSelection);

        // Rangée 2: Rafraîchir et Fermer
        boutonY += hauteurBouton + espacementBoutons;
        boutonActualiser = Button.builder(
            Component.literal("🔄 Actualiser"),
            button -> actualiserListeFichiers()
        ).bounds(listeX, boutonY, (largeurListe - espacementBoutons) / 2, hauteurBouton).build();
        this.addRenderableWidget(boutonActualiser);

        Button boutonFermer = Button.builder(
            Component.literal("Fermer"),
            button -> this.onClose()
        ).bounds(listeX + (largeurListe + espacementBoutons) / 2, boutonY, (largeurListe - espacementBoutons) / 2, hauteurBouton).build();
        this.addRenderableWidget(boutonFermer);

        mettreAJourEtatsBoutons();
    }

    private void mettreAJourEtatsBoutons() {
        boolean aSelection = listeFichiers != null && listeFichiers.getSelected() != null;
        boolean aFichiers = !fichiersDisponibles.isEmpty();

        if (boutonConfirmer != null) boutonConfirmer.active = aSelection;

        // Impossible de supprimer default.txt
        if (boutonSupprimerSelection != null) {
            ListeFichiersBonbons.EntreeFichierBonbons selectionne = listeFichiers != null ? listeFichiers.getSelected() : null;
            boutonSupprimerSelection.active = aSelection && selectionne != null && !"default.txt".equals(selectionne.obtenirNomFichier());
        }
    }

    private void confirmerSelection() {
        ListeFichiersBonbons.EntreeFichierBonbons selectionne = listeFichiers.getSelected();
        if (selectionne != null) {
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetSelectionFichierBonbons(selectionne.obtenirNomFichier()));
            this.onClose();
        }
    }

    private void supprimerFichierSelectionne() {
        ListeFichiersBonbons.EntreeFichierBonbons selectionne = listeFichiers.getSelected();
        if (selectionne != null && !"default.txt".equals(selectionne.obtenirNomFichier())) {
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetSuppressionFichierBonbons(selectionne.obtenirNomFichier()));
            this.minecraft.player.sendSystemMessage(Component.literal("§cSuppression de " + selectionne.obtenirNomFichier() + "..."));
            fichiersDisponibles.remove(selectionne.obtenirNomFichier());
            listeFichiers.children().remove(selectionne);
            mettreAJourEtatsBoutons();
        }
    }

    private void actualiserListeFichiers() {
        GestionnaireReseau.INSTANCE.sendToServer(new PaquetDemandeListeFichiersBonbons());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        if (listeFichiers != null) {
            listeFichiers.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        int centreX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centreX, 20, 0xFFFFFF);

        String texteCompteur = fichiersDisponibles.size() + " fichier(s) disponible(s)";
        guiGraphics.drawCenteredString(this.font, Component.literal(texteCompteur), centreX, 40, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listeFichiers != null && listeFichiers.mouseClicked(mouseX, mouseY, button)) {
            mettreAJourEtatsBoutons();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Classe interne pour la liste déroulante
    public static class ListeFichiersBonbons extends ObjectSelectionList<ListeFichiersBonbons.EntreeFichierBonbons> {
        private final int positionGauche;

        public ListeFichiersBonbons(net.minecraft.client.Minecraft minecraft, int largeur, int hauteur, int haut, int bas, int hauteurElement, List<String> fichiers, int positionGauche) {
            super(minecraft, largeur, hauteur, haut, bas, hauteurElement);
            this.positionGauche = positionGauche;
            for (String fichier : fichiers) {
                this.addEntry(new EntreeFichierBonbons(fichier));
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

        public static class EntreeFichierBonbons extends Entry<EntreeFichierBonbons> {
            private final String nomFichier;
            private final Component nomAffichage;

            public EntreeFichierBonbons(String nomFichier) {
                this.nomFichier = nomFichier;
                // Ajouter une icône pour le fichier par défaut
                String icone = "default.txt".equals(nomFichier) ? "📄" : "📁";
                this.nomAffichage = Component.literal(icone + " " + nomFichier);
            }

            public String obtenirNomFichier() {
                return nomFichier;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int haut, int gauche, int largeur, int hauteur,
                             int mouseX, int mouseY, boolean estSousSouris, float partialTick) {
                // Dessiner l'arrière-plan de sélection
                if (estSousSouris) {
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
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    return true;
                }
                return false;
            }
        }
    }
}
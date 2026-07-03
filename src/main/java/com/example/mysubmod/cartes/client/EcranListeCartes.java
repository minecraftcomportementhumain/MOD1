package com.example.mysubmod.cartes.client;

import com.example.mysubmod.cartes.reseau.PaquetDemandeDonneesCarte;
import com.example.mysubmod.cartes.reseau.PaquetSelectionCarte;
import com.example.mysubmod.cartes.reseau.PaquetSuppressionCarte;
import com.example.mysubmod.reseau.GestionnaireReseau;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Liste des cartes sauvegardées sur le serveur.
 * Mode SELECTION_ACTIVE : « Sélectionner » définit la carte active et revient au menu admin.
 * Mode CHARGEMENT_EDITEUR : « Charger » charge la carte choisie dans l'éditeur.
 */
@OnlyIn(Dist.CLIENT)
public class EcranListeCartes extends Screen {

    public enum Mode {
        SELECTION_ACTIVE,
        CHARGEMENT_EDITEUR
    }

    private final List<String> cartes;
    private final String carteActive;
    private final Mode mode;
    private final EcranEditeurCarte editeurParent;

    private ListeCartes listeCartes;
    private Button boutonAction;
    private Button boutonSupprimer;
    private Button boutonDeselectionner;

    public EcranListeCartes(List<String> cartes, String carteActive, Mode mode) {
        this(cartes, carteActive, mode, null);
    }

    public EcranListeCartes(List<String> cartes, String carteActive, Mode mode, EcranEditeurCarte editeurParent) {
        super(Component.literal("Liste des cartes"));
        this.cartes = new ArrayList<>(cartes);
        this.carteActive = carteActive;
        this.mode = mode;
        this.editeurParent = editeurParent;
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int largeurListe = Math.min(400, this.width - 40);
        int hauteurListe = this.height - 140;
        int xListe = centreX - largeurListe / 2;
        int yListe = 50;

        listeCartes = new ListeCartes(this.minecraft, largeurListe, hauteurListe, yListe, yListe + hauteurListe, 20, cartes, xListe);
        this.addWidget(listeCartes);

        int yBouton = yListe + hauteurListe + 8;
        int largeurBouton = (largeurListe - 10) / 2;

        String texteAction = mode == Mode.SELECTION_ACTIVE ? "Sélectionner" : "Charger";
        boutonAction = Button.builder(
            Component.literal(texteAction),
            bouton -> actionPrincipale()
        ).bounds(xListe, yBouton, largeurBouton, 20).build();
        this.addRenderableWidget(boutonAction);

        boutonSupprimer = Button.builder(
            Component.literal("Supprimer"),
            bouton -> supprimerCarteSelectionnee()
        ).bounds(xListe + largeurBouton + 10, yBouton, largeurBouton, 20).build();
        this.addRenderableWidget(boutonSupprimer);

        yBouton += 25;
        if (mode == Mode.SELECTION_ACTIVE) {
            boutonDeselectionner = Button.builder(
                Component.literal("Désélectionner la carte active"),
                bouton -> deselectionnerCarte()
            ).bounds(xListe, yBouton, largeurBouton, 20).build();
            boutonDeselectionner.active = carteActive != null && !carteActive.isEmpty();
            this.addRenderableWidget(boutonDeselectionner);
        }

        Button boutonFermer = Button.builder(
            Component.literal(mode == Mode.CHARGEMENT_EDITEUR ? "Retour à l'éditeur" : "Fermer"),
            bouton -> this.onClose()
        ).bounds(xListe + largeurBouton + 10, yBouton, largeurBouton, 20).build();
        this.addRenderableWidget(boutonFermer);

        mettreAJourBoutons();
    }

    private void mettreAJourBoutons() {
        boolean aSelection = listeCartes != null && listeCartes.getSelected() != null;
        if (boutonAction != null) {
            boutonAction.active = aSelection;
        }
        if (boutonSupprimer != null) {
            boutonSupprimer.active = aSelection;
        }
    }

    private void actionPrincipale() {
        ListeCartes.EntreeCarte selection = listeCartes.getSelected();
        if (selection == null) {
            return;
        }

        if (mode == Mode.SELECTION_ACTIVE) {
            // Sélectionner la carte : le serveur rouvre le menu admin avec la carte active affichée
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetSelectionCarte(selection.obtenirNom(), true));
        } else {
            // Charger la carte dans l'éditeur
            if (editeurParent != null) {
                this.minecraft.setScreen(editeurParent);
                GestionnaireReseau.INSTANCE.sendToServer(new PaquetDemandeDonneesCarte(selection.obtenirNom()));
            }
        }
    }

    private void supprimerCarteSelectionnee() {
        ListeCartes.EntreeCarte selection = listeCartes.getSelected();
        if (selection != null) {
            int butReponse = mode == Mode.SELECTION_ACTIVE
                ? com.example.mysubmod.cartes.reseau.PaquetListeCartes.BUT_LISTE_SELECTION
                : com.example.mysubmod.cartes.reseau.PaquetListeCartes.BUT_AUCUN;
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetSuppressionCarte(selection.obtenirNom(), butReponse));
            if (mode == Mode.CHARGEMENT_EDITEUR) {
                // L'éditeur gère sa liste localement (le serveur ne renvoie pas de liste)
                cartes.remove(selection.obtenirNom());
                listeCartes.children().remove(selection);
                listeCartes.setSelected(null);
                mettreAJourBoutons();
            }
            // En mode sélection, le serveur renvoie la liste mise à jour (l'écran est rouvert)
        }
    }

    private void deselectionnerCarte() {
        GestionnaireReseau.INSTANCE.sendToServer(new PaquetSelectionCarte("", true));
    }

    @Override
    public void onClose() {
        if (mode == Mode.CHARGEMENT_EDITEUR && editeurParent != null) {
            this.minecraft.setScreen(editeurParent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        if (listeCartes != null) {
            listeCartes.render(guiGraphics, sourisX, sourisY, tickPartiel);
        }

        int centreX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centreX, 15, 0xFFFFFF);

        String texteCarte = carteActive == null || carteActive.isEmpty()
            ? "§7Carte active : §cAucune"
            : "§7Carte active : §a" + carteActive;
        guiGraphics.drawCenteredString(this.font, Component.literal(texteCarte), centreX, 30, 0xFFFFFF);

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean mouseClicked(double sourisX, double sourisY, int bouton) {
        boolean resultat = super.mouseClicked(sourisX, sourisY, bouton);
        mettreAJourBoutons();
        return resultat;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Liste déroulante des cartes
    public class ListeCartes extends ObjectSelectionList<ListeCartes.EntreeCarte> {
        private final int positionGauche;

        public ListeCartes(net.minecraft.client.Minecraft minecraft, int largeur, int hauteur, int haut, int bas,
                           int hauteurElement, List<String> noms, int positionGauche) {
            super(minecraft, largeur, hauteur, haut, bas, hauteurElement);
            this.positionGauche = positionGauche;
            for (String nom : noms) {
                this.addEntry(new EntreeCarte(nom));
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

        public class EntreeCarte extends ObjectSelectionList.Entry<EntreeCarte> {
            private final String nom;

            public EntreeCarte(String nom) {
                this.nom = nom;
            }

            public String obtenirNom() {
                return nom;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int haut, int gauche, int largeur, int hauteur,
                               int sourisX, int sourisY, boolean sourisDessus, float tickPartiel) {
                String affichage = "🗺 " + nom;
                if (nom.equals(carteActive)) {
                    affichage = "🗺 §a" + nom + " §7(active)";
                }
                guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    affichage, gauche + 5, haut + 4, 0xFFFFFF);
            }

            @Override
            public Component getNarration() {
                return Component.literal(nom);
            }

            @Override
            public boolean mouseClicked(double sourisX, double sourisY, int bouton) {
                if (bouton == 0) {
                    ListeCartes.this.setSelected(this);
                    return true;
                }
                return false;
            }
        }
    }
}

package com.example.mysubmod.cartes.client;

import com.example.mysubmod.cartes.reseau.PaquetChoixZoneDepart;
import com.example.mysubmod.reseau.GestionnaireReseau;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Sélection de la zone de départ (parties des Sous-modes 1 et 2 sur carte) :
 * remplace la sélection d'île. Les joueurs choisissent parmi les zones de type
 * Île de la carte ; les non-choisissants sont assignés aléatoirement.
 */
@OnlyIn(Dist.CLIENT)
public class EcranSelectionZoneDepart extends Screen {
    private static final int LARGEUR_BOUTON = 220;
    private static final int HAUTEUR_BOUTON = 20;
    private static final int ESPACEMENT_BOUTON = 24;

    private final List<String> zones;
    private final List<Integer> tailles; // en blocs ; -1 = non communiquée (affichage du nom seul)
    private int tempsRestant;
    private int compteurTicks = 0;

    // Pagination quand la liste ne tient pas à l'écran : seule la tranche
    // [decalage, decalage + lignesVisibles) est affichée (boutons ▲/▼ et molette)
    private int decalage = 0;
    private int lignesVisibles;
    private boolean pagine;
    private int yPagination;

    public EcranSelectionZoneDepart(List<String> zones, List<Integer> tailles, int tempsInitial) {
        super(Component.literal("Sélection de la parcelle de départ"));
        this.zones = zones;
        this.tailles = tailles;
        this.tempsRestant = tempsInitial;
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int hautListe = 55;
        int basListe = this.height - 8;

        // Si toutes les parcelles ne tiennent pas entre le titre et le bas de l'écran,
        // une rangée est réservée aux boutons ▲/▼ et la liste est paginée.
        int capacite = Math.max(1, (basListe - hautListe) / ESPACEMENT_BOUTON);
        pagine = zones.size() > capacite;
        lignesVisibles = pagine ? Math.max(1, capacite - 1) : zones.size();
        decalage = pagine ? Math.min(Math.max(0, decalage), zones.size() - lignesVisibles) : 0;

        int debutY = pagine ? hautListe
            : Math.max(hautListe, this.height / 2 - (zones.size() * ESPACEMENT_BOUTON) / 2);

        for (int i = decalage; i < decalage + lignesVisibles && i < zones.size(); i++) {
            final String nomZone = zones.get(i);
            int taille = (tailles != null && i < tailles.size()) ? tailles.get(i) : -1;
            // Le libellé peut inclure la taille de l'île ; le serveur ne reçoit que le nom brut
            String libelle = taille > 0 ? nomZone + " §7— " + taille + " blocs" : nomZone;
            addRenderableWidget(Button.builder(
                Component.literal(libelle),
                bouton -> selectionnerZone(nomZone)
            ).bounds(centreX - LARGEUR_BOUTON / 2, debutY, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());
            debutY += ESPACEMENT_BOUTON;
        }

        if (pagine) {
            yPagination = debutY;
            Button haut = Button.builder(Component.literal("▲"), b -> {
                decalage = Math.max(0, decalage - lignesVisibles);
                rebuildWidgets();
            }).bounds(centreX - LARGEUR_BOUTON / 2, debutY, 40, HAUTEUR_BOUTON).build();
            haut.active = decalage > 0;
            addRenderableWidget(haut);

            Button bas = Button.builder(Component.literal("▼"), b -> {
                decalage = Math.min(zones.size() - lignesVisibles, decalage + lignesVisibles);
                rebuildWidgets();
            }).bounds(centreX + LARGEUR_BOUTON / 2 - 40, debutY, 40, HAUTEUR_BOUTON).build();
            bas.active = decalage + lignesVisibles < zones.size();
            addRenderableWidget(bas);
        }
    }

    @Override
    public boolean mouseScrolled(double sourisX, double sourisY, double delta) {
        if (pagine) {
            int max = zones.size() - lignesVisibles;
            int nouveau = Math.max(0, Math.min(max, decalage - (int) Math.signum(delta)));
            if (nouveau != decalage) {
                decalage = nouveau;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(sourisX, sourisY, delta);
    }

    private void selectionnerZone(String nomZone) {
        GestionnaireReseau.INSTANCE.sendToServer(new PaquetChoixZoneDepart(nomZone));
        this.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        compteurTicks++;

        if (compteurTicks >= 20) {
            compteurTicks = 0;
            tempsRestant--;

            if (tempsRestant <= 0) {
                this.onClose(); // Fermeture automatique quand le temps est écoulé
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        int centreX = this.width / 2;

        guiGraphics.drawCenteredString(this.font, this.title, centreX, 15, 0xFFFFFF);

        String texteTemps = "Temps restant: " + tempsRestant + "s";
        int couleurTemps = tempsRestant <= 10 ? 0xFF5555 : 0xFFFF55;
        guiGraphics.drawCenteredString(this.font, Component.literal(texteTemps), centreX, 28, couleurTemps);

        guiGraphics.drawCenteredString(this.font,
            Component.literal("Choisissez votre parcelle de départ"), centreX, 40, 0xAAAAAA);

        if (pagine) {
            guiGraphics.drawCenteredString(this.font, Component.literal(
                (decalage + 1) + "–" + Math.min(decalage + lignesVisibles, zones.size())
                    + " / " + zones.size()),
                centreX, yPagination + 6, 0xAAAAAA);
        }

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Empêcher la fermeture avec Échap
    }
}

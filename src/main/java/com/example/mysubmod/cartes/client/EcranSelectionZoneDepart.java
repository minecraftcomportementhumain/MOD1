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
    private int tempsRestant;
    private int compteurTicks = 0;

    public EcranSelectionZoneDepart(List<String> zones, int tempsInitial) {
        super(Component.literal("Sélection de la zone de départ"));
        this.zones = zones;
        this.tempsRestant = tempsInitial;
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int debutY = Math.max(55, this.height / 2 - (zones.size() * ESPACEMENT_BOUTON) / 2);

        for (String zone : zones) {
            final String nomZone = zone;
            addRenderableWidget(Button.builder(
                Component.literal(nomZone),
                bouton -> selectionnerZone(nomZone)
            ).bounds(centreX - LARGEUR_BOUTON / 2, debutY, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());
            debutY += ESPACEMENT_BOUTON;
        }
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
            Component.literal("Choisissez votre zone de départ"), centreX, 40, 0xAAAAAA);

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

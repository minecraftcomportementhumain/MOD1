package com.example.mysubmod.sousmodes.sousmode2.client;

import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode2.iles.TypeIle;
import com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetChoixIle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EcranSelectionIle extends Screen {
    private static final int LARGEUR_BOUTON = 200;
    private static final int HAUTEUR_BOUTON = 20;
    private static final int ESPACEMENT_BOUTON = 25;

    private int tempsRestant;
    private int compteurTicks = 0;

    public EcranSelectionIle(int tempsInitial) {
        super(Component.literal("Sélection d'Île - Sous-mode 2"));
        this.tempsRestant = tempsInitial;
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int debutY = this.height / 2 - 50;

        addRenderableWidget(Button.builder(
            Component.literal(TypeIle.PETITE.obtenirNomAffichage()),
            button -> selectionnerIle(TypeIle.PETITE)
        ).bounds(centreX - LARGEUR_BOUTON / 2, debutY, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());

        addRenderableWidget(Button.builder(
            Component.literal(TypeIle.MOYENNE.obtenirNomAffichage()),
            button -> selectionnerIle(TypeIle.MOYENNE)
        ).bounds(centreX - LARGEUR_BOUTON / 2, debutY + ESPACEMENT_BOUTON, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());

        addRenderableWidget(Button.builder(
            Component.literal(TypeIle.GRANDE.obtenirNomAffichage()),
            button -> selectionnerIle(TypeIle.GRANDE)
        ).bounds(centreX - LARGEUR_BOUTON / 2, debutY + ESPACEMENT_BOUTON * 2, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());

        addRenderableWidget(Button.builder(
            Component.literal(TypeIle.TRES_GRANDE.obtenirNomAffichage()),
            button -> selectionnerIle(TypeIle.TRES_GRANDE)
        ).bounds(centreX - LARGEUR_BOUTON / 2, debutY + ESPACEMENT_BOUTON * 3, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());
    }

    private void selectionnerIle(TypeIle ile) {
        GestionnaireReseau.INSTANCE.sendToServer(new PaquetChoixIle(ile));
        this.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        compteurTicks++;

        if (compteurTicks >= 20) { // Chaque seconde
            compteurTicks = 0;
            tempsRestant--;

            if (tempsRestant <= 0) {
                this.onClose(); // Fermeture automatique quand le temps est écoulé
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centreX = this.width / 2;
        int titreY = this.height / 2 - 80;

        guiGraphics.drawCenteredString(this.font, this.title, centreX, titreY, 0xFFFFFF);

        String texteTemps = "Temps restant: " + tempsRestant + "s";
        int couleurTemps = tempsRestant <= 10 ? 0xFF5555 : 0xFFFF55;
        guiGraphics.drawCenteredString(this.font, Component.literal(texteTemps), centreX, titreY + 10, couleurTemps);

        String texteInstruction = "Choisissez votre île de départ";
        guiGraphics.drawCenteredString(this.font, Component.literal(texteInstruction), centreX, titreY + 20, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
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

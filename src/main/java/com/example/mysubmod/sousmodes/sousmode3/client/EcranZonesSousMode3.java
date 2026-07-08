package com.example.mysubmod.sousmodes.sousmode3.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Écran de sélection de zone du HUD (Sous-mode 3) : cliquer sur une zone active
 * la flèche de navigation pointant vers son centre géométrique. Cliquer sur une
 * autre zone redirige immédiatement la flèche.
 */
@OnlyIn(Dist.CLIENT)
public class EcranZonesSousMode3 extends Screen {
    private static final int LARGEUR_BOUTON = 240;
    private static final int HAUTEUR_BOUTON = 20;
    private static final int ESPACEMENT = 23;

    public EcranZonesSousMode3() {
        super(Component.literal("Zones de la carte"));
    }

    @Override
    protected void init() {
        super.init();

        List<HUDZonesSousMode3.ZoneClient> zones = HUDZonesSousMode3.obtenirZones();
        int centreX = this.width / 2;
        int y = Math.max(40, this.height / 2 - (zones.size() * ESPACEMENT + 40) / 2);

        for (HUDZonesSousMode3.ZoneClient zone : zones) {
            final String nomZone = zone.nom;
            boolean estCiblee = nomZone.equals(HUDZonesSousMode3.obtenirZoneCiblee());
            String etiquette = (estCiblee ? "§b➤ " : "") + nomZone + " §7— "
                + HUDZonesSousMode3.formaterCompteurs(zone);
            addRenderableWidget(Button.builder(
                Component.literal(etiquette),
                bouton -> {
                    HUDZonesSousMode3.ciblerZone(nomZone);
                    this.onClose();
                }
            ).bounds(centreX - LARGEUR_BOUTON / 2, y, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());
            y += ESPACEMENT;
        }

        if (HUDZonesSousMode3.obtenirZoneCiblee() != null) {
            addRenderableWidget(Button.builder(
                Component.literal("§cDésactiver la flèche"),
                bouton -> {
                    HUDZonesSousMode3.ciblerZone(null);
                    this.onClose();
                }
            ).bounds(centreX - LARGEUR_BOUTON / 2, y + 5, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());
            y += ESPACEMENT + 5;
        }

        addRenderableWidget(Button.builder(
            Component.literal("Fermer"),
            bouton -> this.onClose()
        ).bounds(centreX - LARGEUR_BOUTON / 2, y + 5, LARGEUR_BOUTON, HAUTEUR_BOUTON).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
            "§7Cliquez sur une zone pour activer la flèche de navigation", this.width / 2, 32, 0xAAAAAA);
        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

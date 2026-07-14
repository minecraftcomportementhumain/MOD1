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

    // Pagination quand la liste ne tient pas à l'écran : seule la tranche
    // [decalage, decalage + lignesVisibles) est affichée (boutons ▲/▼ et molette) ;
    // « Désactiver la flèche » et « Fermer » restent toujours visibles en bas.
    private int decalage = 0;
    private int lignesVisibles;
    private boolean pagine;
    private int yPagination;
    private int totalZones;

    public EcranZonesSousMode3() {
        super(Component.literal("Parcelles de la carte"));
    }

    @Override
    protected void init() {
        super.init();

        List<HUDZonesSousMode3.ZoneClient> zones = HUDZonesSousMode3.obtenirZones();
        totalZones = zones.size();
        int centreX = this.width / 2;
        int hautListe = 40;
        int basEcran = this.height - 8;

        // Hauteur des boutons fixes du bas (« Fermer » + « Désactiver la flèche » éventuel)
        boolean flecheActive = HUDZonesSousMode3.obtenirZoneCiblee() != null;
        int hauteurFixe = (ESPACEMENT + 5) + (flecheActive ? ESPACEMENT + 5 : 0);

        // Si toutes les parcelles ne tiennent pas au-dessus des boutons fixes,
        // une rangée est réservée aux boutons ▲/▼ et la liste est paginée.
        int capacite = Math.max(1, (basEcran - hautListe - hauteurFixe) / ESPACEMENT);
        pagine = zones.size() > capacite;
        lignesVisibles = pagine ? Math.max(1, capacite - 1) : zones.size();
        decalage = pagine ? Math.min(Math.max(0, decalage), zones.size() - lignesVisibles) : 0;

        int y = pagine ? hautListe
            : Math.max(hautListe, this.height / 2 - (zones.size() * ESPACEMENT + hauteurFixe) / 2);

        for (int i = decalage; i < decalage + lignesVisibles && i < zones.size(); i++) {
            HUDZonesSousMode3.ZoneClient zone = zones.get(i);
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

        if (pagine) {
            yPagination = y;
            Button haut = Button.builder(Component.literal("▲"), b -> {
                decalage = Math.max(0, decalage - lignesVisibles);
                rebuildWidgets();
            }).bounds(centreX - LARGEUR_BOUTON / 2, y, 40, HAUTEUR_BOUTON).build();
            haut.active = decalage > 0;
            addRenderableWidget(haut);

            Button bas = Button.builder(Component.literal("▼"), b -> {
                decalage = Math.min(totalZones - lignesVisibles, decalage + lignesVisibles);
                rebuildWidgets();
            }).bounds(centreX + LARGEUR_BOUTON / 2 - 40, y, 40, HAUTEUR_BOUTON).build();
            bas.active = decalage + lignesVisibles < zones.size();
            addRenderableWidget(bas);
            y += ESPACEMENT;
        }

        if (flecheActive) {
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
    public boolean mouseScrolled(double sourisX, double sourisY, double delta) {
        if (pagine) {
            int max = totalZones - lignesVisibles;
            int nouveau = Math.max(0, Math.min(max, decalage - (int) Math.signum(delta)));
            if (nouveau != decalage) {
                decalage = nouveau;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(sourisX, sourisY, delta);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
            "§7Cliquez sur une parcelle pour activer la flèche de navigation", this.width / 2, 32, 0xAAAAAA);
        if (pagine) {
            guiGraphics.drawCenteredString(this.font, Component.literal(
                (decalage + 1) + "–" + Math.min(decalage + lignesVisibles, totalZones)
                    + " / " + totalZones),
                this.width / 2, yPagination + 6, 0xAAAAAA);
        }
        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

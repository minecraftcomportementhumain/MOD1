package com.example.mysubmod.cartes.client;

import com.example.mysubmod.MonSubMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Barre de chargement affichée pendant la génération de la carte d'un sous-mode.
 * Les joueurs (sur la plateforme spectateur) voient l'avancement en pourcentage
 * pendant que la carte se construit dans le monde.
 */
@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class RenduChargementCarte {

    private static final int LARGEUR_BARRE_MAX = 260;
    private static final int HAUTEUR_BARRE = 12;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ChargementCarteClient.estActif()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        int largeurEcran = mc.getWindow().getGuiScaledWidth();
        int hauteurEcran = mc.getWindow().getGuiScaledHeight();

        int pourcent = ChargementCarteClient.obtenirPourcent();
        String nomCarte = ChargementCarteClient.obtenirNomCarte();

        int largeurBarre = Math.min(LARGEUR_BARRE_MAX, (int) (largeurEcran * 0.6));
        int centreX = largeurEcran / 2;
        int gaucheBarre = centreX - largeurBarre / 2;
        // Positionnée sous le centre de l'écran, au-dessus de la barre d'action
        int hautBarre = (int) (hauteurEcran * 0.62);

        // Panneau de fond
        int margeInterne = 8;
        int hautPanneau = hautBarre - 24;
        int basPanneau = hautBarre + HAUTEUR_BARRE + margeInterne + mc.font.lineHeight + 4;
        g.fill(gaucheBarre - margeInterne, hautPanneau,
            gaucheBarre + largeurBarre + margeInterne, basPanneau, 0xC8000000);

        // Titre (« Génération de la carte », « Nettoyage de la carte »...)
        String libelle = ChargementCarteClient.obtenirTitre();
        if (libelle.isEmpty()) {
            libelle = "Génération de la carte";
        }
        Component titre = nomCarte.isEmpty()
            ? Component.literal("§e" + libelle + "...")
            : Component.literal("§e" + libelle + " §f« " + nomCarte + " §f»");
        g.drawCenteredString(mc.font, titre, centreX, hautBarre - 18, 0xFFFFFF);

        // Piste de la barre
        g.fill(gaucheBarre, hautBarre, gaucheBarre + largeurBarre, hautBarre + HAUTEUR_BARRE, 0xFF202020);

        // Portion remplie
        int largeurRemplie = (int) (largeurBarre * (pourcent / 100.0));
        if (largeurRemplie > 0) {
            g.fill(gaucheBarre, hautBarre, gaucheBarre + largeurRemplie, hautBarre + HAUTEUR_BARRE, 0xFF4CC24C);
        }

        // Cadre de la barre
        int cadre = 0xFFFFFFFF;
        g.fill(gaucheBarre - 1, hautBarre - 1, gaucheBarre + largeurBarre + 1, hautBarre, cadre); // haut
        g.fill(gaucheBarre - 1, hautBarre + HAUTEUR_BARRE, gaucheBarre + largeurBarre + 1, hautBarre + HAUTEUR_BARRE + 1, cadre); // bas
        g.fill(gaucheBarre - 1, hautBarre, gaucheBarre, hautBarre + HAUTEUR_BARRE, cadre); // gauche
        g.fill(gaucheBarre + largeurBarre, hautBarre, gaucheBarre + largeurBarre + 1, hautBarre + HAUTEUR_BARRE, cadre); // droite

        // Pourcentage
        g.drawCenteredString(mc.font, Component.literal(pourcent + " %"),
            centreX, hautBarre + HAUTEUR_BARRE + margeInterne - 2, 0xFFFFFF);
    }
}

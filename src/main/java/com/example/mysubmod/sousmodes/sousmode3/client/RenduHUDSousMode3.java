package com.example.mysubmod.sousmodes.sousmode3.client;

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
 * Rendu du HUD du Sous-mode 3 : minuterie de partie, zones (compteurs de bonbons)
 * et flèche de navigation.
 */
@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class RenduHUDSousMode3 {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int largeurEcran = mc.getWindow().getGuiScaledWidth();
        int hauteurEcran = mc.getWindow().getGuiScaledHeight();

        // Minuterie de partie (coin supérieur droit)
        if (MinuterieJeuClientSousMode3.estActif()) {
            String texteTimer = MinuterieJeuClientSousMode3.obtenirTempsFormate();
            int largeurTexte = mc.font.width(texteTimer);
            int x = largeurEcran - largeurTexte - 10;
            int y = 10;

            guiGraphics.fill(x - 5, y - 2, x + largeurTexte + 5, y + mc.font.lineHeight + 2, 0x80000000);
            guiGraphics.drawString(mc.font, Component.literal(texteTimer), x, y, 0xFFFFFF);

            int secondesRestantes = MinuterieJeuClientSousMode3.obtenirSecondesRestantes();
            if (secondesRestantes <= 60) {
                int couleur = secondesRestantes <= 30 ? 0xFFFF0000 : 0xFFFFAA00;
                guiGraphics.fill(x - 5, y - 2, x + largeurTexte + 5, y + mc.font.lineHeight + 2, couleur & 0x40FFFFFF);
            }
        }

        // HUD des zones + flèche de navigation
        if (HUDZonesSousMode3.estActif()) {
            HUDZonesSousMode3.afficher(guiGraphics, largeurEcran, hauteurEcran);
        }
    }
}

package com.example.mysubmod.client;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.sousmodes.sousmode1.client.MinuterieJeuClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class HUDSousMode1 {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!MinuterieJeuClient.estActif()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int largeurÉcran = mc.getWindow().getGuiScaledWidth();

        // Afficher le minuterie dans le coin supérieur droit
        String texteTimer = MinuterieJeuClient.obtenirTempsFormate();
        int largeurTexte = mc.font.width(texteTimer);
        int x = largeurÉcran - largeurTexte - 10;
        int y = 10;

        // Arrière-plan
        guiGraphics.fill(x - 5, y - 2, x + largeurTexte + 5, y + mc.font.lineHeight + 2, 0x80000000);

        // Texte du minuterie
        guiGraphics.drawString(mc.font, Component.literal(texteTimer), x, y, 0xFFFFFF);

        // Couleurs d'avertissement pour temps faible
        int secondesRestantes = MinuterieJeuClient.obtenirSecondesRestantes();
        if (secondesRestantes <= 60) {
            int couleur = secondesRestantes <= 30 ? 0xFFFF0000 : 0xFFFFAA00;
            guiGraphics.fill(x - 5, y - 2, x + largeurTexte + 5, y + mc.font.lineHeight + 2, couleur & 0x40FFFFFF);
        }
    }
}

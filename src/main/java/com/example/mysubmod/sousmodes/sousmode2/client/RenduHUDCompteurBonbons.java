package com.example.mysubmod.sousmodes.sousmode2.client;

import com.example.mysubmod.MonSubMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RenduHUDCompteurBonbons {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

        // Ne pas afficher les HUD si le joueur est en mode spectateur (lobby d'attente ou mort)
        if (mc.player != null && mc.player.isSpectator()) {
            return;
        }

        // Afficher HUD compteur de bonbons
        if (HUDCompteurBonbons.estActif()) {
            HUDCompteurBonbons.afficher(event.getGuiGraphics(),
                event.getGuiGraphics().guiWidth(),
                event.getGuiGraphics().guiHeight());
        }

        // Afficher HUD minuterie de pénalité
        if (HUDMinuteriePenalite.estActif()) {
            HUDMinuteriePenalite.afficher(event.getGuiGraphics(),
                event.getGuiGraphics().guiWidth(),
                event.getGuiGraphics().guiHeight());
        }
    }
}

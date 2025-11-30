package com.example.mysubmod.sousmodes.sousmode1.client;

import com.example.mysubmod.MonSubMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RenduHUDCompteurBonbons {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (HUDCompteurBonbons.estActif()) {
            HUDCompteurBonbons.afficher(event.getGuiGraphics(),
                event.getGuiGraphics().guiWidth(),
                event.getGuiGraphics().guiHeight());
        }
    }
}

package com.example.mysubmod.submodes.submode2.client;

import com.example.mysubmod.MySubMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CandyCountHUDRenderer {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

        // Don't show HUDs if player is in spectator mode (parking lobby or dead)
        if (mc.player != null && mc.player.isSpectator()) {
            return;
        }

        // Render candy count HUD
        if (CandyCountHUD.isActive()) {
            CandyCountHUD.render(event.getGuiGraphics(),
                event.getGuiGraphics().guiWidth(),
                event.getGuiGraphics().guiHeight());
        }

        // Render penalty timer HUD
        if (PenaltyTimerHUD.isActive()) {
            PenaltyTimerHUD.render(event.getGuiGraphics(),
                event.getGuiGraphics().guiWidth(),
                event.getGuiGraphics().guiHeight());
        }
    }
}

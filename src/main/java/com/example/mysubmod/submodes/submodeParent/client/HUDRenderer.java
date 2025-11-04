package com.example.mysubmod.submodes.submodeParent.client;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode1.client.SubMode1HUD;
import com.example.mysubmod.submodes.submode2.client.SubMode2HUD;
import com.example.mysubmod.submodes.submode2.client.PenaltyTimerHUD;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class HUDRenderer {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // Don't show HUDs if player is in spectator mode (parking lobby or dead)
        if (mc.player != null && mc.player.isSpectator()) {
            return;
        }

        // SUB_MODE_1
            if (SubMode1HUD.isActive()) {
                SubMode1HUD.render(event.getGuiGraphics(),
                        event.getGuiGraphics().guiWidth(),
                        event.getGuiGraphics().guiHeight());
            }
            // SUB_MODE_2
            if (SubMode2HUD.isActive()) {
                SubMode2HUD.render(event.getGuiGraphics(),
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



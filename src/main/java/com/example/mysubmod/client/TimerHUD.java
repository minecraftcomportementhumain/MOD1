package com.example.mysubmod.client;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submodeParent.client.ClientGameTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class TimerHUD {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientGameTimer.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Display timer in top-right corner
        String timerText = ClientGameTimer.getFormattedTime();
        int textWidth = mc.font.width(timerText);
        int x = screenWidth - textWidth - 10;
        int y = 10;

        // Background
        guiGraphics.fill(x - 5, y - 2, x + textWidth + 5, y + mc.font.lineHeight + 2, 0x80000000);

        // Timer text
        guiGraphics.drawString(mc.font, Component.literal(timerText), x, y, 0xFFFFFF);

        // Warning colors for low time
        int secondsLeft = ClientGameTimer.getSecondsLeft();
        if (secondsLeft <= 60) {
            int color = secondsLeft <= 30 ? 0xFFFF0000 : 0xFFFFAA00;
            guiGraphics.fill(x - 5, y - 2, x + textWidth + 5, y + mc.font.lineHeight + 2, color & 0x40FFFFFF);
        }
    }
}
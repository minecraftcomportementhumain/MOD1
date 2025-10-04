package com.example.mysubmod.client;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.client.gui.SubModeControlScreen;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.submode1.network.ClientPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();

        // M key - Open sub-mode control screen
        if (event.getKey() == GLFW.GLFW_KEY_M && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new SubModeControlScreen());
            }
        }

        // N key - Open candy file selection screen (ONLY in SubMode1)
        if (event.getKey() == GLFW.GLFW_KEY_N && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.screen == null && mc.player != null) {
                // Only allow in SubMode1 (use ClientSubModeManager for client-side mode check)
                if (ClientSubModeManager.getCurrentMode() == SubMode.SUB_MODE_1) {
                    ClientPacketHandler.openCandyFileSelectionScreen();
                }
            }
        }
    }
}
package com.example.mysubmod.client;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.client.gui.SubModeControlScreen;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.submode1.network.ClientPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {

    private static boolean autoSuggestionsDisabled = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // Disable command suggestions and tutorial once when the game is loaded
        if (!autoSuggestionsDisabled && mc.options != null) {
            mc.options.autoSuggestions().set(false);

            // Force tutorial to NONE to disable all tips
            if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
                mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                mc.options.save();
                MySubMod.LOGGER.info("Tutorial forced to NONE (disabled)");
            }

            autoSuggestionsDisabled = true;
            MySubMod.LOGGER.info("Command suggestions and tutorial disabled automatically");
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();

        // M key - Open sub-mode control screen
        if (event.getKey() == GLFW.GLFW_KEY_M && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.screen == null && mc.player != null) {
                // Request screen from server with player count
                com.example.mysubmod.network.NetworkHandler.INSTANCE.sendToServer(
                    new com.example.mysubmod.network.SubModeControlScreenRequestPacket()
                );
            }
        }

        // N key - Open candy file selection screen (SubMode1 and SubMode2)
        if (event.getKey() == GLFW.GLFW_KEY_N && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.screen == null && mc.player != null) {
                SubMode currentMode = ClientSubModeManager.getCurrentMode();

                if (currentMode == SubMode.SUB_MODE_1) {
                    // Check if game has ended
                    if (com.example.mysubmod.submodes.submode1.client.ClientGameTimer.hasGameEnded()) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cLe menu de sélection de fichier est désactivé après la fin de la partie"));
                    } else {
                        ClientPacketHandler.openCandyFileSelectionScreen();
                    }
                } else if (currentMode == SubMode.SUB_MODE_2) {
                    // Check if game has ended
                    if (com.example.mysubmod.submodes.submode2.client.ClientGameTimer.hasGameEnded()) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cLe menu de sélection de fichier est désactivé après la fin de la partie"));
                    } else {
                        com.example.mysubmod.submodes.submode2.network.ClientPacketHandler.openCandyFileSelectionScreen();
                    }
                }
            }
        }
    }
}
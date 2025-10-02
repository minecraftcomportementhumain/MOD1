package com.example.mysubmod.submodes.submode1.network;

import com.example.mysubmod.submodes.submode1.client.CandyFileSelectionScreen;
import com.example.mysubmod.submodes.submode1.client.ClientGameTimer;
import com.example.mysubmod.submodes.submode1.client.IslandSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void openIslandSelectionScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new IslandSelectionScreen());
    }

    public static void updateGameTimer(int secondsLeft) {
        ClientGameTimer.updateTimer(secondsLeft);
    }

    public static void handleCandyFileList(List<String> availableFiles) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new CandyFileSelectionScreen(availableFiles));
    }
}
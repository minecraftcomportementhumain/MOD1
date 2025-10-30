package com.example.mysubmod.submodes.submode2.network;

import com.example.mysubmod.submodes.submode2.client.CandyFileListManager;
import com.example.mysubmod.submodes.submode2.client.CandyFileSelectionScreen;
import com.example.mysubmod.submodes.submode2.client.ClientGameTimer;
import com.example.mysubmod.submodes.submode2.client.IslandSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void openIslandSelectionScreen(int timeLeftSeconds) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new IslandSelectionScreen(timeLeftSeconds));
    }

    public static void updateGameTimer(int secondsLeft) {
        ClientGameTimer.updateTimer(secondsLeft);
    }

    public static void handleCandyFileList(List<String> availableFiles, boolean openScreen) {
        // Store the file list
        CandyFileListManager.getInstance().setFileList(availableFiles);

        // Open the screen only if requested and we have files
        if (openScreen && !availableFiles.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new CandyFileSelectionScreen(availableFiles));
        }
    }

    public static void openCandyFileSelectionScreen() {
        // Always request fresh file list from server when opening the menu
        com.example.mysubmod.network.NetworkHandler.INSTANCE.sendToServer(new CandyFileListRequestPacket());
    }

    public static void handleGameEnd() {
        ClientGameTimer.markGameAsEnded();
    }
}

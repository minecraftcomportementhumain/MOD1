package com.example.mysubmod.client;

import com.example.mysubmod.client.gui.LogManagementScreen;
import com.example.mysubmod.network.LogListPacket;
import net.minecraft.client.Minecraft;

public class LogPacketHandler {
    public static void handleLogListPacket(LogListPacket packet) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new LogManagementScreen(packet.getLogFolders()));
        });
    }
}

package com.example.mysubmod.submodes.submode2.network;

import com.example.mysubmod.submodes.submodeParent.client.FileListManager;
import com.example.mysubmod.submodes.submodeParent.client.FileSelectionScreen;
import com.example.mysubmod.submodes.submodeParent.client.ClientGameTimer;
import com.example.mysubmod.submodes.submodeParent.client.IslandSelectionScreen;
import com.example.mysubmod.submodes.submodeParent.network.ClientPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class SubMode2ClientPacketHandler extends ClientPacketHandler {

    public static void handleGameEnd() {
        ClientGameTimer.markGameAsEnded();
    }
}

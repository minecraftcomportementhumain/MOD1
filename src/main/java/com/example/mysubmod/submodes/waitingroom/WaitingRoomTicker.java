package com.example.mysubmod.submodes.waitingroom;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WaitingRoomTicker {
    private static int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20; // Check every second (20 ticks)

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (SubModeManager.getInstance().getCurrentMode() != SubMode.WAITING_ROOM) {
            return;
        }

        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;

            // Check all players for boundary violations
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                WaitingRoomEventHandler.checkPlayerBoundaries(player);
            }
        }
    }
}
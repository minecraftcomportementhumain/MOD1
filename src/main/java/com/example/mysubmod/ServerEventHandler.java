package com.example.mysubmod;

import com.example.mysubmod.commands.SubModeCommand;
import com.example.mysubmod.network.AdminStatusPacket;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.network.SubModeChangePacket;
import com.example.mysubmod.submodes.SubModeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SubModeCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SubModeChangePacket(SubModeManager.getInstance().getCurrentMode())
            );

            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new AdminStatusPacket(SubModeManager.getInstance().isAdmin(player))
            );
        }
    }
}
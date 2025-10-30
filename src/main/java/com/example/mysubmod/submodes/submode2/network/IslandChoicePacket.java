package com.example.mysubmod.submodes.submode2.network;

import com.example.mysubmod.submodes.submode2.SubMode2Manager;
import com.example.mysubmod.submodes.submode2.islands.IslandType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class IslandChoicePacket {
    private final IslandType selectedIsland;

    public IslandChoicePacket(IslandType selectedIsland) {
        this.selectedIsland = selectedIsland;
    }

    public static void encode(IslandChoicePacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.selectedIsland);
    }

    public static IslandChoicePacket decode(FriendlyByteBuf buf) {
        return new IslandChoicePacket(buf.readEnum(IslandType.class));
    }

    public static void handle(IslandChoicePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                SubMode2Manager.getInstance().selectIsland(player, packet.selectedIsland);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

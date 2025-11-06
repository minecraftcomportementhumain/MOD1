package com.example.mysubmod.submodes.submode2.network;

import com.example.mysubmod.submodes.submode2.ResourceType;
import com.example.mysubmod.submodes.submode2.SpecializationManager;
import com.example.mysubmod.submodes.submode2.client.PenaltyTimerHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Packet pour synchroniser l'état de pénalité avec le client
 * Envoyé quand une pénalité est appliquée ou lors de la reconnexion
 */
public class PenaltySyncPacket {
    private final boolean hasPenalty;
    private final UUID playerId;
    private Integer playerSpecialization =0;
    private Long playerPenaltyEndTime = 0L;

    public PenaltySyncPacket(boolean hasPenalty, UUID playerId) {
        this.hasPenalty = hasPenalty;
        this.playerId = playerId;
    }

    public PenaltySyncPacket(boolean hasPenalty, UUID playerId,
                             Integer playerSpecialization,
                             Long playerPenaltyEndTime) {
        this.hasPenalty = hasPenalty;
        this.playerId = playerId;
        this.playerSpecialization = playerSpecialization;
        this.playerPenaltyEndTime = playerPenaltyEndTime;
    }

    public static void encode(PenaltySyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.hasPenalty);
        buf.writeUUID(packet.playerId);
        buf.writeInt(packet.playerSpecialization);
        buf.writeLong(packet.playerPenaltyEndTime);
    }

    public static PenaltySyncPacket decode(FriendlyByteBuf buf) {
        return new PenaltySyncPacket(buf.readBoolean(), buf.readUUID(),buf.readInt(),buf.readLong());
    }

    public static void handle(PenaltySyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Vérifier que c'est bien le joueur local
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.getUUID().equals(packet.playerId)) {
                    if (packet.hasPenalty) {
                        SpecializationManager.getInstance().setPlayerSpecialization(packet.playerId, packet.playerSpecialization);
                        SpecializationManager.getInstance().setPlayerPenaltyEndTime(packet.playerId,packet.playerPenaltyEndTime);
                        PenaltyTimerHUD.activate(packet.playerId);
                    } else {
                        PenaltyTimerHUD.deactivate();
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}

package com.example.mysubmod.submodes.submode2.network;

import com.example.mysubmod.submodes.submode2.client.PenaltyTimerHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet pour synchroniser l'état de pénalité avec le client
 * Envoyé quand une pénalité est appliquée ou lors de la reconnexion
 */
public class PenaltySyncPacket {
    private final boolean hasPenalty;
    private final UUID playerId;
    private final long penaltyEndTime; // Temps de fin de la pénalité en ms (epoch)

    public PenaltySyncPacket(boolean hasPenalty, UUID playerId) {
        this.hasPenalty = hasPenalty;
        this.playerId = playerId;
        this.penaltyEndTime = 0;
    }

    public PenaltySyncPacket(boolean hasPenalty, UUID playerId, long penaltyEndTime) {
        this.hasPenalty = hasPenalty;
        this.playerId = playerId;
        this.penaltyEndTime = penaltyEndTime;
    }

    public static void encode(PenaltySyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.hasPenalty);
        buf.writeUUID(packet.playerId);
        buf.writeLong(packet.penaltyEndTime);
    }

    public static PenaltySyncPacket decode(FriendlyByteBuf buf) {
        boolean hasPenalty = buf.readBoolean();
        UUID playerId = buf.readUUID();
        long penaltyEndTime = buf.readLong();
        return new PenaltySyncPacket(hasPenalty, playerId, penaltyEndTime);
    }

    public static void handle(PenaltySyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Vérifier que c'est bien le joueur local
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.getUUID().equals(packet.playerId)) {
                    if (packet.hasPenalty) {
                        PenaltyTimerHUD.activate(packet.playerId, packet.penaltyEndTime);
                    } else {
                        PenaltyTimerHUD.deactivate();
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}

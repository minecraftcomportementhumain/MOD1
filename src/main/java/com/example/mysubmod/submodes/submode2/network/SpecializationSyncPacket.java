package com.example.mysubmod.submodes.submode2.network;

import com.example.mysubmod.submodes.submode2.ResourceType;
import com.example.mysubmod.submodes.submode2.client.CandyCountHUD;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet to sync player specialization from server to client
 */
public class SpecializationSyncPacket {
    private final ResourceType specialization; // null if no specialization yet

    public SpecializationSyncPacket(ResourceType specialization) {
        this.specialization = specialization;
    }

    public static void encode(SpecializationSyncPacket packet, FriendlyByteBuf buf) {
        // Write whether specialization exists
        buf.writeBoolean(packet.specialization != null);
        if (packet.specialization != null) {
            buf.writeUtf(packet.specialization.name());
        }
    }

    public static SpecializationSyncPacket decode(FriendlyByteBuf buf) {
        boolean hasSpecialization = buf.readBoolean();
        ResourceType specialization = null;
        if (hasSpecialization) {
            specialization = ResourceType.valueOf(buf.readUtf());
        }
        return new SpecializationSyncPacket(specialization);
    }

    public static void handle(SpecializationSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                CandyCountHUD.setPlayerSpecialization(packet.specialization);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}

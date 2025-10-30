package com.example.mysubmod.submodes.submode2.network;

import com.example.mysubmod.submodes.submode2.ResourceType;
import com.example.mysubmod.submodes.submode2.client.CandyCountHUD;
import com.example.mysubmod.submodes.submode2.islands.IslandType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Packet to update candy counts on client
 * Extended for SubMode2 to track counts by BOTH island AND resource type
 */
public class CandyCountUpdatePacket {
    private final Map<IslandType, Map<ResourceType, Integer>> candyCounts;

    public CandyCountUpdatePacket(Map<IslandType, Map<ResourceType, Integer>> candyCounts) {
        this.candyCounts = candyCounts;
    }

    public CandyCountUpdatePacket(FriendlyByteBuf buf) {
        this.candyCounts = new HashMap<>();
        int islandCount = buf.readInt();

        for (int i = 0; i < islandCount; i++) {
            IslandType island = buf.readEnum(IslandType.class);
            int typeCount = buf.readInt();

            Map<ResourceType, Integer> typeCounts = new HashMap<>();
            for (int j = 0; j < typeCount; j++) {
                ResourceType type = buf.readEnum(ResourceType.class);
                int count = buf.readInt();
                typeCounts.put(type, count);
            }

            candyCounts.put(island, typeCounts);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(candyCounts.size());

        for (Map.Entry<IslandType, Map<ResourceType, Integer>> islandEntry : candyCounts.entrySet()) {
            buf.writeEnum(islandEntry.getKey());

            Map<ResourceType, Integer> typeCounts = islandEntry.getValue();
            buf.writeInt(typeCounts.size());

            for (Map.Entry<ResourceType, Integer> typeEntry : typeCounts.entrySet()) {
                buf.writeEnum(typeEntry.getKey());
                buf.writeInt(typeEntry.getValue());
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Update client-side HUD data
            CandyCountHUD.updateCandyCounts(candyCounts);
        });
        ctx.get().setPacketHandled(true);
    }
}

package com.example.mysubmod.submodes.submode1.network;

import com.example.mysubmod.submodes.submode1.client.CandyCountHUD;
import com.example.mysubmod.submodes.submode1.islands.IslandType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class CandyCountUpdatePacket {
    private final Map<IslandType, Integer> candyCounts;

    public CandyCountUpdatePacket(Map<IslandType, Integer> candyCounts) {
        this.candyCounts = candyCounts;
    }

    public CandyCountUpdatePacket(FriendlyByteBuf buf) {
        this.candyCounts = new HashMap<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            IslandType island = buf.readEnum(IslandType.class);
            int count = buf.readInt();
            candyCounts.put(island, count);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(candyCounts.size());
        for (Map.Entry<IslandType, Integer> entry : candyCounts.entrySet()) {
            buf.writeEnum(entry.getKey());
            buf.writeInt(entry.getValue());
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

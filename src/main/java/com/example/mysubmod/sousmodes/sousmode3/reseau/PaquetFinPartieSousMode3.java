package com.example.mysubmod.sousmodes.sousmode3.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet serveur -> client : la partie du Sous-mode 3 est terminée.
 */
public class PaquetFinPartieSousMode3 {

    public PaquetFinPartieSousMode3() {
    }

    public static void encode(PaquetFinPartieSousMode3 paquet, FriendlyByteBuf tampon) {
    }

    public static PaquetFinPartieSousMode3 decode(FriendlyByteBuf tampon) {
        return new PaquetFinPartieSousMode3();
    }

    public static void traiter(PaquetFinPartieSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.example.mysubmod.sousmodes.sousmode3.client.MinuterieJeuClientSousMode3.marquerPartieTerminee();
        });
        ctx.get().setPacketHandled(true);
    }
}

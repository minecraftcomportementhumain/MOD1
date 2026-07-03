package com.example.mysubmod.sousmodes.sousmode3.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet serveur -> client : mise à jour de la minuterie de partie du Sous-mode 3.
 * Une valeur négative désactive la minuterie côté client.
 */
public class PaquetMinuterieJeuSousMode3 {
    private final int secondesRestantes;

    public PaquetMinuterieJeuSousMode3(int secondesRestantes) {
        this.secondesRestantes = secondesRestantes;
    }

    public static void encode(PaquetMinuterieJeuSousMode3 paquet, FriendlyByteBuf tampon) {
        tampon.writeInt(paquet.secondesRestantes);
    }

    public static PaquetMinuterieJeuSousMode3 decode(FriendlyByteBuf tampon) {
        return new PaquetMinuterieJeuSousMode3(tampon.readInt());
    }

    public static void traiter(PaquetMinuterieJeuSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.example.mysubmod.sousmodes.sousmode3.client.MinuterieJeuClientSousMode3
                .mettreAJourMinuterie(paquet.secondesRestantes);
        });
        ctx.get().setPacketHandled(true);
    }
}

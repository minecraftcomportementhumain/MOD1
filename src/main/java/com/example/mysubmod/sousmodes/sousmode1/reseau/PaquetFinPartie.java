package com.example.mysubmod.sousmodes.sousmode1.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet envoyé du serveur au client pour notifier que la partie est terminée
 */
public class PaquetFinPartie {

    public PaquetFinPartie() {
    }

    public static void encode(PaquetFinPartie paquet, FriendlyByteBuf tampon) {
        // Aucune donnée à encoder
    }

    public static PaquetFinPartie decode(FriendlyByteBuf tampon) {
        return new PaquetFinPartie();
    }

    public static void traiter(PaquetFinPartie paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Exécuter uniquement côté client
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GestionnairePaquetsClient.gererFinPartie());
        });
        ctx.get().setPacketHandled(true);
    }

    // Classe de gestion des paquets côté client
    public static class GestionnairePaquetsClient {
        public static void gererFinPartie() {
            com.example.mysubmod.sousmodes.sousmode1.client.MinuterieJeuClient.marquerPartieTerminee();
            // Désactiver aussi le minuterie pour arrêter l'affichage
            com.example.mysubmod.sousmodes.sousmode1.client.MinuterieJeuClient.desactiver();
        }
    }
}

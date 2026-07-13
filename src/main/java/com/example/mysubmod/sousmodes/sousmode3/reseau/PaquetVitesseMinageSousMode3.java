package com.example.mysubmod.sousmodes.sousmode3.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet serveur -> client : temps de minage imposé par la config de partie (menu N ›
 * Avancé), en secondes par bloc (0 = vitesses vanilla). Le client DOIT connaître la
 * valeur : la progression de casse est prédite côté client, et un client resté aux
 * vitesses vanilla verrait sa casse rejetée (ou acceptée trop tôt) par le serveur.
 */
public class PaquetVitesseMinageSousMode3 {
    private final float tempsSecondes;

    public PaquetVitesseMinageSousMode3(float tempsSecondes) {
        this.tempsSecondes = tempsSecondes;
    }

    public static void encode(PaquetVitesseMinageSousMode3 paquet, FriendlyByteBuf tampon) {
        tampon.writeFloat(paquet.tempsSecondes);
    }

    public static PaquetVitesseMinageSousMode3 decode(FriendlyByteBuf tampon) {
        return new PaquetVitesseMinageSousMode3(tampon.readFloat());
    }

    public static void traiter(PaquetVitesseMinageSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.example.mysubmod.sousmodes.sousmode3.client.VitesseMinageClientSousMode3
                    .definir(paquet.tempsSecondes));
        });
        ctx.get().setPacketHandled(true);
    }
}

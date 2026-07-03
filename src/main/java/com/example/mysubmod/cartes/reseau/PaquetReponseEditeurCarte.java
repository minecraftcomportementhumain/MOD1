package com.example.mysubmod.cartes.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet serveur -> client : réponse à la demande d'accès à l'outil de création de carte.
 */
public class PaquetReponseEditeurCarte {
    private final boolean accesAccorde;
    private final String occupePar;

    public PaquetReponseEditeurCarte(boolean accesAccorde, String occupePar) {
        this.accesAccorde = accesAccorde;
        this.occupePar = occupePar;
    }

    public static void encode(PaquetReponseEditeurCarte paquet, FriendlyByteBuf tampon) {
        tampon.writeBoolean(paquet.accesAccorde);
        tampon.writeUtf(paquet.occupePar);
    }

    public static PaquetReponseEditeurCarte decode(FriendlyByteBuf tampon) {
        return new PaquetReponseEditeurCarte(tampon.readBoolean(), tampon.readUtf());
    }

    public static void traiter(PaquetReponseEditeurCarte paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.example.mysubmod.cartes.client.GestionnairePaquetsCartes.gererReponseEditeur(
                paquet.accesAccorde, paquet.occupePar);
        });
        ctx.get().setPacketHandled(true);
    }
}

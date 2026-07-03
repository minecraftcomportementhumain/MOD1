package com.example.mysubmod.cartes.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet serveur -> client : résultat d'une sauvegarde de carte.
 */
public class PaquetResultatSauvegardeCarte {
    public static final int CODE_SUCCES = 0;
    public static final int CODE_EXISTE_DEJA = 1;
    public static final int CODE_ERREUR = 2;

    private final int code;
    private final String message;

    public PaquetResultatSauvegardeCarte(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static void encode(PaquetResultatSauvegardeCarte paquet, FriendlyByteBuf tampon) {
        tampon.writeInt(paquet.code);
        tampon.writeUtf(paquet.message);
    }

    public static PaquetResultatSauvegardeCarte decode(FriendlyByteBuf tampon) {
        return new PaquetResultatSauvegardeCarte(tampon.readInt(), tampon.readUtf());
    }

    public static void traiter(PaquetResultatSauvegardeCarte paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.example.mysubmod.cartes.client.GestionnairePaquetsCartes.gererResultatSauvegarde(
                paquet.code, paquet.message);
        });
        ctx.get().setPacketHandled(true);
    }
}

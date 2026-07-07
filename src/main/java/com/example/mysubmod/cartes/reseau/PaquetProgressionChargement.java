package com.example.mysubmod.cartes.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet serveur -> client : progression de la génération de la carte dans le monde.
 * Affiché sous forme de barre de chargement pendant que le sous-mode se prépare.
 *
 * <p>{@code actif} = false masque la barre (fin de génération). {@code pourcent} est
 * l'avancement 0..100. {@code nomCarte} est affiché à côté de la barre.</p>
 */
public class PaquetProgressionChargement {
    private final boolean actif;
    private final int pourcent;
    private final String nomCarte;

    public PaquetProgressionChargement(boolean actif, int pourcent, String nomCarte) {
        this.actif = actif;
        this.pourcent = pourcent;
        this.nomCarte = nomCarte != null ? nomCarte : "";
    }

    public static void encode(PaquetProgressionChargement paquet, FriendlyByteBuf tampon) {
        tampon.writeBoolean(paquet.actif);
        tampon.writeVarInt(paquet.pourcent);
        tampon.writeUtf(paquet.nomCarte);
    }

    public static PaquetProgressionChargement decode(FriendlyByteBuf tampon) {
        boolean actif = tampon.readBoolean();
        int pourcent = tampon.readVarInt();
        String nomCarte = tampon.readUtf();
        return new PaquetProgressionChargement(actif, pourcent, nomCarte);
    }

    public static void traiter(PaquetProgressionChargement paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            com.example.mysubmod.cartes.client.ChargementCarteClient.mettreAJour(
                paquet.actif, paquet.pourcent, paquet.nomCarte));
        ctx.get().setPacketHandled(true);
    }
}

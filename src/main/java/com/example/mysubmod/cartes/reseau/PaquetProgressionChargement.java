package com.example.mysubmod.cartes.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet serveur -> client : progression d'un travail de longue durée sur la carte
 * (génération dans le monde, nettoyage à la désactivation). Affiché sous forme de
 * barre de chargement.
 *
 * <p>{@code actif} = false masque la barre (fin du travail). {@code pourcent} est
 * l'avancement 0..100. {@code titre} est le libellé de la barre (« Génération de la
 * carte », « Nettoyage de la carte »...) et {@code nomCarte} est affiché à côté.</p>
 */
public class PaquetProgressionChargement {
    public static final String TITRE_GENERATION = "Génération de la carte";
    public static final String TITRE_NETTOYAGE = "Nettoyage de la carte";

    private final boolean actif;
    private final int pourcent;
    private final String nomCarte;
    private final String titre;

    public PaquetProgressionChargement(boolean actif, int pourcent, String nomCarte) {
        this(actif, pourcent, nomCarte, TITRE_GENERATION);
    }

    public PaquetProgressionChargement(boolean actif, int pourcent, String nomCarte, String titre) {
        this.actif = actif;
        this.pourcent = pourcent;
        this.nomCarte = nomCarte != null ? nomCarte : "";
        this.titre = titre != null ? titre : TITRE_GENERATION;
    }

    public static void encode(PaquetProgressionChargement paquet, FriendlyByteBuf tampon) {
        tampon.writeBoolean(paquet.actif);
        tampon.writeVarInt(paquet.pourcent);
        tampon.writeUtf(paquet.nomCarte);
        tampon.writeUtf(paquet.titre);
    }

    public static PaquetProgressionChargement decode(FriendlyByteBuf tampon) {
        boolean actif = tampon.readBoolean();
        int pourcent = tampon.readVarInt();
        String nomCarte = tampon.readUtf();
        String titre = tampon.readUtf();
        return new PaquetProgressionChargement(actif, pourcent, nomCarte, titre);
    }

    public static void traiter(PaquetProgressionChargement paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            com.example.mysubmod.cartes.client.ChargementCarteClient.mettreAJour(
                paquet.actif, paquet.pourcent, paquet.nomCarte, paquet.titre));
        ctx.get().setPacketHandled(true);
    }
}

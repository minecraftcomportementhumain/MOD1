package com.example.mysubmod.cartes.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paquet serveur -> client : liste des cartes sauvegardées + carte active.
 */
public class PaquetListeCartes {
    public static final int BUT_AUCUN = 0;               // Mise à jour silencieuse des données client
    public static final int BUT_LISTE_SELECTION = 1;     // Ouvrir l'écran « Liste des cartes »
    public static final int BUT_CHARGEMENT_EDITEUR = 2;  // Ouvrir la liste pour charger une carte dans l'éditeur

    private final List<String> cartes;
    private final String carteSelectionnee;
    private final int but;

    public PaquetListeCartes(List<String> cartes, String carteSelectionnee, int but) {
        this.cartes = cartes;
        this.carteSelectionnee = carteSelectionnee;
        this.but = but;
    }

    public static void encode(PaquetListeCartes paquet, FriendlyByteBuf tampon) {
        tampon.writeInt(paquet.cartes.size());
        for (String carte : paquet.cartes) {
            tampon.writeUtf(carte);
        }
        tampon.writeUtf(paquet.carteSelectionnee);
        tampon.writeInt(paquet.but);
    }

    public static PaquetListeCartes decode(FriendlyByteBuf tampon) {
        int taille = tampon.readInt();
        List<String> cartes = new ArrayList<>();
        for (int i = 0; i < taille; i++) {
            cartes.add(tampon.readUtf());
        }
        String carteSelectionnee = tampon.readUtf();
        int but = tampon.readInt();
        return new PaquetListeCartes(cartes, carteSelectionnee, but);
    }

    public static void traiter(PaquetListeCartes paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.example.mysubmod.cartes.client.GestionnairePaquetsCartes.gererListeCartes(
                paquet.cartes, paquet.carteSelectionnee, paquet.but);
        });
        ctx.get().setPacketHandled(true);
    }
}

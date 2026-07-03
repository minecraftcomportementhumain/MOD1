package com.example.mysubmod.cartes.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paquet serveur -> client : le lancement d'un sous-mode a été refusé
 * (ex. bonbons non typés pour le Sous-mode 2). Affiche une fenêtre modale.
 */
public class PaquetRefusLancement {
    private final String titre;
    private final List<String> lignes;

    public PaquetRefusLancement(String titre, List<String> lignes) {
        this.titre = titre;
        this.lignes = lignes;
    }

    public static void encode(PaquetRefusLancement paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.titre);
        tampon.writeInt(paquet.lignes.size());
        for (String ligne : paquet.lignes) {
            tampon.writeUtf(ligne);
        }
    }

    public static PaquetRefusLancement decode(FriendlyByteBuf tampon) {
        String titre = tampon.readUtf();
        int taille = tampon.readInt();
        List<String> lignes = new ArrayList<>();
        for (int i = 0; i < taille; i++) {
            lignes.add(tampon.readUtf());
        }
        return new PaquetRefusLancement(titre, lignes);
    }

    public static void traiter(PaquetRefusLancement paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Appel statique vers la classe client (chargement paresseux : jamais exécuté côté serveur)
            com.example.mysubmod.cartes.client.GestionnairePaquetsCartes.afficherRefusLancement(
                paquet.titre, paquet.lignes);
        });
        ctx.get().setPacketHandled(true);
    }
}

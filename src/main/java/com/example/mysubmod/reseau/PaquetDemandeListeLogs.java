package com.example.mysubmod.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet pour demander la liste des fichiers journaux disponibles
 */
public class PaquetDemandeListeLogs {
    public PaquetDemandeListeLogs() {
    }

    public PaquetDemandeListeLogs(FriendlyByteBuf tampon) {
        // Aucune donnée à lire
    }

    public void toBytes(FriendlyByteBuf tampon) {
        // Aucune donnée à écrire
    }

    public boolean traiter(Supplier<NetworkEvent.Context> fournisseur) {
        NetworkEvent.Context contexte = fournisseur.get();
        contexte.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer joueur = contexte.getSender();
            if (joueur != null && com.example.mysubmod.sousmodes.GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                com.example.mysubmod.serveur.GestionnaireLogs.envoyerListeLogs(joueur);
            }
        });
        return true;
    }
}

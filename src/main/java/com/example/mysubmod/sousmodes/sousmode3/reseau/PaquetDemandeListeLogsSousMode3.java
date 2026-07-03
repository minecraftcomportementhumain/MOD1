package com.example.mysubmod.sousmodes.sousmode3.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet client -> serveur : demander la liste des journaux du Sous-mode 3
 * (même système de logs que pour les autres sous-modes).
 */
public class PaquetDemandeListeLogsSousMode3 {

    public PaquetDemandeListeLogsSousMode3() {
    }

    public PaquetDemandeListeLogsSousMode3(FriendlyByteBuf tampon) {
    }

    public void toBytes(FriendlyByteBuf tampon) {
    }

    public boolean traiter(Supplier<NetworkEvent.Context> fournisseur) {
        NetworkEvent.Context contexte = fournisseur.get();
        contexte.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer joueur = contexte.getSender();
            if (joueur != null && com.example.mysubmod.sousmodes.GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                com.example.mysubmod.serveur.GestionnaireLogs.envoyerListeLogs(joueur, 3);
            }
        });
        return true;
    }
}

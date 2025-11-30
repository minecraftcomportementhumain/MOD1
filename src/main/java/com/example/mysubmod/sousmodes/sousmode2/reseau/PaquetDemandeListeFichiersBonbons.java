package com.example.mysubmod.sousmodes.sousmode2.reseau;

import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode2.GestionnaireSousMode2;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PaquetDemandeListeFichiersBonbons {
    public PaquetDemandeListeFichiersBonbons() {
    }

    public PaquetDemandeListeFichiersBonbons(FriendlyByteBuf tampon) {
        // Aucune donnée à lire
    }

    public void toBytes(FriendlyByteBuf tampon) {
        // Aucune donnée à écrire
    }

    public boolean traiter(Supplier<NetworkEvent.Context> fournisseur) {
        NetworkEvent.Context contexte = fournisseur.get();
        contexte.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer joueur = contexte.getSender();
            if (joueur != null && GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                // Vérifier si une partie est déjà active ou si la phase de sélection est active
                if (GestionnaireSousMode2.getInstance().estPartieActive() || GestionnaireSousMode2.getInstance().estPhaseSelection()) {
                    joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§cImpossible de rafraîchir - Une partie est déjà en cours!"));
                    return;
                }
                GestionnaireSousMode2.getInstance().envoyerListeFichiersBonbonsAuJoueur(joueur);
            }
        });
        return true;
    }
}

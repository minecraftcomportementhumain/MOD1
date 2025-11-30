package com.example.mysubmod.sousmodes.sousmode1.reseau;

import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode1.GestionnaireSousMode1;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PaquetSelectionFichierBonbons {
    private final String fichierSelectionne;

    public PaquetSelectionFichierBonbons(String fichierSelectionne) {
        this.fichierSelectionne = fichierSelectionne;
    }

    public static void encoder(PaquetSelectionFichierBonbons paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.fichierSelectionne);
    }

    public static PaquetSelectionFichierBonbons decoder(FriendlyByteBuf tampon) {
        return new PaquetSelectionFichierBonbons(tampon.readUtf());
    }

    public static void traiter(PaquetSelectionFichierBonbons paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur != null) {
                // Vérifier les permissions admin côté serveur
                if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                    // Vérifier si une partie est déjà active ou si la phase de sélection est active
                    if (GestionnaireSousMode1.getInstance().estPartieActive() || GestionnaireSousMode1.getInstance().estPhaseSelection()) {
                        joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cImpossible de sélectionner un fichier - Une partie est déjà en cours!"));
                        return;
                    }

                    // Vérifier s'il y a au moins un joueur authentifié non-admin connecté
                    java.util.List<ServerPlayer> joueursAuthentifies = com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(joueur.getServer());
                    boolean auMoinsUnJoueurNonAdmin = joueursAuthentifies.stream()
                        .anyMatch(j -> !GestionnaireSousModes.getInstance().estAdmin(j));

                    if (!auMoinsUnJoueurNonAdmin) {
                        joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cImpossible de démarrer la partie - Aucun joueur authentifié connecté!"));
                        return;
                    }

                    GestionnaireSousMode1.getInstance().definirFichierApparitionBonbons(paquet.fichierSelectionne);
                    // Démarrer la sélection d'île après que le fichier soit défini
                    GestionnaireSousMode1.getInstance().demarrerSelectionIle(joueur.getServer());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String obtenirFichierSelectionne() {
        return fichierSelectionne;
    }
}

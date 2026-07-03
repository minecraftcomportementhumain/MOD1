package com.example.mysubmod.reseau;

import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Paquet envoyé du client au serveur pour demander l'ouverture de l'écran de contrôle des sous-modes
 */
public class PaquetDemandeEcranControleSousMode {

    public PaquetDemandeEcranControleSousMode() {
    }

    public static void encode(PaquetDemandeEcranControleSousMode paquet, FriendlyByteBuf tampon) {
        // Aucune donnée à encoder
    }

    public static PaquetDemandeEcranControleSousMode decode(FriendlyByteBuf tampon) {
        return new PaquetDemandeEcranControleSousMode();
    }

    public static void traiter(PaquetDemandeEcranControleSousMode paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur != null) {
                // Utiliser le même compte que TAB (filtré par MixinComptageListeJoueurs)
                // Ceci exclut les joueurs non authentifiés et les admins authentifiés
                int nombreJoueurs = joueur.server.getPlayerList().getPlayerCount();

                // Carte active du système de cartes (affichée dans le menu admin)
                String carteActive = com.example.mysubmod.cartes.GestionnaireCartes.getInstance().obtenirCarteSelectionnee();

                // Envoyer la réponse avec le nombre de joueurs et la carte active
                GestionnaireReseau.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> joueur),
                    new PaquetEcranControleSousMode(nombreJoueurs, carteActive != null ? carteActive : "")
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

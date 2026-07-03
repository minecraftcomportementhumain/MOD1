package com.example.mysubmod.cartes.reseau;

import com.example.mysubmod.cartes.GestionnaireCartes;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Paquet client -> serveur : supprimer définitivement une carte.
 * Le serveur renvoie la liste mise à jour selon le contexte de l'écran client
 * (liste de sélection ou chargement dans l'éditeur).
 */
public class PaquetSuppressionCarte {
    private final String nomCarte;
    private final int butReponse; // Voir PaquetListeCartes.BUT_*

    public PaquetSuppressionCarte(String nomCarte, int butReponse) {
        this.nomCarte = nomCarte;
        this.butReponse = butReponse;
    }

    public static void encode(PaquetSuppressionCarte paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.nomCarte);
        tampon.writeInt(paquet.butReponse);
    }

    public static PaquetSuppressionCarte decode(FriendlyByteBuf tampon) {
        return new PaquetSuppressionCarte(tampon.readUtf(), tampon.readInt());
    }

    public static void traiter(PaquetSuppressionCarte paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null || !GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            GestionnaireCartes gestionnaire = GestionnaireCartes.getInstance();
            if (gestionnaire.supprimerCarte(paquet.nomCarte)) {
                joueur.sendSystemMessage(Component.literal("§aCarte supprimée : " + paquet.nomCarte));
            } else {
                joueur.sendSystemMessage(Component.literal("§cImpossible de supprimer la carte : " + paquet.nomCarte));
            }

            // Rafraîchir l'écran de liste uniquement en mode sélection (l'éditeur gère sa liste localement)
            if (paquet.butReponse == PaquetListeCartes.BUT_LISTE_SELECTION) {
                String carteActive = gestionnaire.obtenirCarteSelectionnee();
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new PaquetListeCartes(gestionnaire.obtenirCartesDisponibles(),
                        carteActive != null ? carteActive : "", PaquetListeCartes.BUT_LISTE_SELECTION));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

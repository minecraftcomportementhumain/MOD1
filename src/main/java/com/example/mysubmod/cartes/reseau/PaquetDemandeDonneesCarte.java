package com.example.mysubmod.cartes.reseau;

import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.cartes.GestionnaireCartes;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquet client -> serveur : demander les données complètes d'une carte
 * (pour la charger dans l'éditeur). Le serveur répond en morceaux via PaquetDonneesCarte.
 */
public class PaquetDemandeDonneesCarte {
    private static final int TAILLE_MORCEAU = 30000;

    private final String nomCarte;

    public PaquetDemandeDonneesCarte(String nomCarte) {
        this.nomCarte = nomCarte;
    }

    public static void encode(PaquetDemandeDonneesCarte paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.nomCarte);
    }

    public static PaquetDemandeDonneesCarte decode(FriendlyByteBuf tampon) {
        return new PaquetDemandeDonneesCarte(tampon.readUtf());
    }

    public static void traiter(PaquetDemandeDonneesCarte paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null || !GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            CarteDonnees carte = GestionnaireCartes.getInstance().chargerCarte(paquet.nomCarte);
            if (carte == null) {
                joueur.sendSystemMessage(Component.literal("§cCarte introuvable : " + paquet.nomCarte));
                return;
            }

            byte[] donnees = carte.versJson().getBytes(StandardCharsets.UTF_8);
            UUID idTransfert = UUID.randomUUID();
            int nombreTotalMorceaux = Math.max(1, (int) Math.ceil((double) donnees.length / TAILLE_MORCEAU));

            for (int i = 0; i < nombreTotalMorceaux; i++) {
                int debut = i * TAILLE_MORCEAU;
                int longueur = Math.min(donnees.length - debut, TAILLE_MORCEAU);
                byte[] morceau = new byte[longueur];
                System.arraycopy(donnees, debut, morceau, 0, longueur);
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new PaquetDonneesCarte(idTransfert, nombreTotalMorceaux, i, morceau));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

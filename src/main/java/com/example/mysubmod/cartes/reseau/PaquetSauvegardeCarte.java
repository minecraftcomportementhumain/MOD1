package com.example.mysubmod.cartes.reseau;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.cartes.GestionnaireCartes;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquet client -> serveur : sauvegarder une carte (JSON envoyé en morceaux).
 */
public class PaquetSauvegardeCarte {
    private final UUID idTransfert;
    private final int nombreTotalMorceaux;
    private final int indexMorceau;
    private final byte[] donneesMorceau;
    private final boolean ecraserConfirme;

    public PaquetSauvegardeCarte(UUID idTransfert, int nombreTotalMorceaux, int indexMorceau,
                                 byte[] donneesMorceau, boolean ecraserConfirme) {
        this.idTransfert = idTransfert;
        this.nombreTotalMorceaux = nombreTotalMorceaux;
        this.indexMorceau = indexMorceau;
        this.donneesMorceau = donneesMorceau;
        this.ecraserConfirme = ecraserConfirme;
    }

    public static void encode(PaquetSauvegardeCarte paquet, FriendlyByteBuf tampon) {
        tampon.writeUUID(paquet.idTransfert);
        tampon.writeInt(paquet.nombreTotalMorceaux);
        tampon.writeInt(paquet.indexMorceau);
        tampon.writeInt(paquet.donneesMorceau.length);
        tampon.writeBytes(paquet.donneesMorceau);
        tampon.writeBoolean(paquet.ecraserConfirme);
    }

    public static PaquetSauvegardeCarte decode(FriendlyByteBuf tampon) {
        UUID idTransfert = tampon.readUUID();
        int nombreTotalMorceaux = tampon.readInt();
        int indexMorceau = tampon.readInt();
        int longueur = tampon.readInt();
        byte[] donnees = new byte[longueur];
        tampon.readBytes(donnees);
        boolean ecraserConfirme = tampon.readBoolean();
        return new PaquetSauvegardeCarte(idTransfert, nombreTotalMorceaux, indexMorceau, donnees, ecraserConfirme);
    }

    public static void traiter(PaquetSauvegardeCarte paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null || !GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            String json = GestionnaireCartes.getInstance().gererMorceauCarte(
                paquet.idTransfert, paquet.indexMorceau, paquet.nombreTotalMorceaux, paquet.donneesMorceau);
            if (json == null) {
                return; // En attente d'autres morceaux
            }

            try {
                CarteDonnees carte = CarteDonnees.depuisJson(json);
                carte.nom = CarteDonnees.assainirNom(carte.nom);

                GestionnaireCartes gestionnaire = GestionnaireCartes.getInstance();

                // Demander confirmation si une carte du même nom existe déjà
                if (gestionnaire.carteExiste(carte.nom) && !paquet.ecraserConfirme) {
                    GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                        new PaquetResultatSauvegardeCarte(PaquetResultatSauvegardeCarte.CODE_EXISTE_DEJA,
                            carte.nom));
                    return;
                }

                String erreur = gestionnaire.sauvegarderCarte(carte);
                if (erreur == null) {
                    GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                        new PaquetResultatSauvegardeCarte(PaquetResultatSauvegardeCarte.CODE_SUCCES, carte.nom));
                } else {
                    GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                        new PaquetResultatSauvegardeCarte(PaquetResultatSauvegardeCarte.CODE_ERREUR, erreur));
                }
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la réception d'une sauvegarde de carte", e);
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new PaquetResultatSauvegardeCarte(PaquetResultatSauvegardeCarte.CODE_ERREUR,
                        "Données de carte invalides"));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

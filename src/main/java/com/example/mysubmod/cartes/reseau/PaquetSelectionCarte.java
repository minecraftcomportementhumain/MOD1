package com.example.mysubmod.cartes.reseau;

import com.example.mysubmod.cartes.GestionnaireCartes;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.reseau.PaquetEcranControleSousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Paquet client -> serveur : sélectionner une carte (nom vide = désélectionner).
 * Après la sélection, le serveur rouvre le menu admin avec la carte active affichée.
 */
public class PaquetSelectionCarte {
    private final String nomCarte;
    private final boolean rouvrirMenuAdmin;

    public PaquetSelectionCarte(String nomCarte, boolean rouvrirMenuAdmin) {
        this.nomCarte = nomCarte;
        this.rouvrirMenuAdmin = rouvrirMenuAdmin;
    }

    public static void encode(PaquetSelectionCarte paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.nomCarte);
        tampon.writeBoolean(paquet.rouvrirMenuAdmin);
    }

    public static PaquetSelectionCarte decode(FriendlyByteBuf tampon) {
        return new PaquetSelectionCarte(tampon.readUtf(), tampon.readBoolean());
    }

    public static void traiter(PaquetSelectionCarte paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null || !GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            GestionnaireCartes.getInstance().definirCarteSelectionnee(
                paquet.nomCarte.isEmpty() ? null : paquet.nomCarte);

            if (paquet.rouvrirMenuAdmin) {
                // Revenir au menu admin avec la carte active affichée
                int nombreJoueurs = joueur.server.getPlayerList().getPlayerCount();
                String carteActive = GestionnaireCartes.getInstance().obtenirCarteSelectionnee();
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new PaquetEcranControleSousMode(nombreJoueurs, carteActive != null ? carteActive : ""));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

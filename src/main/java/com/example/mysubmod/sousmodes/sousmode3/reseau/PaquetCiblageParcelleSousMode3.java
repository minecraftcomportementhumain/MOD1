package com.example.mysubmod.sousmodes.sousmode3.reseau;

import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet client -> serveur : rapporte le ciblage de la flèche de navigation pour la
 * journalisation CSV de recherche. Le ciblage est un état purement client — sans ce paquet,
 * le serveur ignorerait quelle parcelle un joueur vise et quand. Aucun effet de jeu :
 * uniquement une ligne CIBLAGE_PARCELLE dans le journal du joueur.
 */
public class PaquetCiblageParcelleSousMode3 {

    /** Parcelle choisie dans l'écran [N]. */
    public static final String RAISON_CHOISIE = "CHOISIE";
    /** Flèche désactivée par le joueur (bouton « Désactiver la flèche »). */
    public static final String RAISON_DESACTIVEE = "DESACTIVEE";
    /** Extinction automatique de la flèche à l'arrivée au but. */
    public static final String RAISON_ARRIVEE = "ARRIVEE";

    private final String zone;   // vide quand la flèche est désactivée
    private final String raison;

    public PaquetCiblageParcelleSousMode3(String zone, String raison) {
        this.zone = zone;
        this.raison = raison;
    }

    public static void encode(PaquetCiblageParcelleSousMode3 paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.zone, 64);
        tampon.writeUtf(paquet.raison, 16);
    }

    public static PaquetCiblageParcelleSousMode3 decode(FriendlyByteBuf tampon) {
        return new PaquetCiblageParcelleSousMode3(tampon.readUtf(64), tampon.readUtf(16));
    }

    public static void traiter(PaquetCiblageParcelleSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null) {
                return;
            }
            if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_3) {
                return;
            }
            var enregistreur = GestionnaireSousMode3.getInstance().obtenirEnregistreurDonnees();
            if (enregistreur == null) {
                return; // Aucune session d'enregistrement en cours
            }
            // Borner la raison aux valeurs connues (client modifié) — la zone, texte libre,
            // est neutralisée par l'échappement CSV de l'enregistreur.
            String raison = paquet.raison;
            if (!RAISON_CHOISIE.equals(raison) && !RAISON_DESACTIVEE.equals(raison)
                && !RAISON_ARRIVEE.equals(raison)) {
                raison = "INCONNUE";
            }
            enregistreur.enregistrerCiblageParcelle(joueur, paquet.zone, raison);
        });
        ctx.get().setPacketHandled(true);
    }
}

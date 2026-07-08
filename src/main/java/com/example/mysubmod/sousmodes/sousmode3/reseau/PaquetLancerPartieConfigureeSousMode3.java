package com.example.mysubmod.sousmodes.sousmode3.reseau;

import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode3.ConfigPartieSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

/**
 * Paquet client -> serveur : un admin lance la partie du Sous-mode 3 (menu N) en fournissant
 * les conditions de partie choisies via les cases à cocher. Remplace le paquet vide historique
 * pour le seul Sous-mode 3 ; les Sous-modes 1 et 2 continuent d'utiliser
 * {@link PaquetLancerPartieSousMode3}.
 */
public class PaquetLancerPartieConfigureeSousMode3 {

    private final ConfigPartieSousMode3 config;

    public PaquetLancerPartieConfigureeSousMode3(ConfigPartieSousMode3 config) {
        this.config = config;
    }

    public static void encode(PaquetLancerPartieConfigureeSousMode3 paquet, FriendlyByteBuf tampon) {
        paquet.config.ecrire(tampon);
    }

    public static PaquetLancerPartieConfigureeSousMode3 decode(FriendlyByteBuf tampon) {
        return new PaquetLancerPartieConfigureeSousMode3(ConfigPartieSousMode3.lire(tampon));
    }

    public static void traiter(PaquetLancerPartieConfigureeSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null || !GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }
            if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_3) {
                return;
            }

            ConfigPartieSousMode3 config = paquet.config;
            config.borner();

            // Options du Groupe 3 pas encore implémentées : neutralisées côté serveur par sécurité
            // (l'écran ne les propose pas ; ceci évite toute activation accidentelle).
            config.specialisation = false;
            config.selectionZoneDepart = false;

            // Garde-fou : une partie doit pouvoir se terminer d'elle-même.
            if (!config.peutSeTerminer()) {
                GestionnaireReseau.INSTANCE.sendTo(
                    new com.example.mysubmod.cartes.reseau.PaquetRefusLancement(
                        "Configuration impossible",
                        List.of(
                            "§eLa partie ne pourrait jamais se terminer.",
                            "§7Avec « Sans limite de temps », activez la dégradation",
                            "§7de la santé, désactivez la réapparition et la",
                            "§7régénération naturelle, ou remettez une limite de temps.")),
                    joueur.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                return;
            }

            GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();

            // Validation dépendante de la carte (ex. spécialisation sans bonbons typés).
            List<String> problemes = gestionnaire.validerConfigContreCarte(config);
            if (!problemes.isEmpty()) {
                java.util.List<String> lignes = new java.util.ArrayList<>();
                lignes.add("§eCes options sont incompatibles avec la carte :");
                for (String p : problemes) {
                    lignes.add("§7- " + p);
                }
                GestionnaireReseau.INSTANCE.sendTo(
                    new com.example.mysubmod.cartes.reseau.PaquetRefusLancement("Configuration impossible", lignes),
                    joueur.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                return;
            }

            // Ne remplacer la config active que si le lancement est accepté : un second admin
            // qui clique pendant le décompte ne doit pas écraser la config de la partie en cours.
            ConfigPartieSousMode3 configPrecedente = gestionnaire.obtenirConfig();
            gestionnaire.definirConfig(config);
            if (!gestionnaire.lancerPartie(joueur.server, joueur)) {
                gestionnaire.definirConfig(configPrecedente);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

package com.example.mysubmod.reseau;

import com.example.mysubmod.MonSubMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Gestionnaire de réseau pour l'enregistrement de tous les paquets du mod
 */
public class GestionnaireReseau {
    // Version du protocole réseau = version exacte du build (mods.toml). Le canal exige
    // que le client ait EXACTEMENT la même version que le serveur, sinon la connexion est
    // refusée dès le handshake FML -> un client d'une version différente ne peut pas entrer.
    private static String VERSION_PROTOCOLE = "1";
    public static SimpleChannel INSTANCE;

    private static int idPaquet = 0;

    /** Version du mod (unique par build) utilisée comme version du protocole réseau. */
    private static String versionMod() {
        try {
            return net.minecraftforge.fml.ModList.get()
                .getModContainerById(MonSubMod.ID_MOD)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("1");
        } catch (Throwable t) {
            return "1";
        }
    }

    /** Version exacte de ce build, comparée à celle des clients. */
    public static String versionProtocole() {
        return VERSION_PROTOCOLE;
    }

    public static void init() {
        VERSION_PROTOCOLE = versionMod();
        // Version du protocole = version exacte du build. Le canal exige que le client ait
        // EXACTEMENT la même version, sinon la connexion est refusée dès le handshake FML.
        INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(MonSubMod.MOD_ID, "main"),
            () -> VERSION_PROTOCOLE,
            VERSION_PROTOCOLE::equals,
            VERSION_PROTOCOLE::equals
        );
        MonSubMod.JOURNALISEUR.info("Canal réseau initialisé (version protocole = {})", VERSION_PROTOCOLE);

        INSTANCE.registerMessage(
            idPaquet++,
            PaquetChangementSousMode.class,
            PaquetChangementSousMode::encode,
            PaquetChangementSousMode::decode,
            PaquetChangementSousMode::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            PaquetDemandeSousMode.class,
            PaquetDemandeSousMode::encode,
            PaquetDemandeSousMode::decode,
            PaquetDemandeSousMode::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            PaquetDemandeEcranControleSousMode.class,
            PaquetDemandeEcranControleSousMode::encode,
            PaquetDemandeEcranControleSousMode::decode,
            PaquetDemandeEcranControleSousMode::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            PaquetEcranControleSousMode.class,
            PaquetEcranControleSousMode::encode,
            PaquetEcranControleSousMode::decode,
            PaquetEcranControleSousMode::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            PaquetStatutAdmin.class,
            PaquetStatutAdmin::encode,
            PaquetStatutAdmin::decode,
            PaquetStatutAdmin::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            PaquetListeLogs.class,
            PaquetListeLogs::toBytes,
            PaquetListeLogs::new,
            PaquetListeLogs::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            PaquetTelechargementLogs.class,
            PaquetTelechargementLogs::toBytes,
            PaquetTelechargementLogs::new,
            PaquetTelechargementLogs::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            PaquetSuppressionLogs.class,
            PaquetSuppressionLogs::toBytes,
            PaquetSuppressionLogs::new,
            PaquetSuppressionLogs::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            PaquetDonneesLogs.class,
            PaquetDonneesLogs::toBytes,
            PaquetDonneesLogs::new,
            PaquetDonneesLogs::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // Paquets d'authentification administrateur
        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.authentification.PaquetDemandeAuthAdmin.class,
            com.example.mysubmod.authentification.PaquetDemandeAuthAdmin::encode,
            com.example.mysubmod.authentification.PaquetDemandeAuthAdmin::decode,
            com.example.mysubmod.authentification.PaquetDemandeAuthAdmin::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.authentification.PaquetAuthAdmin.class,
            com.example.mysubmod.authentification.PaquetAuthAdmin::encode,
            com.example.mysubmod.authentification.PaquetAuthAdmin::decode,
            com.example.mysubmod.authentification.PaquetAuthAdmin::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.authentification.PaquetReponseAuthAdmin.class,
            com.example.mysubmod.authentification.PaquetReponseAuthAdmin::encode,
            com.example.mysubmod.authentification.PaquetReponseAuthAdmin::decode,
            com.example.mysubmod.authentification.PaquetReponseAuthAdmin::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // Paquets de jetons de file d'attente
        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.authentification.PaquetJetonFile.class,
            com.example.mysubmod.authentification.PaquetJetonFile::encode,
            com.example.mysubmod.authentification.PaquetJetonFile::decode,
            com.example.mysubmod.authentification.PaquetJetonFile::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.authentification.PaquetDemandeJetonFile.class,
            com.example.mysubmod.authentification.PaquetDemandeJetonFile::encode,
            com.example.mysubmod.authentification.PaquetDemandeJetonFile::decode,
            com.example.mysubmod.authentification.PaquetDemandeJetonFile::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.authentification.PaquetVerificationJetonFile.class,
            com.example.mysubmod.authentification.PaquetVerificationJetonFile::encode,
            com.example.mysubmod.authentification.PaquetVerificationJetonFile::decode,
            com.example.mysubmod.authentification.PaquetVerificationJetonFile::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // ==================== Paquets du système de cartes ====================
        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetDemandeListeCartes.class,
            com.example.mysubmod.cartes.reseau.PaquetDemandeListeCartes::encode,
            com.example.mysubmod.cartes.reseau.PaquetDemandeListeCartes::decode,
            com.example.mysubmod.cartes.reseau.PaquetDemandeListeCartes::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetListeCartes.class,
            com.example.mysubmod.cartes.reseau.PaquetListeCartes::encode,
            com.example.mysubmod.cartes.reseau.PaquetListeCartes::decode,
            com.example.mysubmod.cartes.reseau.PaquetListeCartes::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetSelectionCarte.class,
            com.example.mysubmod.cartes.reseau.PaquetSelectionCarte::encode,
            com.example.mysubmod.cartes.reseau.PaquetSelectionCarte::decode,
            com.example.mysubmod.cartes.reseau.PaquetSelectionCarte::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetSuppressionCarte.class,
            com.example.mysubmod.cartes.reseau.PaquetSuppressionCarte::encode,
            com.example.mysubmod.cartes.reseau.PaquetSuppressionCarte::decode,
            com.example.mysubmod.cartes.reseau.PaquetSuppressionCarte::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetDemandeEditeurCarte.class,
            com.example.mysubmod.cartes.reseau.PaquetDemandeEditeurCarte::encode,
            com.example.mysubmod.cartes.reseau.PaquetDemandeEditeurCarte::decode,
            com.example.mysubmod.cartes.reseau.PaquetDemandeEditeurCarte::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetReponseEditeurCarte.class,
            com.example.mysubmod.cartes.reseau.PaquetReponseEditeurCarte::encode,
            com.example.mysubmod.cartes.reseau.PaquetReponseEditeurCarte::decode,
            com.example.mysubmod.cartes.reseau.PaquetReponseEditeurCarte::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetFermetureEditeurCarte.class,
            com.example.mysubmod.cartes.reseau.PaquetFermetureEditeurCarte::encode,
            com.example.mysubmod.cartes.reseau.PaquetFermetureEditeurCarte::decode,
            com.example.mysubmod.cartes.reseau.PaquetFermetureEditeurCarte::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetSauvegardeCarte.class,
            com.example.mysubmod.cartes.reseau.PaquetSauvegardeCarte::encode,
            com.example.mysubmod.cartes.reseau.PaquetSauvegardeCarte::decode,
            com.example.mysubmod.cartes.reseau.PaquetSauvegardeCarte::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetResultatSauvegardeCarte.class,
            com.example.mysubmod.cartes.reseau.PaquetResultatSauvegardeCarte::encode,
            com.example.mysubmod.cartes.reseau.PaquetResultatSauvegardeCarte::decode,
            com.example.mysubmod.cartes.reseau.PaquetResultatSauvegardeCarte::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetDemandeDonneesCarte.class,
            com.example.mysubmod.cartes.reseau.PaquetDemandeDonneesCarte::encode,
            com.example.mysubmod.cartes.reseau.PaquetDemandeDonneesCarte::decode,
            com.example.mysubmod.cartes.reseau.PaquetDemandeDonneesCarte::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetDonneesCarte.class,
            com.example.mysubmod.cartes.reseau.PaquetDonneesCarte::encode,
            com.example.mysubmod.cartes.reseau.PaquetDonneesCarte::decode,
            com.example.mysubmod.cartes.reseau.PaquetDonneesCarte::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // ==================== Paquets Sous-mode 3 ====================
        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetMinuterieJeuSousMode3.class,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetMinuterieJeuSousMode3::encode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetMinuterieJeuSousMode3::decode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetMinuterieJeuSousMode3::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetFinPartieSousMode3.class,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetFinPartieSousMode3::encode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetFinPartieSousMode3::decode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetFinPartieSousMode3::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetZonesSousMode3.class,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetZonesSousMode3::encode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetZonesSousMode3::decode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetZonesSousMode3::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetDemandeListeLogsSousMode3.class,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetDemandeListeLogsSousMode3::toBytes,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetDemandeListeLogsSousMode3::new,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetDemandeListeLogsSousMode3::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // ==================== Parties sur carte (partagés avec le Sous-mode 3) ====================
        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetSelectionZoneDepart.class,
            com.example.mysubmod.cartes.reseau.PaquetSelectionZoneDepart::encode,
            com.example.mysubmod.cartes.reseau.PaquetSelectionZoneDepart::decode,
            com.example.mysubmod.cartes.reseau.PaquetSelectionZoneDepart::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetChoixZoneDepart.class,
            com.example.mysubmod.cartes.reseau.PaquetChoixZoneDepart::encode,
            com.example.mysubmod.cartes.reseau.PaquetChoixZoneDepart::decode,
            com.example.mysubmod.cartes.reseau.PaquetChoixZoneDepart::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetRefusLancement.class,
            com.example.mysubmod.cartes.reseau.PaquetRefusLancement::encode,
            com.example.mysubmod.cartes.reseau.PaquetRefusLancement::decode,
            com.example.mysubmod.cartes.reseau.PaquetRefusLancement::traiter
        );

        // Progression de la génération de carte (barre de chargement) — serveur -> client
        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.cartes.reseau.PaquetProgressionChargement.class,
            com.example.mysubmod.cartes.reseau.PaquetProgressionChargement::encode,
            com.example.mysubmod.cartes.reseau.PaquetProgressionChargement::decode,
            com.example.mysubmod.cartes.reseau.PaquetProgressionChargement::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // Lancement configuré du Sous-mode 3 (menu N avec cases à cocher) — client -> serveur
        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetLancerPartieConfigureeSousMode3.class,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetLancerPartieConfigureeSousMode3::encode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetLancerPartieConfigureeSousMode3::decode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetLancerPartieConfigureeSousMode3::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // Faits sur la carte active du Sous-mode 3 (grisage des options du menu N) — serveur -> client
        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetFaitsCarteSousMode3.class,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetFaitsCarteSousMode3::encode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetFaitsCarteSousMode3::decode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetFaitsCarteSousMode3::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // Spécialisation Bleu/Rouge du Sous-mode 3 (option du menu N) — serveur -> client
        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetSyncSpecialisation.class,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetSyncSpecialisation::encode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetSyncSpecialisation::decode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetSyncSpecialisation::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetSynchronisationPenalite.class,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetSynchronisationPenalite::encode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetSynchronisationPenalite::decode,
            com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetSynchronisationPenalite::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    /**
     * Méthode utilitaire pour envoyer un paquet à un joueur spécifique
     */
    public static void sendToPlayer(Object paquet, net.minecraft.server.level.ServerPlayer joueur) {
        INSTANCE.sendTo(paquet, joueur.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
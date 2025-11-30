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
    private static final String VERSION_PROTOCOLE = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(MonSubMod.MOD_ID, "main"),
        () -> VERSION_PROTOCOLE,
        VERSION_PROTOCOLE::equals,
        VERSION_PROTOCOLE::equals
    );

    private static int idPaquet = 0;

    public static void init() {
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
            PaquetStatutAdmin::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSelectionIle.class,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSelectionIle::encoder,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSelectionIle::decoder,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSelectionIle::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetChoixIle.class,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetChoixIle::encode,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetChoixIle::decode,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetChoixIle::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMinuterieJeu.class,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMinuterieJeu::encode,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMinuterieJeu::decode,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMinuterieJeu::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetListeFichiersBonbons.class,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetListeFichiersBonbons::encoder,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetListeFichiersBonbons::decoder,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetListeFichiersBonbons::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSelectionFichierBonbons.class,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSelectionFichierBonbons::encoder,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSelectionFichierBonbons::decoder,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSelectionFichierBonbons::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetTeleversementFichierBonbons.class,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetTeleversementFichierBonbons::encode,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetTeleversementFichierBonbons::decode,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetTeleversementFichierBonbons::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSuppressionFichierBonbons.class,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSuppressionFichierBonbons::encode,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSuppressionFichierBonbons::decode,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetSuppressionFichierBonbons::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMiseAJourCompteurBonbons.class,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMiseAJourCompteurBonbons::encoder,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMiseAJourCompteurBonbons::new,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMiseAJourCompteurBonbons::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetDemandeListeFichiersBonbons.class,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetDemandeListeFichiersBonbons::toBytes,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetDemandeListeFichiersBonbons::new,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetDemandeListeFichiersBonbons::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // Paquets de gestion des journaux
        INSTANCE.registerMessage(
            idPaquet++,
            PaquetDemandeListeLogs.class,
            PaquetDemandeListeLogs::toBytes,
            PaquetDemandeListeLogs::new,
            PaquetDemandeListeLogs::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            PaquetListeLogs.class,
            PaquetListeLogs::toBytes,
            PaquetListeLogs::new,
            PaquetListeLogs::traiter
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
            PaquetDonneesLogs::traiter
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
            com.example.mysubmod.authentification.PaquetReponseAuthAdmin::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetFinPartie.class,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetFinPartie::encode,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetFinPartie::decode,
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetFinPartie::traiter
        );

        // Paquets Sous-mode 2
        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSelectionIle.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSelectionIle::encoder,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSelectionIle::decoder,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSelectionIle::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetChoixIle.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetChoixIle::encode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetChoixIle::decode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetChoixIle::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMinuterieJeu.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMinuterieJeu::encode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMinuterieJeu::decode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMinuterieJeu::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetListeFichiersBonbons.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetListeFichiersBonbons::encoder,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetListeFichiersBonbons::decoder,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetListeFichiersBonbons::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSelectionFichierBonbons.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSelectionFichierBonbons::encoder,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSelectionFichierBonbons::decoder,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSelectionFichierBonbons::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetTeleversementFichierBonbons.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetTeleversementFichierBonbons::encode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetTeleversementFichierBonbons::decode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetTeleversementFichierBonbons::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSuppressionFichierBonbons.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSuppressionFichierBonbons::encode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSuppressionFichierBonbons::decode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSuppressionFichierBonbons::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMiseAJourCompteurBonbons.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMiseAJourCompteurBonbons::encoder,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMiseAJourCompteurBonbons::new,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMiseAJourCompteurBonbons::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetDemandeListeFichiersBonbons.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetDemandeListeFichiersBonbons::toBytes,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetDemandeListeFichiersBonbons::new,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetDemandeListeFichiersBonbons::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetFinPartie.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetFinPartie::encode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetFinPartie::decode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetFinPartie::traiter
        );

        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSynchronisationPenalite.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSynchronisationPenalite::encoder,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSynchronisationPenalite::decoder,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSynchronisationPenalite::traiter
        );

        // Paquet de synchronisation de spécialisation Sous-mode 2
        INSTANCE.registerMessage(
            idPaquet++,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSyncSpecialisation.class,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSyncSpecialisation::encode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSyncSpecialisation::decode,
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSyncSpecialisation::traiter
        );

        // Paquet de gestion des journaux Sous-mode 2
        INSTANCE.registerMessage(
            idPaquet++,
            PaquetDemandeListeLogsSousMode2.class,
            PaquetDemandeListeLogsSousMode2::toBytes,
            PaquetDemandeListeLogsSousMode2::new,
            PaquetDemandeListeLogsSousMode2::traiter,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
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
    }

    /**
     * Méthode utilitaire pour envoyer un paquet à un joueur spécifique
     */
    public static void sendToPlayer(Object paquet, net.minecraft.server.level.ServerPlayer joueur) {
        INSTANCE.sendTo(paquet, joueur.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
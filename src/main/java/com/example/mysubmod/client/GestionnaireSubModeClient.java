package com.example.mysubmod.client;

import com.example.mysubmod.sousmodes.SousMode;

public class GestionnaireSubModeClient {
    private static SousMode modeActuel = SousMode.SALLE_ATTENTE;
    private static boolean estAdmin = false;

    public static void definirModeActuel(SousMode mode) {
        SousMode ancienMode = modeActuel;
        modeActuel = mode;

        // Nettoyer les éléments du HUD lors de l'entrée dans le Sous-mode 2 (inclut le cas Sous-mode 2 -> Sous-mode 2)
        if (mode == SousMode.SOUS_MODE_2) {
            // Réinitialiser le HUD de comptage de bonbons du Sous-mode 2 (efface aussi la spécialisation)
            com.example.mysubmod.sousmodes.sousmode2.client.HUDCompteurBonbons.desactiver();

            // Réinitialiser le HUD de minuterie de pénalité du Sous-mode 2
            com.example.mysubmod.sousmodes.sousmode2.client.HUDMinuteriePenalite.desactiver();

            // Réinitialiser la minuterie du Sous-mode 2
            com.example.mysubmod.sousmodes.sousmode2.client.MinuterieJeuClient.desactiver();
        }

        // Nettoyer les éléments du HUD lors de la sortie du Sous-mode 2 vers un autre mode
        if (ancienMode == SousMode.SOUS_MODE_2 && mode != SousMode.SOUS_MODE_2) {
            // Désactiver la minuterie du Sous-mode 2
            com.example.mysubmod.sousmodes.sousmode2.client.MinuterieJeuClient.desactiver();

            // Désactiver le HUD de comptage de bonbons du Sous-mode 2
            com.example.mysubmod.sousmodes.sousmode2.client.HUDCompteurBonbons.desactiver();

            // Désactiver le HUD de minuterie de pénalité du Sous-mode 2
            com.example.mysubmod.sousmodes.sousmode2.client.HUDMinuteriePenalite.desactiver();
        }

        // Nettoyer les éléments du HUD lors de la sortie du Sous-mode 1
        if (ancienMode == SousMode.SOUS_MODE_1 && mode != SousMode.SOUS_MODE_1) {
            // Désactiver la minuterie du Sous-mode 1
            com.example.mysubmod.sousmodes.sousmode1.client.MinuterieJeuClient.desactiver();

            // Désactiver le HUD de comptage de bonbons du Sous-mode 1
            com.example.mysubmod.sousmodes.sousmode1.client.HUDCompteurBonbons.desactiver();
        }

        // Note : le HUD des zones (parties sur carte) n'est PAS effacé à la sortie des
        // Sous-modes 1/2 — le serveur envoie les paquets de zones (vidage puis liste du
        // nouveau mode) AVANT le paquet de changement de mode ; un nettoyage ici
        // effacerait les zones du mode suivant. Le retour en salle d'attente fait foi.
        if (mode == SousMode.SALLE_ATTENTE) {
            com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3.desactiver();
        }

        // Nettoyer les éléments du HUD lors de l'entrée dans le Sous-mode 3 (réinitialise la flèche).
        // La spécialisation et la minuterie de pénalité (HUD partagés avec le Sous-mode 2) sont
        // aussi remises à zéro : le Sous-mode 3 les utilise quand l'option « Spécialisation »
        // de la config de partie est cochée.
        if (mode == SousMode.SOUS_MODE_3) {
            com.example.mysubmod.sousmodes.sousmode3.client.MinuterieJeuClientSousMode3.desactiver();
            com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3.desactiver();
            com.example.mysubmod.sousmodes.sousmode2.client.HUDCompteurBonbons.definirSpecialisationJoueur(null);
            com.example.mysubmod.sousmodes.sousmode2.client.HUDMinuteriePenalite.desactiver();
        }

        // Nettoyer la minuterie lors de la sortie du Sous-mode 3. Le HUD des zones n'est
        // PAS effacé ici : lors d'un passage Sous-mode 3 -> Sous-mode 1/2 avec carte, le
        // serveur envoie les zones du nouveau mode (vidage puis liste complète) AVANT le
        // paquet de changement de mode — un nettoyage ici les effacerait et la touche N
        // retomberait sur le menu de sélection de fichier.
        if (ancienMode == SousMode.SOUS_MODE_3 && mode != SousMode.SOUS_MODE_3) {
            com.example.mysubmod.sousmodes.sousmode3.client.MinuterieJeuClientSousMode3.desactiver();
            // Purger la spécialisation/pénalité du Sous-mode 3, sauf en entrant au Sous-mode 2
            // (son bloc d'entrée les remet déjà à zéro de toute façon)
            if (mode != SousMode.SOUS_MODE_2) {
                com.example.mysubmod.sousmodes.sousmode2.client.HUDCompteurBonbons.definirSpecialisationJoueur(null);
                com.example.mysubmod.sousmodes.sousmode2.client.HUDMinuteriePenalite.desactiver();
            }
        }
    }

    public static SousMode obtenirModeActuel() {
        return modeActuel;
    }

    public static void definirEstAdmin(boolean admin) {
        estAdmin = admin;
    }

    public static boolean estAdmin() {
        return estAdmin;
    }
}

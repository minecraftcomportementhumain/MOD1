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

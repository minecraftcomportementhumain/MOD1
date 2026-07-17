package com.example.mysubmod.client;

import com.example.mysubmod.sousmodes.SousMode;

public class GestionnaireSubModeClient {
    private static SousMode modeActuel = SousMode.SALLE_ATTENTE;
    private static boolean estAdmin = false;

    public static void definirModeActuel(SousMode mode) {
        SousMode ancienMode = modeActuel;
        modeActuel = mode;

        // Retour en salle d'attente : effacer le HUD des zones de la partie précédente
        if (mode == SousMode.SALLE_ATTENTE) {
            com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3.desactiver();
        }

        // Entrée dans le Sous-mode 3 : réinitialiser les HUD (flèche, minuterie,
        // spécialisation et minuterie de pénalité de la config de partie)
        if (mode == SousMode.SOUS_MODE_3) {
            com.example.mysubmod.sousmodes.sousmode3.client.MinuterieJeuClientSousMode3.desactiver();
            com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3.desactiver();
            com.example.mysubmod.sousmodes.sousmode3.client.SpecialisationClientSousMode3.reinitialiser();
            com.example.mysubmod.sousmodes.sousmode3.client.HUDPenaliteSousMode3.desactiver();
            com.example.mysubmod.sousmodes.sousmode3.client.VitesseMinageClientSousMode3.definir(0);
            com.example.mysubmod.sousmodes.sousmode3.client.OptionsHudClientSousMode3.reinitialiser();
        }

        // Sortie du Sous-mode 3 : purger minuterie, spécialisation et pénalité
        if (ancienMode == SousMode.SOUS_MODE_3 && mode != SousMode.SOUS_MODE_3) {
            com.example.mysubmod.sousmodes.sousmode3.client.MinuterieJeuClientSousMode3.desactiver();
            com.example.mysubmod.sousmodes.sousmode3.client.SpecialisationClientSousMode3.reinitialiser();
            com.example.mysubmod.sousmodes.sousmode3.client.HUDPenaliteSousMode3.desactiver();
            com.example.mysubmod.sousmodes.sousmode3.client.VitesseMinageClientSousMode3.definir(0);
            com.example.mysubmod.sousmodes.sousmode3.client.OptionsHudClientSousMode3.reinitialiser();
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

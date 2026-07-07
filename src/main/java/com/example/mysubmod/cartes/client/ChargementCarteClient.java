package com.example.mysubmod.cartes.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * État côté client de la progression de génération de carte (barre de chargement).
 * Mis à jour par {@link com.example.mysubmod.cartes.reseau.PaquetProgressionChargement}.
 */
@OnlyIn(Dist.CLIENT)
public class ChargementCarteClient {
    private static volatile boolean actif = false;
    private static volatile int pourcent = 0;
    private static volatile String nomCarte = "";

    public static void mettreAJour(boolean actif, int pourcent, String nomCarte) {
        ChargementCarteClient.actif = actif;
        ChargementCarteClient.pourcent = Math.max(0, Math.min(100, pourcent));
        ChargementCarteClient.nomCarte = nomCarte != null ? nomCarte : "";
    }

    public static boolean estActif() {
        return actif;
    }

    public static int obtenirPourcent() {
        return pourcent;
    }

    public static String obtenirNomCarte() {
        return nomCarte;
    }

    public static void desactiver() {
        actif = false;
        pourcent = 0;
        nomCarte = "";
    }
}

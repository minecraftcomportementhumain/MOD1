package com.example.mysubmod.sousmodes.sousmode3.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Temps de minage imposé par la partie en cours, côté client (0 = vitesses vanilla).
 * Reçu au lancement / à la connexion via
 * {@link com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetVitesseMinageSousMode3},
 * remis à zéro à la fin de la partie. Consommé par le handler client de BreakSpeed —
 * la prédiction de casse du client doit rester alignée sur le calcul du serveur.
 */
@OnlyIn(Dist.CLIENT)
public final class VitesseMinageClientSousMode3 {

    private static float tempsSecondes = 0;

    private VitesseMinageClientSousMode3() {
    }

    public static void definir(float secondes) {
        tempsSecondes = secondes;
    }

    public static float obtenirTempsSecondes() {
        return tempsSecondes;
    }
}

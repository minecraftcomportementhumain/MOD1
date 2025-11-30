package com.example.mysubmod.sousmodes.sousmode1.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MinuterieJeuClient {
    private static int secondesRestantes = 0;
    private static boolean actif = false;
    private static boolean partieTerminee = false; // Suivre si la partie est terminée

    public static void mettreAJourMinuterie(int secondes) {
        if (secondes < 0) {
            // Cas spécial: valeur négative signifie désactiver
            desactiver();
        } else {
            secondesRestantes = secondes;
            actif = secondes > 0;

            // Si la minuterie atteint 0 pendant une partie active, marquer la partie comme terminée
            if (actif && secondes == 0) {
                partieTerminee = true;
            }
        }
    }

    public static boolean estActif() {
        return actif;
    }

    public static String obtenirTempsFormate() {
        if (!actif) return "";

        int minutes = secondesRestantes / 60;
        int secondes = secondesRestantes % 60;
        return String.format("§e%02d:%02d", minutes, secondes);
    }

    public static int obtenirSecondesRestantes() {
        return secondesRestantes;
    }

    public static void desactiver() {
        actif = false;
        secondesRestantes = 0;
        partieTerminee = false; // Réinitialiser lors de la désactivation (changement de mode)
    }

    public static boolean partieEstTerminee() {
        return partieTerminee;
    }

    public static void marquerPartieTerminee() {
        partieTerminee = true;
    }
}

package com.example.mysubmod.sousmodes.sousmode3.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MinuterieJeuClientSousMode3 {
    private static int secondesRestantes = 0;
    private static boolean actif = false;
    private static boolean partieTerminee = false;

    public static void mettreAJourMinuterie(int secondes) {
        if (secondes < 0) {
            desactiver();
        } else {
            secondesRestantes = secondes;
            actif = secondes > 0;
            if (actif && secondes == 0) {
                partieTerminee = true;
            }
        }
    }

    public static boolean estActif() {
        return actif;
    }

    public static String obtenirTempsFormate() {
        if (!actif) {
            return "";
        }
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
        partieTerminee = false;
    }

    public static boolean partieEstTerminee() {
        return partieTerminee;
    }

    public static void marquerPartieTerminee() {
        partieTerminee = true;
    }
}

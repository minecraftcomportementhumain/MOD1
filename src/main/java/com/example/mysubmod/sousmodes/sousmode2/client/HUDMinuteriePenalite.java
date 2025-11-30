package com.example.mysubmod.sousmodes.sousmode2.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.UUID;

/**
 * Affichage HUD pour la minuterie de pénalité de spécialisation
 * S'affiche dans le coin supérieur gauche quand le joueur a une pénalité active
 */
@OnlyIn(Dist.CLIENT)
public class HUDMinuteriePenalite {
    private static boolean actif = false;
    private static long tempsFinPenalite = 0; // Temps de fin de la pénalité en ms (epoch)
    private static UUID idJoueurLocal = null;

    public static void activer(UUID idJoueur, long tempsFin) {
        actif = true;
        idJoueurLocal = idJoueur;
        tempsFinPenalite = tempsFin;
    }

    public static void desactiver() {
        actif = false;
        tempsFinPenalite = 0;
        idJoueurLocal = null;
    }

    public static boolean estActif() {
        return actif && idJoueurLocal != null;
    }

    public static void afficher(GuiGraphics guiGraphics, int largeurEcran, int hauteurEcran) {
        if (!estActif()) return;

        // Calculer le temps restant
        long maintenant = System.currentTimeMillis();
        long penaliteRestanteMs = tempsFinPenalite - maintenant;

        // Si la pénalité est expirée ou presque expirée (marge de 100ms), désactiver
        // Cette marge assure que le HUD disparaît en même temps ou légèrement avant
        // que la pénalité soit retirée côté serveur
        if (penaliteRestanteMs <= 100) {
            desactiver();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font police = mc.font;

        // Position: coin supérieur gauche, non-invasif
        int posX = 10;
        int posY = 10;

        // Formater le temps comme "M:SS"
        String tempsStr = formaterTemps(penaliteRestanteMs);

        // Arrière-plan pour meilleure lisibilité
        String texteAffichage = "§cPénalité: " + tempsStr;
        int largeurTexte = police.width(texteAffichage);
        int arrierePlanX = posX - 3;
        int arrierePlanY = posY - 3;
        int largeurArrierePlan = largeurTexte + 6;
        int hauteurArrierePlan = police.lineHeight + 6;
        guiGraphics.fill(arrierePlanX, arrierePlanY, arrierePlanX + largeurArrierePlan, arrierePlanY + hauteurArrierePlan, 0x80000000); // Noir semi-transparent

        // Afficher le texte de pénalité
        guiGraphics.drawString(police, texteAffichage, posX, posY, 0xFF5555); // Couleur rouge
    }

    /**
     * Formater le temps en millisecondes comme "M:SS"
     */
    private static String formaterTemps(long millisecondes) {
        long secondes = millisecondes / 1000;
        long minutes = secondes / 60;
        long secondesRestantes = secondes % 60;
        return String.format("%d:%02d", minutes, secondesRestantes);
    }

    /**
     * Définir l'ID du joueur local (appelé au début de la partie)
     */
    public static void definirJoueurLocal(UUID idJoueur) {
        idJoueurLocal = idJoueur;
    }

    public static UUID obtenirIdJoueurLocal() {
        return idJoueurLocal;
    }
}

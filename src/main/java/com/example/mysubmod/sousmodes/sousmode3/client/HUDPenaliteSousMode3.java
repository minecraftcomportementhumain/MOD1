package com.example.mysubmod.sousmodes.sousmode3.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.UUID;

/**
 * Affichage HUD de la minuterie de pénalité de spécialisation (option du menu N) :
 * coin supérieur gauche quand le joueur local a une pénalité active.
 * (Relocalisé depuis le Sous-mode 2 lors de sa suppression.)
 */
@OnlyIn(Dist.CLIENT)
public class HUDPenaliteSousMode3 {
    private static boolean actif = false;
    private static long tempsFinPenalite = 0; // Temps de fin de la pénalité en ms (horloge locale)
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
        if (!estActif()) {
            return;
        }

        // Marge de 100 ms : le HUD disparaît en même temps (ou juste avant) que la
        // pénalité soit considérée expirée côté serveur
        long penaliteRestanteMs = tempsFinPenalite - System.currentTimeMillis();
        if (penaliteRestanteMs <= 100) {
            desactiver();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font police = mc.font;

        int posX = 10;
        int posY = 10;
        String texteAffichage = "§cPénalité: " + formaterTemps(penaliteRestanteMs);
        int largeurTexte = police.width(texteAffichage);
        guiGraphics.fill(posX - 3, posY - 3, posX + largeurTexte + 3, posY + police.lineHeight + 3, 0x80000000);
        guiGraphics.drawString(police, texteAffichage, posX, posY, 0xFF5555);
    }

    /** 165000 ms → « 2:45 » */
    private static String formaterTemps(long millisecondes) {
        long secondes = millisecondes / 1000;
        return String.format("%d:%02d", secondes / 60, secondes % 60);
    }
}

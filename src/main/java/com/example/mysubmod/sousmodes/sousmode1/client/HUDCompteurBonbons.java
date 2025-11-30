package com.example.mysubmod.sousmodes.sousmode1.client;

import com.example.mysubmod.sousmodes.sousmode1.iles.TypeIle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class HUDCompteurBonbons {
    private static final Map<TypeIle, Integer> COMPTEURS_BONBONS = new HashMap<>();
    private static boolean actif = false;

    public static void mettreAJourCompteursBonbons(Map<TypeIle, Integer> compteurs) {
        COMPTEURS_BONBONS.clear();
        if (compteurs.isEmpty()) {
            actif = false; // Désactiver si vide (partie terminée)
        } else {
            COMPTEURS_BONBONS.putAll(compteurs);
            actif = true;
        }
    }

    public static void desactiver() {
        actif = false;
        COMPTEURS_BONBONS.clear();
    }

    public static boolean estActif() {
        return actif;
    }

    public static void afficher(GuiGraphics guiGraphics, int largeurEcran, int hauteurEcran) {
        if (!actif) return;

        Minecraft mc = Minecraft.getInstance();
        Font police = mc.font;

        // Position: coin supérieur droit, en dessous du minuteur, non-invasif
        int x = largeurEcran - 150;
        int y = 35; // Déplacé vers le bas pour éviter le chevauchement avec le minuteur (était 10)
        int hauteurLigne = 12;

        // Arrière-plan pour meilleure lisibilité
        int arrierePlanX = x - 5;
        int arrierePlanY = y - 3;
        int largeurArrierePlan = 145;
        int hauteurArrierePlan = 60;
        guiGraphics.fill(arrierePlanX, arrierePlanY, arrierePlanX + largeurArrierePlan, arrierePlanY + hauteurArrierePlan, 0x80000000); // Noir semi-transparent

        // Titre
        guiGraphics.drawString(police, "§6Bonbons disponibles:", x, y, 0xFFFFFF);
        y += hauteurLigne;

        // Afficher les compteurs par île
        afficherCompteurIle(guiGraphics, police, x, y, TypeIle.PETITE, COMPTEURS_BONBONS.getOrDefault(TypeIle.PETITE, 0));
        y += hauteurLigne;

        afficherCompteurIle(guiGraphics, police, x, y, TypeIle.MOYENNE, COMPTEURS_BONBONS.getOrDefault(TypeIle.MOYENNE, 0));
        y += hauteurLigne;

        afficherCompteurIle(guiGraphics, police, x, y, TypeIle.GRANDE, COMPTEURS_BONBONS.getOrDefault(TypeIle.GRANDE, 0));
        y += hauteurLigne;

        afficherCompteurIle(guiGraphics, police, x, y, TypeIle.TRES_GRANDE, COMPTEURS_BONBONS.getOrDefault(TypeIle.TRES_GRANDE, 0));
    }

    private static void afficherCompteurIle(GuiGraphics guiGraphics, Font police, int x, int y, TypeIle ile, int compteur) {
        String texte = obtenirNomCourtIle(ile) + ": " + compteur;
        int couleur = obtenirCouleurIle(ile);
        guiGraphics.drawString(police, texte, x + 5, y, couleur);
    }

    private static String obtenirNomCourtIle(TypeIle ile) {
        return switch (ile) {
            case PETITE -> "§fPetite";
            case MOYENNE -> "§fMoyenne";
            case GRANDE -> "§fGrande";
            case TRES_GRANDE -> "§fTrès Grande";
        };
    }

    private static int obtenirCouleurIle(TypeIle ile) {
        return switch (ile) {
            case PETITE -> 0xFFFFFF;      // Blanc
            case MOYENNE -> 0x55FF55;     // Vert
            case GRANDE -> 0x5555FF;      // Bleu
            case TRES_GRANDE -> 0xFFAA00; // Orange
        };
    }
}

package com.example.mysubmod.sousmodes.sousmode2.client;

import com.example.mysubmod.sousmodes.sousmode2.TypeRessource;
import com.example.mysubmod.sousmodes.sousmode2.iles.TypeIle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * HUD pour SousMode2 affichant les compteurs de bonbons par ÎLE ET type de ressource
 */
@OnlyIn(Dist.CLIENT)
public class HUDCompteurBonbons {
    private static final Map<TypeIle, Map<TypeRessource, Integer>> compteursBonbons = new HashMap<>();
    private static boolean actif = false;
    private static TypeRessource specialisationJoueur = null;

    public static void mettreAJourCompteursBonbons(Map<TypeIle, Map<TypeRessource, Integer>> compteurs) {
        compteursBonbons.clear();
        if (compteurs.isEmpty()) {
            actif = false; // Désactiver si vide (partie terminée)
        } else {
            compteursBonbons.putAll(compteurs);
            actif = true;
        }
    }

    public static void desactiver() {
        actif = false;
        compteursBonbons.clear();
        specialisationJoueur = null;
    }

    public static void definirSpecialisationJoueur(TypeRessource specialisation) {
        specialisationJoueur = specialisation;
    }

    public static boolean estActif() {
        return actif;
    }

    public static void afficher(GuiGraphics guiGraphics, int largeurEcran, int hauteurEcran) {
        if (!actif) return;

        Minecraft mc = Minecraft.getInstance();
        Font police = mc.font;

        // Position: coin droit, disposition compacte
        // Largeur augmentée pour accommoder "Spécialisation: Rouge" sans débordement
        int x = largeurEcran - 120; // Plus d'espace pour le texte long
        int y = 35; // En dessous du minuteur
        int hauteurLigne = 10; // Très compact

        // Afficher la spécialisation du joueur en premier si définie
        if (specialisationJoueur != null) {
            String texteSpec = "Spécialisation: " + obtenirNomSpecialisation(specialisationJoueur);
            guiGraphics.drawString(police, texteSpec, x, y, specialisationJoueur.obtenirCouleur());
            y += hauteurLigne;
        }

        // Titre
        String titre = "§6Bonbon(s) par île:";
        guiGraphics.drawString(police, titre, x, y, 0xFFFFFF);
        y += hauteurLigne;

        // Afficher les compteurs par île avec détail par type
        y = afficherIleAvecTypes(guiGraphics, police, x, y, TypeIle.PETITE, hauteurLigne);
        y = afficherIleAvecTypes(guiGraphics, police, x, y, TypeIle.MOYENNE, hauteurLigne);
        y = afficherIleAvecTypes(guiGraphics, police, x, y, TypeIle.GRANDE, hauteurLigne);
        afficherIleAvecTypes(guiGraphics, police, x, y, TypeIle.TRES_GRANDE, hauteurLigne);
    }

    private static int afficherIleAvecTypes(GuiGraphics guiGraphics, Font police, int x, int y, TypeIle ile, int hauteurLigne) {
        Map<TypeRessource, Integer> compteursTypes = compteursBonbons.getOrDefault(ile, new HashMap<>());

        int nombreTypeA = compteursTypes.getOrDefault(TypeRessource.BONBON_BLEU, 0);
        int nombreTypeB = compteursTypes.getOrDefault(TypeRessource.BONBON_ROUGE, 0);
        int nombreTotal = nombreTypeA + nombreTypeB;

        // Nom de l'île avec total (légèrement indenté)
        String texteIle = "  " + obtenirNomCourtIle(ile) + ": " + nombreTotal;
        int couleurIle = obtenirCouleurIle(ile);
        guiGraphics.drawString(police, texteIle, x, y+5, couleurIle);

        // Détail par type (indentation minimale)
        String texteTypeA = "§9" + nombreTypeA;
        guiGraphics.drawString(police, texteTypeA, x + 90, y+hauteurLigne/2 +5, TypeRessource.BONBON_BLEU.obtenirCouleur());

        String texteTypeB = "§c" + nombreTypeB;
        guiGraphics.drawString(police, texteTypeB, x + 90, y-hauteurLigne/2 +5, TypeRessource.BONBON_ROUGE.obtenirCouleur());
        y += hauteurLigne + hauteurLigne/2 + 10;

        return y;
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

    private static String obtenirNomSpecialisation(TypeRessource type) {
        return switch (type) {
            case BONBON_BLEU -> "Bleu";
            case BONBON_ROUGE -> "Rouge";
        };
    }
}

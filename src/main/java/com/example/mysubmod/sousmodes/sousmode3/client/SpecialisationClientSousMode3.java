package com.example.mysubmod.sousmodes.sousmode3.client;

import com.example.mysubmod.sousmodes.sousmode3.TypeRessource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Spécialisation Bleu/Rouge du joueur local, synchronisée par le serveur
 * (option « Spécialisation » du menu N). Affichée par le HUD des zones.
 * (Remplace le stockage qui vivait dans le HUD compteur du Sous-mode 2.)
 */
@OnlyIn(Dist.CLIENT)
public class SpecialisationClientSousMode3 {
    private static TypeRessource specialisationJoueur = null;

    private SpecialisationClientSousMode3() {
    }

    public static void definirSpecialisation(TypeRessource specialisation) {
        specialisationJoueur = specialisation;
    }

    public static TypeRessource obtenirSpecialisation() {
        return specialisationJoueur;
    }

    public static void reinitialiser() {
        specialisationJoueur = null;
    }
}

package com.example.mysubmod.sousmodes.sousmode3.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Options d'interface de la partie en cours, côté client (menu N › Interface) :
 * autorisation de la flèche de navigation (touche N des joueurs) et du panneau des
 * parcelles (touche F). Reçues au lancement / à la connexion via
 * {@link com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetOptionsHudSousMode3},
 * remises aux défauts (tout autorisé) aux transitions de mode — même cycle de vie que
 * {@link VitesseMinageClientSousMode3}.
 */
@OnlyIn(Dist.CLIENT)
public final class OptionsHudClientSousMode3 {

    private static boolean flecheAutorisee = true;
    private static boolean panneauAutorise = true;

    private OptionsHudClientSousMode3() {
    }

    public static void definir(boolean fleche, boolean panneau) {
        flecheAutorisee = fleche;
        panneauAutorise = panneau;
    }

    /** Retour aux défauts (tout autorisé) — appelé aux transitions de mode. */
    public static void reinitialiser() {
        definir(true, true);
    }

    /** Flèche de navigation et ciblage de parcelle (touche N) autorisés dans cette partie. */
    public static boolean flecheAutorisee() {
        return flecheAutorisee;
    }

    /** Panneau des parcelles (compteurs de bonbons, touche F) autorisé dans cette partie. */
    public static boolean panneauAutorise() {
        return panneauAutorise;
    }
}

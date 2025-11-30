package com.example.mysubmod.authentification;

import com.example.mysubmod.MonSubMod;

import java.util.HashMap;
import java.util.Map;

/**
 * Stockage côté client pour les jetons de file
 * Stocke les jetons en mémoire pour reconnexion automatique
 */
public class StockageJetonsFile {
    private static final Map<String, DonneesJeton> jetons = new HashMap<>();

    public static class DonneesJeton {
        public final String jeton;
        public final long monopoleDebutMs;
        public final long monopoleFinMs;

        public DonneesJeton(String jeton, long monopoleDebutMs, long monopoleFinMs) {
            this.jeton = jeton;
            this.monopoleDebutMs = monopoleDebutMs;
            this.monopoleFinMs = monopoleFinMs;
        }

        public boolean estExpire() {
            return System.currentTimeMillis() > monopoleFinMs;
        }

        public boolean estActif() {
            long maintenant = System.currentTimeMillis();
            return maintenant >= monopoleDebutMs && maintenant <= monopoleFinMs;
        }

        public long tempsJusquauDebut() {
            return Math.max(0, monopoleDebutMs - System.currentTimeMillis());
        }
    }

    /**
     * Stocke un jeton pour un compte
     */
    public static void stockerJeton(String nomCompte, String jeton, long monopoleDebutMs, long monopoleFinMs) {
        jetons.put(nomCompte.toLowerCase(), new DonneesJeton(jeton, monopoleDebutMs, monopoleFinMs));
        MonSubMod.JOURNALISEUR.info("Jeton stocké pour {}: {} (valide de {} à {})",
            nomCompte, jeton, monopoleDebutMs, monopoleFinMs);
    }

    /**
     * Obtient le jeton pour un compte
     */
    public static String obtenirJeton(String nomCompte) {
        DonneesJeton donnees = jetons.get(nomCompte.toLowerCase());
        if (donnees == null || donnees.estExpire()) {
            return null;
        }
        return donnees.jeton;
    }

    /**
     * Obtient les données de jeton pour un compte
     */
    public static DonneesJeton obtenirDonneesJeton(String nomCompte) {
        DonneesJeton donnees = jetons.get(nomCompte.toLowerCase());
        if (donnees == null || donnees.estExpire()) {
            return null;
        }
        return donnees;
    }

    /**
     * Vérifie si nous avons un jeton valide pour un compte
     */
    public static boolean aJeton(String nomCompte) {
        return obtenirJeton(nomCompte) != null;
    }

    /**
     * Retire le jeton pour un compte (après connexion réussie)
     */
    public static void retirerJeton(String nomCompte) {
        jetons.remove(nomCompte.toLowerCase());
        MonSubMod.JOURNALISEUR.info("Jeton retiré pour {}", nomCompte);
    }

    /**
     * Efface tous les jetons
     */
    public static void effacerTout() {
        jetons.clear();
    }
}

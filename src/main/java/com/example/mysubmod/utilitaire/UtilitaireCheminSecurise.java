package com.example.mysubmod.utilitaire;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Utilitaire de sécurité pour les chemins de fichiers construits à partir de noms
 * fournis par le réseau (paquets client -> serveur).
 *
 * Objectif : empêcher tout parcours de chemin (« path traversal ») — un nom comme
 * « ../identifiants_auth.json », « ..\\world » ou un chemin absolu « C:\\... » ne doit
 * jamais permettre de lire, écrire ou supprimer un fichier hors du répertoire prévu.
 */
public final class UtilitaireCheminSecurise {

    /** Longueur maximale acceptée pour un nom de fichier/dossier reçu du réseau. */
    private static final int LONGUEUR_MAX_NOM = 128;

    private UtilitaireCheminSecurise() {
    }

    /**
     * Vérifie qu'un nom est un simple nom de base sûr : pas de séparateur de chemin,
     * pas de « .. », pas de deux-points (lecteur Windows), uniquement des caractères
     * de la liste blanche. Rejette null/vide/trop long.
     */
    public static boolean nomFichierSur(String nom) {
        if (nom == null || nom.isEmpty() || nom.length() > LONGUEUR_MAX_NOM) {
            return false;
        }
        if (nom.contains("/") || nom.contains("\\") || nom.contains("..") || nom.contains(":")) {
            return false;
        }
        // Lettres, chiffres, tiret, underscore, point (pour l'extension) uniquement.
        return nom.matches("[A-Za-z0-9_.-]+");
    }

    /**
     * Résout {@code nomFichier} à l'intérieur de {@code repertoireBase} en garantissant
     * le confinement. Retourne le chemin résolu, ou {@code null} si le nom est invalide
     * ou tente de s'échapper du répertoire de base.
     */
    public static Path resoudreConfine(Path repertoireBase, String nomFichier) {
        if (!nomFichierSur(nomFichier)) {
            return null;
        }
        Path base = repertoireBase.toAbsolutePath().normalize();
        Path resolu = base.resolve(nomFichier).normalize();
        if (!resolu.startsWith(base)) {
            return null;
        }
        return resolu;
    }

    /**
     * Vérifie qu'un {@link File} résolu reste bien à l'intérieur du répertoire de base,
     * via le chemin canonique (résout aussi les liens symboliques). Défense de dernier
     * recours pour les API basées sur {@link File}.
     */
    public static boolean estConfine(File repertoireBase, File cible) {
        try {
            String base = repertoireBase.getCanonicalPath();
            String chemin = cible.getCanonicalPath();
            return chemin.equals(base) || chemin.startsWith(base + File.separator);
        } catch (IOException e) {
            return false;
        }
    }
}

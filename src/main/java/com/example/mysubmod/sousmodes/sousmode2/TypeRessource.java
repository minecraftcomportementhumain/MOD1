package com.example.mysubmod.sousmodes.sousmode2;

/**
 * Types de ressources disponibles dans le Sous-mode 2
 * Chaque type a un nom d'affichage et une couleur pour l'interface
 */
public enum TypeRessource {
    BONBON_BLEU("Bonbon Bleu", 0x5555FF),  // Bleu
    BONBON_ROUGE("Bonbon Rouge", 0xFF5555);  // Rouge

    private final String nomAffichage;
    private final int couleur;

    TypeRessource(String nomAffichage, int couleur) {
        this.nomAffichage = nomAffichage;
        this.couleur = couleur;
    }

    public String obtenirNomAffichage() {
        return nomAffichage;
    }

    public int obtenirCouleur() {
        return couleur;
    }

    /**
     * Analyse le type de ressource depuis une chaîne (insensible à la casse)
     */
    public static TypeRessource depuisChaine(String str) {
        if (str == null) return null;
        String majuscule = str.toUpperCase();
        if (majuscule.equals("A") || majuscule.equals("TYPE_A") || majuscule.equals("BONBON_BLEU")) {
            return BONBON_BLEU;
        } else if (majuscule.equals("B") || majuscule.equals("TYPE_B") || majuscule.equals("BONBON_ROUGE")) {
            return BONBON_ROUGE;
        }
        return null;
    }

}

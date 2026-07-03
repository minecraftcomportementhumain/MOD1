package com.example.mysubmod.cartes;

/**
 * Type d'un bonbon visible sur la carte.
 * STANDARD : bonbon classique (Sous-modes 1 et 3).
 * BLEU / ROUGE : bonbons de ressources du Sous-mode 2 (le Sous-mode 2 exige que
 * tous les bonbons visibles de la carte soient typés Bleu ou Rouge).
 * Les Sous-modes 1 et 3 traitent tous les types comme des bonbons standards.
 */
public enum TypeBonbonCarte {
    STANDARD("Standard", 0xFFFFC81E),
    BLEU("Bleu", 0xFF3C78F0),
    ROUGE("Rouge", 0xFFE03C3C);

    private final String nomAffichage;
    private final int couleurIcone;

    TypeBonbonCarte(String nomAffichage, int couleurIcone) {
        this.nomAffichage = nomAffichage;
        this.couleurIcone = couleurIcone;
    }

    public String obtenirNomAffichage() {
        return nomAffichage;
    }

    public int obtenirCouleurIcone() {
        return couleurIcone;
    }

    public TypeBonbonCarte suivant() {
        TypeBonbonCarte[] valeurs = values();
        return valeurs[(ordinal() + 1) % valeurs.length];
    }
}

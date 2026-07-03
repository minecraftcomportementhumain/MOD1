package com.example.mysubmod.cartes;

/**
 * Type de l'élément de base d'un bloc du plan cartésien
 */
public enum TypeElementCarte {
    VIDE("Vide"),
    EAU("Eau"),
    ILE("Île"),
    PIERRE("Pierre"),
    LIMITE("Limite");

    private final String nomAffichage;

    TypeElementCarte(String nomAffichage) {
        this.nomAffichage = nomAffichage;
    }

    public String obtenirNomAffichage() {
        return nomAffichage;
    }

    public boolean estElementDeBase() {
        return this == EAU || this == ILE || this == PIERRE;
    }
}

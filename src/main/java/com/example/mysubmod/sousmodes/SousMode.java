package com.example.mysubmod.sousmodes;

public enum SousMode {
    SALLE_ATTENTE("Salle d'attente"),
    SOUS_MODE_1("Sous-mode 1"),
    SOUS_MODE_2("Sous-mode 2");

    private final String nomAffichage;

    SousMode(String nomAffichage) {
        this.nomAffichage = nomAffichage;
    }

    public String obtenirNomAffichage() {
        return nomAffichage;
    }
}
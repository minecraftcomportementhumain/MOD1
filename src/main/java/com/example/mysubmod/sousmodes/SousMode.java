package com.example.mysubmod.sousmodes;

public enum SousMode {
    SALLE_ATTENTE("Salle d'attente"),
    SOUS_MODE_3("Sous-mode 3");

    private final String nomAffichage;

    SousMode(String nomAffichage) {
        this.nomAffichage = nomAffichage;
    }

    public String obtenirNomAffichage() {
        return nomAffichage;
    }
}
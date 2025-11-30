package com.example.mysubmod.sousmodes.sousmode1.iles;

public enum TypeIle {
    PETITE("Petite Île (60x60)", 30),
    MOYENNE("Île Moyenne (90x90)", 45),
    GRANDE("Grande Île (120x120)", 60),
    TRES_GRANDE("Très Grande Île (150x150)", 75);

    private final String nomAffichage;
    private final int rayon;

    TypeIle(String nomAffichage, int rayon) {
        this.nomAffichage = nomAffichage;
        this.rayon = rayon;
    }

    public String obtenirNomAffichage() {
        return nomAffichage;
    }

    public int obtenirRayon() {
        return rayon;
    }

}

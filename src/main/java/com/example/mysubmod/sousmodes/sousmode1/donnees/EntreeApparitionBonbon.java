package com.example.mysubmod.sousmodes.sousmode1.donnees;

import net.minecraft.core.BlockPos;

/**
 * Entrée pour l'apparition de bonbons dans le Sous-mode 1
 */
public class EntreeApparitionBonbon {
    private final int tempsSecondes;      // Temps en secondes (max 900 = 15 minutes)
    private final int nombreBonbons;      // Nombre de bonbons (max 100)
    private final BlockPos position;      // Coordonnées exactes

    public EntreeApparitionBonbon(int tempsSecondes, int nombreBonbons, BlockPos position) {
        this.tempsSecondes = tempsSecondes;
        this.nombreBonbons = nombreBonbons;
        this.position = position;
    }

    public int obtenirTempsSecondes() {
        return tempsSecondes;
    }

    public int obtenirNombreBonbons() {
        return nombreBonbons;
    }

    public BlockPos obtenirPosition() {
        return position;
    }

    public long obtenirTempsMs() {
        return tempsSecondes * 1000L;
    }

    @Override
    public String toString() {
        return String.format("EntreeApparitionBonbon{temps=%ds, nombre=%d, pos=(%d,%d,%d)}",
            tempsSecondes, nombreBonbons, position.getX(), position.getY(), position.getZ());
    }

}

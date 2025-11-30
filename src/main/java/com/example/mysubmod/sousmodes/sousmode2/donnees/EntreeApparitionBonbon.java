package com.example.mysubmod.sousmodes.sousmode2.donnees;

import com.example.mysubmod.sousmodes.sousmode2.TypeRessource;
import net.minecraft.core.BlockPos;

/**
 * Entrée pour l'apparition de bonbons dans le Sous-mode 2
 * Contient le type de ressource (A ou B) en plus des infos de base
 */
public class EntreeApparitionBonbon {
    private final int tempsSecondes;       // Temps en secondes (max 900 = 15 minutes)
    private final int nombreBonbons;        // Nombre de bonbons (max 100)
    private final BlockPos position;        // Coordonnées exactes
    private final TypeRessource type;       // Type de ressource (A ou B)

    public EntreeApparitionBonbon(int tempsSecondes, int nombreBonbons, BlockPos position, TypeRessource type) {
        this.tempsSecondes = tempsSecondes;
        this.nombreBonbons = nombreBonbons;
        this.position = position;
        this.type = type;
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

    public TypeRessource obtenirType() {
        return type;
    }

    public long obtenirTempsMs() {
        return tempsSecondes * 1000L;
    }

    @Override
    public String toString() {
        return String.format("EntreeApparitionBonbon{temps=%ds, nombre=%d, pos=(%d,%d,%d), type=%s}",
            tempsSecondes, nombreBonbons, position.getX(), position.getY(), position.getZ(), type);
    }

}

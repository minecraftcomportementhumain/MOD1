package com.example.mysubmod.cartes;

import java.util.ArrayList;
import java.util.List;

/**
 * Zone logique d'une carte : ensemble de blocs Île adjacents (île logique unique)
 * ou ensemble de blocs Pierre adjacents. Le nom est généré automatiquement à la
 * sauvegarde de la carte (ex. « Île A », « Zone Pierre 1 »).
 */
public class ZoneCarte {
    public String nom;
    public TypeElementCarte type; // ILE ou PIERRE
    public List<int[]> cellules = new ArrayList<>(); // paires [x, z] en coordonnées de carte

    public ZoneCarte() {
    }

    public ZoneCarte(String nom, TypeElementCarte type) {
        this.nom = nom;
        this.type = type;
    }

    /** Centre géométrique de la zone en coordonnées de carte */
    public double[] obtenirCentre() {
        if (cellules.isEmpty()) {
            return new double[]{0, 0};
        }
        double sommeX = 0;
        double sommeZ = 0;
        for (int[] cellule : cellules) {
            sommeX += cellule[0];
            sommeZ += cellule[1];
        }
        return new double[]{sommeX / cellules.size(), sommeZ / cellules.size()};
    }

    public boolean contientCellule(int x, int z) {
        for (int[] cellule : cellules) {
            if (cellule[0] == x && cellule[1] == z) {
                return true;
            }
        }
        return false;
    }
}

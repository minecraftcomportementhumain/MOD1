package com.example.mysubmod.cartes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Zone logique d'une carte : ensemble de blocs Île adjacents (île logique unique)
 * ou ensemble de blocs Pierre adjacents. Le nom est généré automatiquement à la
 * sauvegarde de la carte (ex. « Île A », « Zone Pierre 1 »).
 *
 * <p>Les cellules sont stockées en plages triées « z, x0, longueur » (coordonnées de
 * carte) et ne sont jamais développées cellule par cellule : une zone d'une carte
 * 2500×2500 peut compter des millions de cellules, que des listes de paires [x, z]
 * feraient peser des centaines de Mo.</p>
 */
public class ZoneCarte {
    public String nom;
    public TypeElementCarte type; // ILE ou PIERRE
    /** Plages de cellules triées par (z, x0) : {z, x0, longueur} */
    public List<int[]> plages = new ArrayList<>();

    public ZoneCarte() {
    }

    public ZoneCarte(String nom, TypeElementCarte type) {
        this.nom = nom;
        this.type = type;
    }

    /** Nombre total de cellules de la zone */
    public long nombreCellules() {
        long total = 0;
        for (int[] plage : plages) {
            total += plage[2];
        }
        return total;
    }

    /** Centre géométrique de la zone en coordonnées de carte */
    public double[] obtenirCentre() {
        long nombre = 0;
        double sommeX = 0;
        double sommeZ = 0;
        for (int[] plage : plages) {
            int longueur = plage[2];
            nombre += longueur;
            sommeZ += (double) plage[0] * longueur;
            // Somme des x d'une plage : longueur × (x0 + (longueur − 1) / 2)
            sommeX += (plage[1] + (longueur - 1) / 2.0) * longueur;
        }
        if (nombre == 0) {
            return new double[]{0, 0};
        }
        return new double[]{sommeX / nombre, sommeZ / nombre};
    }

    public boolean contientCellule(int x, int z) {
        return plagesContiennent(plages, x, z);
    }

    /** La cellule (cx, cz) appartient-elle à ces plages triées ? Recherche binaire, O(log n). */
    public static boolean plagesContiennent(List<int[]> plages, int cx, int cz) {
        int bas = 0;
        int haut = plages.size() - 1;
        while (bas <= haut) {
            int milieu = (bas + haut) >>> 1;
            int[] plage = plages.get(milieu);
            if (plage[0] < cz || (plage[0] == cz && plage[1] <= cx)) {
                bas = milieu + 1;
            } else {
                haut = milieu - 1;
            }
        }
        if (haut < 0) {
            return false;
        }
        int[] plage = plages.get(haut);
        return plage[0] == cz && cx < plage[1] + plage[2];
    }

    /** Cellules {x, z} -> plages triées « z, x0, longueur » (mêmes coordonnées que l'entrée) */
    public static List<int[]> plagesDepuisCellules(List<int[]> cellules) {
        long[] indices = new long[cellules.size()];
        int n = 0;
        for (int[] cellule : cellules) {
            // Clé triable : z signé en poids fort, x décalé en non-signé en poids faible
            indices[n++] = (((long) cellule[1]) << 32) | ((cellule[0] - (long) Integer.MIN_VALUE) & 0xFFFFFFFFL);
        }
        Arrays.sort(indices);

        List<int[]> plages = new ArrayList<>();
        int i = 0;
        while (i < indices.length) {
            int debut = i;
            while (i + 1 < indices.length && indices[i + 1] == indices[i] + 1
                && (indices[i + 1] & 0xFFFFFFFFL) != 0) { // ne pas franchir un changement de rangée
                i++;
            }
            int z = (int) (indices[debut] >> 32);
            int x0 = (int) ((indices[debut] & 0xFFFFFFFFL) + Integer.MIN_VALUE);
            plages.add(new int[]{z, x0, i - debut + 1});
            i++;
        }
        return plages;
    }
}

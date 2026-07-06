package com.example.mysubmod.cartes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Données complètes d'une carte créée avec l'outil de création de carte.
 * Sérialisée en JSON (un fichier par carte) dans un dossier dédié du serveur.
 */
public class CarteDonnees {
    public static final int ELEVATION_MIN = -15;
    public static final int ELEVATION_MAX = 15;
    public static final int LONGUEUR_MAX_NOM = 32;
    /** Dimension maximale d'une carte (borne anti-DoS au décodage). */
    public static final int DIMENSION_MAX = 512;
    /** Nombre maximal de blocs stockés dans une carte (borne anti-DoS au décodage). */
    public static final int BLOCS_MAX = DIMENSION_MAX * DIMENSION_MAX;

    public String nom = "";
    public int largeur = 1;
    public int hauteur = 1;
    public long seed = 0; // Seed fixe générée automatiquement à la première sauvegarde
    public int apparitionX = -1; // Point d'apparition des joueurs (-1 = absent)
    public int apparitionZ = -1;
    public Map<Long, BlocCarte> blocs = new HashMap<>(); // clé = cle(x, z), seuls les blocs non vides sont stockés
    public List<ZoneCarte> zones = new ArrayList<>(); // Générées à la sauvegarde

    public static long cle(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    public static int cleX(long cle) {
        return (int) (cle >> 32);
    }

    public static int cleZ(long cle) {
        return (int) cle;
    }

    public boolean estDansAire(int x, int z) {
        return x >= 0 && x < largeur && z >= 0 && z < hauteur;
    }

    /** Retourne le bloc à cette position (jamais null; un bloc vide par défaut) */
    public BlocCarte obtenirBloc(int x, int z) {
        BlocCarte bloc = blocs.get(cle(x, z));
        return bloc != null ? bloc : new BlocCarte();
    }

    public BlocCarte obtenirBlocOuNull(int x, int z) {
        return blocs.get(cle(x, z));
    }

    public void definirBloc(int x, int z, BlocCarte bloc) {
        if (bloc == null || bloc.estVide()) {
            blocs.remove(cle(x, z));
        } else {
            blocs.put(cle(x, z), bloc);
        }
    }

    public boolean aPointApparition() {
        return apparitionX >= 0 && apparitionZ >= 0;
    }

    // ==================== Périmètre Limite ====================

    /**
     * Calcule l'ensemble des cellules strictement à l'intérieur du périmètre Limite.
     * Méthode : remplissage depuis l'extérieur de l'aire (les cellules hors aire sont « extérieures »),
     * bloqué par les blocs Limite. Les cellules non atteintes et non-Limite sont l'intérieur.
     */
    public Set<Long> calculerInterieurLimite() {
        Set<Long> exterieur = new HashSet<>();
        Deque<long[]> pile = new ArrayDeque<>();

        // Amorcer avec toutes les cellules du bord de l'aire qui ne sont pas des Limite
        for (int x = 0; x < largeur; x++) {
            amorcerExterieur(pile, exterieur, x, 0);
            amorcerExterieur(pile, exterieur, x, hauteur - 1);
        }
        for (int z = 0; z < hauteur; z++) {
            amorcerExterieur(pile, exterieur, 0, z);
            amorcerExterieur(pile, exterieur, largeur - 1, z);
        }

        // Remplissage (4-adjacence)
        while (!pile.isEmpty()) {
            long[] pos = pile.pop();
            int x = (int) pos[0];
            int z = (int) pos[1];
            int[][] voisins = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
            for (int[] voisin : voisins) {
                amorcerExterieur(pile, exterieur, voisin[0], voisin[1]);
            }
        }

        Set<Long> interieur = new HashSet<>();
        for (int x = 0; x < largeur; x++) {
            for (int z = 0; z < hauteur; z++) {
                long c = cle(x, z);
                if (!exterieur.contains(c) && obtenirBloc(x, z).type != TypeElementCarte.LIMITE) {
                    interieur.add(c);
                }
            }
        }
        return interieur;
    }

    private void amorcerExterieur(Deque<long[]> pile, Set<Long> exterieur, int x, int z) {
        if (!estDansAire(x, z)) {
            return;
        }
        long c = cle(x, z);
        if (exterieur.contains(c)) {
            return;
        }
        if (obtenirBloc(x, z).type == TypeElementCarte.LIMITE) {
            return;
        }
        exterieur.add(c);
        pile.push(new long[]{x, z});
    }

    /**
     * Vérifie que le périmètre Limite forme une boucle fermée entourant complètement
     * des blocs non-Limite (au moins un bloc intérieur, et chaque bloc Limite touche l'intérieur).
     */
    public boolean limiteEstBoucleFermeeValide() {
        Set<Long> cellulesLimite = new HashSet<>();
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            if (entree.getValue().type == TypeElementCarte.LIMITE) {
                cellulesLimite.add(entree.getKey());
            }
        }
        if (cellulesLimite.isEmpty()) {
            return false;
        }

        Set<Long> interieur = calculerInterieurLimite();
        if (interieur.isEmpty()) {
            return false; // Rien d'entouré : pas une boucle fermée utile
        }

        // Il ne peut exister qu'un seul périmètre : tous les blocs Limite doivent
        // participer à la boucle qui entoure l'intérieur (adjacents 8-directions à l'intérieur)
        for (long c : cellulesLimite) {
            int x = cleX(c);
            int z = cleZ(c);
            boolean toucheInterieur = false;
            for (int dx = -1; dx <= 1 && !toucheInterieur; dx++) {
                for (int dz = -1; dz <= 1 && !toucheInterieur; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (interieur.contains(cle(x + dx, z + dz))) {
                        toucheInterieur = true;
                    }
                }
            }
            if (!toucheInterieur) {
                return false;
            }
        }

        // L'intérieur doit être connexe (un seul périmètre par carte)
        Set<Long> visites = new HashSet<>();
        Deque<Long> pile = new ArrayDeque<>();
        long premier = interieur.iterator().next();
        pile.push(premier);
        visites.add(premier);
        while (!pile.isEmpty()) {
            long c = pile.pop();
            int x = cleX(c);
            int z = cleZ(c);
            long[] voisins = {cle(x + 1, z), cle(x - 1, z), cle(x, z + 1), cle(x, z - 1)};
            for (long voisin : voisins) {
                if (interieur.contains(voisin) && !visites.contains(voisin)) {
                    visites.add(voisin);
                    pile.push(voisin);
                }
            }
        }
        return visites.size() == interieur.size();
    }

    // ==================== Validation ====================

    /**
     * Valide la carte pour la sauvegarde. Retourne la liste des erreurs (vide si valide).
     */
    public List<String> validerPourSauvegarde() {
        List<String> erreurs = new ArrayList<>();

        // Nom obligatoire et valide
        if (nom == null || nom.trim().isEmpty()) {
            erreurs.add("Un nom de carte est obligatoire");
        } else if (nom.length() > LONGUEUR_MAX_NOM) {
            erreurs.add("Le nom ne peut pas dépasser " + LONGUEUR_MAX_NOM + " caractères");
        } else if (!nom.matches("[A-Za-z0-9_-]+")) {
            erreurs.add("Le nom ne peut contenir que des lettres, chiffres, tirets et underscores");
        }

        // Le périmètre Limite doit former une boucle fermée valide
        if (!limiteEstBoucleFermeeValide()) {
            erreurs.add("Le périmètre Limite doit former une boucle fermée entourant des blocs non-Limite");
            return erreurs; // Les validations suivantes dépendent d'un périmètre valide
        }

        Set<Long> interieur = calculerInterieurLimite();

        // Point d'apparition présent et à l'intérieur du périmètre
        if (!aPointApparition()) {
            erreurs.add("Le point d'apparition des joueurs est absent");
        } else if (!interieur.contains(cle(apparitionX, apparitionZ))) {
            erreurs.add("Le point d'apparition doit être à l'intérieur du périmètre Limite");
        }

        // Tout bloc à l'intérieur du périmètre doit contenir un élément de base
        int blocsSansBase = 0;
        for (long c : interieur) {
            BlocCarte bloc = blocs.get(c);
            if (bloc == null || !bloc.type.estElementDeBase()) {
                blocsSansBase++;
            }
        }
        if (blocsSansBase > 0) {
            erreurs.add(blocsSansBase + " bloc(s) à l'intérieur du périmètre Limite sans élément de base (Île, Pierre ou Eau)");
        }

        // Un bonbon sans élément de base est invalide
        int bonbonsSansBase = 0;
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            BlocCarte bloc = entree.getValue();
            if ((bloc.qteBonbonVisible > 0 || bloc.qteBonbonNonVisible > 0) && !bloc.type.estElementDeBase()) {
                bonbonsSansBase++;
            }
        }
        if (bonbonsSansBase > 0) {
            erreurs.add(bonbonsSansBase + " bonbon(s) présents sur un bloc sans élément de base");
        }

        // Un bonbon visible sur un bloc à l'élévation maximale est inatteignable
        // (sa surface touche le plafond de la cage)
        int bonbonsInatteignables = 0;
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            BlocCarte bloc = entree.getValue();
            if (bloc.qteBonbonVisible > 0
                && (bloc.type == TypeElementCarte.ILE || bloc.type == TypeElementCarte.PIERRE)
                && bloc.elevation >= ELEVATION_MAX) {
                bonbonsInatteignables++;
            }
        }
        if (bonbonsInatteignables > 0) {
            erreurs.add(bonbonsInatteignables
                + " bonbon(s) visible(s) sur un bloc à élévation +15 (inatteignables sous le plafond de la cage)");
        }

        return erreurs;
    }

    // ==================== Zones ====================

    /**
     * Recalcule les zones (îles logiques et zones de pierre) et leur assigne des noms
     * automatiques : « Île A », « Île B », ..., « Zone Pierre 1 », « Zone Pierre 2 », ...
     * Appelé à la sauvegarde de la carte.
     */
    public void recalculerZones() {
        zones.clear();
        Set<Long> visites = new HashSet<>();
        int compteurIles = 0;
        int compteurPierre = 0;

        // Parcours déterministe (ordre de balayage) pour un nommage stable
        for (int z = 0; z < hauteur; z++) {
            for (int x = 0; x < largeur; x++) {
                long c = cle(x, z);
                if (visites.contains(c)) {
                    continue;
                }
                BlocCarte bloc = blocs.get(c);
                if (bloc == null || (bloc.type != TypeElementCarte.ILE && bloc.type != TypeElementCarte.PIERRE)) {
                    continue;
                }

                // Remplissage de la zone connexe du même type (8-adjacence : les diagonales comptent)
                TypeElementCarte typeZone = bloc.type;
                ZoneCarte zone = new ZoneCarte();
                zone.type = typeZone;
                Deque<Long> pile = new ArrayDeque<>();
                pile.push(c);
                visites.add(c);
                while (!pile.isEmpty()) {
                    long courant = pile.pop();
                    int cx = cleX(courant);
                    int cz = cleZ(courant);
                    zone.cellules.add(new int[]{cx, cz});
                    long[] voisins = {
                        cle(cx + 1, cz), cle(cx - 1, cz), cle(cx, cz + 1), cle(cx, cz - 1),
                        cle(cx + 1, cz + 1), cle(cx + 1, cz - 1), cle(cx - 1, cz + 1), cle(cx - 1, cz - 1)
                    };
                    for (long voisin : voisins) {
                        if (visites.contains(voisin)) {
                            continue;
                        }
                        int vx = cleX(voisin);
                        int vz = cleZ(voisin);
                        if (!estDansAire(vx, vz)) {
                            continue;
                        }
                        BlocCarte blocVoisin = blocs.get(voisin);
                        if (blocVoisin != null && blocVoisin.type == typeZone) {
                            visites.add(voisin);
                            pile.push(voisin);
                        }
                    }
                }

                if (typeZone == TypeElementCarte.ILE) {
                    zone.nom = "Île " + lettreZone(compteurIles);
                    compteurIles++;
                } else {
                    compteurPierre++;
                    zone.nom = "Zone Pierre " + compteurPierre;
                }
                zones.add(zone);
            }
        }
    }

    private static String lettreZone(int index) {
        // A, B, ..., Z, AA, AB, ...
        StringBuilder sb = new StringBuilder();
        index++;
        while (index > 0) {
            index--;
            sb.insert(0, (char) ('A' + (index % 26)));
            index /= 26;
        }
        return sb.toString();
    }

    // ==================== Redimensionnement ====================

    /**
     * Redimensionne l'aire de la carte. Agrandir : les nouveaux blocs sont vides.
     * Rétrécir : les éléments hors de la nouvelle aire sont supprimés.
     */
    public void redimensionner(int nouvelleLargeur, int nouvelleHauteur) {
        nouvelleLargeur = Math.max(1, nouvelleLargeur);
        nouvelleHauteur = Math.max(1, nouvelleHauteur);
        this.largeur = nouvelleLargeur;
        this.hauteur = nouvelleHauteur;
        blocs.keySet().removeIf(c -> !estDansAire(cleX(c), cleZ(c)));
        if (aPointApparition() && !estDansAire(apparitionX, apparitionZ)) {
            apparitionX = -1;
            apparitionZ = -1;
        }
    }

    // ==================== Sérialisation JSON ====================

    public String versJson() {
        JsonObject racine = new JsonObject();
        racine.addProperty("nom", nom);
        racine.addProperty("largeur", largeur);
        racine.addProperty("hauteur", hauteur);
        racine.addProperty("seed", seed);
        racine.addProperty("apparitionX", apparitionX);
        racine.addProperty("apparitionZ", apparitionZ);

        JsonArray tableauBlocs = new JsonArray();
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            BlocCarte bloc = entree.getValue();
            JsonObject objetBloc = new JsonObject();
            objetBloc.addProperty("x", cleX(entree.getKey()));
            objetBloc.addProperty("z", cleZ(entree.getKey()));
            objetBloc.addProperty("type", bloc.type.name());
            objetBloc.addProperty("elevation", bloc.elevation);
            if (bloc.qteBonbonVisible > 0) {
                objetBloc.addProperty("bonbonsVisibles", bloc.qteBonbonVisible);
                objetBloc.addProperty("delaiVisible", bloc.delaiBonbonVisible);
                if (bloc.typeBonbonVisible != TypeBonbonCarte.STANDARD) {
                    objetBloc.addProperty("typeVisible", bloc.typeBonbonVisible.name());
                }
                if (bloc.delaiApparitionInitiale > 0) {
                    objetBloc.addProperty("apparitionInitiale", bloc.delaiApparitionInitiale);
                }
            }
            if (bloc.qteBonbonNonVisible > 0) {
                objetBloc.addProperty("bonbonsNonVisibles", bloc.qteBonbonNonVisible);
                objetBloc.addProperty("delaiNonVisible", bloc.delaiBonbonNonVisible);
                if (bloc.delaiApparitionInitialeNonVisible > 0) {
                    objetBloc.addProperty("apparitionInitialeNonVisible", bloc.delaiApparitionInitialeNonVisible);
                }
            }
            tableauBlocs.add(objetBloc);
        }
        racine.add("blocs", tableauBlocs);

        JsonArray tableauZones = new JsonArray();
        for (ZoneCarte zone : zones) {
            JsonObject objetZone = new JsonObject();
            objetZone.addProperty("nom", zone.nom);
            objetZone.addProperty("type", zone.type.name());
            JsonArray cellules = new JsonArray();
            for (int[] cellule : zone.cellules) {
                JsonArray paire = new JsonArray();
                paire.add(cellule[0]);
                paire.add(cellule[1]);
                cellules.add(paire);
            }
            objetZone.add("cellules", cellules);
            tableauZones.add(objetZone);
        }
        racine.add("zones", tableauZones);

        Gson gson = new GsonBuilder().create();
        return gson.toJson(racine);
    }

    public static CarteDonnees depuisJson(String json) {
        JsonObject racine = JsonParser.parseString(json).getAsJsonObject();
        CarteDonnees carte = new CarteDonnees();
        carte.nom = racine.get("nom").getAsString();
        carte.largeur = racine.get("largeur").getAsInt();
        carte.hauteur = racine.get("hauteur").getAsInt();
        if (carte.largeur < 1 || carte.largeur > DIMENSION_MAX
            || carte.hauteur < 1 || carte.hauteur > DIMENSION_MAX) {
            throw new IllegalArgumentException(
                "Dimensions de carte hors bornes: " + carte.largeur + "x" + carte.hauteur);
        }
        carte.seed = racine.has("seed") ? racine.get("seed").getAsLong() : 0;
        carte.apparitionX = racine.has("apparitionX") ? racine.get("apparitionX").getAsInt() : -1;
        carte.apparitionZ = racine.has("apparitionZ") ? racine.get("apparitionZ").getAsInt() : -1;

        if (racine.has("blocs")) {
            JsonArray blocsJson = racine.getAsJsonArray("blocs");
            if (blocsJson.size() > BLOCS_MAX) {
                throw new IllegalArgumentException("Trop de blocs dans la carte: " + blocsJson.size());
            }
            for (JsonElement element : blocsJson) {
                JsonObject objetBloc = element.getAsJsonObject();
                int x = objetBloc.get("x").getAsInt();
                int z = objetBloc.get("z").getAsInt();
                BlocCarte bloc = new BlocCarte();
                bloc.type = TypeElementCarte.valueOf(objetBloc.get("type").getAsString());
                bloc.elevation = objetBloc.has("elevation") ? objetBloc.get("elevation").getAsInt() : 0;
                bloc.qteBonbonVisible = objetBloc.has("bonbonsVisibles") ? objetBloc.get("bonbonsVisibles").getAsInt() : 0;
                bloc.delaiBonbonVisible = objetBloc.has("delaiVisible") ? objetBloc.get("delaiVisible").getAsInt() : 0;
                bloc.typeBonbonVisible = objetBloc.has("typeVisible")
                    ? TypeBonbonCarte.valueOf(objetBloc.get("typeVisible").getAsString()) : TypeBonbonCarte.STANDARD;
                bloc.delaiApparitionInitiale = objetBloc.has("apparitionInitiale")
                    ? objetBloc.get("apparitionInitiale").getAsInt() : 0;
                bloc.qteBonbonNonVisible = objetBloc.has("bonbonsNonVisibles") ? objetBloc.get("bonbonsNonVisibles").getAsInt() : 0;
                bloc.delaiBonbonNonVisible = objetBloc.has("delaiNonVisible") ? objetBloc.get("delaiNonVisible").getAsInt() : 0;
                bloc.delaiApparitionInitialeNonVisible = objetBloc.has("apparitionInitialeNonVisible")
                    ? objetBloc.get("apparitionInitialeNonVisible").getAsInt() : 0;
                carte.blocs.put(cle(x, z), bloc);
            }
        }

        if (racine.has("zones")) {
            for (JsonElement element : racine.getAsJsonArray("zones")) {
                JsonObject objetZone = element.getAsJsonObject();
                ZoneCarte zone = new ZoneCarte();
                zone.nom = objetZone.get("nom").getAsString();
                zone.type = TypeElementCarte.valueOf(objetZone.get("type").getAsString());
                for (JsonElement elementCellule : objetZone.getAsJsonArray("cellules")) {
                    JsonArray paire = elementCellule.getAsJsonArray();
                    zone.cellules.add(new int[]{paire.get(0).getAsInt(), paire.get(1).getAsInt()});
                }
                carte.zones.add(zone);
            }
        }

        return carte;
    }

    /** Assainit un nom de carte : espaces remplacés par des underscores */
    public static String assainirNom(String nomBrut) {
        if (nomBrut == null) {
            return "";
        }
        return nomBrut.trim().replace(' ', '_');
    }

    // ==================== Comptages pour les validations de lancement ====================

    /** Nombre total de bonbons visibles à l'intérieur du périmètre Limite */
    public int compterBonbonsVisiblesInterieur() {
        Set<Long> interieur = calculerInterieurLimite();
        int total = 0;
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            if (interieur.contains(entree.getKey())) {
                total += entree.getValue().qteBonbonVisible;
            }
        }
        return total;
    }

    /** Nombre de bonbons visibles de type STANDARD à l'intérieur du périmètre (refus strict au Sous-mode 2) */
    public int compterBonbonsVisiblesStandardInterieur() {
        Set<Long> interieur = calculerInterieurLimite();
        int total = 0;
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            BlocCarte bloc = entree.getValue();
            if (interieur.contains(entree.getKey()) && bloc.qteBonbonVisible > 0
                && bloc.typeBonbonVisible == TypeBonbonCarte.STANDARD) {
                total += bloc.qteBonbonVisible;
            }
        }
        return total;
    }

    /** Nombre total de bonbons non-visibles à l'intérieur du périmètre (ignorés aux Sous-modes 1 et 2) */
    public int compterBonbonsNonVisiblesInterieur() {
        Set<Long> interieur = calculerInterieurLimite();
        int total = 0;
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            if (interieur.contains(entree.getKey())) {
                total += entree.getValue().qteBonbonNonVisible;
            }
        }
        return total;
    }
}

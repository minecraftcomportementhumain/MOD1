package com.example.mysubmod.cartes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Données complètes d'une carte créée avec l'outil de création de carte.
 * Sérialisée en JSON (un fichier par carte) dans un dossier dédié du serveur.
 * Format v2 (compact : terrain en plages, bonbons en tableaux) écrit à la
 * sauvegarde ; les fichiers v1 historiques restent lisibles.
 */
public class CarteDonnees {
    public static final int ELEVATION_MIN = -15;
    public static final int ELEVATION_MAX = 15;
    public static final int LONGUEUR_MAX_NOM = 32;
    /**
     * Dimension maximale d'une carte (borne anti-DoS au décodage). 1800×1800 = 3,24 M
     * de cellules : le modèle en mémoire (Map de BlocCarte) d'une carte pleine reste
     * sous ~400 Mo, jouable sans augmenter la mémoire du serveur.
     */
    public static final int DIMENSION_MAX = 1800;
    /** Nombre maximal de blocs stockés dans une carte (borne anti-DoS au décodage). */
    public static final int BLOCS_MAX = DIMENSION_MAX * DIMENSION_MAX;
    /** Version du format d'écriture (2 = terrain en plages + bonbons compacts). */
    private static final int VERSION_FORMAT = 2;

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
     * L'ensemble retourné est adossé à un BitSet (index = z·largeur + x) : appartenance en O(1)
     * sans millions de Long boxés, indispensable aux grandes cartes (jusqu'à 1800×1800).
     */
    public Set<Long> calculerInterieurLimite() {
        return interieurLimite();
    }

    /** Variante interne typée : évite un transtypage du Set public dans les usages internes */
    private EnsembleCellules interieurLimite() {
        int total = largeur * hauteur;
        BitSet limites = new BitSet(total);
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            if (entree.getValue().type == TypeElementCarte.LIMITE) {
                int x = cleX(entree.getKey());
                int z = cleZ(entree.getKey());
                if (estDansAire(x, z)) {
                    limites.set(z * largeur + x);
                }
            }
        }

        // Remplissage extérieur (4-adjacence) amorcé sur tout le bord de l'aire
        BitSet exterieur = new BitSet(total);
        PileIndices pile = new PileIndices();
        for (int x = 0; x < largeur; x++) {
            amorcerExterieur(pile, exterieur, limites, x, 0);
            amorcerExterieur(pile, exterieur, limites, x, hauteur - 1);
        }
        for (int z = 0; z < hauteur; z++) {
            amorcerExterieur(pile, exterieur, limites, 0, z);
            amorcerExterieur(pile, exterieur, limites, largeur - 1, z);
        }
        while (!pile.estVide()) {
            int index = pile.depiler();
            int x = index % largeur;
            int z = index / largeur;
            amorcerExterieur(pile, exterieur, limites, x + 1, z);
            amorcerExterieur(pile, exterieur, limites, x - 1, z);
            amorcerExterieur(pile, exterieur, limites, x, z + 1);
            amorcerExterieur(pile, exterieur, limites, x, z - 1);
        }

        BitSet interieur = new BitSet(total);
        if (total > 0) {
            interieur.set(0, total);
        }
        interieur.andNot(exterieur);
        interieur.andNot(limites);
        return new EnsembleCellules(largeur, hauteur, interieur);
    }

    private void amorcerExterieur(PileIndices pile, BitSet exterieur, BitSet limites, int x, int z) {
        if (!estDansAire(x, z)) {
            return;
        }
        int index = z * largeur + x;
        if (exterieur.get(index) || limites.get(index)) {
            return;
        }
        exterieur.set(index);
        pile.empiler(index);
    }

    /**
     * Vérifie que le périmètre Limite forme une boucle fermée entourant complètement
     * des blocs non-Limite (au moins un bloc intérieur, et chaque bloc Limite touche l'intérieur).
     */
    public boolean limiteEstBoucleFermeeValide() {
        boolean aLimite = false;
        for (BlocCarte bloc : blocs.values()) {
            if (bloc.type == TypeElementCarte.LIMITE) {
                aLimite = true;
                break;
            }
        }
        if (!aLimite) {
            return false;
        }

        EnsembleCellules interieur = interieurLimite();
        if (interieur.isEmpty()) {
            return false; // Rien d'entouré : pas une boucle fermée utile
        }

        // Il ne peut exister qu'un seul périmètre : tous les blocs Limite doivent
        // participer à la boucle qui entoure l'intérieur (adjacents 8-directions à l'intérieur)
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            if (entree.getValue().type != TypeElementCarte.LIMITE) {
                continue;
            }
            int x = cleX(entree.getKey());
            int z = cleZ(entree.getKey());
            boolean toucheInterieur = false;
            for (int dx = -1; dx <= 1 && !toucheInterieur; dx++) {
                for (int dz = -1; dz <= 1 && !toucheInterieur; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (interieur.contientXZ(x + dx, z + dz)) {
                        toucheInterieur = true;
                    }
                }
            }
            if (!toucheInterieur) {
                return false;
            }
        }

        // L'intérieur doit être connexe (un seul périmètre par carte)
        BitSet bitsInterieur = interieur.bits();
        BitSet visites = new BitSet(largeur * hauteur);
        PileIndices pile = new PileIndices();
        int premier = bitsInterieur.nextSetBit(0);
        pile.empiler(premier);
        visites.set(premier);
        int nombreVisites = 1;
        while (!pile.estVide()) {
            int index = pile.depiler();
            int x = index % largeur;
            int z = index / largeur;
            int[] voisins = {
                x + 1 < largeur ? index + 1 : -1,
                x - 1 >= 0 ? index - 1 : -1,
                z + 1 < hauteur ? index + largeur : -1,
                z - 1 >= 0 ? index - largeur : -1
            };
            for (int voisin : voisins) {
                if (voisin >= 0 && bitsInterieur.get(voisin) && !visites.get(voisin)) {
                    visites.set(voisin);
                    nombreVisites++;
                    pile.empiler(voisin);
                }
            }
        }
        return nombreVisites == interieur.size();
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

        // Chaque bonbon doit appartenir à une parcelle : la navigation en jeu (HUD,
        // flèche, compteurs) fonctionne parcelle par parcelle
        int bonbonsSansParcelle = 0;
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            BlocCarte bloc = entree.getValue();
            if ((bloc.qteBonbonVisible > 0 || bloc.qteBonbonNonVisible > 0)
                && (bloc.zone <= 0 || bloc.zone > zones.size())) {
                bonbonsSansParcelle++;
            }
        }
        if (bonbonsSansParcelle > 0) {
            erreurs.add(bonbonsSansParcelle + " bonbon(s) hors de toute parcelle "
                + "(outil Parcelle : peignez une parcelle sur chaque bloc à bonbons)");
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

    // ==================== Parcelles (« zones » dans le format de fichier) ====================

    /**
     * Reconstruit les plages et le type de chaque parcelle depuis le champ {@code zone}
     * des blocs : une passe sur les blocs vers une grille d'ids, puis un balayage
     * row-major qui émet les plages triées. Le type devient Île si la parcelle contient
     * au moins une cellule Île (prérequis du choix de parcelle de départ), sinon
     * Pierre. Un nom vide est remplacé par « Parcelle n ».
     */
    public void reconstruireZonesDepuisBlocs() {
        int nombreZones = zones.size();
        if (nombreZones == 0) {
            return;
        }
        int[] grille = new int[largeur * hauteur];
        boolean[] contientIle = new boolean[nombreZones + 1];
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            BlocCarte bloc = entree.getValue();
            if (bloc.zone <= 0 || bloc.zone > nombreZones || !bloc.type.estElementDeBase()) {
                continue;
            }
            int x = cleX(entree.getKey());
            int z = cleZ(entree.getKey());
            if (!estDansAire(x, z)) {
                continue;
            }
            grille[z * largeur + x] = bloc.zone;
            if (bloc.type == TypeElementCarte.ILE) {
                contientIle[bloc.zone] = true;
            }
        }
        List<List<int[]>> plagesParZone = new ArrayList<>(nombreZones);
        for (int i = 0; i < nombreZones; i++) {
            plagesParZone.add(new ArrayList<>());
        }
        for (int z = 0; z < hauteur; z++) {
            int idCourant = 0;
            int debut = 0;
            for (int x = 0; x <= largeur; x++) {
                int id = x < largeur ? grille[z * largeur + x] : 0;
                if (id != idCourant) {
                    if (idCourant > 0) {
                        plagesParZone.get(idCourant - 1).add(new int[]{z, debut, x - debut});
                    }
                    idCourant = id;
                    debut = x;
                }
            }
        }
        for (int i = 0; i < nombreZones; i++) {
            ZoneCarte zone = zones.get(i);
            zone.plages = plagesParZone.get(i);
            zone.type = contientIle[i + 1] ? TypeElementCarte.ILE : TypeElementCarte.PIERRE;
            if (zone.nom == null || zone.nom.isBlank()) {
                zone.nom = "Parcelle " + (i + 1);
            }
        }
    }

    /**
     * Reporte les zones (plages) dans le champ {@code zone} des blocs — l'inverse de
     * {@link #reconstruireZonesDepuisBlocs()}. Appelé au décodage d'une carte à zonage
     * manuel, et par l'éditeur au chargement pour matérialiser les zones existantes.
     */
    public void assignerZonesAuxBlocs() {
        for (BlocCarte bloc : blocs.values()) {
            bloc.zone = 0;
        }
        for (int i = 0; i < zones.size(); i++) {
            for (int[] plage : zones.get(i).plages) {
                for (int k = 0; k < plage[2]; k++) {
                    BlocCarte bloc = blocs.get(cle(plage[1] + k, plage[0]));
                    if (bloc != null && bloc.type.estElementDeBase()) {
                        bloc.zone = i + 1;
                    }
                }
            }
        }
    }

    /**
     * UTILITAIRE (tests / outillage) : découpe la carte en parcelles automatiques (une
     * par masse de terre connexe) nommées « Île A », ..., « Zone Pierre 1 », ... et les
     * matérialise dans les blocs. N'est PLUS appelé à la sauvegarde : le zonage est
     * exclusivement manuel (outil Parcelle de l'éditeur).
     */
    public void recalculerZones() {
        zones.clear();
        int compteurIles = 0;
        int compteurPierre = 0;

        // Grille des types (index = z·largeur + x) : évite un accès Map par cellule visitée
        byte[] types = grilleTypes();
        BitSet visites = new BitSet(largeur * hauteur);
        PileIndices pile = new PileIndices();

        // Parcours déterministe (ordre de balayage) pour un nommage stable
        for (int z = 0; z < hauteur; z++) {
            for (int x = 0; x < largeur; x++) {
                int index = z * largeur + x;
                if (visites.get(index)) {
                    continue;
                }
                byte typeIci = types[index];
                if (typeIci != CODE_ILE && typeIci != CODE_PIERRE) {
                    continue;
                }

                // Remplissage de la zone connexe du même type (8-adjacence : les diagonales comptent)
                ZoneCarte zone = new ZoneCarte();
                zone.type = typeIci == CODE_ILE ? TypeElementCarte.ILE : TypeElementCarte.PIERRE;
                List<int[]> cellulesZone = new ArrayList<>();
                pile.empiler(index);
                visites.set(index);
                while (!pile.estVide()) {
                    int courant = pile.depiler();
                    int cx = courant % largeur;
                    int cz = courant / largeur;
                    cellulesZone.add(new int[]{cx, cz});
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            int vx = cx + dx;
                            int vz = cz + dz;
                            if (!estDansAire(vx, vz)) {
                                continue;
                            }
                            int voisin = vz * largeur + vx;
                            if (!visites.get(voisin) && types[voisin] == typeIci) {
                                visites.set(voisin);
                                pile.empiler(voisin);
                            }
                        }
                    }
                }

                // Plages triées en ordre de balayage : représentation identique en mémoire,
                // dans le fichier v2 et après rechargement (les égalités de distance au
                // centre dans le choix du point d'apparition d'une zone se départagent
                // donc toujours de la même façon)
                zone.plages = ZoneCarte.plagesDepuisCellules(cellulesZone);

                if (zone.type == TypeElementCarte.ILE) {
                    zone.nom = "Île " + lettreZone(compteurIles);
                    compteurIles++;
                } else {
                    compteurPierre++;
                    zone.nom = "Zone Pierre " + compteurPierre;
                }
                zones.add(zone);
            }
        }
        // Cohérence avec le modèle manuel : les blocs portent l'id de leur parcelle
        assignerZonesAuxBlocs();
    }

    // Codes de type dans la grille compacte (ordinaux de TypeElementCarte)
    private static final byte CODE_VIDE = (byte) TypeElementCarte.VIDE.ordinal();
    private static final byte CODE_ILE = (byte) TypeElementCarte.ILE.ordinal();
    private static final byte CODE_PIERRE = (byte) TypeElementCarte.PIERRE.ordinal();

    /** Grille des types de blocs (index = z·largeur + x, CODE_VIDE par défaut) */
    private byte[] grilleTypes() {
        byte[] types = new byte[largeur * hauteur];
        Arrays.fill(types, CODE_VIDE); // Explicite : l'invariant ne dépend pas de VIDE.ordinal() == 0
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            int x = cleX(entree.getKey());
            int z = cleZ(entree.getKey());
            if (estDansAire(x, z)) {
                types[z * largeur + x] = (byte) entree.getValue().type.ordinal();
            }
        }
        return types;
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

    /**
     * Sérialise la carte au format v2 compact :
     * - « terrain » : balayage rangée par rangée de toute l'aire, en plages « n:TÉ »
     *   (n cellules consécutives du type T à l'élévation É ; T ∈ V/E/I/P/L, élévation
     *   omise quand nulle) ;
     * - « bonbons » : un tableau de 10 entiers par bloc porteur de bonbons ;
     * - « zones » : cellules en plages « z,x0,longueur ».
     * Une grande carte passe ainsi de dizaines/centaines de Mo (v1 : un objet JSON par
     * bloc) à quelques dizaines/centaines de Ko. Les fichiers v1 restent lisibles.
     */
    public String versJson() {
        JsonObject racine = new JsonObject();
        racine.addProperty("version", VERSION_FORMAT);
        racine.addProperty("nom", nom);
        racine.addProperty("largeur", largeur);
        racine.addProperty("hauteur", hauteur);
        racine.addProperty("seed", seed);
        racine.addProperty("apparitionX", apparitionX);
        racine.addProperty("apparitionZ", apparitionZ);
        racine.addProperty("terrain", encoderTerrain());

        JsonArray tableauBonbons = new JsonArray();
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            BlocCarte bloc = entree.getValue();
            if (bloc.qteBonbonVisible <= 0 && bloc.qteBonbonNonVisible <= 0) {
                continue;
            }
            // Même filtre que le terrain : un bloc hors de l'aire (donnée v1 héritée)
            // serait rejeté par le décodeur v2 et rendrait la carte insauvegardable
            if (!estDansAire(cleX(entree.getKey()), cleZ(entree.getKey()))) {
                continue;
            }
            JsonArray entreeBonbon = new JsonArray();
            entreeBonbon.add(cleX(entree.getKey()));
            entreeBonbon.add(cleZ(entree.getKey()));
            entreeBonbon.add(bloc.qteBonbonVisible);
            entreeBonbon.add(bloc.delaiBonbonVisible);
            entreeBonbon.add(codePourTypeBonbon(bloc.typeBonbonVisible));
            entreeBonbon.add(bloc.delaiApparitionInitiale);
            entreeBonbon.add(bloc.qteBonbonNonVisible);
            entreeBonbon.add(bloc.delaiBonbonNonVisible);
            entreeBonbon.add(codePourTypeBonbon(bloc.typeBonbonNonVisible));
            entreeBonbon.add(bloc.delaiApparitionInitialeNonVisible);
            tableauBonbons.add(entreeBonbon);
        }
        racine.add("bonbons", tableauBonbons);

        // Les parcelles sont toujours dérivées de l'état courant des blocs (zonage
        // exclusivement manuel) ; celles restées vides ne sont pas sérialisées
        reconstruireZonesDepuisBlocs();
        JsonArray tableauZones = new JsonArray();
        for (ZoneCarte zone : zones) {
            if (zone.plages.isEmpty()) {
                continue;
            }
            JsonObject objetZone = new JsonObject();
            objetZone.addProperty("nom", zone.nom);
            objetZone.addProperty("type", zone.type.name());
            objetZone.addProperty("plages", encoderPlagesZone(zone));
            tableauZones.add(objetZone);
        }
        racine.add("zones", tableauZones);

        Gson gson = new GsonBuilder().create();
        return gson.toJson(racine);
    }

    /** Terrain en plages : grille compacte (type + élévation) puis fusion des cellules consécutives égales */
    private String encoderTerrain() {
        int total = largeur * hauteur;
        int[] grille = new int[total];
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            int x = cleX(entree.getKey());
            int z = cleZ(entree.getKey());
            BlocCarte bloc = entree.getValue();
            if (!estDansAire(x, z) || bloc.type == TypeElementCarte.VIDE) {
                continue; // Un bloc vide porteur de bonbons est décrit par « bonbons » seulement
            }
            grille[z * largeur + x] = (bloc.type.ordinal() << 8) | (bloc.elevation + 128);
        }

        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < total) {
            int valeur = grille[i];
            int debut = i;
            do {
                i++;
            } while (i < total && grille[i] == valeur);
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(i - debut).append(':');
            if (valeur == 0) {
                sb.append('V');
            } else {
                sb.append(carPourType(valeur >> 8));
                int elevation = (valeur & 0xFF) - 128;
                if (elevation != 0) {
                    sb.append(elevation);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Plages d'une zone au format fichier « z,x0,longueur ». Les plages sont rognées à
     * l'aire de la carte : des zones périmées peuvent subsister côté client après un
     * rétrécissement (le redimensionnement ne touche pas carte.zones), et le décodeur
     * v2 strict rendrait sinon la carte insauvegardable.
     */
    private String encoderPlagesZone(ZoneCarte zone) {
        StringBuilder sb = new StringBuilder();
        for (int[] plage : zone.plages) {
            int z = plage[0];
            if (z < 0 || z >= hauteur) {
                continue;
            }
            int x0 = Math.max(0, plage[1]);
            long fin = Math.min(largeur, (long) plage[1] + plage[2]);
            if (fin <= x0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(z).append(',').append(x0).append(',').append(fin - x0);
        }
        return sb.toString();
    }

    private static char carPourType(int ordinalType) {
        return switch (TypeElementCarte.values()[ordinalType]) {
            case EAU -> 'E';
            case ILE -> 'I';
            case PIERRE -> 'P';
            case LIMITE -> 'L';
            default -> 'V';
        };
    }

    private static TypeElementCarte typePourCar(char car) {
        return switch (car) {
            case 'V' -> TypeElementCarte.VIDE;
            case 'E' -> TypeElementCarte.EAU;
            case 'I' -> TypeElementCarte.ILE;
            case 'P' -> TypeElementCarte.PIERRE;
            case 'L' -> TypeElementCarte.LIMITE;
            default -> throw new IllegalArgumentException("Type de terrain inconnu : " + car);
        };
    }

    /** Codes stables des types de bonbon dans le format v2 (indépendants de l'ordre de l'enum) */
    private static int codePourTypeBonbon(TypeBonbonCarte type) {
        return switch (type) {
            case STANDARD -> 0;
            case BLEU -> 1;
            case ROUGE -> 2;
        };
    }

    private static TypeBonbonCarte typeBonbonPourCode(int code) {
        return switch (code) {
            case 0 -> TypeBonbonCarte.STANDARD;
            case 1 -> TypeBonbonCarte.BLEU;
            case 2 -> TypeBonbonCarte.ROUGE;
            default -> throw new IllegalArgumentException("Code de type de bonbon inconnu : " + code);
        };
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

        int version = racine.has("version") ? racine.get("version").getAsInt() : 1;
        if (version >= 2) {
            if (racine.has("terrain")) {
                decoderTerrain(carte, racine.get("terrain").getAsString());
            }
            if (racine.has("bonbons")) {
                decoderBonbons(carte, racine.getAsJsonArray("bonbons"));
            }
            if (racine.has("zones")) {
                decoderZonesV2(carte, racine.getAsJsonArray("zones"));
            }
            // Les blocs portent l'id de leur parcelle (source de vérité à l'édition
            // comme à la re-sérialisation)
            carte.assignerZonesAuxBlocs();
            return carte;
        }

        // ----- Format v1 historique : un objet JSON par bloc -----
        if (racine.has("blocs")) {
            JsonArray blocsJson = racine.getAsJsonArray("blocs");
            if (blocsJson.size() > BLOCS_MAX) {
                throw new IllegalArgumentException("Trop de blocs dans la carte: " + blocsJson.size());
            }
            carte.blocs = new HashMap<>(capacitePourElements(blocsJson.size()));
            for (JsonElement element : blocsJson) {
                JsonObject objetBloc = element.getAsJsonObject();
                int x = objetBloc.get("x").getAsInt();
                int z = objetBloc.get("z").getAsInt();
                BlocCarte bloc = new BlocCarte();
                bloc.type = TypeElementCarte.valueOf(objetBloc.get("type").getAsString());
                // Les fichiers v1 n'étaient pas bornés : ramener l'élévation dans le domaine
                // pour que le ré-enregistrement en v2 (8 bits, décodeur strict) reste valide
                int elevationV1 = objetBloc.has("elevation") ? objetBloc.get("elevation").getAsInt() : 0;
                bloc.elevation = Math.max(ELEVATION_MIN, Math.min(ELEVATION_MAX, elevationV1));
                bloc.qteBonbonVisible = objetBloc.has("bonbonsVisibles") ? objetBloc.get("bonbonsVisibles").getAsInt() : 0;
                bloc.delaiBonbonVisible = objetBloc.has("delaiVisible") ? objetBloc.get("delaiVisible").getAsInt() : 0;
                bloc.typeBonbonVisible = objetBloc.has("typeVisible")
                    ? TypeBonbonCarte.valueOf(objetBloc.get("typeVisible").getAsString()) : TypeBonbonCarte.STANDARD;
                bloc.delaiApparitionInitiale = objetBloc.has("apparitionInitiale")
                    ? objetBloc.get("apparitionInitiale").getAsInt() : 0;
                bloc.qteBonbonNonVisible = objetBloc.has("bonbonsNonVisibles") ? objetBloc.get("bonbonsNonVisibles").getAsInt() : 0;
                bloc.delaiBonbonNonVisible = objetBloc.has("delaiNonVisible") ? objetBloc.get("delaiNonVisible").getAsInt() : 0;
                bloc.typeBonbonNonVisible = objetBloc.has("typeNonVisible")
                    ? TypeBonbonCarte.valueOf(objetBloc.get("typeNonVisible").getAsString()) : TypeBonbonCarte.STANDARD;
                bloc.delaiApparitionInitialeNonVisible = objetBloc.has("apparitionInitialeNonVisible")
                    ? objetBloc.get("apparitionInitialeNonVisible").getAsInt() : 0;
                carte.blocs.put(cle(x, z), bloc);
            }
        }

        if (racine.has("zones")) {
            long totalCellules = 0;
            for (JsonElement element : racine.getAsJsonArray("zones")) {
                JsonObject objetZone = element.getAsJsonObject();
                ZoneCarte zone = new ZoneCarte();
                zone.nom = objetZone.get("nom").getAsString();
                zone.type = TypeElementCarte.valueOf(objetZone.get("type").getAsString());
                // Expansion transitoire (format v1 seulement) puis conversion en plages
                List<int[]> cellules = new ArrayList<>();
                for (JsonElement elementCellule : objetZone.getAsJsonArray("cellules")) {
                    totalCellules++;
                    if (totalCellules > BLOCS_MAX) {
                        // Même garde anti-DoS que les zones v2
                        throw new IllegalArgumentException("Trop de cellules de parcelles");
                    }
                    JsonArray paire = elementCellule.getAsJsonArray();
                    cellules.add(new int[]{paire.get(0).getAsInt(), paire.get(1).getAsInt()});
                }
                zone.plages = ZoneCarte.plagesDepuisCellules(cellules);
                carte.zones.add(zone);
            }
        }

        carte.assignerZonesAuxBlocs();
        return carte;
    }

    /**
     * Décode la chaîne de plages « n:TÉ;… » du format v2 (balayage rangée par rangée de
     * toute l'aire). Une seule passe d'analyse valide toutes les plages (bornes calculées
     * en long : insensibles au débordement d'entier), puis le remplissage s'effectue dans
     * une table pré-dimensionnée — sur les très grandes cartes, les redimensionnements
     * successifs de la HashMap domineraient sinon le temps de décodage.
     */
    private static void decoderTerrain(CarteDonnees carte, String terrain) {
        int total = carte.largeur * carte.hauteur;

        int nbPlages = 0;
        int[] nombres = new int[64];
        TypeElementCarte[] types = new TypeElementCarte[64];
        int[] elevations = new int[64];
        long cellulesDecrites = 0;
        long nonVides = 0;

        int i = 0;
        int n = terrain.length();
        while (i < n) {
            int fin = terrain.indexOf(';', i);
            if (fin < 0) {
                fin = n;
            }
            int deuxPoints = terrain.indexOf(':', i);
            if (deuxPoints < 0 || deuxPoints + 1 >= fin) {
                throw new IllegalArgumentException("Plage de terrain mal formée");
            }
            int nombre;
            int elevation;
            try {
                nombre = Integer.parseInt(terrain.substring(i, deuxPoints));
                elevation = deuxPoints + 2 < fin ? Integer.parseInt(terrain.substring(deuxPoints + 2, fin)) : 0;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Plage de terrain mal formée", e);
            }
            TypeElementCarte type = typePourCar(terrain.charAt(deuxPoints + 1));

            // Comparaison en long : « index + nombre > total » en int serait contournable
            // par débordement (ex. nombre proche de 2^31), annulant la borne anti-DoS
            if (nombre < 1 || nombre > total - cellulesDecrites) {
                throw new IllegalArgumentException("Plage de terrain hors bornes");
            }
            if (elevation < ELEVATION_MIN || elevation > ELEVATION_MAX) {
                throw new IllegalArgumentException("Élévation hors bornes: " + elevation);
            }

            if (nbPlages == nombres.length) {
                nombres = Arrays.copyOf(nombres, nbPlages * 2);
                types = Arrays.copyOf(types, nbPlages * 2);
                elevations = Arrays.copyOf(elevations, nbPlages * 2);
            }
            nombres[nbPlages] = nombre;
            types[nbPlages] = type;
            elevations[nbPlages] = elevation;
            nbPlages++;
            cellulesDecrites += nombre;
            if (type != TypeElementCarte.VIDE) {
                nonVides += nombre;
            }
            i = fin + 1;
        }
        if (cellulesDecrites != total) {
            throw new IllegalArgumentException(
                "Terrain incomplet: " + cellulesDecrites + " cellules décrites sur " + total);
        }

        if (nonVides > 0) {
            carte.blocs = new HashMap<>(capacitePourElements((int) nonVides));
        }
        int index = 0;
        for (int p = 0; p < nbPlages; p++) {
            if (types[p] != TypeElementCarte.VIDE) {
                for (int k = 0; k < nombres[p]; k++) {
                    int cellule = index + k;
                    carte.blocs.put(cle(cellule % carte.largeur, cellule / carte.largeur),
                        new BlocCarte(types[p], elevations[p]));
                }
            }
            index += nombres[p];
        }
    }

    /** Capacité initiale d'une HashMap destinée à recevoir ce nombre d'éléments sans redimensionnement */
    private static int capacitePourElements(int elements) {
        return Math.max(16, (int) (elements / 0.75f) + 1);
    }

    /** Décode le tableau « bonbons » du format v2 (10 entiers par bloc porteur de bonbons) */
    private static void decoderBonbons(CarteDonnees carte, JsonArray bonbonsJson) {
        if (bonbonsJson.size() > BLOCS_MAX) {
            throw new IllegalArgumentException("Trop de blocs à bonbons: " + bonbonsJson.size());
        }
        for (JsonElement element : bonbonsJson) {
            JsonArray entree = element.getAsJsonArray();
            if (entree.size() < 10) {
                throw new IllegalArgumentException("Entrée de bonbons incomplète");
            }
            int x = entree.get(0).getAsInt();
            int z = entree.get(1).getAsInt();
            if (!carte.estDansAire(x, z)) {
                throw new IllegalArgumentException("Bloc à bonbons hors de l'aire: (" + x + ", " + z + ")");
            }
            long c = cle(x, z);
            BlocCarte bloc = carte.blocs.get(c);
            if (bloc == null) {
                bloc = new BlocCarte(); // Bonbons sur bloc vide (données v1 héritées)
                carte.blocs.put(c, bloc);
            }
            bloc.qteBonbonVisible = entree.get(2).getAsInt();
            bloc.delaiBonbonVisible = entree.get(3).getAsInt();
            bloc.typeBonbonVisible = typeBonbonPourCode(entree.get(4).getAsInt());
            bloc.delaiApparitionInitiale = entree.get(5).getAsInt();
            bloc.qteBonbonNonVisible = entree.get(6).getAsInt();
            bloc.delaiBonbonNonVisible = entree.get(7).getAsInt();
            bloc.typeBonbonNonVisible = typeBonbonPourCode(entree.get(8).getAsInt());
            bloc.delaiApparitionInitialeNonVisible = entree.get(9).getAsInt();
        }
    }

    /** Décode les zones v2 (cellules en plages « z,x0,longueur ») */
    private static void decoderZonesV2(CarteDonnees carte, JsonArray zonesJson) {
        long totalCellules = 0;
        for (JsonElement element : zonesJson) {
            JsonObject objetZone = element.getAsJsonObject();
            ZoneCarte zone = new ZoneCarte();
            zone.nom = objetZone.get("nom").getAsString();
            zone.type = TypeElementCarte.valueOf(objetZone.get("type").getAsString());
            String plages = objetZone.has("plages") ? objetZone.get("plages").getAsString() : "";
            int i = 0;
            int n = plages.length();
            while (i < n) {
                int fin = plages.indexOf(';', i);
                if (fin < 0) {
                    fin = n;
                }
                int virgule1 = plages.indexOf(',', i);
                int virgule2 = virgule1 < 0 ? -1 : plages.indexOf(',', virgule1 + 1);
                if (virgule1 < 0 || virgule2 < 0 || virgule2 >= fin) {
                    throw new IllegalArgumentException("Plage de parcelle mal formée");
                }
                int z = Integer.parseInt(plages.substring(i, virgule1));
                int x0 = Integer.parseInt(plages.substring(virgule1 + 1, virgule2));
                int longueur = Integer.parseInt(plages.substring(virgule2 + 1, fin));
                // Comparaison en long : « x0 + longueur » en int serait contournable par débordement
                if (longueur < 1 || z < 0 || z >= carte.hauteur
                    || x0 < 0 || (long) x0 + longueur > carte.largeur) {
                    throw new IllegalArgumentException("Plage de parcelle hors bornes");
                }
                totalCellules += longueur;
                if (totalCellules > BLOCS_MAX) {
                    throw new IllegalArgumentException("Trop de cellules de parcelles");
                }
                zone.plages.add(new int[]{z, x0, longueur});
                i = fin + 1;
            }
            // L'appartenance se teste par recherche binaire : garantir le tri même
            // pour un fichier forgé dont les plages seraient désordonnées
            zone.plages.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
            carte.zones.add(zone);
        }
    }

    /**
     * Extrait la seed d'un JSON de carte sans construire l'arbre complet (lecture en
     * flux : les gros champs comme « terrain » ou « blocs » sont sautés). Retourne 0
     * si la seed est absente ou le JSON illisible.
     */
    public static long seedDepuisJson(String json) {
        try (com.google.gson.stream.JsonReader lecteur =
                 new com.google.gson.stream.JsonReader(new java.io.StringReader(json))) {
            lecteur.beginObject();
            while (lecteur.hasNext()) {
                if ("seed".equals(lecteur.nextName())) {
                    return lecteur.nextLong();
                }
                lecteur.skipValue();
            }
        } catch (Exception e) {
            // JSON illisible ou seed absente : traité comme « pas de seed »
        }
        return 0;
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

    /**
     * Nombre de bonbons non-visibles de type STANDARD à l'intérieur du périmètre
     * (la spécialisation du Sous-mode 3 exige que tous les bonbons soient typés Bleu/Rouge)
     */
    public int compterBonbonsNonVisiblesStandardInterieur() {
        Set<Long> interieur = calculerInterieurLimite();
        int total = 0;
        for (Map.Entry<Long, BlocCarte> entree : blocs.entrySet()) {
            BlocCarte bloc = entree.getValue();
            if (interieur.contains(entree.getKey()) && bloc.qteBonbonNonVisible > 0
                && bloc.typeBonbonNonVisible == TypeBonbonCarte.STANDARD) {
                total += bloc.qteBonbonNonVisible;
            }
        }
        return total;
    }

    // ==================== Structures compactes ====================

    /**
     * Ensemble immuable de cellules adossé à un BitSet (index = z·largeur + x).
     * Vu comme un Set&lt;Long&gt; de clés cle(x, z) par les appelants existants :
     * appartenance en O(1) et itération sans stocker des millions de Long boxés.
     */
    private static final class EnsembleCellules extends AbstractSet<Long> {
        private final int largeur;
        private final int hauteur;
        private final BitSet cellules;
        private final int cardinal;

        EnsembleCellules(int largeur, int hauteur, BitSet cellules) {
            this.largeur = largeur;
            this.hauteur = hauteur;
            this.cellules = cellules;
            this.cardinal = cellules.cardinality();
        }

        BitSet bits() {
            return cellules;
        }

        boolean contientXZ(int x, int z) {
            return x >= 0 && x < largeur && z >= 0 && z < hauteur && cellules.get(z * largeur + x);
        }

        @Override
        public boolean contains(Object objet) {
            if (!(objet instanceof Long valeur)) {
                return false;
            }
            return contientXZ(cleX(valeur), cleZ(valeur));
        }

        @Override
        public int size() {
            return cardinal;
        }

        @Override
        public Iterator<Long> iterator() {
            return new Iterator<>() {
                private int prochain = cellules.nextSetBit(0);

                @Override
                public boolean hasNext() {
                    return prochain >= 0;
                }

                @Override
                public Long next() {
                    if (prochain < 0) {
                        throw new NoSuchElementException();
                    }
                    int index = prochain;
                    prochain = cellules.nextSetBit(index + 1);
                    return cle(index % largeur, index / largeur);
                }
            };
        }
    }

    /** Pile d'indices de cellules en primitifs (remplissages sans boxing) */
    private static final class PileIndices {
        private int[] valeurs = new int[1024];
        private int taille = 0;

        void empiler(int valeur) {
            if (taille == valeurs.length) {
                valeurs = Arrays.copyOf(valeurs, valeurs.length * 2);
            }
            valeurs[taille++] = valeur;
        }

        int depiler() {
            return valeurs[--taille];
        }

        boolean estVide() {
            return taille == 0;
        }
    }
}

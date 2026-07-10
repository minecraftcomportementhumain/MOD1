package com.example.mysubmod.cartes;

import com.example.mysubmod.cartes.reseau.UtilitaireCompressionCarte;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du domaine « cartes » (aucune dépendance Minecraft) : format v2 en plages,
 * compatibilité avec le format v1 historique, parité des remplissages BitSet avec
 * les implémentations d'origine (oracles copiés en bas de ce fichier), compression
 * réseau, bornes anti-abus et tenue à la dimension maximale.
 *
 * <p>Ces tests s'exécutent via {@code gradlew test} et font partie de {@code gradlew
 * build} : la CI refuse de publier un build dont ils échouent.</p>
 */
class CarteDonneesTest {

    // ==================== Aller-retour v2 et compatibilité v1 ====================

    @Test
    void allerRetourV2ConserveTouteLaCarte() {
        CarteDonnees carte = carteVariee(60, 40, 42);
        carte.recalculerZones();
        CarteDonnees relue = CarteDonnees.depuisJson(carte.versJson());
        verifierCartesEgales(carte, relue);
    }

    @Test
    void lectureV1ProduitLaMemeCarteQueV2() {
        CarteDonnees carte = carteVariee(60, 40, 42);
        carte.recalculerZones();
        CarteDonnees relueV1 = CarteDonnees.depuisJson(versJsonV1(carte));
        verifierCartesEgales(carte, relueV1);
    }

    @Test
    void seedLisibleEnFluxSurLesDeuxFormats() {
        CarteDonnees carte = carteVariee(30, 30, 42);
        assertEquals(42, CarteDonnees.seedDepuisJson(carte.versJson()));
        assertEquals(42, CarteDonnees.seedDepuisJson(versJsonV1(carte)));
        assertEquals(0, CarteDonnees.seedDepuisJson("pas du json"));
    }

    // ==================== Parité avec les implémentations d'origine ====================

    @Test
    void interieurEtBoucleIdentiquesAuxAnciennesImplementations() {
        for (long graine = 0; graine < 200; graine++) {
            CarteDonnees carte = graine % 5 == 0
                ? carteVariee(4 + (int) (graine % 30), 4 + (int) (graine % 23), graine)
                : carteAleatoire(graine);

            Set<Long> reference = referenceInterieur(carte);
            Set<Long> nouveau = carte.calculerInterieurLimite();
            assertEquals(reference.size(), nouveau.size(), "taille intérieur, graine=" + graine);
            for (long cle : reference) {
                assertTrue(nouveau.contains(cle), "cellule intérieure manquante, graine=" + graine);
            }
            assertEquals(referenceBoucleValide(carte), carte.limiteEstBoucleFermeeValide(),
                "validité de boucle, graine=" + graine);
        }
    }

    @Test
    void zonesIdentiquesAuxAnciennesImplementations() {
        for (long graine = 0; graine < 200; graine++) {
            CarteDonnees carte = graine % 5 == 0
                ? carteVariee(4 + (int) (graine % 30), 4 + (int) (graine % 23), graine)
                : carteAleatoire(graine);
            List<RefZone> reference = referenceZones(carte);
            carte.recalculerZones();
            assertEquals(reference.size(), carte.zones.size(), "nombre de zones, graine=" + graine);
            for (int i = 0; i < reference.size(); i++) {
                RefZone attendue = reference.get(i);
                ZoneCarte reelle = carte.zones.get(i);
                assertEquals(attendue.nom, reelle.nom, "nom de zone, graine=" + graine);
                assertEquals(attendue.type, reelle.type, "type de zone, graine=" + graine);
                assertEquals(attendue.cellules, ensembleDepuisPlages(reelle.plages),
                    "cellules de la zone " + attendue.nom + ", graine=" + graine);
            }
        }
    }

    @Test
    void appartenanceAuxPlagesIdentiqueAuBalayageLineaire() {
        Random alea = new Random(7);
        for (int essai = 0; essai < 50; essai++) {
            List<int[]> cellules = new ArrayList<>();
            Set<Long> attendu = new HashSet<>();
            for (int i = 0; i < 300; i++) {
                int x = alea.nextInt(60) - 30;
                int z = alea.nextInt(60) - 30;
                if (attendu.add(CarteDonnees.cle(x, z))) {
                    cellules.add(new int[]{x, z});
                }
            }
            List<int[]> plages = ZoneCarte.plagesDepuisCellules(cellules);
            for (int x = -32; x <= 32; x++) {
                for (int z = -32; z <= 32; z++) {
                    assertEquals(attendu.contains(CarteDonnees.cle(x, z)),
                        ZoneCarte.plagesContiennent(plages, x, z),
                        "appartenance en (" + x + ", " + z + "), essai=" + essai);
                }
            }
        }
    }

    // ==================== Compression réseau ====================

    @Test
    void gzipAllerRetourEtDonneesNonCompresseesInchangees() {
        byte[] brut = carteVariee(40, 40, 3).versJson().getBytes(StandardCharsets.UTF_8);
        byte[] comprime = UtilitaireCompressionCarte.compresser(brut);
        assertTrue(Arrays.equals(brut, UtilitaireCompressionCarte.decompresserSiGzip(comprime)));
        assertTrue(Arrays.equals(brut, UtilitaireCompressionCarte.decompresserSiGzip(brut)));
    }

    // ==================== Bornes et malformations ====================

    @Test
    void dimensionsAuDelaDeLaLimiteRefusees() {
        assertThrows(IllegalArgumentException.class, () -> CarteDonnees.depuisJson(
            "{\"nom\":\"x\",\"largeur\":" + (CarteDonnees.DIMENSION_MAX + 1) + ",\"hauteur\":10}"));
    }

    @Test
    void terrainsMalformesOuHostilesRejetesProprement() {
        assertThrows(IllegalArgumentException.class,
            () -> depuisTerrain("1:V;2147483647:I"), "débordement d'entier (anti-DoS)");
        assertThrows(IllegalArgumentException.class, () -> depuisTerrain("5:"), "plage sans type");
        assertThrows(IllegalArgumentException.class, () -> depuisTerrain("3:I"), "terrain incomplet");
        assertThrows(IllegalArgumentException.class, () -> depuisTerrain("2:I;abc:V"), "nombre non numérique");
        assertThrows(IllegalArgumentException.class, () -> depuisTerrain("2:I20;2:V"), "élévation hors bornes");
        assertThrows(IllegalArgumentException.class, () -> depuisTerrain("2:X;2:V"), "type inconnu");
    }

    @Test
    void plageDeZoneEnDebordementRejetee() {
        assertThrows(IllegalArgumentException.class, () -> CarteDonnees.depuisJson(
            "{\"version\":2,\"nom\":\"z\",\"largeur\":10,\"hauteur\":10,\"terrain\":\"100:V\","
                + "\"zones\":[{\"nom\":\"Ile A\",\"type\":\"ILE\",\"plages\":\"0,2147483640,8\"}]}"));
    }

    @Test
    void elevationsV1HorsBornesRameneesDansLeDomaine() {
        CarteDonnees carte = CarteDonnees.depuisJson("{\"nom\":\"c\",\"largeur\":3,\"hauteur\":1,"
            + "\"blocs\":[{\"x\":0,\"z\":0,\"type\":\"ILE\",\"elevation\":20},"
            + "{\"x\":1,\"z\":0,\"type\":\"PIERRE\",\"elevation\":-99}]}");
        assertEquals(CarteDonnees.ELEVATION_MAX, carte.obtenirBloc(0, 0).elevation);
        assertEquals(CarteDonnees.ELEVATION_MIN, carte.obtenirBloc(1, 0).elevation);
        // Et la carte clampée doit se re-sauvegarder en v2 sans erreur
        verifierCartesEgales(carte, CarteDonnees.depuisJson(carte.versJson()));
    }

    @Test
    void zonesPerimeesApresRetrecissementNEmpechentPasLaSauvegarde() {
        CarteDonnees carte = carteVariee(20, 20, 5);
        carte.recalculerZones();
        carte.redimensionner(10, 10); // les zones ne sont pas recalculées : cellules hors aire
        CarteDonnees relue = CarteDonnees.depuisJson(carte.versJson());
        assertEquals(10, relue.largeur);
        assertEquals(10, relue.hauteur);
    }

    // ==================== Tenue à la dimension maximale ====================

    @Test
    void carteVideALaDimensionMaximale() {
        CarteDonnees carte = new CarteDonnees();
        carte.nom = "max";
        carte.largeur = CarteDonnees.DIMENSION_MAX;
        carte.hauteur = CarteDonnees.DIMENSION_MAX;
        verifierCartesEgales(carte, CarteDonnees.depuisJson(carte.versJson()));
    }

    @Test
    void cartePleineALaDimensionMaximale() {
        int cote = CarteDonnees.DIMENSION_MAX;
        CarteDonnees carte = new CarteDonnees();
        carte.nom = "grande";
        carte.largeur = cote;
        carte.hauteur = cote;
        carte.seed = 7;
        Random alea = new Random(7);
        for (int x = 0; x < cote; x++) {
            for (int z = 0; z < cote; z++) {
                boolean bord = x == 0 || z == 0 || x == cote - 1 || z == cote - 1;
                BlocCarte bloc = bord ? new BlocCarte(TypeElementCarte.LIMITE, 0)
                    : (((x / 40) + (z / 40)) % 2 == 0
                        ? new BlocCarte(TypeElementCarte.EAU, 0)
                        : new BlocCarte(TypeElementCarte.ILE, (x / 40 + z / 40) % 8));
                carte.blocs.put(CarteDonnees.cle(x, z), bloc);
            }
        }
        for (int i = 0; i < 2000; i++) {
            BlocCarte bloc = carte.blocs.get(CarteDonnees.cle(
                1 + alea.nextInt(cote - 2), 1 + alea.nextInt(cote - 2)));
            if (bloc.type == TypeElementCarte.ILE) {
                bloc.qteBonbonVisible = 1 + alea.nextInt(3);
                bloc.delaiBonbonVisible = 30;
            }
        }
        carte.apparitionX = cote / 2;
        carte.apparitionZ = cote / 2;

        assertTrue(carte.limiteEstBoucleFermeeValide(), "boucle Limite valide");
        assertEquals((long) (cote - 2) * (cote - 2), (long) carte.calculerInterieurLimite().size(),
            "taille de l'intérieur");
        carte.recalculerZones();
        verifierCartesEgales(carte, CarteDonnees.depuisJson(carte.versJson()));
    }

    // ==================== Aides ====================

    private static void depuisTerrain(String terrain) {
        CarteDonnees.depuisJson("{\"version\":2,\"nom\":\"t\",\"largeur\":4,\"hauteur\":1,\"terrain\":\""
            + terrain + "\"}");
    }

    private static void verifierCartesEgales(CarteDonnees a, CarteDonnees b) {
        assertEquals(a.nom, b.nom);
        assertEquals(a.largeur, b.largeur);
        assertEquals(a.hauteur, b.hauteur);
        assertEquals(a.seed, b.seed);
        assertEquals(a.apparitionX, b.apparitionX);
        assertEquals(a.apparitionZ, b.apparitionZ);
        assertEquals(a.blocs.keySet(), b.blocs.keySet(), "ensembles de blocs");
        for (Map.Entry<Long, BlocCarte> entree : a.blocs.entrySet()) {
            assertTrue(entree.getValue().memeContenu(b.blocs.get(entree.getKey())),
                "bloc différent en (" + CarteDonnees.cleX(entree.getKey())
                    + ", " + CarteDonnees.cleZ(entree.getKey()) + ")");
        }
        assertEquals(a.zones.size(), b.zones.size(), "nombre de zones");
        for (int i = 0; i < a.zones.size(); i++) {
            assertEquals(a.zones.get(i).nom, b.zones.get(i).nom);
            assertEquals(a.zones.get(i).type, b.zones.get(i).type);
            assertEquals(ensembleDepuisPlages(a.zones.get(i).plages),
                ensembleDepuisPlages(b.zones.get(i).plages), "cellules de la zone " + a.zones.get(i).nom);
        }
    }

    private static Set<Long> ensembleDepuisPlages(List<int[]> plages) {
        Set<Long> ensemble = new HashSet<>();
        for (int[] plage : plages) {
            for (int k = 0; k < plage[2]; k++) {
                ensemble.add(CarteDonnees.cle(plage[1] + k, plage[0]));
            }
        }
        return ensemble;
    }

    /** Carte valide : anneau Limite, intérieur eau/île/pierre avec élévations, bonbons variés */
    private static CarteDonnees carteVariee(int largeur, int hauteur, long graine) {
        Random alea = new Random(graine);
        CarteDonnees carte = new CarteDonnees();
        carte.nom = "test_" + graine;
        carte.largeur = largeur;
        carte.hauteur = hauteur;
        carte.seed = graine;
        for (int x = 0; x < largeur; x++) {
            for (int z = 0; z < hauteur; z++) {
                boolean bord = x == 0 || z == 0 || x == largeur - 1 || z == hauteur - 1;
                if (bord) {
                    carte.blocs.put(CarteDonnees.cle(x, z), new BlocCarte(TypeElementCarte.LIMITE, 0));
                    continue;
                }
                int tirage = alea.nextInt(10);
                BlocCarte bloc;
                if (tirage < 5) {
                    bloc = new BlocCarte(TypeElementCarte.EAU, -alea.nextInt(4));
                } else if (tirage < 8) {
                    bloc = new BlocCarte(TypeElementCarte.ILE, alea.nextInt(31) - 15);
                } else {
                    bloc = new BlocCarte(TypeElementCarte.PIERRE, alea.nextInt(31) - 15);
                }
                if (bloc.type != TypeElementCarte.EAU && alea.nextInt(12) == 0) {
                    bloc.qteBonbonNonVisible = 1 + alea.nextInt(3);
                    bloc.delaiBonbonNonVisible = alea.nextInt(3) == 0 ? 0 : alea.nextInt(600);
                    bloc.typeBonbonNonVisible = TypeBonbonCarte.values()[alea.nextInt(3)];
                    bloc.delaiApparitionInitialeNonVisible = alea.nextInt(2) == 0 ? 0 : alea.nextInt(300);
                    if (bloc.elevation <= CarteDonnees.ELEVATION_MIN) {
                        bloc.elevation = CarteDonnees.ELEVATION_MIN + 1;
                    }
                }
                if (alea.nextInt(10) == 0) {
                    bloc.qteBonbonVisible = 1 + alea.nextInt(5);
                    bloc.delaiBonbonVisible = alea.nextInt(3) == 0 ? 0 : alea.nextInt(600);
                    bloc.typeBonbonVisible = TypeBonbonCarte.values()[alea.nextInt(3)];
                    bloc.delaiApparitionInitiale = alea.nextInt(2) == 0 ? 0 : alea.nextInt(300);
                    if (bloc.elevation >= CarteDonnees.ELEVATION_MAX) {
                        bloc.elevation = CarteDonnees.ELEVATION_MAX - 1;
                    }
                }
                carte.blocs.put(CarteDonnees.cle(x, z), bloc);
            }
        }
        carte.apparitionX = largeur / 2;
        carte.apparitionZ = hauteur / 2;
        // Bloc dégénéré hérité du v1 : bonbon visible sur bloc VIDE (élévation 0)
        BlocCarte degenere = new BlocCarte(TypeElementCarte.VIDE, 0);
        degenere.qteBonbonVisible = 2;
        degenere.delaiBonbonVisible = 45;
        carte.blocs.put(CarteDonnees.cle(1, 1), degenere);
        return carte;
    }

    /** Carte aléatoire quelconque (pas forcément valide) pour les tests de parité */
    private static CarteDonnees carteAleatoire(long graine) {
        Random alea = new Random(graine);
        CarteDonnees carte = new CarteDonnees();
        carte.nom = "alea_" + graine;
        carte.largeur = 1 + alea.nextInt(40);
        carte.hauteur = 1 + alea.nextInt(40);
        int nombre = alea.nextInt(carte.largeur * carte.hauteur + 1);
        TypeElementCarte[] types = TypeElementCarte.values();
        for (int i = 0; i < nombre; i++) {
            int x = alea.nextInt(carte.largeur);
            int z = alea.nextInt(carte.hauteur);
            TypeElementCarte type = types[alea.nextInt(types.length)];
            if (type == TypeElementCarte.VIDE) {
                continue;
            }
            carte.blocs.put(CarteDonnees.cle(x, z), new BlocCarte(type, alea.nextInt(31) - 15));
        }
        return carte;
    }

    // ==================== Oracles : implémentations d'origine (avant BitSet/plages) ====================

    /** Ancien encodeur v1 (un objet JSON par bloc), copié du code d'origine */
    private static String versJsonV1(CarteDonnees carte) {
        JsonObject racine = new JsonObject();
        racine.addProperty("nom", carte.nom);
        racine.addProperty("largeur", carte.largeur);
        racine.addProperty("hauteur", carte.hauteur);
        racine.addProperty("seed", carte.seed);
        racine.addProperty("apparitionX", carte.apparitionX);
        racine.addProperty("apparitionZ", carte.apparitionZ);
        JsonArray tableauBlocs = new JsonArray();
        for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
            BlocCarte bloc = entree.getValue();
            JsonObject objetBloc = new JsonObject();
            objetBloc.addProperty("x", CarteDonnees.cleX(entree.getKey()));
            objetBloc.addProperty("z", CarteDonnees.cleZ(entree.getKey()));
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
                if (bloc.typeBonbonNonVisible != TypeBonbonCarte.STANDARD) {
                    objetBloc.addProperty("typeNonVisible", bloc.typeBonbonNonVisible.name());
                }
                if (bloc.delaiApparitionInitialeNonVisible > 0) {
                    objetBloc.addProperty("apparitionInitialeNonVisible", bloc.delaiApparitionInitialeNonVisible);
                }
            }
            tableauBlocs.add(objetBloc);
        }
        racine.add("blocs", tableauBlocs);
        JsonArray tableauZones = new JsonArray();
        for (ZoneCarte zone : carte.zones) {
            JsonObject objetZone = new JsonObject();
            objetZone.addProperty("nom", zone.nom);
            objetZone.addProperty("type", zone.type.name());
            JsonArray cellules = new JsonArray();
            for (int[] plage : zone.plages) {
                for (int k = 0; k < plage[2]; k++) {
                    JsonArray paire = new JsonArray();
                    paire.add(plage[1] + k);
                    paire.add(plage[0]);
                    cellules.add(paire);
                }
            }
            objetZone.add("cellules", cellules);
            tableauZones.add(objetZone);
        }
        racine.add("zones", tableauZones);
        return new GsonBuilder().create().toJson(racine);
    }

    /** Ancien calcul de l'intérieur (HashSet), copié du code d'origine */
    private static Set<Long> referenceInterieur(CarteDonnees carte) {
        Set<Long> exterieur = new HashSet<>();
        Deque<long[]> pile = new ArrayDeque<>();
        for (int x = 0; x < carte.largeur; x++) {
            refAmorcer(carte, pile, exterieur, x, 0);
            refAmorcer(carte, pile, exterieur, x, carte.hauteur - 1);
        }
        for (int z = 0; z < carte.hauteur; z++) {
            refAmorcer(carte, pile, exterieur, 0, z);
            refAmorcer(carte, pile, exterieur, carte.largeur - 1, z);
        }
        while (!pile.isEmpty()) {
            long[] pos = pile.pop();
            int x = (int) pos[0];
            int z = (int) pos[1];
            refAmorcer(carte, pile, exterieur, x + 1, z);
            refAmorcer(carte, pile, exterieur, x - 1, z);
            refAmorcer(carte, pile, exterieur, x, z + 1);
            refAmorcer(carte, pile, exterieur, x, z - 1);
        }
        Set<Long> interieur = new HashSet<>();
        for (int x = 0; x < carte.largeur; x++) {
            for (int z = 0; z < carte.hauteur; z++) {
                long c = CarteDonnees.cle(x, z);
                if (!exterieur.contains(c) && carte.obtenirBloc(x, z).type != TypeElementCarte.LIMITE) {
                    interieur.add(c);
                }
            }
        }
        return interieur;
    }

    private static void refAmorcer(CarteDonnees carte, Deque<long[]> pile, Set<Long> exterieur, int x, int z) {
        if (!carte.estDansAire(x, z)) {
            return;
        }
        long c = CarteDonnees.cle(x, z);
        if (exterieur.contains(c) || carte.obtenirBloc(x, z).type == TypeElementCarte.LIMITE) {
            return;
        }
        exterieur.add(c);
        pile.push(new long[]{x, z});
    }

    /** Ancienne validation de boucle fermée, copiée du code d'origine */
    private static boolean referenceBoucleValide(CarteDonnees carte) {
        Set<Long> cellulesLimite = new HashSet<>();
        for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
            if (entree.getValue().type == TypeElementCarte.LIMITE) {
                cellulesLimite.add(entree.getKey());
            }
        }
        if (cellulesLimite.isEmpty()) {
            return false;
        }
        Set<Long> interieur = referenceInterieur(carte);
        if (interieur.isEmpty()) {
            return false;
        }
        for (long c : cellulesLimite) {
            int x = CarteDonnees.cleX(c);
            int z = CarteDonnees.cleZ(c);
            boolean touche = false;
            for (int dx = -1; dx <= 1 && !touche; dx++) {
                for (int dz = -1; dz <= 1 && !touche; dz++) {
                    if ((dx != 0 || dz != 0) && interieur.contains(CarteDonnees.cle(x + dx, z + dz))) {
                        touche = true;
                    }
                }
            }
            if (!touche) {
                return false;
            }
        }
        Set<Long> visites = new HashSet<>();
        Deque<Long> pile = new ArrayDeque<>();
        long premier = interieur.iterator().next();
        pile.push(premier);
        visites.add(premier);
        while (!pile.isEmpty()) {
            long c = pile.pop();
            int x = CarteDonnees.cleX(c);
            int z = CarteDonnees.cleZ(c);
            long[] voisins = {CarteDonnees.cle(x + 1, z), CarteDonnees.cle(x - 1, z),
                CarteDonnees.cle(x, z + 1), CarteDonnees.cle(x, z - 1)};
            for (long voisin : voisins) {
                if (interieur.contains(voisin) && !visites.contains(voisin)) {
                    visites.add(voisin);
                    pile.push(voisin);
                }
            }
        }
        return visites.size() == interieur.size();
    }

    /** Zone au format historique : cellules développées dans un ensemble */
    private static class RefZone {
        String nom;
        TypeElementCarte type;
        final Set<Long> cellules = new HashSet<>();
    }

    /** Anciennes zones (HashSet + Deque), copiées du code d'origine */
    private static List<RefZone> referenceZones(CarteDonnees carte) {
        List<RefZone> zones = new ArrayList<>();
        Set<Long> visites = new HashSet<>();
        int compteurIles = 0;
        int compteurPierre = 0;
        for (int z = 0; z < carte.hauteur; z++) {
            for (int x = 0; x < carte.largeur; x++) {
                long c = CarteDonnees.cle(x, z);
                if (visites.contains(c)) {
                    continue;
                }
                BlocCarte bloc = carte.blocs.get(c);
                if (bloc == null || (bloc.type != TypeElementCarte.ILE && bloc.type != TypeElementCarte.PIERRE)) {
                    continue;
                }
                TypeElementCarte typeZone = bloc.type;
                RefZone zone = new RefZone();
                zone.type = typeZone;
                Deque<Long> pile = new ArrayDeque<>();
                pile.push(c);
                visites.add(c);
                while (!pile.isEmpty()) {
                    long courant = pile.pop();
                    int cx = CarteDonnees.cleX(courant);
                    int cz = CarteDonnees.cleZ(courant);
                    zone.cellules.add(courant);
                    long[] voisins = {
                        CarteDonnees.cle(cx + 1, cz), CarteDonnees.cle(cx - 1, cz),
                        CarteDonnees.cle(cx, cz + 1), CarteDonnees.cle(cx, cz - 1),
                        CarteDonnees.cle(cx + 1, cz + 1), CarteDonnees.cle(cx + 1, cz - 1),
                        CarteDonnees.cle(cx - 1, cz + 1), CarteDonnees.cle(cx - 1, cz - 1)
                    };
                    for (long voisin : voisins) {
                        if (visites.contains(voisin)) {
                            continue;
                        }
                        int vx = CarteDonnees.cleX(voisin);
                        int vz = CarteDonnees.cleZ(voisin);
                        if (!carte.estDansAire(vx, vz)) {
                            continue;
                        }
                        BlocCarte blocVoisin = carte.blocs.get(voisin);
                        if (blocVoisin != null && blocVoisin.type == typeZone) {
                            visites.add(voisin);
                            pile.push(voisin);
                        }
                    }
                }
                if (typeZone == TypeElementCarte.ILE) {
                    zone.nom = "Île " + refLettre(compteurIles);
                    compteurIles++;
                } else {
                    compteurPierre++;
                    zone.nom = "Zone Pierre " + compteurPierre;
                }
                zones.add(zone);
            }
        }
        return zones;
    }

    private static String refLettre(int index) {
        StringBuilder sb = new StringBuilder();
        index++;
        while (index > 0) {
            index--;
            sb.insert(0, (char) ('A' + (index % 26)));
            index /= 26;
        }
        return sb.toString();
    }

    // Vérification de cohérence du helper de test lui-même
    @Test
    void ensembleDepuisPlagesCoherent() {
        List<int[]> plages = ZoneCarte.plagesDepuisCellules(List.of(
            new int[]{3, 5}, new int[]{4, 5}, new int[]{5, 5}, new int[]{3, 6}));
        assertEquals(Set.of(CarteDonnees.cle(3, 5), CarteDonnees.cle(4, 5),
            CarteDonnees.cle(5, 5), CarteDonnees.cle(3, 6)), ensembleDepuisPlages(plages));
        assertFalse(ZoneCarte.plagesContiennent(plages, 6, 5));
    }

    // ==================== Zones manuelles ====================

    @Test
    void zonesManuellesRoundTrip() {
        CarteDonnees carte = new CarteDonnees();
        carte.nom = "zones_manuelles";
        carte.largeur = 8;
        carte.hauteur = 6;
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 6; z++) {
                TypeElementCarte type = x < 4 ? TypeElementCarte.ILE
                    : (x < 6 ? TypeElementCarte.EAU : TypeElementCarte.PIERRE);
                carte.blocs.put(CarteDonnees.cle(x, z), new BlocCarte(type, 0));
            }
        }

        ZoneCarte nord = new ZoneCarte();
        nord.nom = "Plage nord";
        nord.type = TypeElementCarte.PIERRE; // volontairement faux : redérivé des cellules à la sauvegarde
        carte.zones.add(nord);
        ZoneCarte recif = new ZoneCarte();
        recif.nom = "Récif";
        recif.type = TypeElementCarte.ILE; // idem : zone d'eau seulement -> PIERRE attendu
        carte.zones.add(recif);
        ZoneCarte jamaisPeinte = new ZoneCarte();
        jamaisPeinte.nom = "Jamais peinte";
        jamaisPeinte.type = TypeElementCarte.ILE;
        carte.zones.add(jamaisPeinte);

        // « Plage nord » : deux rangées de terre ; « Récif » : de l'eau seulement
        for (int x = 1; x <= 3; x++) {
            carte.blocs.get(CarteDonnees.cle(x, 1)).zone = 1;
            carte.blocs.get(CarteDonnees.cle(x, 2)).zone = 1;
        }
        carte.blocs.get(CarteDonnees.cle(4, 3)).zone = 2;
        carte.blocs.get(CarteDonnees.cle(5, 3)).zone = 2;
        carte.zonesManuelles = true;

        CarteDonnees relue = CarteDonnees.depuisJson(carte.versJson());

        assertTrue(relue.zonesManuelles);
        // La zone jamais peinte n'est pas sérialisée
        assertEquals(2, relue.zones.size());
        assertEquals("Plage nord", relue.zones.get(0).nom);
        assertEquals("Récif", relue.zones.get(1).nom);
        // Types redérivés des cellules : contient de la terre -> ILE, eau seule -> PIERRE
        assertEquals(TypeElementCarte.ILE, relue.zones.get(0).type);
        assertEquals(TypeElementCarte.PIERRE, relue.zones.get(1).type);
        // Plages exactes
        assertEquals(Set.of(CarteDonnees.cle(1, 1), CarteDonnees.cle(2, 1), CarteDonnees.cle(3, 1),
                CarteDonnees.cle(1, 2), CarteDonnees.cle(2, 2), CarteDonnees.cle(3, 2)),
            ensembleDepuisPlages(relue.zones.get(0).plages));
        // bloc.zone réassignés au décodage (l'éditeur peut reprendre le zonage tel quel)
        assertEquals(1, relue.blocs.get(CarteDonnees.cle(2, 2)).zone);
        assertEquals(2, relue.blocs.get(CarteDonnees.cle(5, 3)).zone);
        assertEquals(0, relue.blocs.get(CarteDonnees.cle(6, 4)).zone);

        // Second aller-retour : état stable
        CarteDonnees relue2 = CarteDonnees.depuisJson(relue.versJson());
        assertTrue(relue2.zonesManuelles);
        assertEquals(2, relue2.zones.size());
        assertEquals(ensembleDepuisPlages(relue.zones.get(0).plages),
            ensembleDepuisPlages(relue2.zones.get(0).plages));
    }

    @Test
    void carteSansZonesManuellesResteAutomatique() {
        CarteDonnees carte = carteVariee(24, 18, 4242L);
        carte.recalculerZones();
        CarteDonnees relue = CarteDonnees.depuisJson(carte.versJson());
        assertFalse(relue.zonesManuelles);
        assertEquals(carte.zones.size(), relue.zones.size());
    }
}

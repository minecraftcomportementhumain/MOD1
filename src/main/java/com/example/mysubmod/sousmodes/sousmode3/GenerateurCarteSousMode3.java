package com.example.mysubmod.sousmodes.sousmode3;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.cartes.BlocCarte;
import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.cartes.TypeElementCarte;
import com.example.mysubmod.objets.BlocsMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Génère dans le monde la carte sélectionnée pour le Sous-mode 3 :
 * colonnes Île / Pierre (visuel varié à partir de la seed fixe de la carte),
 * eau, blocs bonbons non-visibles, cage de blocs barrier (plancher −15 / plafond +15
 * et murs sur les blocs Limite). Suit précisément chaque bloc placé afin de
 * pouvoir tout supprimer à la désactivation, quelle que soit la carte.
 */
public class GenerateurCarteSousMode3 {
    /** Niveau de la mer : un bloc à élévation e a sa surface à Y = NIVEAU_MER + e */
    public static final int NIVEAU_MER = 100;
    /** Base des colonnes (limite inférieure de la cage, élévation −15) */
    public static final int Y_BAS_COLONNE = NIVEAU_MER + CarteDonnees.ELEVATION_MIN; // 85
    /** Haut de l'espace jouable (élévation +15) */
    public static final int Y_HAUT_JOUABLE = NIVEAU_MER + CarteDonnees.ELEVATION_MAX; // 115
    /** Plancher barrier de la cage */
    public static final int Y_PLANCHER_BARRIER = Y_BAS_COLONNE - 1; // 84
    /** Plafond barrier de la cage */
    public static final int Y_PLAFOND_BARRIER = Y_HAUT_JOUABLE + 1; // 116

    /**
     * Drapeaux de {@code setBlock} pour la génération et l'effacement en masse :
     * {@code UPDATE_CLIENTS} (2) synchronise les clients, {@code UPDATE_KNOWN_SHAPE} (16)
     * évite les mises à jour de forme entre voisins. On omet volontairement
     * {@code UPDATE_NEIGHBORS} (1) : la carte est un ensemble de blocs statiques et de
     * sources d'eau explicitement placées (aucun écoulement à déclencher), donc notifier
     * les voisins ne ferait que multiplier le coût, éjecter des blocs attachés du terrain
     * remplacé et planifier des ticks de fluide inutiles. L'écoulement de l'eau reprend
     * normalement quand un joueur mine un bloc adjacent (minage en jeu = drapeau 3).
     */
    public static final int DRAPEAU_PLACEMENT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final String FICHIER_REGION = "donnees_monsubmod/sousmode3_region.json";

    /** Résultat de la génération d'une carte dans le monde */
    public static class ResultatGeneration {
        public final Set<BlockPos> blocsPlaces = new HashSet<>();
        public Set<Long> cellulesInterieur = new HashSet<>();
        public int origineX;
        public int origineZ;
        public BlockPos pointApparitionMonde;
    }

    /**
     * Génère la carte dans le monde, centrée sur l'origine (0, 0).
     * Seuls les blocs à l'intérieur du périmètre Limite existent dans le monde.
     */
    public static ResultatGeneration generer(ServerLevel niveau, CarteDonnees carte) {
        return generer(niveau, carte, true);
    }

    /**
     * Variante avec contrôle des blocs bonbons non-visibles : les Sous-modes 1 et 2
     * (sans minage) ne les génèrent pas — ils sont ignorés sur ces modes.
     */
    public static ResultatGeneration generer(ServerLevel niveau, CarteDonnees carte, boolean genererBonbonsCaches) {
        // Chemin synchrone : exécute la tâche resumable jusqu'au bout en un seul appel.
        // Le résultat est strictement identique à la génération étalée sur plusieurs ticks.
        Tache tache = new Tache(niveau, carte, genererBonbonsCaches);
        while (!tache.avancer(Long.MAX_VALUE)) {
            // boucle jusqu'à l'achèvement
        }
        return tache.resultat();
    }

    // ==================== Génération étalée sur plusieurs ticks ====================

    /**
     * Tâche de génération resumable : place la carte par tranches sur plusieurs ticks
     * serveur au lieu de figer un tick entier. Chaque {@link #avancer(long)} traite des
     * colonnes/arbres jusqu'à épuisement du budget de temps, puis rend la main.
     *
     * <p>La logique de placement (barrier, colonnes, eau, arbres, point d'apparition) est
     * exactement celle du chemin synchrone : l'ordre colonne par colonne est préservé et,
     * grâce au drapeau {@link #DRAPEAU_PLACEMENT} (aucune notification de voisin), l'eau ne
     * s'écoule jamais pendant la génération — chaque bloc d'eau est une source explicitement
     * posée. L'état final ne dépend donc pas du découpage temporel.</p>
     */
    public static class Tache {
        private enum Phase { INIT, COLONNES, ARBRES, FINALISATION, TERMINE }

        private static final int COUT_ARBRE = 4; // poids indicatif par cellule d'île (progression)

        private final ServerLevel niveau;
        private final CarteDonnees carte;
        private final boolean genererBonbonsCaches;
        private final ResultatGeneration resultat = new ResultatGeneration();

        private Phase phase = Phase.INIT;
        private Set<Long> cellulesRemplissageEau;
        private int indexColonne;
        private int totalColonnes;
        private List<Long> clesArbres;
        private int indexArbre;

        private long poidsTotal;
        private long poidsFait;

        public Tache(ServerLevel niveau, CarteDonnees carte, boolean genererBonbonsCaches) {
            this.niveau = niveau;
            this.carte = carte;
            this.genererBonbonsCaches = genererBonbonsCaches;
        }

        /** Résultat en cours de construction ; complet une fois {@link #estTermine()} vrai. */
        public ResultatGeneration resultat() {
            return resultat;
        }

        public boolean estTermine() {
            return phase == Phase.TERMINE;
        }

        /** Fraction de progression [0..1], monotone, atteint 1 à l'achèvement. */
        public float progression() {
            if (phase == Phase.TERMINE) {
                return 1.0f;
            }
            if (poidsTotal <= 0) {
                return phase == Phase.INIT ? 0.0f : 1.0f;
            }
            return Math.min(1.0f, (float) poidsFait / (float) poidsTotal);
        }

        /**
         * Poursuit la génération pendant au plus {@code budgetNanos} nanosecondes.
         * @return true si la génération est terminée.
         */
        public boolean avancer(long budgetNanos) {
            long debut = System.nanoTime();

            if (phase == Phase.INIT) {
                initialiser();
                phase = Phase.COLONNES;
            }

            while (phase == Phase.COLONNES) {
                if (indexColonne >= totalColonnes) {
                    phase = Phase.ARBRES;
                    break;
                }
                int cx = indexColonne / carte.hauteur;
                int cz = indexColonne % carte.hauteur;
                traiterColonne(cx, cz);
                poidsFait += estimerCoutColonne(cx, cz);
                indexColonne++;
                if (System.nanoTime() - debut >= budgetNanos) {
                    return false;
                }
            }

            while (phase == Phase.ARBRES) {
                if (indexArbre >= clesArbres.size()) {
                    phase = Phase.FINALISATION;
                    break;
                }
                genererArbrePourCellule(niveau, resultat, carte, clesArbres.get(indexArbre));
                poidsFait += COUT_ARBRE;
                indexArbre++;
                if ((indexArbre & 15) == 0 && System.nanoTime() - debut >= budgetNanos) {
                    return false;
                }
            }

            if (phase == Phase.FINALISATION) {
                finaliser();
                phase = Phase.TERMINE;
            }
            return phase == Phase.TERMINE;
        }

        private void initialiser() {
            resultat.origineX = -carte.largeur / 2;
            resultat.origineZ = -carte.hauteur / 2;
            resultat.cellulesInterieur = carte.calculerInterieurLimite();
            cellulesRemplissageEau = calculerRemplissageEau(carte, resultat.cellulesInterieur);
            totalColonnes = carte.largeur * carte.hauteur;

            // Cellules d'île éligibles aux arbres (mêmes filtres externes que genererArbres)
            clesArbres = new ArrayList<>();
            for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
                BlocCarte bloc = entree.getValue();
                if (bloc.type == TypeElementCarte.ILE && resultat.cellulesInterieur.contains(entree.getKey())) {
                    clesArbres.add(entree.getKey());
                }
            }

            // Poids total pour la progression (estimation du nombre de blocs à poser)
            poidsTotal = (long) clesArbres.size() * COUT_ARBRE;
            for (int i = 0; i < totalColonnes; i++) {
                poidsTotal += estimerCoutColonne(i / carte.hauteur, i % carte.hauteur);
            }

            MonSubMod.JOURNALISEUR.info("Génération de la carte « {} » ({}x{}, {} cellules intérieures) à l'origine ({}, {})",
                carte.nom, carte.largeur, carte.hauteur, resultat.cellulesInterieur.size(),
                resultat.origineX, resultat.origineZ);

            // Écrire la région dès maintenant : un arrêt en pleine génération est alors couvert
            // par le nettoyage de secours (nettoyerRegionResiduelle).
            sauvegarderFichierRegion(resultat.origineX, resultat.origineZ,
                resultat.origineX + carte.largeur - 1, resultat.origineZ + carte.hauteur - 1);
        }

        private void traiterColonne(int cx, int cz) {
            long cle = CarteDonnees.cle(cx, cz);
            BlocCarte bloc = carte.obtenirBlocOuNull(cx, cz);
            int mondeX = resultat.origineX + cx;
            int mondeZ = resultat.origineZ + cz;

            if (bloc != null && bloc.type == TypeElementCarte.LIMITE) {
                // Blocs Limite : uniquement des blocs barrier (mur complet de la cage)
                for (int y = Y_PLANCHER_BARRIER; y <= Y_PLAFOND_BARRIER; y++) {
                    placer(niveau, resultat, new BlockPos(mondeX, y, mondeZ), Blocks.BARRIER.defaultBlockState());
                }
                return;
            }

            if (!resultat.cellulesInterieur.contains(cle)) {
                return; // Les blocs hors du périmètre Limite n'existent pas dans le monde
            }

            // Plancher et plafond barrier de chaque colonne intérieure
            placer(niveau, resultat, new BlockPos(mondeX, Y_PLANCHER_BARRIER, mondeZ), Blocks.BARRIER.defaultBlockState());
            placer(niveau, resultat, new BlockPos(mondeX, Y_PLAFOND_BARRIER, mondeZ), Blocks.BARRIER.defaultBlockState());

            if (bloc == null || !bloc.type.estElementDeBase()) {
                return; // Ne devrait pas arriver (validation à la sauvegarde)
            }

            switch (bloc.type) {
                case EAU -> genererColonneEau(niveau, resultat, mondeX, mondeZ);
                case ILE, PIERRE -> genererColonneSolide(niveau, resultat, carte, bloc, cx, cz, mondeX, mondeZ,
                    cellulesRemplissageEau.contains(cle), genererBonbonsCaches);
                default -> {
                }
            }
        }

        /** Estimation du nombre de setBlock d'une colonne (pour une progression fluide). */
        private int estimerCoutColonne(int cx, int cz) {
            long cle = CarteDonnees.cle(cx, cz);
            BlocCarte bloc = carte.obtenirBlocOuNull(cx, cz);
            if (bloc != null && bloc.type == TypeElementCarte.LIMITE) {
                return Y_PLAFOND_BARRIER - Y_PLANCHER_BARRIER + 1;
            }
            if (!resultat.cellulesInterieur.contains(cle)) {
                return 0;
            }
            int cout = 2; // plancher + plafond barrier
            if (bloc == null || !bloc.type.estElementDeBase()) {
                return cout;
            }
            if (bloc.type == TypeElementCarte.EAU) {
                return cout + (NIVEAU_MER - Y_BAS_COLONNE + 1);
            }
            // ILE / PIERRE
            int surfaceY = NIVEAU_MER + bloc.elevation;
            cout += (surfaceY - Y_BAS_COLONNE + 1);
            if (genererBonbonsCaches && bloc.qteBonbonNonVisible > 0
                && bloc.delaiApparitionInitialeNonVisible == 0 && (surfaceY - 1) >= Y_BAS_COLONNE) {
                cout += 1;
            }
            if (cellulesRemplissageEau.contains(cle) && bloc.elevation < 0) {
                cout += (NIVEAU_MER - surfaceY);
            }
            return cout;
        }

        private void finaliser() {
            // Point d'apparition des joueurs
            if (carte.aPointApparition()) {
                BlocCarte blocApparition = carte.obtenirBloc(carte.apparitionX, carte.apparitionZ);
                int surfaceY = blocApparition.type.estElementDeBase() && blocApparition.type != TypeElementCarte.EAU
                    ? NIVEAU_MER + blocApparition.elevation
                    : NIVEAU_MER;
                resultat.pointApparitionMonde = new BlockPos(
                    resultat.origineX + carte.apparitionX,
                    Math.max(NIVEAU_MER, surfaceY) + 1,
                    resultat.origineZ + carte.apparitionZ);
            }
            MonSubMod.JOURNALISEUR.info("Carte générée : {} blocs placés", resultat.blocsPlaces.size());
        }
    }

    /**
     * Cellules Île/Pierre submergées (élévation < 0) connectées à un bloc Eau :
     * l'eau remplit automatiquement l'espace au-dessus jusqu'au niveau de la mer.
     */
    private static Set<Long> calculerRemplissageEau(CarteDonnees carte, Set<Long> interieur) {
        Set<Long> remplies = new HashSet<>();
        Deque<Long> pile = new ArrayDeque<>();

        // Amorcer depuis toutes les cellules Eau intérieures
        for (long cle : interieur) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc != null && bloc.type == TypeElementCarte.EAU) {
                pile.push(cle);
            }
        }

        Set<Long> visitees = new HashSet<>(pile);
        while (!pile.isEmpty()) {
            long cle = pile.pop();
            int cx = CarteDonnees.cleX(cle);
            int cz = CarteDonnees.cleZ(cle);
            long[] voisins = {CarteDonnees.cle(cx + 1, cz), CarteDonnees.cle(cx - 1, cz),
                CarteDonnees.cle(cx, cz + 1), CarteDonnees.cle(cx, cz - 1)};
            for (long voisin : voisins) {
                if (visitees.contains(voisin) || !interieur.contains(voisin)) {
                    continue;
                }
                BlocCarte blocVoisin = carte.blocs.get(voisin);
                if (blocVoisin == null) {
                    continue;
                }
                boolean submerge = (blocVoisin.type == TypeElementCarte.ILE || blocVoisin.type == TypeElementCarte.PIERRE)
                    && blocVoisin.elevation < 0;
                if (submerge) {
                    visitees.add(voisin);
                    remplies.add(voisin);
                    pile.push(voisin); // L'eau continue de se propager au-dessus des blocs submergés
                }
            }
        }
        return remplies;
    }

    private static void genererColonneEau(ServerLevel niveau, ResultatGeneration resultat, int mondeX, int mondeZ) {
        for (int y = Y_BAS_COLONNE; y <= NIVEAU_MER; y++) {
            placer(niveau, resultat, new BlockPos(mondeX, y, mondeZ), Blocks.WATER.defaultBlockState());
        }
        // Dégager l'espace au-dessus de l'eau
        degagerAir(niveau, mondeX, mondeZ, NIVEAU_MER + 1);
    }

    private static void genererColonneSolide(ServerLevel niveau, ResultatGeneration resultat, CarteDonnees carte,
                                             BlocCarte bloc, int cx, int cz, int mondeX, int mondeZ,
                                             boolean remplirEauAuDessus, boolean genererBonbonsCaches) {
        boolean estPierre = bloc.type == TypeElementCarte.PIERRE;
        int surfaceY = NIVEAU_MER + bloc.elevation;
        Random aleatoire = aleatoirePourCellule(carte.seed, cx, cz);

        // Colonne solide depuis la limite inférieure de la cage (−15) jusqu'à l'élévation définie
        for (int y = Y_BAS_COLONNE; y <= surfaceY; y++) {
            Block materiau;
            if (y == surfaceY) {
                materiau = estPierre ? choisirSurfacePierre(aleatoire) : choisirSurfaceIle(aleatoire);
            } else {
                materiau = estPierre ? choisirSousSolPierre(aleatoire) : Blocks.DIRT;
            }
            placer(niveau, resultat, new BlockPos(mondeX, y, mondeZ), materiau.defaultBlockState());
        }

        // Bloc bonbon non-visible : 1 bloc sous la surface, faces identiques aux blocs environnants.
        // Avec un délai d'apparition initiale, le bloc de sous-sol normal reste en place : le bloc
        // bonbon le remplacera au moment planifié après le début de la partie.
        if (genererBonbonsCaches && bloc.qteBonbonNonVisible > 0) {
            int yBonbon = surfaceY - 1;
            if (yBonbon >= Y_BAS_COLONNE) {
                if (bloc.delaiApparitionInitialeNonVisible == 0) {
                    Block blocBonbon = estPierre ? BlocsMod.BONBON_CACHE_PIERRE.get() : BlocsMod.BONBON_CACHE_TERRE.get();
                    placer(niveau, resultat, new BlockPos(mondeX, yBonbon, mondeZ), blocBonbon.defaultBlockState());
                }
            } else {
                MonSubMod.JOURNALISEUR.warn("Bonbon non-visible ignoré en ({}, {}) : élévation trop basse", cx, cz);
            }
        }

        // Bloc submergé : l'eau remplit l'espace au-dessus jusqu'au niveau de la mer
        if (remplirEauAuDessus && bloc.elevation < 0) {
            for (int y = surfaceY + 1; y <= NIVEAU_MER; y++) {
                placer(niveau, resultat, new BlockPos(mondeX, y, mondeZ), Blocks.WATER.defaultBlockState());
            }
            degagerAir(niveau, mondeX, mondeZ, NIVEAU_MER + 1);
        } else {
            degagerAir(niveau, mondeX, mondeZ, surfaceY + 1);
        }
    }

    /** Dégage l'air au-dessus d'une colonne jusqu'au haut de l'espace jouable */
    private static void degagerAir(ServerLevel niveau, int mondeX, int mondeZ, int depuisY) {
        for (int y = depuisY; y <= Y_HAUT_JOUABLE; y++) {
            BlockPos pos = new BlockPos(mondeX, y, mondeZ);
            if (!niveau.getBlockState(pos).isAir()) {
                niveau.setBlock(pos, Blocks.AIR.defaultBlockState(), DRAPEAU_PLACEMENT);
            }
        }
    }

    /** Générateur aléatoire déterministe par cellule (rendu identique à chaque chargement) */
    private static Random aleatoirePourCellule(long seed, int cx, int cz) {
        return new Random(seed ^ (cx * 341873128712L + cz * 132897987541L));
    }

    private static Block choisirSurfaceIle(Random aleatoire) {
        float valeur = aleatoire.nextFloat();
        if (valeur < 0.72f) {
            return Blocks.GRASS_BLOCK;
        } else if (valeur < 0.84f) {
            return Blocks.MOSS_BLOCK;
        } else if (valeur < 0.94f) {
            return Blocks.COARSE_DIRT;
        } else {
            return Blocks.PODZOL;
        }
    }

    private static Block choisirSurfacePierre(Random aleatoire) {
        float valeur = aleatoire.nextFloat();
        if (valeur < 0.6f) {
            return Blocks.STONE;
        } else if (valeur < 0.78f) {
            return Blocks.ANDESITE;
        } else if (valeur < 0.92f) {
            return Blocks.COBBLESTONE;
        } else {
            return Blocks.MOSSY_COBBLESTONE;
        }
    }

    private static Block choisirSousSolPierre(Random aleatoire) {
        return aleatoire.nextFloat() < 0.8f ? Blocks.STONE : Blocks.ANDESITE;
    }

    /** Arbres décoratifs clairsemés sur les îles (déterministes via la seed de la carte) */
    private static void genererArbres(ServerLevel niveau, ResultatGeneration resultat, CarteDonnees carte) {
        for (Long cle : carte.blocs.keySet()) {
            genererArbrePourCellule(niveau, resultat, carte, cle);
        }
    }

    /**
     * Génère l'arbre décoratif éventuel d'une cellule (déterministe via la seed).
     * Auto-filtrant : ne fait rien si la cellule n'est pas une île intérieure éligible.
     * Partagé par le chemin synchrone et la génération étalée sur plusieurs ticks.
     */
    private static void genererArbrePourCellule(ServerLevel niveau, ResultatGeneration resultat,
                                                CarteDonnees carte, long cle) {
        BlocCarte bloc = carte.blocs.get(cle);
        if (bloc == null || bloc.type != TypeElementCarte.ILE || !resultat.cellulesInterieur.contains(cle)) {
            return;
        }
        if (bloc.elevation < 0 || bloc.qteBonbonVisible > 0 || bloc.qteBonbonNonVisible > 0) {
            return;
        }

        int cx = CarteDonnees.cleX(cle);
        int cz = CarteDonnees.cleZ(cle);
        Random aleatoire = aleatoirePourCellule(carte.seed * 31 + 7, cx, cz);
        if (aleatoire.nextFloat() >= 0.02f) {
            return;
        }

        int surfaceY = NIVEAU_MER + bloc.elevation;
        int hauteurTronc = 3 + aleatoire.nextInt(2);
        if (surfaceY + hauteurTronc + 2 > Y_HAUT_JOUABLE) {
            return; // L'arbre dépasserait le plafond de la cage
        }

        int mondeX = resultat.origineX + cx;
        int mondeZ = resultat.origineZ + cz;

        // Tronc
        for (int y = 1; y <= hauteurTronc; y++) {
            placer(niveau, resultat, new BlockPos(mondeX, surfaceY + y, mondeZ), Blocks.OAK_LOG.defaultBlockState());
        }
        // Feuillage
        int baseFeuilles = surfaceY + hauteurTronc;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + dy > 3 || (dx == 0 && dz == 0 && dy == 0)) {
                        continue;
                    }
                    if (aleatoire.nextFloat() < 0.2f) {
                        continue;
                    }
                    BlockPos posFeuille = new BlockPos(mondeX + dx, baseFeuilles + dy, mondeZ + dz);
                    if (posFeuille.getY() <= Y_HAUT_JOUABLE && niveau.getBlockState(posFeuille).isAir()) {
                        placer(niveau, resultat, posFeuille,
                            Blocks.OAK_LEAVES.defaultBlockState().setValue(
                                net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                    }
                }
            }
        }
    }

    private static void placer(ServerLevel niveau, ResultatGeneration resultat, BlockPos pos,
                               net.minecraft.world.level.block.state.BlockState etat) {
        niveau.setBlock(pos, etat, DRAPEAU_PLACEMENT);
        resultat.blocsPlaces.add(pos.immutable());
    }

    // ==================== Nettoyage ====================

    /** Supprime tous les blocs suivis (placés lors du chargement ou par les joueurs) */
    public static void effacer(ServerLevel niveau, Set<BlockPos> blocsPlaces) {
        if (niveau == null || blocsPlaces == null) {
            return;
        }
        int effaces = 0;
        for (BlockPos pos : blocsPlaces) {
            if (!niveau.getBlockState(pos).isAir()) {
                niveau.setBlock(pos, Blocks.AIR.defaultBlockState(), DRAPEAU_PLACEMENT);
                effaces++;
            }
        }
        MonSubMod.JOURNALISEUR.info("Carte Sous-mode 3 effacée : {} blocs supprimés ({} positions suivies)",
            effaces, blocsPlaces.size());
        supprimerFichierRegion();
    }

    private static void sauvegarderFichierRegion(int minX, int minZ, int maxX, int maxZ) {
        try {
            Path chemin = Paths.get(FICHIER_REGION);
            Files.createDirectories(chemin.getParent());
            JsonObject objet = new JsonObject();
            objet.addProperty("minX", minX);
            objet.addProperty("minZ", minZ);
            objet.addProperty("maxX", maxX);
            objet.addProperty("maxZ", maxZ);
            Files.writeString(chemin, objet.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Impossible de sauvegarder le fichier de région du Sous-mode 3", e);
        }
    }

    public static void supprimerFichierRegion() {
        try {
            Files.deleteIfExists(Paths.get(FICHIER_REGION));
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Impossible de supprimer le fichier de région du Sous-mode 3", e);
        }
    }

    /**
     * Nettoyage de secours après un arrêt inattendu du serveur : efface toute la
     * bande de la cage (Y 84..116) dans la région enregistrée lors de la dernière génération.
     */
    public static void nettoyerRegionResiduelle(ServerLevel niveau) {
        File fichier = new File(FICHIER_REGION);
        if (!fichier.exists() || niveau == null) {
            return;
        }
        try {
            JsonObject objet = JsonParser.parseString(Files.readString(fichier.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            int minX = objet.get("minX").getAsInt();
            int minZ = objet.get("minZ").getAsInt();
            int maxX = objet.get("maxX").getAsInt();
            int maxZ = objet.get("maxZ").getAsInt();

            MonSubMod.JOURNALISEUR.info("Nettoyage de la région résiduelle du Sous-mode 3 : ({}, {}) à ({}, {})",
                minX, minZ, maxX, maxZ);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = Y_PLANCHER_BARRIER; y <= Y_PLAFOND_BARRIER; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!niveau.getBlockState(pos).isAir()) {
                            niveau.setBlock(pos, Blocks.AIR.defaultBlockState(), DRAPEAU_PLACEMENT);
                        }
                    }
                }
            }

            // Retirer les items au sol restants dans la région
            net.minecraft.world.phys.AABB boite = new net.minecraft.world.phys.AABB(
                minX - 2, Y_PLANCHER_BARRIER - 2, minZ - 2, maxX + 3, Y_PLAFOND_BARRIER + 3, maxZ + 3);
            for (net.minecraft.world.entity.item.ItemEntity item :
                niveau.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, boite)) {
                item.discard();
            }

            // Effacer aussi la plateforme spectateur résiduelle (0, 150, 0)
            for (int x = -15; x <= 15; x++) {
                for (int z = -15; z <= 15; z++) {
                    for (int y = 145; y <= 160; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!niveau.getBlockState(pos).isAir()) {
                            niveau.setBlock(pos, Blocks.AIR.defaultBlockState(), DRAPEAU_PLACEMENT);
                        }
                    }
                }
            }

            supprimerFichierRegion();
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors du nettoyage de la région résiduelle du Sous-mode 3", e);
        }
    }
}

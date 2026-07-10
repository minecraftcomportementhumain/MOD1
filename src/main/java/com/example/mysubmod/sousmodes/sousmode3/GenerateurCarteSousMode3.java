package com.example.mysubmod.sousmodes.sousmode3;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.cartes.BlocCarte;
import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.cartes.TypeElementCarte;
import com.example.mysubmod.objets.BlocsMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
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
        /**
         * Blocs suivis hors génération de masse (bonbons réapparus, blocs posés par les
         * joueurs). La carte elle-même n'y est plus suivie bloc par bloc : l'effacement
         * balaie la bande de la cage ({@link #effacerCarte}) — suivre 100 M de positions
         * dans un HashSet coûterait plusieurs Go sur une carte 2500×2500.
         */
        public final Set<BlockPos> blocsPlaces = new HashSet<>();
        public Set<Long> cellulesInterieur = new HashSet<>();
        /** Chunks écrits par la génération (recalcul d'éclairage groupé à la fin) */
        public final Set<ChunkPos> chunksModifies = new HashSet<>();
        public int origineX;
        public int origineZ;
        public int largeurCarte;
        public int hauteurCarte;
        public long blocsEcrits;
        public BlockPos pointApparitionMonde;
    }

    // États réutilisés par la génération et l'effacement en masse
    private static final net.minecraft.world.level.block.state.BlockState ETAT_BARRIER =
        Blocks.BARRIER.defaultBlockState();
    private static final net.minecraft.world.level.block.state.BlockState ETAT_EAU =
        Blocks.WATER.defaultBlockState();
    private static final net.minecraft.world.level.block.state.BlockState ETAT_AIR =
        Blocks.AIR.defaultBlockState();

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
        private enum Phase { INIT, CHUNKS, ARBRES, FINALISATION, TERMINE }

        private static final int COUT_ARBRE = 4;   // poids indicatif par arbre (progression)
        private static final int POIDS_CHUNK = 64; // poids d'un chunk traité (progression)
        /** Chunks demandés d'avance : leur terrain se génère sur les threads de worldgen */
        private static final int FENETRE_PRECHARGEMENT = 16;
        /**
         * Contre-pression : sans plafond, l'écriture (rapide) distancerait le déchargement
         * des chunks et le moteur d'éclairage — sur une carte 2500×2500 (~24 600 chunks),
         * les chunks chargés et la file d'éclairage s'accumulaient en plusieurs Go et
         * épuisaient la mémoire du serveur.
         */
        private static final int MAX_CHUNKS_PAR_TICK = 6;
        /** Pause de la génération tant que le serveur n'a pas déchargé sous ce seuil */
        private static final int MAX_CHUNKS_CHARGES = 3000;

        private final ServerLevel niveau;
        private final CarteDonnees carte;
        private final boolean genererBonbonsCaches;
        private final ResultatGeneration resultat = new ResultatGeneration();

        private Phase phase = Phase.INIT;
        private java.util.BitSet cellulesRemplissageEau; // index = cz·largeur + cx
        private List<ChunkPos> chunksCibles;
        private int indexChunk;
        private int prochainTicket; // premier chunk sans ticket de préchargement
        private final List<BlockPos> semencesLumiere = new ArrayList<>();
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
         * La carte est écrite chunk par chunk, directement dans les sections (aucune
         * notification par bloc) ; les chunks à venir sont préchargés en asynchrone,
         * de sorte que la génération vanilla du terrain s'exécute sur les threads de
         * worldgen et non sur le thread serveur.
         * @return true si la génération est terminée.
         */
        public boolean avancer(long budgetNanos) {
            long debut = System.nanoTime();
            // Chemin synchrone (budget « infini ») : chargements de chunks bloquants
            boolean synchrone = budgetNanos >= Long.MAX_VALUE / 2;

            if (phase == Phase.INIT) {
                initialiser();
                phase = Phase.CHUNKS;
            }

            int chunksCeTick = 0;
            while (phase == Phase.CHUNKS) {
                if (indexChunk >= chunksCibles.size()) {
                    abandonner(); // plus aucun ticket de préchargement à conserver
                    phase = Phase.ARBRES;
                    break;
                }
                if (!synchrone) {
                    // Contre-pression : laisser le déchargement des chunks et le moteur
                    // d'éclairage suivre le rythme avant de continuer à écrire
                    if (chunksCeTick >= MAX_CHUNKS_PAR_TICK
                        || niveau.getChunkSource().getLoadedChunksCount() > MAX_CHUNKS_CHARGES) {
                        return false;
                    }
                    precharger();
                    ChunkPos pos = chunksCibles.get(indexChunk);
                    if (!niveau.getChunkSource().hasChunk(pos.x, pos.z)) {
                        return false; // le terrain se génère hors du thread serveur : revenir au tick suivant
                    }
                }
                ChunkPos pos = chunksCibles.get(indexChunk);
                genererChunk(niveau.getChunk(pos.x, pos.z));
                libererTicket(indexChunk);
                chunksCeTick++;
                poidsFait += POIDS_CHUNK;
                indexChunk++;
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

        /** Libère les tickets de préchargement restants (fin de phase ou annulation). */
        public void abandonner() {
            if (chunksCibles == null) {
                return;
            }
            for (int i = Math.max(indexChunk, 0); i < prochainTicket; i++) {
                ChunkPos pos = chunksCibles.get(i);
                niveau.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, 0, pos);
            }
            prochainTicket = indexChunk;
        }

        /** Pose des tickets sur les prochains chunks : leur génération démarre en tâche de fond */
        private void precharger() {
            int fin = Math.min(chunksCibles.size(), indexChunk + FENETRE_PRECHARGEMENT);
            while (prochainTicket < fin) {
                ChunkPos pos = chunksCibles.get(prochainTicket);
                niveau.getChunkSource().addRegionTicket(TicketType.FORCED, pos, 0, pos);
                prochainTicket++;
            }
        }

        private void libererTicket(int index) {
            if (index < prochainTicket) {
                ChunkPos pos = chunksCibles.get(index);
                niveau.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, 0, pos);
            }
        }

        private void initialiser() {
            resultat.origineX = -carte.largeur / 2;
            resultat.origineZ = -carte.hauteur / 2;
            resultat.largeurCarte = carte.largeur;
            resultat.hauteurCarte = carte.hauteur;
            resultat.cellulesInterieur = carte.calculerInterieurLimite();
            cellulesRemplissageEau = calculerRemplissageEau(carte, resultat.cellulesInterieur);

            // Chunks couvrant l'aire de la carte, en ordre de balayage
            int chunkXMin = resultat.origineX >> 4;
            int chunkXMax = (resultat.origineX + carte.largeur - 1) >> 4;
            int chunkZMin = resultat.origineZ >> 4;
            int chunkZMax = (resultat.origineZ + carte.hauteur - 1) >> 4;
            chunksCibles = new ArrayList<>();
            for (int chunkZ = chunkZMin; chunkZ <= chunkZMax; chunkZ++) {
                for (int chunkX = chunkXMin; chunkX <= chunkXMax; chunkX++) {
                    chunksCibles.add(new ChunkPos(chunkX, chunkZ));
                }
            }

            // Cellules d'île éligibles aux arbres, triées par chunk : la phase ARBRES
            // recharge chaque chunk au plus une fois au lieu de sauter au hasard
            clesArbres = new ArrayList<>();
            for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
                BlocCarte bloc = entree.getValue();
                if (bloc.type == TypeElementCarte.ILE && resultat.cellulesInterieur.contains(entree.getKey())) {
                    clesArbres.add(entree.getKey());
                }
            }
            clesArbres.sort((a, b) -> {
                int chunkA = (((resultat.origineZ + CarteDonnees.cleZ(a)) >> 4) << 16)
                    ^ (((resultat.origineX + CarteDonnees.cleX(a)) >> 4) & 0xFFFF);
                int chunkB = (((resultat.origineZ + CarteDonnees.cleZ(b)) >> 4) << 16)
                    ^ (((resultat.origineX + CarteDonnees.cleX(b)) >> 4) & 0xFFFF);
                return Integer.compare(chunkA, chunkB);
            });

            poidsTotal = (long) chunksCibles.size() * POIDS_CHUNK + (long) clesArbres.size() * COUT_ARBRE;

            MonSubMod.JOURNALISEUR.info(
                "Génération de la carte « {} » ({}x{}, {} cellules intérieures, {} chunks) à l'origine ({}, {})",
                carte.nom, carte.largeur, carte.hauteur, resultat.cellulesInterieur.size(),
                chunksCibles.size(), resultat.origineX, resultat.origineZ);

            // Écrire la région dès maintenant : un arrêt en pleine génération est alors couvert
            // par le nettoyage de secours (nettoyerRegionResiduelle).
            sauvegarderFichierRegion(resultat.origineX, resultat.origineZ,
                resultat.origineX + carte.largeur - 1, resultat.origineZ + carte.hauteur - 1);
        }

        /** Écrit toutes les colonnes de la carte couvertes par ce chunk, puis le finalise */
        private void genererChunk(LevelChunk chunk) {
            ChunkPos pos = chunk.getPos();
            int mondeXMin = Math.max(pos.getMinBlockX(), resultat.origineX);
            int mondeXMax = Math.min(pos.getMaxBlockX(), resultat.origineX + carte.largeur - 1);
            int mondeZMin = Math.max(pos.getMinBlockZ(), resultat.origineZ);
            int mondeZMax = Math.min(pos.getMaxBlockZ(), resultat.origineZ + carte.hauteur - 1);
            for (int mondeX = mondeXMin; mondeX <= mondeXMax; mondeX++) {
                for (int mondeZ = mondeZMin; mondeZ <= mondeZMax; mondeZ++) {
                    genererColonne(chunk, mondeX - resultat.origineX, mondeZ - resultat.origineZ, mondeX, mondeZ);
                }
            }
            finaliserChunkModifie(niveau, chunk);
            semerLumiere(niveau, semencesLumiere);
            resultat.chunksModifies.add(pos);
        }

        /** Logique de colonne identique à l'ancien chemin bloc par bloc, en écriture par sections */
        private void genererColonne(LevelChunk chunk, int cx, int cz, int mondeX, int mondeZ) {
            long cle = CarteDonnees.cle(cx, cz);
            BlocCarte bloc = carte.obtenirBlocOuNull(cx, cz);

            if (bloc != null && bloc.type == TypeElementCarte.LIMITE) {
                // Blocs Limite : uniquement des blocs barrier (mur complet de la cage)
                for (int y = Y_PLANCHER_BARRIER; y <= Y_PLAFOND_BARRIER; y++) {
                    ecrire(chunk, mondeX, y, mondeZ, ETAT_BARRIER);
                }
                semerColonneBase(mondeX, mondeZ);
                return;
            }

            if (!resultat.cellulesInterieur.contains(cle)) {
                return; // Les blocs hors du périmètre Limite n'existent pas dans le monde
            }

            // Plancher et plafond barrier de chaque colonne intérieure
            ecrire(chunk, mondeX, Y_PLANCHER_BARRIER, mondeZ, ETAT_BARRIER);
            ecrire(chunk, mondeX, Y_PLAFOND_BARRIER, mondeZ, ETAT_BARRIER);

            if (bloc == null || !bloc.type.estElementDeBase()) {
                return; // Ne devrait pas arriver (validation à la sauvegarde)
            }

            switch (bloc.type) {
                case EAU -> {
                    for (int y = Y_BAS_COLONNE; y <= NIVEAU_MER; y++) {
                        ecrire(chunk, mondeX, y, mondeZ, ETAT_EAU);
                    }
                    degagerAir(chunk, mondeX, mondeZ, NIVEAU_MER + 1);
                    semerColonneBase(mondeX, mondeZ);
                    semerLumiereA(mondeX, NIVEAU_MER, mondeZ);
                    semerLumiereA(mondeX, NIVEAU_MER + 1, mondeZ);
                }
                case ILE, PIERRE -> colonneSolide(chunk, bloc, cx, cz, mondeX, mondeZ,
                    cellulesRemplissageEau.get(cz * carte.largeur + cx));
                default -> {
                }
            }
        }

        /**
         * Semences d'éclairage communes à toute colonne écrite : plancher (l'opacité y
         * change par rapport au terrain d'origine) et entrée du ciel dans la cage.
         */
        private void semerColonneBase(int mondeX, int mondeZ) {
            semerLumiereA(mondeX, Y_PLANCHER_BARRIER, mondeZ);
            semerLumiereA(mondeX, Y_HAUT_JOUABLE, mondeZ);
            semerLumiereA(mondeX, Y_PLAFOND_BARRIER, mondeZ);
        }

        private void semerLumiereA(int mondeX, int y, int mondeZ) {
            semencesLumiere.add(new BlockPos(mondeX, y, mondeZ));
        }

        private void colonneSolide(LevelChunk chunk, BlocCarte bloc, int cx, int cz,
                                   int mondeX, int mondeZ, boolean remplirEauAuDessus) {
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
                ecrire(chunk, mondeX, y, mondeZ, materiau.defaultBlockState());
            }

            // Bloc bonbon non-visible : 1 bloc sous la surface, faces identiques aux blocs environnants.
            // Avec un délai d'apparition initiale, le bloc de sous-sol normal reste en place : le bloc
            // bonbon le remplacera au moment planifié après le début de la partie.
            if (genererBonbonsCaches && bloc.qteBonbonNonVisible > 0) {
                int yBonbon = surfaceY - 1;
                if (yBonbon >= Y_BAS_COLONNE) {
                    if (bloc.delaiApparitionInitialeNonVisible == 0) {
                        Block blocBonbon = estPierre ? BlocsMod.BONBON_CACHE_PIERRE.get() : BlocsMod.BONBON_CACHE_TERRE.get();
                        ecrire(chunk, mondeX, yBonbon, mondeZ, blocBonbon.defaultBlockState());
                    }
                } else {
                    MonSubMod.JOURNALISEUR.warn("Bonbon non-visible ignoré en ({}, {}) : élévation trop basse", cx, cz);
                }
            }

            // Bloc submergé : l'eau remplit l'espace au-dessus jusqu'au niveau de la mer
            if (remplirEauAuDessus && bloc.elevation < 0) {
                for (int y = surfaceY + 1; y <= NIVEAU_MER; y++) {
                    ecrire(chunk, mondeX, y, mondeZ, ETAT_EAU);
                }
                degagerAir(chunk, mondeX, mondeZ, NIVEAU_MER + 1);
                semerLumiereA(mondeX, NIVEAU_MER, mondeZ);
                semerLumiereA(mondeX, NIVEAU_MER + 1, mondeZ);
            } else {
                degagerAir(chunk, mondeX, mondeZ, surfaceY + 1);
            }

            // Semences d'éclairage de la colonne : plancher/plafond + surface (transition opaque/air)
            semerColonneBase(mondeX, mondeZ);
            semerLumiereA(mondeX, surfaceY, mondeZ);
            if (surfaceY + 1 <= Y_HAUT_JOUABLE) {
                semerLumiereA(mondeX, surfaceY + 1, mondeZ);
            }
        }

        /** Dégage l'air au-dessus d'une colonne jusqu'au haut de l'espace jouable */
        private void degagerAir(LevelChunk chunk, int mondeX, int mondeZ, int depuisY) {
            for (int y = depuisY; y <= Y_HAUT_JOUABLE; y++) {
                ecrire(chunk, mondeX, y, mondeZ, ETAT_AIR);
            }
        }

        private void ecrire(LevelChunk chunk, int mondeX, int y, int mondeZ, BlockState etat) {
            if (ecrireBlocSection(chunk, mondeX, y, mondeZ, etat, semencesLumiere)) {
                resultat.blocsEcrits++;
            }
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
            MonSubMod.JOURNALISEUR.info("Carte générée : {} blocs écrits dans {} chunks",
                resultat.blocsEcrits, resultat.chunksModifies.size());
        }
    }

    /**
     * Cellules Île/Pierre submergées (élévation < 0) connectées à un bloc Eau :
     * l'eau remplit automatiquement l'espace au-dessus jusqu'au niveau de la mer.
     * BitSet indexé cz·largeur + cx : les ensembles boxés coûtaient ~0,5 Go à 2500×2500.
     */
    private static java.util.BitSet calculerRemplissageEau(CarteDonnees carte, Set<Long> interieur) {
        int largeur = carte.largeur;
        int total = largeur * carte.hauteur;
        java.util.BitSet remplies = new java.util.BitSet(total);
        java.util.BitSet visitees = new java.util.BitSet(total);
        int[] pile = new int[1024];
        int taille = 0;

        // Amorcer depuis toutes les cellules Eau intérieures
        for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
            if (entree.getValue().type != TypeElementCarte.EAU || !interieur.contains(entree.getKey())) {
                continue;
            }
            int index = CarteDonnees.cleZ(entree.getKey()) * largeur + CarteDonnees.cleX(entree.getKey());
            visitees.set(index);
            if (taille == pile.length) {
                pile = java.util.Arrays.copyOf(pile, taille * 2);
            }
            pile[taille++] = index;
        }

        while (taille > 0) {
            int index = pile[--taille];
            int cx = index % largeur;
            int cz = index / largeur;
            int[][] voisins = {{cx + 1, cz}, {cx - 1, cz}, {cx, cz + 1}, {cx, cz - 1}};
            for (int[] voisin : voisins) {
                int vx = voisin[0];
                int vz = voisin[1];
                if (!carte.estDansAire(vx, vz)) {
                    continue;
                }
                int indexVoisin = vz * largeur + vx;
                if (visitees.get(indexVoisin) || !interieur.contains(CarteDonnees.cle(vx, vz))) {
                    continue;
                }
                BlocCarte blocVoisin = carte.blocs.get(CarteDonnees.cle(vx, vz));
                if (blocVoisin == null) {
                    continue;
                }
                boolean submerge = (blocVoisin.type == TypeElementCarte.ILE || blocVoisin.type == TypeElementCarte.PIERRE)
                    && blocVoisin.elevation < 0;
                if (submerge) {
                    visitees.set(indexVoisin);
                    remplies.set(indexVoisin);
                    if (taille == pile.length) {
                        pile = java.util.Arrays.copyOf(pile, taille * 2);
                    }
                    pile[taille++] = indexVoisin; // L'eau continue de se propager au-dessus des blocs submergés
                }
            }
        }
        return remplies;
    }

    // ==================== Écriture en masse par sections de chunks ====================

    /**
     * Écrit un état directement dans la section du chunk : aucune notification de bloc,
     * de voisin ni d'éclairage. L'éclairage est ensuite recalculé par « semences » :
     * {@code checkBlock} aux points de transition d'opacité de chaque colonne (voir les
     * appelants), le moteur d'éclairage threadé de 1.20 propageant le reste en arrière-plan.
     * Un éventuel bloc-entité du bloc remplacé est retiré pour ne pas laisser d'orphelin,
     * et la position d'un ancien bloc émetteur de lumière est ajoutée aux semences.
     * @return true si l'état a changé
     */
    private static boolean ecrireBlocSection(LevelChunk chunk, int mondeX, int y, int mondeZ, BlockState etat,
                                             List<BlockPos> semencesLumiere) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        int localX = mondeX & 15;
        int localY = y & 15;
        int localZ = mondeZ & 15;
        BlockState ancien = section.getBlockState(localX, localY, localZ);
        if (ancien == etat) {
            return false;
        }
        if (ancien.hasBlockEntity()) {
            chunk.removeBlockEntity(new BlockPos(mondeX, y, mondeZ));
        }
        if (ancien.getLightEmission() > 0 && semencesLumiere != null) {
            // Ancien émetteur (magma, lichen...) : sans semence, sa lumière fantôme persisterait
            semencesLumiere.add(new BlockPos(mondeX, y, mondeZ));
        }
        section.setBlockState(localX, localY, localZ, etat, false);
        return true;
    }

    /**
     * Finalise un chunk écrit en masse : heightmaps recalculées, chunk marqué à
     * sauvegarder, sections signalées au moteur d'éclairage, et renvoi du chunk aux
     * joueurs qui l'observent déjà (les autres le recevront normalement à l'approche).
     */
    private static void finaliserChunkModifie(ServerLevel niveau, LevelChunk chunk) {
        Heightmap.primeHeightmaps(chunk, EnumSet.of(
            Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR,
            Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
        chunk.setUnsaved(true);

        int indexMin = chunk.getSectionIndex(Y_PLANCHER_BARRIER);
        int indexMax = chunk.getSectionIndex(Y_PLAFOND_BARRIER);
        for (int index = indexMin; index <= indexMax; index++) {
            LevelChunkSection section = chunk.getSection(index);
            niveau.getLightEngine().updateSectionStatus(
                SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(index)), section.hasOnlyAir());
        }

        for (ServerPlayer joueur : ((ServerChunkCache) niveau.getChunkSource()).chunkMap
            .getPlayers(chunk.getPos(), false)) {
            joueur.connection.send(new ClientboundLevelChunkWithLightPacket(
                chunk, niveau.getLightEngine(), null, null));
        }
    }

    /**
     * Enfile les semences de recalcul d'éclairage d'un chunk : le moteur d'éclairage
     * (threadé en 1.20) repropage la lumière depuis chaque point vérifié, en tâche de fond.
     */
    private static void semerLumiere(ServerLevel niveau, List<BlockPos> semences) {
        for (BlockPos pos : semences) {
            niveau.getLightEngine().checkBlock(pos);
        }
        semences.clear();
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
                               BlockState etat) {
        // Chemin « fin » (arbres, sparses) : setBlock normal, éclairage couvert par le
        // recalcul groupé de la finalisation
        niveau.setBlock(pos, etat, DRAPEAU_PLACEMENT);
        resultat.blocsEcrits++;
    }

    // ==================== Nettoyage ====================

    /** Marge autour du périmètre : le feuillage des arbres peut déborder de 2 cellules */
    private static final int MARGE_EFFACEMENT = 2;

    /**
     * Efface la carte générée : balayage par sections de la bande de la cage (Y 84..116),
     * restreint aux cellules à au plus {@link #MARGE_EFFACEMENT} cellules de l'intérieur
     * du périmètre (couvre les murs Limite, le feuillage débordant, les blocs bonbons
     * réapparus et les blocs posés par les joueurs — tous dans la cage). Le terrain
     * naturel hors du périmètre n'est pas touché.
     */
    public static void effacerCarte(ServerLevel niveau, ResultatGeneration generation) {
        if (niveau == null || generation == null) {
            return;
        }
        long effaces = 0;
        int chunksModifies = 0;
        List<BlockPos> semencesLumiere = new ArrayList<>();
        int mondeXMin = generation.origineX - MARGE_EFFACEMENT;
        int mondeXMax = generation.origineX + generation.largeurCarte - 1 + MARGE_EFFACEMENT;
        int mondeZMin = generation.origineZ - MARGE_EFFACEMENT;
        int mondeZMax = generation.origineZ + generation.hauteurCarte - 1 + MARGE_EFFACEMENT;

        for (int chunkZ = mondeZMin >> 4; chunkZ <= mondeZMax >> 4; chunkZ++) {
            for (int chunkX = mondeXMin >> 4; chunkX <= mondeXMax >> 4; chunkX++) {
                LevelChunk chunk = niveau.getChunk(chunkX, chunkZ);
                boolean modifie = false;
                int xDebut = Math.max(chunkX << 4, mondeXMin);
                int xFin = Math.min((chunkX << 4) + 15, mondeXMax);
                int zDebut = Math.max(chunkZ << 4, mondeZMin);
                int zFin = Math.min((chunkZ << 4) + 15, mondeZMax);
                for (int mondeX = xDebut; mondeX <= xFin; mondeX++) {
                    for (int mondeZ = zDebut; mondeZ <= zFin; mondeZ++) {
                        if (!procheInterieur(generation,
                            mondeX - generation.origineX, mondeZ - generation.origineZ)) {
                            continue;
                        }
                        boolean colonneModifiee = false;
                        for (int y = Y_PLANCHER_BARRIER; y <= Y_PLAFOND_BARRIER; y++) {
                            if (ecrireBlocSection(chunk, mondeX, y, mondeZ, ETAT_AIR, semencesLumiere)) {
                                colonneModifiee = true;
                                effaces++;
                            }
                        }
                        if (colonneModifiee) {
                            modifie = true;
                            // La bande redevient de l'air : la lumière du ciel redescend
                            // depuis le plafond et se repropage depuis le plancher
                            semencesLumiere.add(new BlockPos(mondeX, Y_PLANCHER_BARRIER, mondeZ));
                            semencesLumiere.add(new BlockPos(mondeX, Y_PLAFOND_BARRIER, mondeZ));
                        }
                    }
                }
                if (modifie) {
                    finaliserChunkModifie(niveau, chunk);
                    semerLumiere(niveau, semencesLumiere);
                    chunksModifies++;
                } else {
                    semencesLumiere.clear();
                }
            }
        }
        MonSubMod.JOURNALISEUR.info("Carte Sous-mode 3 effacée : {} blocs supprimés ({} chunks)",
            effaces, chunksModifies);
        supprimerFichierRegion();
    }

    /** La cellule est-elle à au plus MARGE_EFFACEMENT cellules d'une cellule intérieure ? */
    private static boolean procheInterieur(ResultatGeneration generation, int cx, int cz) {
        for (int dx = -MARGE_EFFACEMENT; dx <= MARGE_EFFACEMENT; dx++) {
            for (int dz = -MARGE_EFFACEMENT; dz <= MARGE_EFFACEMENT; dz++) {
                if (generation.cellulesInterieur.contains(CarteDonnees.cle(cx + dx, cz + dz))) {
                    return true;
                }
            }
        }
        return false;
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

            // Balayage par sections de toute la bande de la cage (la carte d'origine est
            // inconnue après un arrêt brutal : toute la région enregistrée est nettoyée)
            List<BlockPos> semencesLumiere = new ArrayList<>();
            for (int chunkZ = (minZ - MARGE_EFFACEMENT) >> 4; chunkZ <= (maxZ + MARGE_EFFACEMENT) >> 4; chunkZ++) {
                for (int chunkX = (minX - MARGE_EFFACEMENT) >> 4; chunkX <= (maxX + MARGE_EFFACEMENT) >> 4; chunkX++) {
                    LevelChunk chunk = niveau.getChunk(chunkX, chunkZ);
                    boolean modifie = false;
                    int xDebut = Math.max(chunkX << 4, minX - MARGE_EFFACEMENT);
                    int xFin = Math.min((chunkX << 4) + 15, maxX + MARGE_EFFACEMENT);
                    int zDebut = Math.max(chunkZ << 4, minZ - MARGE_EFFACEMENT);
                    int zFin = Math.min((chunkZ << 4) + 15, maxZ + MARGE_EFFACEMENT);
                    for (int x = xDebut; x <= xFin; x++) {
                        for (int z = zDebut; z <= zFin; z++) {
                            boolean colonneModifiee = false;
                            for (int y = Y_PLANCHER_BARRIER; y <= Y_PLAFOND_BARRIER; y++) {
                                colonneModifiee |= ecrireBlocSection(chunk, x, y, z, ETAT_AIR, semencesLumiere);
                            }
                            if (colonneModifiee) {
                                modifie = true;
                                semencesLumiere.add(new BlockPos(x, Y_PLANCHER_BARRIER, z));
                                semencesLumiere.add(new BlockPos(x, Y_PLAFOND_BARRIER, z));
                            }
                        }
                    }
                    if (modifie) {
                        finaliserChunkModifie(niveau, chunk);
                        semerLumiere(niveau, semencesLumiere);
                    } else {
                        semencesLumiere.clear();
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

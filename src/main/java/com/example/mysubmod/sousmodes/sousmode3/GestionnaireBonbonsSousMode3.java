package com.example.mysubmod.sousmodes.sousmode3;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.cartes.BlocCarte;
import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.cartes.TypeElementCarte;
import com.example.mysubmod.cartes.ZoneCarte;
import com.example.mysubmod.objets.BlocsMod;
import com.example.mysubmod.objets.ItemsMod;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetZonesSousMode3;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des bonbons du Sous-mode 3 :
 * - bonbons visibles : objets bonbons au sol (ramassables au contact, comme au Sous-mode 1)
 * - bonbons non-visibles : blocs bonbons camouflés, minables
 * - réapparition selon le délai configuré par bloc dans la carte (0 = aucune)
 * - compteurs de bonbons restants par zone (HUD des zones)
 */
public class GestionnaireBonbonsSousMode3 {
    private static GestionnaireBonbonsSousMode3 instance;

    /** Données d'une zone pour le HUD (coordonnées monde) */
    public static class DonneesZone {
        public final String nom;
        /** Point visé par la flèche de navigation : barycentre des bonbons restants de la
         *  zone, recalculé avant chaque envoi ({@link #recalculerCentresNavigation()}) ;
         *  retombe sur le centre géométrique quand la zone est momentanément vide */
        public double centreX;
        public double centreZ;
        public final double centreGeometriqueX;
        public final double centreGeometriqueZ;
        /** Plages de cellules monde triées « z, x0, longueur » — jamais développées
         *  cellule par cellule (des millions sur une grande carte) */
        public final List<int[]> plagesMonde;
        public int bonbonsVisibles;
        public int bonbonsNonVisibles;
        // Détail par type de ressource (parties du Sous-mode 2 sur carte)
        public int bonbonsBleus;
        public int bonbonsRouges;

        public DonneesZone(String nom, double centreX, double centreZ, List<int[]> plagesMonde) {
            this.nom = nom;
            this.centreX = centreX;
            this.centreZ = centreZ;
            this.centreGeometriqueX = centreX;
            this.centreGeometriqueZ = centreZ;
            this.plagesMonde = plagesMonde;
        }
    }

    /** Informations d'une cellule contenant des bonbons visibles */
    private static class InfoCelluleVisible {
        final int mondeX;
        final int mondeZ;
        final int surfaceY; // Y du bloc de surface (l'objet apparaît au-dessus)
        final int delai;
        final String zoneNom; // null si hors zone (ex. bonbon sur l'eau)
        final com.example.mysubmod.cartes.TypeBonbonCarte type; // type défini dans la carte

        InfoCelluleVisible(int mondeX, int mondeZ, int surfaceY, int delai, String zoneNom,
                           com.example.mysubmod.cartes.TypeBonbonCarte type) {
            this.mondeX = mondeX;
            this.mondeZ = mondeZ;
            this.surfaceY = surfaceY;
            this.delai = delai;
            this.zoneNom = zoneNom;
            this.type = type;
        }
    }

    /** Informations d'un bloc bonbon non-visible */
    private static class InfoBonbonCache {
        final int quantite;
        final int delai;
        final boolean estPierre;
        final String zoneNom;
        final com.example.mysubmod.cartes.TypeBonbonCarte type; // type défini dans la carte

        InfoBonbonCache(int quantite, int delai, boolean estPierre, String zoneNom,
                        com.example.mysubmod.cartes.TypeBonbonCarte type) {
            this.quantite = quantite;
            this.delai = delai;
            this.estPierre = estPierre;
            this.zoneNom = zoneNom;
            this.type = type;
        }
    }

    /** Groupe de bonbons visibles dont l'apparition est différée après le début de partie */
    private static class ApparitionDifferee {
        final long celluleCle;
        final int quantite;
        final int delaiSecondes;

        ApparitionDifferee(long celluleCle, int quantite, int delaiSecondes) {
            this.celluleCle = celluleCle;
            this.quantite = quantite;
            this.delaiSecondes = delaiSecondes;
        }
    }

    /** Bloc bonbon non-visible dont l'apparition initiale est différée après le début de partie */
    private static class ApparitionDiffereeCache {
        final BlockPos pos;
        final InfoBonbonCache info;
        final int delaiSecondes;

        ApparitionDiffereeCache(BlockPos pos, InfoBonbonCache info, int delaiSecondes) {
            this.pos = pos;
            this.info = info;
            this.delaiSecondes = delaiSecondes;
        }
    }

    private final Map<Long, InfoCelluleVisible> cellulesVisibles = new ConcurrentHashMap<>();
    private final Map<ItemEntity, Long> entitesBonbonsVisibles = new ConcurrentHashMap<>(); // entité -> clé de cellule
    private final Map<BlockPos, InfoBonbonCache> blocsBonbonsCaches = new ConcurrentHashMap<>();
    private final List<DonneesZone> zones = new ArrayList<>();
    private final Map<String, DonneesZone> zonesParNom = new HashMap<>();
    private final List<ApparitionDifferee> apparitionsDifferees = new ArrayList<>();
    private final List<ApparitionDiffereeCache> apparitionsDiffereesCachees = new ArrayList<>();
    private Timer minuterieReapparition;
    private MinecraftServer serveurJeu;
    /**
     * Bonbons typés Bleu/Rouge actifs (option « Spécialisation » du menu N). Faux par défaut :
     * les bonbons visibles apparaissent en type standard pendant la phase d'attente, puis sont
     * retypés au lancement si l'option est cochée ({@link #activerBonbonsTypes}).
     */
    private boolean bonbonsTypesActifs = false;

    private GestionnaireBonbonsSousMode3() {
    }

    public static GestionnaireBonbonsSousMode3 obtenirInstance() {
        if (instance == null) {
            instance = new GestionnaireBonbonsSousMode3();
        }
        return instance;
    }

    private static long cleCellule(int mondeX, int mondeZ) {
        return (((long) mondeX) << 32) | (mondeZ & 0xFFFFFFFFL);
    }

    /**
     * Initialise l'état des bonbons et des zones depuis la carte générée.
     * Doit être appelé après la génération du monde.
     */
    public void initialiser(MinecraftServer serveur, CarteDonnees carte,
                            GenerateurCarteSousMode3.ResultatGeneration generation) {
        arreter();
        this.serveurJeu = serveur;
        this.minuterieReapparition = new Timer("SousMode3-ReapparitionBonbons", true);

        // Zones nommées de la carte -> plages triées (coordonnées carte pour retrouver la
        // zone d'un bonbon, coordonnées monde pour le HUD). Ni cellules développées ni map
        // cellule→zone : sur une grande carte, ces structures boxées coûtaient des
        // centaines de Mo au serveur.
        List<List<int[]>> plagesCarteParZone = new ArrayList<>();
        for (ZoneCarte zone : carte.zones) {
            double[] centre = zone.obtenirCentre();
            List<int[]> plagesCarte = zone.plages; // déjà en plages triées, aucune conversion
            plagesCarteParZone.add(plagesCarte);
            List<int[]> plagesMonde = new ArrayList<>(plagesCarte.size());
            for (int[] plage : plagesCarte) {
                plagesMonde.add(new int[]{
                    generation.origineZ + plage[0], generation.origineX + plage[1], plage[2]});
            }
            DonneesZone donnees = new DonneesZone(zone.nom,
                generation.origineX + centre[0] + 0.5,
                generation.origineZ + centre[1] + 0.5,
                plagesMonde);
            zones.add(donnees);
            zonesParNom.put(zone.nom, donnees);
        }

        // Inventaire des bonbons de la carte (cellules intérieures uniquement)
        for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
            long cle = entree.getKey();
            BlocCarte bloc = entree.getValue();
            if (!generation.cellulesInterieur.contains(cle) || !bloc.type.estElementDeBase()) {
                continue;
            }

            int cx = CarteDonnees.cleX(cle);
            int cz = CarteDonnees.cleZ(cle);
            int mondeX = generation.origineX + cx;
            int mondeZ = generation.origineZ + cz;
            String zoneNom = null;
            for (int i = 0; i < plagesCarteParZone.size(); i++) {
                if (PaquetZonesSousMode3.plagesContiennent(plagesCarteParZone.get(i), cx, cz)) {
                    zoneNom = carte.zones.get(i).nom;
                    break;
                }
            }

            if (bloc.qteBonbonVisible > 0) {
                // Sur l'Eau : au niveau de la mer ; sur Île/Pierre : à la hauteur de l'élévation du bloc
                int surfaceY = bloc.type == TypeElementCarte.EAU
                    ? GenerateurCarteSousMode3.NIVEAU_MER
                    : GenerateurCarteSousMode3.NIVEAU_MER + bloc.elevation;
                long cleMonde = cleCellule(mondeX, mondeZ);
                cellulesVisibles.put(cleMonde,
                    new InfoCelluleVisible(mondeX, mondeZ, surfaceY, bloc.delaiBonbonVisible, zoneNom,
                        bloc.typeBonbonVisible));
                if (bloc.delaiApparitionInitiale > 0) {
                    // Apparition différée : comptée dans la zone au moment de l'apparition
                    apparitionsDifferees.add(new ApparitionDifferee(cleMonde, bloc.qteBonbonVisible,
                        bloc.delaiApparitionInitiale));
                } else if (zoneNom != null && zonesParNom.containsKey(zoneNom)) {
                    zonesParNom.get(zoneNom).bonbonsVisibles += bloc.qteBonbonVisible;
                }
            }

            if (bloc.qteBonbonNonVisible > 0
                && (bloc.type == TypeElementCarte.ILE || bloc.type == TypeElementCarte.PIERRE)) {
                int yBonbon = GenerateurCarteSousMode3.NIVEAU_MER + bloc.elevation - 1;
                if (yBonbon >= GenerateurCarteSousMode3.Y_BAS_COLONNE) {
                    BlockPos pos = new BlockPos(mondeX, yBonbon, mondeZ);
                    InfoBonbonCache info = new InfoBonbonCache(bloc.qteBonbonNonVisible,
                        bloc.delaiBonbonNonVisible, bloc.type == TypeElementCarte.PIERRE, zoneNom,
                        bloc.typeBonbonNonVisible);
                    if (bloc.delaiApparitionInitialeNonVisible > 0) {
                        // Apparition différée : le bloc bonbon est placé (et compté) au moment planifié
                        apparitionsDiffereesCachees.add(new ApparitionDiffereeCache(pos, info,
                            bloc.delaiApparitionInitialeNonVisible));
                    } else {
                        blocsBonbonsCaches.put(pos, info);
                        if (zoneNom != null && zonesParNom.containsKey(zoneNom)) {
                            zonesParNom.get(zoneNom).bonbonsNonVisibles += bloc.qteBonbonNonVisible;
                        }
                    }
                }
            }
        }

        // Les zones sans aucun bonbon (présent ou différé) n'apparaissent pas dans le HUD
        Map<String, Integer> differesParZone = new HashMap<>();
        for (ApparitionDifferee apparition : apparitionsDifferees) {
            InfoCelluleVisible info = cellulesVisibles.get(apparition.celluleCle);
            if (info != null && info.zoneNom != null) {
                differesParZone.merge(info.zoneNom, apparition.quantite, Integer::sum);
            }
        }
        for (ApparitionDiffereeCache apparition : apparitionsDiffereesCachees) {
            if (apparition.info.zoneNom != null) {
                differesParZone.merge(apparition.info.zoneNom, apparition.info.quantite, Integer::sum);
            }
        }
        zones.removeIf(zone -> {
            boolean vide = zone.bonbonsVisibles == 0 && zone.bonbonsNonVisibles == 0
                && differesParZone.getOrDefault(zone.nom, 0) == 0;
            if (vide) {
                zonesParNom.remove(zone.nom);
            }
            return vide;
        });

        MonSubMod.JOURNALISEUR.info("Bonbons Sous-mode 3 initialisés : {} cellules visibles, {} blocs non-visibles, {} zones",
            cellulesVisibles.size(), blocsBonbonsCaches.size(), zones.size());
    }

    /** Fait apparaître tous les objets bonbons visibles de la carte dans le monde */
    public void genererBonbonsVisibles(ServerLevel niveau, CarteDonnees carte,
                                       GenerateurCarteSousMode3.ResultatGeneration generation) {
        Random aleatoire = new Random();
        int totalApparus = 0;

        for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
            long cle = entree.getKey();
            BlocCarte bloc = entree.getValue();
            if (!generation.cellulesInterieur.contains(cle) || bloc.qteBonbonVisible <= 0
                || !bloc.type.estElementDeBase()) {
                continue;
            }
            if (bloc.delaiApparitionInitiale > 0) {
                continue; // Apparition différée : planifiée au début de la partie
            }
            int mondeX = generation.origineX + CarteDonnees.cleX(cle);
            int mondeZ = generation.origineZ + CarteDonnees.cleZ(cle);
            InfoCelluleVisible info = cellulesVisibles.get(cleCellule(mondeX, mondeZ));
            if (info == null) {
                continue;
            }
            for (int i = 0; i < bloc.qteBonbonVisible; i++) {
                fairApparaitreBonbonVisible(niveau, info, aleatoire);
                totalApparus++;
            }
        }

        MonSubMod.JOURNALISEUR.info("{} bonbons visibles apparus dans le monde ({} groupes différés)",
            totalApparus, apparitionsDifferees.size());
    }

    private void fairApparaitreBonbonVisible(ServerLevel niveau, InfoCelluleVisible info, Random aleatoire) {
        double decalageX = 0.3 + aleatoire.nextDouble() * 0.4;
        double decalageZ = 0.3 + aleatoire.nextDouble() * 0.4;

        ItemEntity entite = new ItemEntity(niveau,
            info.mondeX + decalageX,
            info.surfaceY + 1.1,
            info.mondeZ + decalageZ,
            creerPileBonbonVisible(info));
        entite.setPickUpDelay(40);
        entite.setUnlimitedLifetime();
        entite.setDeltaMovement(0, 0, 0);
        niveau.addFreshEntity(entite);

        entitesBonbonsVisibles.put(entite, cleCellule(info.mondeX, info.mondeZ));
    }

    /** Item d'un bonbon visible : typé Bleu/Rouge quand la spécialisation est active, sinon standard */
    private ItemStack creerPileBonbonVisible(InfoCelluleVisible info) {
        if (bonbonsTypesActifs) {
            if (info.type == com.example.mysubmod.cartes.TypeBonbonCarte.ROUGE) {
                return new ItemStack(ItemsMod.BONBON_ROUGE.get());
            }
            if (info.type == com.example.mysubmod.cartes.TypeBonbonCarte.BLEU) {
                return new ItemStack(ItemsMod.BONBON_BLEU.get());
            }
        }
        return new ItemStack(ItemsMod.BONBON.get());
    }

    /** Vrai pour tout objet bonbon du mod (standard, bleu ou rouge) */
    public static boolean estObjetBonbon(ItemStack pile) {
        return pile.is(ItemsMod.BONBON.get())
            || pile.is(ItemsMod.BONBON_BLEU.get())
            || pile.is(ItemsMod.BONBON_ROUGE.get());
    }

    /** Ajuste les compteurs visibles d'une zone (total + détail Bleu/Rouge si les types sont actifs) */
    private void ajusterCompteursZoneVisible(DonneesZone zone,
                                             com.example.mysubmod.cartes.TypeBonbonCarte type, int delta) {
        zone.bonbonsVisibles = Math.max(0, zone.bonbonsVisibles + delta);
        if (!bonbonsTypesActifs) {
            return;
        }
        if (type == com.example.mysubmod.cartes.TypeBonbonCarte.BLEU) {
            zone.bonbonsBleus = Math.max(0, zone.bonbonsBleus + delta);
        } else if (type == com.example.mysubmod.cartes.TypeBonbonCarte.ROUGE) {
            zone.bonbonsRouges = Math.max(0, zone.bonbonsRouges + delta);
        }
    }

    /**
     * Active les bonbons typés Bleu/Rouge (option « Spécialisation » cochée au lancement) :
     * remplace les objets bonbons standards déjà apparus pendant la phase d'attente par leur
     * version typée, et recompose les compteurs Bleu/Rouge des zones. Les apparitions futures
     * (réapparitions, apparitions initiales différées) sortiront directement typées.
     */
    public void activerBonbonsTypes(ServerLevel niveau) {
        bonbonsTypesActifs = true;

        Map<ItemEntity, Long> anciennes = new HashMap<>(entitesBonbonsVisibles);
        entitesBonbonsVisibles.clear();
        Random aleatoire = new Random();
        int retypes = 0;
        for (Map.Entry<ItemEntity, Long> entree : anciennes.entrySet()) {
            ItemEntity entite = entree.getKey();
            InfoCelluleVisible info = cellulesVisibles.get(entree.getValue());
            int quantite = entite.isAlive() ? Math.max(1, entite.getItem().getCount()) : 0;
            if (entite.isAlive()) {
                entite.discard();
            }
            if (info == null) {
                continue;
            }
            for (int i = 0; i < quantite; i++) {
                fairApparaitreBonbonVisible(niveau, info, aleatoire);
                retypes++;
            }
        }

        // Recomposer le détail Bleu/Rouge des zones depuis les entités présentes
        for (DonneesZone zone : zones) {
            zone.bonbonsBleus = 0;
            zone.bonbonsRouges = 0;
        }
        for (Map.Entry<ItemEntity, Long> entree : entitesBonbonsVisibles.entrySet()) {
            InfoCelluleVisible info = cellulesVisibles.get(entree.getValue());
            if (info == null || info.zoneNom == null) {
                continue;
            }
            DonneesZone zone = zonesParNom.get(info.zoneNom);
            if (zone == null) {
                continue;
            }
            int quantite = Math.max(1, entree.getKey().getItem().getCount());
            if (info.type == com.example.mysubmod.cartes.TypeBonbonCarte.BLEU) {
                zone.bonbonsBleus += quantite;
            } else if (info.type == com.example.mysubmod.cartes.TypeBonbonCarte.ROUGE) {
                zone.bonbonsRouges += quantite;
            }
        }

        MonSubMod.JOURNALISEUR.info("Bonbons typés Bleu/Rouge activés (Sous-mode 3) : {} objets retypés", retypes);
    }

    /**
     * Appelé quand un joueur ramasse un objet bonbon. Retourne la position de la
     * cellule d'origine si c'était un bonbon visible de la carte (sinon null).
     * Gère le compteur de zone et planifie la réapparition selon le délai du bloc.
     * Tient compte de la taille de la pile (les entités bonbons proches peuvent fusionner).
     */
    public BlockPos gererRamassageBonbon(ItemEntity entite) {
        Long cle = entitesBonbonsVisibles.remove(entite);
        if (cle == null) {
            return null; // Bonbon issu d'un bloc miné ou autre : pas un bonbon visible de la carte
        }
        InfoCelluleVisible info = cellulesVisibles.get(cle);
        if (info == null) {
            return null;
        }

        int quantite = Math.max(1, entite.getItem().getCount());

        // Mise à jour du compteur de zone en temps réel
        if (info.zoneNom != null && zonesParNom.containsKey(info.zoneNom)) {
            ajusterCompteursZoneVisible(zonesParNom.get(info.zoneNom), info.type, -quantite);
            envoyerCompteursZones();
        }

        // Réapparition selon le délai configuré sur ce bloc (0 = pas de réapparition)
        if (info.delai > 0 && minuterieReapparition != null) {
            for (int i = 0; i < quantite; i++) {
                planifierReapparitionVisible(info);
            }
        }

        return new BlockPos(info.mondeX, info.surfaceY + 1, info.mondeZ);
    }

    /**
     * Purge les entrées dont l'entité a disparu sans ramassage (fusion d'entités
     * bonbons voisines) : le bonbon existe toujours dans la pile fusionnée,
     * les compteurs de zone restent donc inchangés.
     */
    public void purgerEntitesFusionnees() {
        entitesBonbonsVisibles.keySet().removeIf(entite -> !entite.isAlive());
    }

    private void planifierReapparitionVisible(InfoCelluleVisible info) {
        try {
            minuterieReapparition.schedule(new TimerTask() {
                @Override
                public void run() {
                    MinecraftServer serveur = serveurJeu;
                    if (serveur == null || serveur.isStopped()) {
                        return;
                    }
                    serveur.execute(() -> {
                        if (!GestionnaireSousMode3.getInstance().estPartieActive()
                            || !cellulesVisibles.containsKey(cleCellule(info.mondeX, info.mondeZ))) {
                            return;
                        }
                        ServerLevel niveau = serveur.getLevel(ServerLevel.OVERWORLD);
                        if (niveau == null) {
                            return;
                        }
                        fairApparaitreBonbonVisible(niveau, info, new Random());
                        if (info.zoneNom != null && zonesParNom.containsKey(info.zoneNom)) {
                            ajusterCompteursZoneVisible(zonesParNom.get(info.zoneNom), info.type, 1);
                            envoyerCompteursZones();
                        }
                    });
                }
            }, info.delai * 1000L);
        } catch (IllegalStateException e) {
            // Minuterie annulée pendant la désactivation : ignorer
        }
    }

    /**
     * Planifie les apparitions initiales différées (délai configuré par bloc,
     * compté à partir du début de la partie). Appelé au lancement de la partie.
     */
    public void planifierApparitionsInitiales() {
        if (minuterieReapparition == null
            || (apparitionsDifferees.isEmpty() && apparitionsDiffereesCachees.isEmpty())) {
            return;
        }

        // Blocs bonbons non-visibles différés : placés via la mécanique de réapparition
        // (le bloc bonbon remplace tout bloc présent à cet endroit au moment planifié)
        if (!apparitionsDiffereesCachees.isEmpty()) {
            MonSubMod.JOURNALISEUR.info("Planification de {} blocs bonbons non-visibles à apparition différée",
                apparitionsDiffereesCachees.size());
            for (ApparitionDiffereeCache apparition : new ArrayList<>(apparitionsDiffereesCachees)) {
                planifierReapparitionBonbonCache(apparition.pos, apparition.info,
                    apparition.delaiSecondes * 1000L);
            }
            apparitionsDiffereesCachees.clear();
        }

        if (apparitionsDifferees.isEmpty()) {
            return;
        }
        MonSubMod.JOURNALISEUR.info("Planification de {} groupes de bonbons visibles à apparition différée",
            apparitionsDifferees.size());

        for (ApparitionDifferee apparition : new ArrayList<>(apparitionsDifferees)) {
            try {
                minuterieReapparition.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        MinecraftServer serveur = serveurJeu;
                        if (serveur == null || serveur.isStopped()) {
                            return;
                        }
                        serveur.execute(() -> {
                            if (!GestionnaireSousMode3.getInstance().estPartieActive()) {
                                return;
                            }
                            InfoCelluleVisible info = cellulesVisibles.get(apparition.celluleCle);
                            ServerLevel niveau = serveur.getLevel(ServerLevel.OVERWORLD);
                            if (info == null || niveau == null) {
                                return;
                            }
                            Random aleatoire = new Random();
                            for (int i = 0; i < apparition.quantite; i++) {
                                fairApparaitreBonbonVisible(niveau, info, aleatoire);
                            }
                            if (info.zoneNom != null && zonesParNom.containsKey(info.zoneNom)) {
                                ajusterCompteursZoneVisible(zonesParNom.get(info.zoneNom), info.type,
                                    apparition.quantite);
                                envoyerCompteursZones();
                            }
                        });
                    }
                }, apparition.delaiSecondes * 1000L);
            } catch (IllegalStateException e) {
                // Minuterie annulée pendant la désactivation : ignorer
            }
        }
        apparitionsDifferees.clear();
    }

    /** Vérifie si un bloc du monde est un bloc bonbon non-visible géré */
    public boolean estBlocBonbonCache(BlockPos pos) {
        return blocsBonbonsCaches.containsKey(pos);
    }

    public static boolean estBlocBonbonCache(BlockState etat) {
        return etat.is(BlocsMod.BONBON_CACHE_TERRE.get()) || etat.is(BlocsMod.BONBON_CACHE_PIERRE.get());
    }

    /**
     * Gère le minage d'un bloc bonbon non-visible : le bloc est détruit et laisse
     * tomber autant d'objets bonbons que la quantité indiquée sur ce bloc.
     * Retourne la quantité de bonbons déposés (0 si le bloc n'était pas géré).
     */
    public int gererMinageBonbonCache(ServerLevel niveau, BlockPos pos) {
        InfoBonbonCache info = blocsBonbonsCaches.remove(pos);
        if (info == null) {
            return 0;
        }

        niveau.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);

        // Laisser tomber les objets bonbons (typés Bleu/Rouge quand la spécialisation est
        // active et que le bloc a un type), collectibles selon les règles normales
        Random aleatoire = new Random();
        for (int i = 0; i < info.quantite; i++) {
            ItemStack pile;
            if (bonbonsTypesActifs && info.type == com.example.mysubmod.cartes.TypeBonbonCarte.ROUGE) {
                pile = new ItemStack(ItemsMod.BONBON_ROUGE.get());
            } else if (bonbonsTypesActifs && info.type == com.example.mysubmod.cartes.TypeBonbonCarte.BLEU) {
                pile = new ItemStack(ItemsMod.BONBON_BLEU.get());
            } else {
                pile = new ItemStack(ItemsMod.BONBON.get());
            }
            ItemEntity entite = new ItemEntity(niveau,
                pos.getX() + 0.3 + aleatoire.nextDouble() * 0.4,
                pos.getY() + 0.3,
                pos.getZ() + 0.3 + aleatoire.nextDouble() * 0.4,
                pile);
            entite.setPickUpDelay(10);
            entite.setUnlimitedLifetime();
            niveau.addFreshEntity(entite);
        }

        // Mise à jour du compteur de zone en temps réel
        if (info.zoneNom != null && zonesParNom.containsKey(info.zoneNom)) {
            DonneesZone zone = zonesParNom.get(info.zoneNom);
            zone.bonbonsNonVisibles = Math.max(0, zone.bonbonsNonVisibles - info.quantite);
            envoyerCompteursZones();
        }

        // Réapparition après destruction selon le délai configuré sur ce bloc
        if (info.delai > 0 && minuterieReapparition != null) {
            planifierReapparitionBonbonCache(pos.immutable(), info, info.delai * 1000L);
        }

        return info.quantite;
    }

    private void planifierReapparitionBonbonCache(BlockPos pos, InfoBonbonCache info, long delaiMs) {
        try {
            minuterieReapparition.schedule(new TimerTask() {
                @Override
                public void run() {
                    MinecraftServer serveur = serveurJeu;
                    if (serveur == null || serveur.isStopped()) {
                        return;
                    }
                    serveur.execute(() -> {
                        if (!GestionnaireSousMode3.getInstance().estPartieActive()) {
                            return;
                        }
                        ServerLevel niveau = serveur.getLevel(ServerLevel.OVERWORLD);
                        if (niveau == null) {
                            return;
                        }
                        // Le bloc bonbon remplace tout bloc présent à cet endroit
                        // (y compris un bloc placé par un joueur)
                        Block bloc = info.estPierre ? BlocsMod.BONBON_CACHE_PIERRE.get() : BlocsMod.BONBON_CACHE_TERRE.get();
                        niveau.setBlock(pos, bloc.defaultBlockState(), 3);
                        GestionnaireSousMode3.getInstance().suivreBlocPlace(pos);
                        blocsBonbonsCaches.put(pos, info);
                        if (info.zoneNom != null && zonesParNom.containsKey(info.zoneNom)) {
                            zonesParNom.get(info.zoneNom).bonbonsNonVisibles += info.quantite;
                            envoyerCompteursZones();
                        }
                    });
                }
            }, delaiMs);
        } catch (IllegalStateException e) {
            // Minuterie annulée pendant la désactivation : ignorer
        }
    }

    // ==================== Zones (HUD) ====================

    public List<DonneesZone> obtenirZones() {
        return zones;
    }

    /**
     * Recalcule le point visé par la flèche de chaque zone : le barycentre, pondéré par
     * les quantités, des bonbons restants — objets visibles au sol et blocs non-visibles
     * encore en place. Une zone momentanément vide retombe sur son centre géométrique
     * (les réapparitions la regarniront). Appelé avant chaque envoi de zones au client ;
     * coût O(bonbons restants), négligeable au rythme des ramassages.
     */
    private void recalculerCentresNavigation() {
        Map<String, double[]> sommes = new HashMap<>(); // nom -> {sommeX, sommeZ, poids}
        for (Long cle : entitesBonbonsVisibles.values()) {
            InfoCelluleVisible info = cellulesVisibles.get(cle);
            if (info == null || info.zoneNom == null) {
                continue;
            }
            double[] somme = sommes.computeIfAbsent(info.zoneNom, n -> new double[3]);
            somme[0] += info.mondeX + 0.5;
            somme[1] += info.mondeZ + 0.5;
            somme[2] += 1;
        }
        for (Map.Entry<BlockPos, InfoBonbonCache> entree : blocsBonbonsCaches.entrySet()) {
            InfoBonbonCache info = entree.getValue();
            if (info.zoneNom == null) {
                continue;
            }
            double[] somme = sommes.computeIfAbsent(info.zoneNom, n -> new double[3]);
            somme[0] += (entree.getKey().getX() + 0.5) * info.quantite;
            somme[1] += (entree.getKey().getZ() + 0.5) * info.quantite;
            somme[2] += info.quantite;
        }
        for (DonneesZone zone : zones) {
            double[] somme = sommes.get(zone.nom);
            if (somme != null && somme[2] > 0) {
                zone.centreX = somme[0] / somme[2];
                zone.centreZ = somme[1] / somme[2];
            } else {
                zone.centreX = zone.centreGeometriqueX;
                zone.centreZ = zone.centreGeometriqueZ;
            }
        }
    }

    /** Envoie la liste complète des zones (avec cellules) à un joueur, en parties si nécessaire */
    public void envoyerZonesCompletesAJoueur(ServerPlayer joueur, boolean reinitialiser) {
        recalculerCentresNavigation();
        for (PaquetZonesSousMode3 partie : PaquetZonesSousMode3.completEnParties(zones, reinitialiser)) {
            GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur), partie);
        }
    }

    /** Envoie la liste complète des zones à tous les joueurs authentifiés */
    public void envoyerZonesCompletesATous(boolean reinitialiser) {
        MinecraftServer serveur = serveurJeu;
        if (serveur == null) {
            return;
        }
        recalculerCentresNavigation();
        // Construire les parties une seule fois (l'empaquetage en plages d'une grande
        // carte a un coût réel) : les mêmes paquets sont envoyés à chaque joueur
        List<PaquetZonesSousMode3> parties = PaquetZonesSousMode3.completEnParties(zones, reinitialiser);
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            for (PaquetZonesSousMode3 partie : parties) {
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur), partie);
            }
        }
    }

    /** Envoie uniquement les compteurs mis à jour à tous les joueurs (temps réel) */
    public void envoyerCompteursZones() {
        MinecraftServer serveur = serveurJeu;
        if (serveur == null) {
            return;
        }
        recalculerCentresNavigation();
        PaquetZonesSousMode3 paquet = PaquetZonesSousMode3.compteurs(zones);
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
        }
    }

    // ==================== Nettoyage ====================

    /** Retire tous les objets bonbons du monde (comme au Sous-mode 1) */
    public void retirerTousBonbonsDuMonde(MinecraftServer serveur) {
        ServerLevel overworld = serveur.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            return;
        }
        int nombreRetire = 0;
        for (net.minecraft.world.entity.Entity entite : overworld.getAllEntities()) {
            if (entite instanceof ItemEntity entiteObjet && estObjetBonbon(entiteObjet.getItem())) {
                entiteObjet.discard();
                nombreRetire++;
            }
        }
        entitesBonbonsVisibles.clear();
        MonSubMod.JOURNALISEUR.info("Retiré {} objets bonbons du monde (Sous-mode 3)", nombreRetire);
    }

    /** Positions des blocs bonbons non-visibles encore présents (pour le nettoyage) */
    public Set<BlockPos> obtenirPositionsBonbonsCaches() {
        return blocsBonbonsCaches.keySet();
    }

    public void arreter() {
        if (minuterieReapparition != null) {
            minuterieReapparition.cancel();
            minuterieReapparition = null;
        }
        bonbonsTypesActifs = false;
        cellulesVisibles.clear();
        entitesBonbonsVisibles.clear();
        blocsBonbonsCaches.clear();
        zones.clear();
        zonesParNom.clear();
        apparitionsDifferees.clear();
        apparitionsDiffereesCachees.clear();
        serveurJeu = null;
    }
}

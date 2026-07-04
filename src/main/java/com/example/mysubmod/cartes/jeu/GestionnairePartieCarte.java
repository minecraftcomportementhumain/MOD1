package com.example.mysubmod.cartes.jeu;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.cartes.BlocCarte;
import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.cartes.GestionnaireCartes;
import com.example.mysubmod.cartes.TypeBonbonCarte;
import com.example.mysubmod.cartes.TypeElementCarte;
import com.example.mysubmod.cartes.ZoneCarte;
import com.example.mysubmod.objets.ItemsMod;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode3.GenerateurCarteSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.GestionnaireBonbonsSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetZonesSousMode3;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Partie « sur carte » des Sous-modes 1 et 2 : la carte sélectionnée fournit la
 * géographie (générée sans blocs bonbons non-visibles — ignorés sans minage),
 * les zones nommées, le point d'apparition et les bonbons visibles (apparition
 * initiale différée éventuelle, réapparition par délai, type Bleu/Rouge au Sous-mode 2).
 * Réutilise le générateur, le paquet de zones et le HUD des zones du Sous-mode 3.
 */
public class GestionnairePartieCarte {

    /** Cellule de la carte contenant des bonbons visibles */
    private static class InfoCellule {
        final int mondeX;
        final int mondeZ;
        final int surfaceY;
        final int delaiReapparition;
        final String zoneNom;
        final TypeBonbonCarte typeBonbon;

        InfoCellule(int mondeX, int mondeZ, int surfaceY, int delaiReapparition, String zoneNom,
                    TypeBonbonCarte typeBonbon) {
            this.mondeX = mondeX;
            this.mondeZ = mondeZ;
            this.surfaceY = surfaceY;
            this.delaiReapparition = delaiReapparition;
            this.zoneNom = zoneNom;
            this.typeBonbon = typeBonbon;
        }
    }

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

    private final int numeroSousMode; // 1 ou 2
    private final Supplier<Boolean> partieActive;

    private boolean actif = false;
    private CarteDonnees carte;
    private GenerateurCarteSousMode3.ResultatGeneration generation;

    private final Map<Long, InfoCellule> cellulesVisibles = new ConcurrentHashMap<>();
    private final Map<ItemEntity, Long> entitesBonbons = new ConcurrentHashMap<>();
    private final List<ApparitionDifferee> apparitionsDifferees = new ArrayList<>();
    private final List<GestionnaireBonbonsSousMode3.DonneesZone> zones = new ArrayList<>();
    private final Map<String, GestionnaireBonbonsSousMode3.DonneesZone> zonesParNom = new HashMap<>();
    private final List<String> zonesIle = new ArrayList<>(); // Zones sélectionnables au départ
    private final Map<String, BlockPos> spawnsZones = new HashMap<>();
    private final Map<UUID, String> selectionsZone = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> dernieresPositionsValides = new ConcurrentHashMap<>();

    private Timer minuterieBonbons;
    private MinecraftServer serveurJeu;

    public GestionnairePartieCarte(int numeroSousMode, Supplier<Boolean> partieActive) {
        this.numeroSousMode = numeroSousMode;
        this.partieActive = partieActive;
    }

    private static long cleCellule(int mondeX, int mondeZ) {
        return (((long) mondeX) << 32) | (mondeZ & 0xFFFFFFFFL);
    }

    public boolean estActif() {
        return actif;
    }

    public CarteDonnees obtenirCarte() {
        return carte;
    }

    public String obtenirNomCarte() {
        return carte != null ? carte.nom : "";
    }

    // ==================== Activation / génération ====================

    /**
     * Charge la carte sélectionnée et la génère dans le monde (sans blocs bonbons
     * non-visibles). Prépare les zones, les points d'apparition et l'inventaire
     * des bonbons visibles. Les objets bonbons apparaissent au début de la partie.
     */
    public boolean activer(MinecraftServer serveur) {
        String nomCarte = GestionnaireCartes.getInstance().obtenirCarteSelectionnee();
        carte = nomCarte != null ? GestionnaireCartes.getInstance().chargerCarte(nomCarte) : null;
        if (carte == null) {
            MonSubMod.JOURNALISEUR.error("Sous-mode {} : carte sélectionnée introuvable", numeroSousMode);
            return false;
        }

        ServerLevel monde = serveur.getLevel(ServerLevel.OVERWORLD);
        if (monde == null) {
            return false;
        }

        this.serveurJeu = serveur;
        this.minuterieBonbons = new Timer("SousMode" + numeroSousMode + "-BonbonsCarte");

        // Les bonbons non-visibles sont ignorés aux Sous-modes 1 et 2 (pas de minage)
        int bonbonsIgnores = carte.compterBonbonsNonVisiblesInterieur();
        generation = GenerateurCarteSousMode3.generer(monde, carte, false);

        initialiserZonesEtBonbons();

        // Retirer les résidus de la destruction du terrain principal dans l'aire
        retirerEntitesResiduelles(monde);

        // HUD des zones pour tous les joueurs (flèche réinitialisée)
        envoyerZonesCompletesATous();

        if (bonbonsIgnores > 0) {
            for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§eLa carte contient " + bonbonsIgnores
                        + " bonbon(s) non-visible(s) — ignorés en sous-mode " + numeroSousMode + "."));
            }
        }

        actif = true;
        MonSubMod.JOURNALISEUR.info("Sous-mode {} : carte « {} » générée ({} zones, {} zones Île sélectionnables)",
            numeroSousMode, carte.nom, zones.size(), zonesIle.size());
        return true;
    }

    private void initialiserZonesEtBonbons() {
        zones.clear();
        zonesParNom.clear();
        zonesIle.clear();
        spawnsZones.clear();
        cellulesVisibles.clear();
        apparitionsDifferees.clear();
        selectionsZone.clear();
        dernieresPositionsValides.clear();

        // Zones nommées de la carte -> coordonnées monde + point d'apparition par zone
        Map<Long, String> zoneParCellule = new HashMap<>();
        for (ZoneCarte zone : carte.zones) {
            double[] centre = zone.obtenirCentre();
            List<int[]> cellulesMonde = new ArrayList<>();
            for (int[] cellule : zone.cellules) {
                cellulesMonde.add(new int[]{generation.origineX + cellule[0], generation.origineZ + cellule[1]});
                zoneParCellule.put(CarteDonnees.cle(cellule[0], cellule[1]), zone.nom);
            }
            GestionnaireBonbonsSousMode3.DonneesZone donnees = new GestionnaireBonbonsSousMode3.DonneesZone(
                zone.nom,
                generation.origineX + centre[0] + 0.5,
                generation.origineZ + centre[1] + 0.5,
                cellulesMonde);
            zones.add(donnees);
            zonesParNom.put(zone.nom, donnees);

            // Zones de type Île : sélectionnables comme zone de départ
            if (zone.type == TypeElementCarte.ILE) {
                zonesIle.add(zone.nom);
                spawnsZones.put(zone.nom, calculerSpawnZone(zone, centre));
            }
        }

        // Inventaire des bonbons visibles (cellules intérieures uniquement)
        for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
            long cle = entree.getKey();
            BlocCarte bloc = entree.getValue();
            if (!generation.cellulesInterieur.contains(cle) || !bloc.type.estElementDeBase()
                || bloc.qteBonbonVisible <= 0) {
                continue;
            }

            int cx = CarteDonnees.cleX(cle);
            int cz = CarteDonnees.cleZ(cle);
            int mondeX = generation.origineX + cx;
            int mondeZ = generation.origineZ + cz;
            String zoneNom = zoneParCellule.get(cle);

            int surfaceY = bloc.type == TypeElementCarte.EAU
                ? GenerateurCarteSousMode3.NIVEAU_MER
                : GenerateurCarteSousMode3.NIVEAU_MER + bloc.elevation;

            long cleMonde = cleCellule(mondeX, mondeZ);
            cellulesVisibles.put(cleMonde, new InfoCellule(mondeX, mondeZ, surfaceY,
                bloc.delaiBonbonVisible, zoneNom, bloc.typeBonbonVisible));

            if (bloc.delaiApparitionInitiale > 0) {
                apparitionsDifferees.add(new ApparitionDifferee(cleMonde, bloc.qteBonbonVisible,
                    bloc.delaiApparitionInitiale));
            } else if (zoneNom != null && zonesParNom.containsKey(zoneNom)) {
                ajusterCompteursZone(zonesParNom.get(zoneNom), bloc.typeBonbonVisible, bloc.qteBonbonVisible);
            }
        }

        // Les zones sans aucun bonbon (présent ou différé) n'apparaissent pas dans le HUD
        Map<String, Integer> differesParZone = new HashMap<>();
        for (ApparitionDifferee apparition : apparitionsDifferees) {
            InfoCellule info = cellulesVisibles.get(apparition.celluleCle);
            if (info != null && info.zoneNom != null) {
                differesParZone.merge(info.zoneNom, apparition.quantite, Integer::sum);
            }
        }
        zones.removeIf(zone -> {
            boolean vide = zone.bonbonsVisibles == 0 && differesParZone.getOrDefault(zone.nom, 0) == 0;
            if (vide) {
                zonesParNom.remove(zone.nom);
            }
            return vide;
        });
    }

    /** Ajuste les compteurs d'une zone (total + détail Bleu/Rouge pour le Sous-mode 2) */
    private void ajusterCompteursZone(GestionnaireBonbonsSousMode3.DonneesZone zone,
                                      TypeBonbonCarte type, int delta) {
        zone.bonbonsVisibles = Math.max(0, zone.bonbonsVisibles + delta);
        if (type == TypeBonbonCarte.BLEU) {
            zone.bonbonsBleus = Math.max(0, zone.bonbonsBleus + delta);
        } else if (type == TypeBonbonCarte.ROUGE) {
            zone.bonbonsRouges = Math.max(0, zone.bonbonsRouges + delta);
        }
    }

    /** Point d'apparition d'une zone : surface de la cellule la plus proche du centre géométrique */
    private BlockPos calculerSpawnZone(ZoneCarte zone, double[] centre) {
        int[] meilleure = null;
        double meilleureDistance = Double.MAX_VALUE;
        for (int[] cellule : zone.cellules) {
            double dx = cellule[0] - centre[0];
            double dz = cellule[1] - centre[1];
            double distance = dx * dx + dz * dz;
            if (distance < meilleureDistance) {
                meilleureDistance = distance;
                meilleure = cellule;
            }
        }
        if (meilleure == null) {
            return obtenirPointApparition();
        }
        BlocCarte bloc = carte.obtenirBloc(meilleure[0], meilleure[1]);
        int surfaceY = GenerateurCarteSousMode3.NIVEAU_MER + Math.max(0, bloc.elevation);
        return new BlockPos(generation.origineX + meilleure[0], surfaceY + 1, generation.origineZ + meilleure[1]);
    }

    private void retirerEntitesResiduelles(ServerLevel monde) {
        if (generation == null || carte == null) {
            return;
        }
        net.minecraft.world.phys.AABB boite = new net.minecraft.world.phys.AABB(
            generation.origineX - 2, GenerateurCarteSousMode3.Y_PLANCHER_BARRIER - 2, generation.origineZ - 2,
            generation.origineX + carte.largeur + 2, GenerateurCarteSousMode3.Y_PLAFOND_BARRIER + 3,
            generation.origineZ + carte.hauteur + 2);
        int retires = 0;
        for (ItemEntity item : monde.getEntitiesOfClass(ItemEntity.class, boite)) {
            item.discard();
            retires++;
        }
        for (net.minecraft.world.entity.Mob creature :
            monde.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, boite)) {
            creature.discard();
            retires++;
        }
        if (retires > 0) {
            MonSubMod.JOURNALISEUR.info("{} entités/items résiduels retirés de l'aire de la carte", retires);
        }
    }

    // ==================== Zones de départ ====================

    public boolean aZonesIle() {
        return !zonesIle.isEmpty();
    }

    public List<String> obtenirZonesIle() {
        return new ArrayList<>(zonesIle);
    }

    public BlockPos obtenirPointApparition() {
        if (generation != null && generation.pointApparitionMonde != null) {
            return generation.pointApparitionMonde;
        }
        return new BlockPos(0, GenerateurCarteSousMode3.NIVEAU_MER + 2, 0);
    }

    /** Position de départ d'une zone (repli : point d'apparition de la carte) */
    public BlockPos obtenirSpawnZone(String nomZone) {
        BlockPos spawn = nomZone != null ? spawnsZones.get(nomZone) : null;
        return spawn != null ? spawn : obtenirPointApparition();
    }

    public boolean selectionnerZone(UUID idJoueur, String nomZone) {
        if (!zonesIle.contains(nomZone)) {
            return false;
        }
        selectionsZone.put(idJoueur, nomZone);
        return true;
    }

    public String obtenirZoneChoisie(UUID idJoueur) {
        return selectionsZone.get(idJoueur);
    }

    public String assignerZoneAleatoire(UUID idJoueur) {
        if (zonesIle.isEmpty()) {
            return null;
        }
        String zone = zonesIle.get(new Random().nextInt(zonesIle.size()));
        selectionsZone.put(idJoueur, zone);
        return zone;
    }

    public void migrerSelectionZone(UUID ancienUUID, UUID nouveauUUID) {
        String zone = selectionsZone.remove(ancienUUID);
        if (zone != null) {
            selectionsZone.put(nouveauUUID, zone);
        }
        double[] position = dernieresPositionsValides.remove(ancienUUID);
        if (position != null) {
            dernieresPositionsValides.put(nouveauUUID, position);
        }
    }

    // ==================== Bonbons ====================

    private ItemStack creerPileBonbon(TypeBonbonCarte type) {
        if (numeroSousMode == 2) {
            // Le refus strict au lancement garantit des types Bleu / Rouge
            return type == TypeBonbonCarte.ROUGE
                ? new ItemStack(ItemsMod.BONBON_ROUGE.get())
                : new ItemStack(ItemsMod.BONBON_BLEU.get());
        }
        return new ItemStack(ItemsMod.BONBON.get());
    }

    /** Fait apparaître les bonbons du début de partie et planifie les apparitions différées */
    public void demarrerBonbons(MinecraftServer serveur) {
        ServerLevel monde = serveur.getLevel(ServerLevel.OVERWORLD);
        if (monde == null || carte == null || generation == null) {
            return;
        }

        Random aleatoire = new Random();
        int totalApparus = 0;

        for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
            long cle = entree.getKey();
            BlocCarte bloc = entree.getValue();
            if (!generation.cellulesInterieur.contains(cle) || bloc.qteBonbonVisible <= 0
                || !bloc.type.estElementDeBase() || bloc.delaiApparitionInitiale > 0) {
                continue;
            }
            InfoCellule info = cellulesVisibles.get(cleCellule(
                generation.origineX + CarteDonnees.cleX(cle), generation.origineZ + CarteDonnees.cleZ(cle)));
            if (info == null) {
                continue;
            }
            for (int i = 0; i < bloc.qteBonbonVisible; i++) {
                fairApparaitreBonbon(monde, info, aleatoire);
                totalApparus++;
            }
        }

        // Apparitions initiales différées (comptées à partir du début de la partie)
        for (ApparitionDifferee apparition : new ArrayList<>(apparitionsDifferees)) {
            try {
                minuterieBonbons.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        MinecraftServer serveurActuel = serveurJeu;
                        if (serveurActuel == null || serveurActuel.isStopped()) {
                            return;
                        }
                        serveurActuel.execute(() -> {
                            if (!Boolean.TRUE.equals(partieActive.get()) || !actif) {
                                return;
                            }
                            InfoCellule info = cellulesVisibles.get(apparition.celluleCle);
                            ServerLevel niveau = serveurActuel.getLevel(ServerLevel.OVERWORLD);
                            if (info == null || niveau == null) {
                                return;
                            }
                            Random alea = new Random();
                            for (int i = 0; i < apparition.quantite; i++) {
                                fairApparaitreBonbon(niveau, info, alea);
                            }
                            if (info.zoneNom != null && zonesParNom.containsKey(info.zoneNom)) {
                                ajusterCompteursZone(zonesParNom.get(info.zoneNom), info.typeBonbon, apparition.quantite);
                                envoyerCompteursZones();
                            }
                        });
                    }
                }, apparition.delaiSecondes * 1000L);
            } catch (IllegalStateException e) {
                // Minuterie annulée : ignorer
            }
        }

        MonSubMod.JOURNALISEUR.info("Sous-mode {} : {} bonbons visibles apparus ({} groupes différés)",
            numeroSousMode, totalApparus, apparitionsDifferees.size());
        apparitionsDifferees.clear();
        envoyerZonesCompletesATous();
    }

    private void fairApparaitreBonbon(ServerLevel niveau, InfoCellule info, Random aleatoire) {
        double decalageX = 0.3 + aleatoire.nextDouble() * 0.4;
        double decalageZ = 0.3 + aleatoire.nextDouble() * 0.4;

        ItemEntity entite = new ItemEntity(niveau,
            info.mondeX + decalageX,
            info.surfaceY + 1.1,
            info.mondeZ + decalageZ,
            creerPileBonbon(info.typeBonbon));
        entite.setPickUpDelay(40);
        entite.setUnlimitedLifetime();
        entite.setDeltaMovement(0, 0, 0);
        niveau.addFreshEntity(entite);

        entitesBonbons.put(entite, cleCellule(info.mondeX, info.mondeZ));
    }

    /**
     * Appelé quand un joueur ramasse un objet bonbon : met à jour le compteur de
     * zone et planifie la réapparition selon le délai du bloc.
     * Retourne la position d'origine si c'était un bonbon de la carte (sinon null).
     */
    public BlockPos gererRamassageBonbon(ItemEntity entite) {
        Long cle = entitesBonbons.remove(entite);
        if (cle == null) {
            return null;
        }
        InfoCellule info = cellulesVisibles.get(cle);
        if (info == null) {
            return null;
        }

        int quantite = Math.max(1, entite.getItem().getCount());

        if (info.zoneNom != null && zonesParNom.containsKey(info.zoneNom)) {
            ajusterCompteursZone(zonesParNom.get(info.zoneNom), info.typeBonbon, -quantite);
            envoyerCompteursZones();
        }

        if (info.delaiReapparition > 0 && minuterieBonbons != null) {
            for (int i = 0; i < quantite; i++) {
                planifierReapparition(info);
            }
        }

        return new BlockPos(info.mondeX, info.surfaceY + 1, info.mondeZ);
    }

    private void planifierReapparition(InfoCellule info) {
        try {
            minuterieBonbons.schedule(new TimerTask() {
                @Override
                public void run() {
                    MinecraftServer serveur = serveurJeu;
                    if (serveur == null || serveur.isStopped()) {
                        return;
                    }
                    serveur.execute(() -> {
                        if (!Boolean.TRUE.equals(partieActive.get()) || !actif) {
                            return;
                        }
                        ServerLevel niveau = serveur.getLevel(ServerLevel.OVERWORLD);
                        if (niveau == null) {
                            return;
                        }
                        fairApparaitreBonbon(niveau, info, new Random());
                        if (info.zoneNom != null && zonesParNom.containsKey(info.zoneNom)) {
                            ajusterCompteursZone(zonesParNom.get(info.zoneNom), info.typeBonbon, 1);
                            envoyerCompteursZones();
                        }
                    });
                }
            }, info.delaiReapparition * 1000L);
        } catch (IllegalStateException e) {
            // Minuterie annulée : ignorer
        }
    }

    /** Purge les entrées dont l'entité a disparu sans ramassage (fusion d'entités voisines) */
    public void purgerEntitesFusionnees() {
        entitesBonbons.keySet().removeIf(entite -> !entite.isAlive());
    }

    // ==================== HUD des zones ====================

    public void envoyerZonesCompletesAJoueur(ServerPlayer joueur) {
        GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
            PaquetZonesSousMode3.complet(zones, true));
    }

    public void envoyerZonesCompletesATous() {
        MinecraftServer serveur = serveurJeu;
        if (serveur == null) {
            return;
        }
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            envoyerZonesCompletesAJoueur(joueur);
        }
    }

    public void envoyerCompteursZones() {
        MinecraftServer serveur = serveurJeu;
        if (serveur == null) {
            return;
        }
        PaquetZonesSousMode3 paquet = PaquetZonesSousMode3.compteurs(zones);
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
        }
    }

    // ==================== Cage ====================

    public boolean estDansCage(double x, double y, double z) {
        if (generation == null || carte == null) {
            return false;
        }
        if (y <= GenerateurCarteSousMode3.Y_PLANCHER_BARRIER || y >= GenerateurCarteSousMode3.Y_PLAFOND_BARRIER) {
            return false;
        }
        int cx = (int) Math.floor(x) - generation.origineX;
        int cz = (int) Math.floor(z) - generation.origineZ;
        return generation.cellulesInterieur.contains(CarteDonnees.cle(cx, cz));
    }

    public boolean estDansAireCarte(BlockPos pos) {
        if (generation == null || carte == null) {
            return false;
        }
        int cx = pos.getX() - generation.origineX;
        int cz = pos.getZ() - generation.origineZ;
        return cx >= -2 && cx < carte.largeur + 2 && cz >= -2 && cz < carte.hauteur + 2
            && pos.getY() >= GenerateurCarteSousMode3.Y_PLANCHER_BARRIER - 2
            && pos.getY() <= GenerateurCarteSousMode3.Y_PLAFOND_BARRIER + 2;
    }

    /**
     * Vérifie la position des joueurs vivants (à chaque tick). Un joueur hors
     * cage est retéléporté à sa dernière position valide exacte.
     */
    public void verifierCageJoueurs(MinecraftServer serveur, Predicate<UUID> estVivant) {
        if (!actif || generation == null) {
            return;
        }
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            if (!estVivant.test(joueur.getUUID())) {
                continue;
            }
            double x = joueur.getX();
            double y = joueur.getY();
            double z = joueur.getZ();
            if (estDansCage(x, y, z)) {
                dernieresPositionsValides.put(joueur.getUUID(),
                    new double[]{x, y, z, joueur.getYRot(), joueur.getXRot()});
            } else {
                double[] derniere = dernieresPositionsValides.get(joueur.getUUID());
                if (derniere != null) {
                    joueur.connection.teleport(derniere[0], derniere[1], derniere[2],
                        (float) derniere[3], (float) derniere[4]);
                } else {
                    BlockPos apparition = obtenirPointApparition();
                    joueur.connection.teleport(apparition.getX() + 0.5, apparition.getY(),
                        apparition.getZ() + 0.5, 0, 0);
                }
            }
        }
    }

    public void enregistrerPositionValide(UUID idJoueur, BlockPos pos) {
        dernieresPositionsValides.put(idJoueur,
            new double[]{pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0});
    }

    // ==================== Nettoyage ====================

    /** Supprime le monde généré, les objets bonbons suivis et réinitialise l'état */
    public void nettoyer(MinecraftServer serveur) {
        if (minuterieBonbons != null) {
            minuterieBonbons.cancel();
            minuterieBonbons = null;
        }

        for (ItemEntity entite : entitesBonbons.keySet()) {
            if (entite.isAlive()) {
                entite.discard();
            }
        }
        entitesBonbons.clear();

        try {
            ServerLevel monde = serveur != null ? serveur.getLevel(ServerLevel.OVERWORLD) : null;
            if (monde != null && generation != null) {
                retirerEntitesResiduelles(monde);
                GenerateurCarteSousMode3.effacer(monde, generation.blocsPlaces);
            }
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors de l'effacement de la carte du Sous-mode {}", numeroSousMode, e);
        }

        // Effacer le HUD des zones chez tous les clients
        try {
            GestionnaireReseau.INSTANCE.send(PacketDistributor.ALL.noArg(), PaquetZonesSousMode3.vide());
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors de l'effacement du HUD des zones", e);
        }

        actif = false;
        carte = null;
        generation = null;
        cellulesVisibles.clear();
        apparitionsDifferees.clear();
        zones.clear();
        zonesParNom.clear();
        zonesIle.clear();
        spawnsZones.clear();
        selectionsZone.clear();
        dernieresPositionsValides.clear();
        serveurJeu = null;
    }
}

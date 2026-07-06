package com.example.mysubmod.sousmodes.sousmode3;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.cartes.GestionnaireCartes;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode3.donnees.EnregistreurDonneesSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.minuterie.MinuterieJeu;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetFinPartieSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetMinuterieJeuSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetZonesSousMode3;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sous-mode 3 : reprend la base du Sous-mode 1, mais la géographie de la partie
 * (îles, eau, pierre), les bonbons (visibles et non-visibles), la cage et le point
 * d'apparition des joueurs proviennent de la carte sélectionnée (système de cartes).
 * Les joueurs peuvent miner et replacer les blocs de la carte.
 */
public class GestionnaireSousMode3 {
    private static GestionnaireSousMode3 instance;

    private static final int TEMPS_PARTIE_MINUTES = 15;
    private static final int DECOMPTE_LANCEMENT_SECONDES = 10;

    // Informations de déconnexion (accès typé pour le gestionnaire de santé)
    public static class InfoDeconnexion {
        public UUID ancienUUID;
        public long tempsDeconnexion;
        public double x;
        public double y;
        public double z;
        public float santeADeconnexion;
        public List<ItemStack> inventaireSauvegarde;
        public boolean estMort;

        InfoDeconnexion(UUID ancienUUID, long tempsDeconnexion, double x, double y, double z,
                        float sante, List<ItemStack> inventaire) {
            this.ancienUUID = ancienUUID;
            this.tempsDeconnexion = tempsDeconnexion;
            this.x = x;
            this.y = y;
            this.z = z;
            this.santeADeconnexion = sante;
            this.inventaireSauvegarde = inventaire;
            this.estMort = false;
        }
    }

    private final Map<UUID, List<ItemStack>> inventairesStockes = new ConcurrentHashMap<>();
    private final Set<UUID> joueursVivants = ConcurrentHashMap.newKeySet();
    private final Set<UUID> joueursSpectateurs = ConcurrentHashMap.newKeySet();
    private final Set<UUID> joueursEnAttente = ConcurrentHashMap.newKeySet(); // Participants avant le lancement
    private final Map<String, InfoDeconnexion> joueursDeconnectes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> compteurBonbonsJoueur = new ConcurrentHashMap<>();
    private final Map<UUID, Long> heureMortJoueur = new ConcurrentHashMap<>();
    private final Map<UUID, String> nomsJoueurs = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> dernieresPositionsValides = new ConcurrentHashMap<>(); // x, y, z, yaw, pitch
    private final List<EvenementJournalisationEnAttente> evenementsJournalisationEnAttente = new ArrayList<>();

    private final Object verrouReconnexion = new Object();

    private boolean partieActive = false;
    private boolean phaseAttente = false; // Joueurs sur la plateforme spectateur, en attente du lancement
    private boolean decompteEnCours = false;
    private boolean finPartieEnCours = false;
    private boolean carteGeneree = false;
    private long heureDebutPartie;

    private CarteDonnees carte;
    private GenerateurCarteSousMode3.ResultatGeneration generation;
    private MinuterieJeu minuteurPartie;
    private Timer minuteurDecompte;
    private EnregistreurDonneesSousMode3 enregistreurDonnees;

    private final BlockPos plateformeSpectateur = new BlockPos(0, 150, 0);

    private GestionnaireSousMode3() {
    }

    public static GestionnaireSousMode3 getInstance() {
        if (instance == null) {
            instance = new GestionnaireSousMode3();
        }
        return instance;
    }

    // ==================== Activation ====================

    public void activate(MinecraftServer serveur) {
        activate(serveur, null);
    }

    public void activate(MinecraftServer serveur, ServerPlayer initiateur) {
        MonSubMod.JOURNALISEUR.info("Activation du SousMode3");

        UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur).forEach(joueur ->
            joueur.sendSystemMessage(Component.literal("§e§lChargement du sous-mode 3...")));

        // Charger la carte sélectionnée (obligatoire pour le Sous-mode 3)
        String nomCarte = GestionnaireCartes.getInstance().obtenirCarteSelectionnee();
        carte = nomCarte != null ? GestionnaireCartes.getInstance().chargerCarte(nomCarte) : null;
        if (carte == null) {
            MonSubMod.JOURNALISEUR.error("Sous-mode 3 activé sans carte sélectionnée valide !");
            UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur).forEach(joueur ->
                joueur.sendSystemMessage(Component.literal(
                    "§cAucune carte sélectionnée. Veuillez sélectionner une carte pour lancer le Sous-mode 3.")));
            return;
        }

        ServerLevel monde = serveur.getLevel(ServerLevel.OVERWORLD);
        if (monde == null) {
            return;
        }

        // Nettoyer les bonbons résiduels
        try {
            GestionnaireBonbonsSousMode3.obtenirInstance().retirerTousBonbonsDuMonde(serveur);
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors du nettoyage des bonbons résiduels", e);
        }

        // La carte sélectionnée est générée dans le monde au moment du lancement du sous-mode
        generation = GenerateurCarteSousMode3.generer(monde, carte);
        carteGeneree = true;

        // Générer la plateforme spectateur (seul élément généré « à l'ancienne »)
        genererPlateformeSpectateur(monde);

        // Retirer les résidus de la destruction du terrain principal (items et
        // entités apparus pendant la génération) avant de placer les bonbons
        retirerItemsDansCage(monde);

        // Initialiser les bonbons et les zones, puis faire apparaître les bonbons visibles
        GestionnaireBonbonsSousMode3.obtenirInstance().initialiser(serveur, carte, generation);
        GestionnaireBonbonsSousMode3.obtenirInstance().genererBonbonsVisibles(monde, carte, generation);

        // Jour permanent
        monde.setDayTime(6000);

        // Démarrer la journalisation de la session
        enregistreurDonnees = new EnregistreurDonneesSousMode3();
        enregistreurDonnees.demarrerNouvellePartie();
        traiterEvenementsJournalisationEnAttente();

        // Téléporter tous les joueurs sur la plateforme spectateur
        phaseAttente = true;
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                teleporterVersSpectateur(joueur);
            } else {
                stockerInventaireJoueur(joueur);
                viderInventaireJoueur(joueur);
                joueursEnAttente.add(joueur.getUUID());
                nomsJoueurs.put(joueur.getUUID(), joueur.getName().getString());
                teleporterVersPlateforme(joueur);
            }
        }

        diffuserMessage(serveur, "§eCarte « " + carte.nom + " » chargée. En attente du lancement de la partie...");
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                joueur.sendSystemMessage(Component.literal("§6Appuyez sur N pour ouvrir le menu de lancement de partie"));
            }
        }
    }

    // ==================== Lancement de partie ====================

    /**
     * Lancement demandé par un admin (menu N) : décompte de 10 secondes, puis les
     * joueurs sont téléportés vers le point d'apparition défini dans la carte.
     */
    public boolean lancerPartie(MinecraftServer serveur, ServerPlayer admin) {
        if (!phaseAttente || partieActive) {
            if (admin != null) {
                admin.sendSystemMessage(Component.literal("§cLa partie est déjà lancée"));
            }
            return false;
        }
        if (decompteEnCours) {
            if (admin != null) {
                admin.sendSystemMessage(Component.literal("§cLe décompte est déjà en cours"));
            }
            return false;
        }
        if (!UtilitaireFiltreJoueurs.aParticipantConnecte(serveur)) {
            if (admin != null) {
                admin.sendSystemMessage(Component.literal("§cImpossible de lancer la partie - Aucun joueur (protégé ou libre) connecté!"));
            }
            return false;
        }

        decompteEnCours = true;
        MonSubMod.JOURNALISEUR.info("Lancement de la partie Sous-mode 3 par {} (décompte de {} secondes)",
            admin != null ? admin.getName().getString() : "SERVEUR", DECOMPTE_LANCEMENT_SECONDES);

        minuteurDecompte = new Timer("SousMode3-Decompte", true);
        minuteurDecompte.scheduleAtFixedRate(new TimerTask() {
            private int secondesRestantes = DECOMPTE_LANCEMENT_SECONDES;

            @Override
            public void run() {
                if (serveur == null || serveur.isStopped()) {
                    cancel();
                    return;
                }
                final int secondes = secondesRestantes;
                serveur.execute(() -> {
                    if (!decompteEnCours) {
                        return;
                    }
                    if (secondes > 0) {
                        diffuserMessage(serveur, "§6§lDébut de la partie dans " + secondes + " seconde(s)...");
                    } else {
                        arreterDecompte();
                        demarrerPartie(serveur);
                    }
                });
                secondesRestantes--;
                if (secondesRestantes < -1) {
                    cancel();
                }
            }
        }, 0, 1000);

        return true;
    }

    private void arreterDecompte() {
        decompteEnCours = false;
        if (minuteurDecompte != null) {
            minuteurDecompte.cancel();
            minuteurDecompte = null;
        }
    }

    private void demarrerPartie(MinecraftServer serveur) {
        if (partieActive || !phaseAttente) {
            return;
        }
        // Revérifier à la fin du décompte : le dernier participant a pu se déconnecter entre-temps
        if (!UtilitaireFiltreJoueurs.aParticipantConnecte(serveur)) {
            MonSubMod.JOURNALISEUR.warn("Démarrage de la partie annulé (Sous-mode 3) : aucun joueur (protégé ou libre) connecté à la fin du décompte");
            diffuserMessage(serveur, "§cLancement annulé - Aucun joueur (protégé ou libre) connecté!");
            return;
        }
        phaseAttente = false;
        partieActive = true;
        heureDebutPartie = System.currentTimeMillis();

        ServerLevel monde = serveur.getLevel(ServerLevel.OVERWORLD);
        BlockPos apparition = obtenirPointApparition();

        // Téléporter les joueurs vers le point d'apparition défini dans la carte
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                continue;
            }
            joueursEnAttente.add(joueur.getUUID());
            joueursVivants.add(joueur.getUUID());
            joueursSpectateurs.remove(joueur.getUUID());
            nomsJoueurs.put(joueur.getUUID(), joueur.getName().getString());

            if (monde != null && apparition != null) {
                teleportationSecurisee(joueur, monde,
                    apparition.getX() + 0.5, apparition.getY(), apparition.getZ() + 0.5);
                dernieresPositionsValides.put(joueur.getUUID(), new double[]{
                    apparition.getX() + 0.5, apparition.getY(), apparition.getZ() + 0.5, 0, 0});
            }
            joueur.setGameMode(GameType.SURVIVAL);
        }

        // Réinitialiser la santé et la faim
        reinitialiserSanteTousJoueurs(serveur);

        // Minuterie de partie et dégradation de santé (base du Sous-mode 1)
        minuteurPartie = new MinuterieJeu(TEMPS_PARTIE_MINUTES, serveur);
        minuteurPartie.demarrer();
        GestionnaireSanteSousMode3.getInstance().demarrerDegradationSante(serveur);

        // HUD des zones pour tous les joueurs
        GestionnaireBonbonsSousMode3.obtenirInstance().envoyerZonesCompletesATous(true);

        // Apparitions initiales différées (délai configuré par bloc, depuis le début de partie)
        GestionnaireBonbonsSousMode3.obtenirInstance().planifierApparitionsInitiales();

        diffuserMessage(serveur, "§aLa partie commence ! Survivez " + TEMPS_PARTIE_MINUTES + " minutes !");
        diffuserMessage(serveur, "§7Minez les blocs de la carte pour trouver les bonbons non-visibles !");
    }

    // ==================== Désactivation ====================

    public void deactivate(MinecraftServer serveur) {
        MonSubMod.JOURNALISEUR.info("Désactivation du SousMode3");

        try {
            try {
                for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                    joueur.closeContainer();
                }
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la fermeture des écrans des joueurs", e);
            }

            arreterDecompte();

            try {
                if (minuteurPartie != null) {
                    minuteurPartie.arreter();
                    minuteurPartie = null;
                }
                GestionnaireReseau.INSTANCE.send(PacketDistributor.ALL.noArg(), new PaquetMinuterieJeuSousMode3(-1));
                GestionnaireReseau.INSTANCE.send(PacketDistributor.ALL.noArg(), PaquetZonesSousMode3.vide());
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'arrêt de la minuterie de partie", e);
            }

            try {
                if (enregistreurDonnees != null) {
                    enregistreurDonnees.terminerPartie();
                }
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'arrêt de l'enregistrement des données", e);
            }

            try {
                GestionnaireSanteSousMode3.getInstance().arreterDegradationSante();
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'arrêt de la dégradation de santé", e);
            }

            // Retirer les bonbons (objets au sol) et arrêter les réapparitions
            try {
                GestionnaireBonbonsSousMode3.obtenirInstance().retirerTousBonbonsDuMonde(serveur);
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors du retrait des bonbons", e);
            }

            try {
                restaurerTousInventaires(serveur);
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la restauration des inventaires", e);
            }

            // Supprimer tous les éléments générés dans le monde
            try {
                ServerLevel monde = serveur.getLevel(ServerLevel.OVERWORLD);
                if (monde != null) {
                    if (generation != null) {
                        // Inclure les blocs bonbons réapparus et les blocs placés par les joueurs (déjà suivis)
                        generation.blocsPlaces.addAll(
                            GestionnaireBonbonsSousMode3.obtenirInstance().obtenirPositionsBonbonsCaches());
                        retirerItemsDansCage(monde);
                        GenerateurCarteSousMode3.effacer(monde, generation.blocsPlaces);
                        effacerPlateformeSpectateur(monde);
                    } else {
                        // Nettoyage de secours après un arrêt inattendu du serveur
                        GenerateurCarteSousMode3.nettoyerRegionResiduelle(monde);
                    }
                }
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'effacement de la carte du Sous-mode 3", e);
            }

            try {
                GestionnaireBonbonsSousMode3.obtenirInstance().arreter();
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'arrêt du gestionnaire de bonbons", e);
            }

        } finally {
            partieActive = false;
            phaseAttente = false;
            decompteEnCours = false;
            finPartieEnCours = false;
            carteGeneree = false;
            carte = null;
            generation = null;
            heureDebutPartie = 0;
            enregistreurDonnees = null;
            joueursVivants.clear();
            joueursSpectateurs.clear();
            joueursEnAttente.clear();
            joueursDeconnectes.clear();
            compteurBonbonsJoueur.clear();
            heureMortJoueur.clear();
            nomsJoueurs.clear();
            dernieresPositionsValides.clear();
            evenementsJournalisationEnAttente.clear();

            MonSubMod.JOURNALISEUR.info("Désactivation du SousMode3 terminée");
        }
    }

    /** Retire les items au sol et les créatures (monstres, animaux...) dans la cage (avant effacement) */
    private void retirerItemsDansCage(ServerLevel monde) {
        if (generation == null || carte == null) {
            return;
        }
        net.minecraft.world.phys.AABB boite = new net.minecraft.world.phys.AABB(
            generation.origineX - 2, GenerateurCarteSousMode3.Y_PLANCHER_BARRIER - 2, generation.origineZ - 2,
            generation.origineX + carte.largeur + 2, GenerateurCarteSousMode3.Y_PLAFOND_BARRIER + 3,
            generation.origineZ + carte.hauteur + 2);
        int retires = 0;
        for (net.minecraft.world.entity.item.ItemEntity item :
            monde.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, boite)) {
            item.discard();
            retires++;
        }
        for (net.minecraft.world.entity.Mob creature :
            monde.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, boite)) {
            creature.discard();
            retires++;
        }
        MonSubMod.JOURNALISEUR.info("{} entités/items retirés de la cage du Sous-mode 3", retires);
    }

    // ==================== Fin de partie ====================

    public void terminerPartie(MinecraftServer serveur) {
        if (finPartieEnCours) {
            return;
        }
        finPartieEnCours = true;
        partieActive = false;

        MonSubMod.JOURNALISEUR.info("Fin de la partie SousMode3");

        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur), new PaquetFinPartieSousMode3());
        }

        try {
            GestionnaireSanteSousMode3.getInstance().arreterDegradationSante();
            GestionnaireBonbonsSousMode3.obtenirInstance().retirerTousBonbonsDuMonde(serveur);
            if (minuteurPartie != null) {
                minuteurPartie.arreter();
            }
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors de l'arrêt des systèmes de jeu pendant terminerPartie", e);
        }

        afficherClassement(serveur);
        afficherFelicitations(serveur);

        Timer minuteurDelai = new Timer("SousMode3-FinPartie-minuterie", true);
        minuteurDelai.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    serveur.execute(() -> {
                        try {
                            GestionnaireSousModes.getInstance().changerSousMode(
                                com.example.mysubmod.sousmodes.SousMode.SALLE_ATTENTE, null, serveur);
                        } catch (Exception e) {
                            MonSubMod.JOURNALISEUR.error("Erreur lors du retour à la salle d'attente", e);
                        } finally {
                            minuteurDelai.cancel();
                        }
                    });
                } catch (Exception e) {
                    MonSubMod.JOURNALISEUR.error("Erreur lors de la planification du changement de mode", e);
                    minuteurDelai.cancel();
                }
            }
        }, 5000);
    }

    private void afficherFelicitations(MinecraftServer serveur) {
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            joueur.sendSystemMessage(Component.literal("§6§l=== FÉLICITATIONS ==="));
            joueur.sendSystemMessage(Component.literal("§eMerci d'avoir participé à cette expérience !"));
            joueur.sendSystemMessage(Component.literal("§aRetour à la salle d'attente dans 5 secondes..."));
        }
    }

    private void afficherClassement(MinecraftServer serveur) {
        List<EntreeClassement> entrees = new ArrayList<>();

        Set<UUID> participants = new HashSet<>();
        participants.addAll(joueursVivants);
        participants.addAll(joueursEnAttente);
        participants.addAll(heureMortJoueur.keySet());

        for (UUID idJoueur : participants) {
            String nomJoueur;
            ServerPlayer joueurEnLigne = serveur.getPlayerList().getPlayer(idJoueur);
            if (joueurEnLigne != null) {
                nomJoueur = joueurEnLigne.getName().getString();
            } else if (nomsJoueurs.containsKey(idJoueur)) {
                nomJoueur = nomsJoueurs.get(idJoueur);
            } else {
                continue;
            }

            boolean estVivant = joueursVivants.contains(idJoueur);
            int nombreBonbons = compteurBonbonsJoueur.getOrDefault(idJoueur, 0);
            long tempsSurvie = 0;
            if (!estVivant && heureMortJoueur.containsKey(idJoueur)) {
                tempsSurvie = heureMortJoueur.get(idJoueur) - heureDebutPartie;
            }
            entrees.add(new EntreeClassement(nomJoueur, estVivant, nombreBonbons, tempsSurvie));
        }

        entrees.sort((e1, e2) -> {
            if (e1.estVivant && !e2.estVivant) {
                return -1;
            }
            if (!e1.estVivant && e2.estVivant) {
                return 1;
            }
            if (e1.estVivant) {
                return Integer.compare(e2.nombreBonbons, e1.nombreBonbons);
            }
            return Long.compare(e2.tempsSurvieMs, e1.tempsSurvieMs);
        });

        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            joueur.sendSystemMessage(Component.literal(""));
            joueur.sendSystemMessage(Component.literal("§6§l========== CLASSEMENT FINAL =========="));
            joueur.sendSystemMessage(Component.literal(""));

            for (int i = 0; i < entrees.size(); i++) {
                EntreeClassement entree = entrees.get(i);
                int rang = i + 1;
                String couleurRang = rang == 1 ? "§6" : rang == 2 ? "§7" : rang == 3 ? "§c" : "§f";
                String infoStatut;
                if (entree.estVivant) {
                    infoStatut = String.format("§a✓ Vivant §7- §e%d bonbons", entree.nombreBonbons);
                } else {
                    long secondesSurvie = entree.tempsSurvieMs / 1000;
                    infoStatut = String.format("§c✗ Mort §7- Survie: %dm%ds", secondesSurvie / 60, secondesSurvie % 60);
                }
                joueur.sendSystemMessage(Component.literal(
                    String.format("%s#%d §f- %s %s", couleurRang, rang, entree.nomJoueur, infoStatut)));
            }

            joueur.sendSystemMessage(Component.literal(""));
            joueur.sendSystemMessage(Component.literal("§6§l======================================="));
            joueur.sendSystemMessage(Component.literal(""));
        }
    }

    // ==================== Plateforme spectateur (identique au Sous-mode 1) ====================

    private void genererPlateformeSpectateur(ServerLevel niveau) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                niveau.setBlock(plateformeSpectateur.offset(x, 0, z), Blocks.GLASS.defaultBlockState(), 3);
            }
        }

        for (int x = -11; x <= 11; x++) {
            for (int z = -11; z <= 11; z++) {
                if (x >= -10 && x <= 10 && z >= -10 && z <= 10) {
                    continue;
                }
                for (int y = 1; y <= 3; y++) {
                    niveau.setBlock(plateformeSpectateur.offset(x, y, z), Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }

        niveau.setBlock(plateformeSpectateur.offset(0, 2, 10),
            Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH), 3);
        if (niveau.getBlockEntity(plateformeSpectateur.offset(0, 2, 10)) instanceof SignBlockEntity panneau) {
            Component[] texte = new Component[]{
                Component.literal("Cliquer sur ce"),
                Component.literal("panneau pour aller"),
                Component.literal("en mode"),
                Component.literal("spectateur")
            };
            panneau.setText(new SignText(texte, texte, DyeColor.BLACK, false), true);
            panneau.setChanged();
        }
    }

    private void effacerPlateformeSpectateur(ServerLevel niveau) {
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                for (int y = -5; y <= 10; y++) {
                    niveau.setBlock(plateformeSpectateur.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    // ==================== Téléportations ====================

    public void teleporterVersSpectateur(ServerPlayer joueur) {
        ServerLevel overworld = joueur.server.getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            teleportationSecurisee(joueur, overworld,
                plateformeSpectateur.getX() + 0.5,
                plateformeSpectateur.getY() + 1,
                plateformeSpectateur.getZ() + 0.5);
            joueur.setGameMode(GameType.SURVIVAL);
            joueursSpectateurs.add(joueur.getUUID());
            joueursVivants.remove(joueur.getUUID());
        }
    }

    /** Téléporte un participant (non spectateur) sur la plateforme, en attente du lancement */
    public void teleporterVersPlateforme(ServerPlayer joueur) {
        ServerLevel overworld = joueur.server.getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            teleportationSecurisee(joueur, overworld,
                plateformeSpectateur.getX() + 0.5,
                plateformeSpectateur.getY() + 1,
                plateformeSpectateur.getZ() + 0.5);
            joueur.setGameMode(GameType.SURVIVAL);
        }
    }

    private void teleportationSecurisee(ServerPlayer joueur, ServerLevel niveau, double x, double y, double z) {
        BlockPos destination = new BlockPos((int) x, (int) y, (int) z);
        niveau.getChunkAt(destination);
        joueur.connection.resetPosition();
        joueur.moveTo(x, y, z, 0.0f, 0.0f);
        joueur.teleportTo(niveau, x, y, z, 0.0f, 0.0f);
        joueur.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket(
            x, y, z, 0.0f, 0.0f, java.util.Collections.emptySet(), 0));
    }

    // ==================== Cage (vérification à chaque tick) ====================

    /** Convertit une position monde en clé de cellule de carte (null si hors de l'aire) */
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

    public boolean estDansCage(BlockPos pos) {
        if (generation == null || carte == null) {
            return false;
        }
        if (pos.getY() <= GenerateurCarteSousMode3.Y_PLANCHER_BARRIER
            || pos.getY() >= GenerateurCarteSousMode3.Y_PLAFOND_BARRIER) {
            return false;
        }
        int cx = pos.getX() - generation.origineX;
        int cz = pos.getZ() - generation.origineZ;
        return generation.cellulesInterieur.contains(CarteDonnees.cle(cx, cz));
    }

    /** Zone rectangulaire de l'aire de la carte (pour filtrer les items/monstres) */
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
     * Vérifie la position de chaque joueur vivant (appelé à chaque tick).
     * Un joueur hors cage est retéléporté à sa dernière position valide exacte.
     */
    public void verifierCageJoueurs(MinecraftServer serveur) {
        if (!partieActive || generation == null) {
            return;
        }
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            if (!joueursVivants.contains(joueur.getUUID())) {
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
                    if (apparition != null) {
                        joueur.connection.teleport(apparition.getX() + 0.5, apparition.getY(),
                            apparition.getZ() + 0.5, 0, 0);
                    }
                }
            }
        }
    }

    public BlockPos obtenirPointApparition() {
        if (generation != null && generation.pointApparitionMonde != null) {
            return generation.pointApparitionMonde;
        }
        return plateformeSpectateur.above();
    }

    // ==================== Déconnexions / reconnexions (base du Sous-mode 1) ====================

    public void gererDeconnexionJoueur(ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString();
        com.example.mysubmod.authentification.GestionnaireSalleAttente lobbyAttente =
            com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();
        if (lobbyAttente.estNomFileTemporaire(nomJoueur)) {
            return;
        }

        if (joueursDeconnectes.containsKey(nomJoueur)) {
            return; // Ne jamais écraser une info existante (système de file d'attente)
        }

        List<ItemStack> inventaireSauvegarde = new ArrayList<>();
        for (int i = 0; i < joueur.getInventory().getContainerSize(); i++) {
            ItemStack pile = joueur.getInventory().getItem(i);
            inventaireSauvegarde.add(pile.isEmpty() ? ItemStack.EMPTY : pile.copy());
        }

        InfoDeconnexion info = new InfoDeconnexion(
            joueur.getUUID(),
            System.currentTimeMillis(),
            joueur.getX(), joueur.getY(), joueur.getZ(),
            joueur.getHealth(),
            inventaireSauvegarde);
        joueursDeconnectes.put(nomJoueur, info);
        MonSubMod.JOURNALISEUR.info("Joueur {} déconnecté pendant SousMode3 à ({}, {}, {}) avec {} PV",
            nomJoueur, joueur.getX(), joueur.getY(), joueur.getZ(), joueur.getHealth());

        if (enregistreurDonnees != null) {
            enregistreurDonnees.enregistrerActionJoueur(joueur, "DECONNECTE");
        } else {
            evenementsJournalisationEnAttente.add(new EvenementJournalisationEnAttente(joueur, "DECONNECTE"));
        }
    }

    public boolean etaitJoueurDeconnecte(String nomJoueur) {
        return joueursDeconnectes.containsKey(nomJoueur);
    }

    public void effacerInfoDeconnexion(String nomJoueur) {
        joueursDeconnectes.remove(nomJoueur);
    }

    public Map<String, InfoDeconnexion> obtenirInfoJoueursDeconnectes() {
        synchronized (verrouReconnexion) {
            return new HashMap<>(joueursDeconnectes);
        }
    }

    public void gererMortJoueurDeconnecte(String nomJoueur, UUID idJoueur) {
        synchronized (verrouReconnexion) {
            if (!joueursDeconnectes.containsKey(nomJoueur)) {
                return;
            }
            InfoDeconnexion info = joueursDeconnectes.get(nomJoueur);
            if (!info.ancienUUID.equals(idJoueur)) {
                return;
            }
            if (!joueursVivants.contains(idJoueur)) {
                return;
            }

            MonSubMod.JOURNALISEUR.info("Le joueur déconnecté {} est mort côté serveur (Sous-mode 3)", nomJoueur);
            joueursVivants.remove(idJoueur);
            joueursSpectateurs.add(idJoueur);
            enregistrerMortJoueur(idJoueur);
            info.estMort = true;
            info.inventaireSauvegarde = new ArrayList<>();
        }
    }

    public void gererReconnexionJoueur(ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString();
        UUID nouveauUUID = joueur.getUUID();

        if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
            return;
        }

        InfoDeconnexion infoDeconnexion;
        boolean joueurEstMort;
        synchronized (verrouReconnexion) {
            infoDeconnexion = joueursDeconnectes.remove(nomJoueur);
            if (infoDeconnexion == null) {
                return;
            }
            joueurEstMort = infoDeconnexion.estMort;
        }

        // Restaurer le HUD des zones — nécessaire quand la reconnexion passe par
        // l'authentification (le onPlayerJoin du sous-mode est ignoré pour les joueurs restreints).
        // Seulement une fois la partie lancée : avant, les compteurs ne reflètent rien.
        if (partieActive) {
            GestionnaireBonbonsSousMode3.obtenirInstance().envoyerZonesCompletesAJoueur(joueur, true);
        }

        if (joueurEstMort) {
            teleporterVersSpectateur(joueur);
            joueur.sendSystemMessage(Component.literal(
                "§c§lVous êtes mort pendant votre déconnexion\n\n§7Vous avez été téléporté en zone spectateur."));
            return;
        }

        synchronized (verrouReconnexion) {
            UUID ancienUUID = infoDeconnexion.ancienUUID;
            if (!ancienUUID.equals(nouveauUUID)) {
                MonSubMod.JOURNALISEUR.info("Migration des données du joueur de l'UUID {} vers {} (compte {})",
                    ancienUUID, nouveauUUID, nomJoueur);
                if (joueursVivants.remove(ancienUUID)) {
                    joueursVivants.add(nouveauUUID);
                }
                if (joueursSpectateurs.remove(ancienUUID)) {
                    joueursSpectateurs.add(nouveauUUID);
                }
                if (joueursEnAttente.remove(ancienUUID)) {
                    joueursEnAttente.add(nouveauUUID);
                }
                if (compteurBonbonsJoueur.containsKey(ancienUUID)) {
                    compteurBonbonsJoueur.put(nouveauUUID, compteurBonbonsJoueur.remove(ancienUUID));
                }
                if (heureMortJoueur.containsKey(ancienUUID)) {
                    heureMortJoueur.put(nouveauUUID, heureMortJoueur.remove(ancienUUID));
                }
                if (dernieresPositionsValides.containsKey(ancienUUID)) {
                    dernieresPositionsValides.put(nouveauUUID, dernieresPositionsValides.remove(ancienUUID));
                }
                nomsJoueurs.remove(ancienUUID);
                nomsJoueurs.put(nouveauUUID, nomJoueur);
            }
        }

        long tempsDeconnexion = infoDeconnexion.tempsDeconnexion;
        long tempsActuel = System.currentTimeMillis();

        // Pénalité de santé : ticks de dégradation manqués pendant la déconnexion
        int ticksSanteManques = 0;
        float perteSante = 0.0f;
        if (partieActive && heureDebutPartie > 0) {
            long tempsDeconnexionEffectif = Math.max(tempsDeconnexion, heureDebutPartie);
            long numeroTickADeconnexion = (tempsDeconnexionEffectif - heureDebutPartie) / 10000;
            long numeroTickAReconnexion = (tempsActuel - heureDebutPartie) / 10000;
            ticksSanteManques = (int) (numeroTickAReconnexion - numeroTickADeconnexion);
            if (ticksSanteManques > 0) {
                perteSante = ticksSanteManques * 1.0f;
            }
        }

        MinecraftServer serveur = joueur.getServer();
        ServerLevel monde = serveur != null ? serveur.getLevel(ServerLevel.OVERWORLD) : null;

        // Reconnexion pendant la phase d'attente (avant le lancement)
        if (!partieActive && phaseAttente) {
            joueursEnAttente.add(nouveauUUID);
            nomsJoueurs.put(nouveauUUID, nomJoueur);
            teleporterVersPlateforme(joueur);
            joueur.sendSystemMessage(Component.literal(
                "§eVous avez été reconnecté. En attente du lancement de la partie..."));
            if (enregistreurDonnees != null) {
                enregistreurDonnees.enregistrerActionJoueur(joueur, "RECONNECTE (phase attente)");
            }
            return;
        }

        // Le joueur était en attente et la partie a commencé pendant sa déconnexion
        if (partieActive && joueursEnAttente.contains(nouveauUUID) && !joueursVivants.contains(nouveauUUID)
            && !joueursSpectateurs.contains(nouveauUUID)) {
            joueursVivants.add(nouveauUUID);
            nomsJoueurs.put(nouveauUUID, nomJoueur);

            BlockPos apparition = obtenirPointApparition();
            if (monde != null && apparition != null) {
                teleportationSecurisee(joueur, monde,
                    apparition.getX() + 0.5, apparition.getY(), apparition.getZ() + 0.5);
            }
            joueur.setGameMode(GameType.SURVIVAL);

            // Santé pleine puis pénalité pour le temps manqué depuis le début de la partie
            joueur.setHealth(joueur.getMaxHealth());
            joueur.getFoodData().setFoodLevel(10);
            joueur.getFoodData().setSaturation(5.0f);
            float nouvelleSante = Math.max(0.5f, joueur.getMaxHealth() - perteSante);
            joueur.setHealth(nouvelleSante);

            joueur.sendSystemMessage(Component.literal(String.format(
                "§eVous avez été reconnecté. Le jeu a démarré pendant votre absence. Perte de santé: %.1f cœurs",
                perteSante / 2.0f)));
            if (enregistreurDonnees != null) {
                enregistreurDonnees.enregistrerActionJoueur(joueur, "RECONNECTE (debut partie manque)");
            }
            verifierMortApresReconnexion(joueur, nouvelleSante);
            return;
        }

        if (joueursVivants.contains(nouveauUUID)) {
            boolean deconnecteAvantDebutPartie = heureDebutPartie > 0 && tempsDeconnexion < heureDebutPartie;

            if (monde != null) {
                if (deconnecteAvantDebutPartie) {
                    BlockPos apparition = obtenirPointApparition();
                    teleportationSecurisee(joueur, monde,
                        apparition.getX() + 0.5, apparition.getY(), apparition.getZ() + 0.5);
                } else {
                    teleportationSecurisee(joueur, monde, infoDeconnexion.x, infoDeconnexion.y, infoDeconnexion.z);
                }
            }
            joueur.setGameMode(GameType.SURVIVAL);

            // Restaurer l'inventaire sauvegardé à la déconnexion
            if (infoDeconnexion.inventaireSauvegarde != null && !infoDeconnexion.inventaireSauvegarde.isEmpty()) {
                joueur.getInventory().clearContent();
                for (int i = 0; i < Math.min(infoDeconnexion.inventaireSauvegarde.size(),
                    joueur.getInventory().getContainerSize()); i++) {
                    joueur.getInventory().setItem(i, infoDeconnexion.inventaireSauvegarde.get(i).copy());
                }
            } else if (!joueur.getInventory().isEmpty()) {
                viderInventaireJoueur(joueur);
            }

            float santeActuelle = joueur.getHealth();
            if (deconnecteAvantDebutPartie) {
                joueur.setHealth(joueur.getMaxHealth());
                joueur.getFoodData().setFoodLevel(10);
                joueur.getFoodData().setSaturation(5.0f);
                santeActuelle = joueur.getMaxHealth();
            }

            float nouvelleSante = Math.max(0.5f, santeActuelle - perteSante);
            joueur.setHealth(nouvelleSante);
            joueur.sendSystemMessage(Component.literal(String.format(
                "§eVous avez été reconnecté. Perte de santé: %.1f cœurs (%d ticks de dégradation manqués)",
                perteSante / 2.0f, ticksSanteManques)));

            if (enregistreurDonnees != null) {
                enregistreurDonnees.enregistrerActionJoueur(joueur,
                    String.format("RECONNECTE (-%d ticks, -%.1f PV)", ticksSanteManques, perteSante));
            } else {
                evenementsJournalisationEnAttente.add(new EvenementJournalisationEnAttente(joueur,
                    String.format("RECONNECTE (-%d ticks, -%.1f PV)", ticksSanteManques, perteSante)));
            }

            verifierMortApresReconnexion(joueur, nouvelleSante);
        } else if (joueursSpectateurs.contains(nouveauUUID)) {
            teleporterVersSpectateur(joueur);
            joueur.sendSystemMessage(Component.literal("§eVous avez été reconnecté en mode spectateur"));
        }
    }

    private void verifierMortApresReconnexion(ServerPlayer joueur, float nouvelleSante) {
        if (nouvelleSante > 0.5f) {
            return;
        }
        joueur.sendSystemMessage(Component.literal("§cVous êtes mort pendant votre déconnexion !"));
        enregistrerMortJoueur(joueur.getUUID());
        teleporterVersSpectateur(joueur);

        MinecraftServer serveur = joueur.getServer();
        if (serveur != null) {
            String messageMort = "§e" + joueur.getName().getString() + " §cest mort pendant sa déconnexion !";
            for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                j.sendSystemMessage(Component.literal(messageMort));
            }
            if (obtenirJoueursVivants().isEmpty()) {
                serveur.execute(() -> {
                    for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                        j.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                    }
                    terminerPartie(serveur);
                });
            }
        }
    }

    /** Ajoute un joueur nouvellement connecté à la phase d'attente */
    public void ajouterJoueurEnAttente(ServerPlayer joueur) {
        joueursEnAttente.add(joueur.getUUID());
        nomsJoueurs.put(joueur.getUUID(), joueur.getName().getString());
        stockerInventaireJoueur(joueur);
        viderInventaireJoueur(joueur);
        teleporterVersPlateforme(joueur);
    }

    // ==================== Inventaires ====================

    private void stockerInventaireJoueur(ServerPlayer joueur) {
        List<ItemStack> inventaire = new ArrayList<>();
        for (int i = 0; i < joueur.getInventory().getContainerSize(); i++) {
            inventaire.add(joueur.getInventory().getItem(i).copy());
        }
        inventairesStockes.put(joueur.getUUID(), inventaire);
    }

    private void viderInventaireJoueur(ServerPlayer joueur) {
        joueur.getInventory().clearContent();
    }

    private void restaurerTousInventaires(MinecraftServer serveur) {
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            joueur.getInventory().clearContent();
            List<ItemStack> inventaire = inventairesStockes.remove(joueur.getUUID());
            if (inventaire != null) {
                for (int i = 0; i < Math.min(inventaire.size(), joueur.getInventory().getContainerSize()); i++) {
                    joueur.getInventory().setItem(i, inventaire.get(i));
                }
            }
        }
        inventairesStockes.clear();
    }

    private void reinitialiserSanteTousJoueurs(MinecraftServer serveur) {
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                continue;
            }
            joueur.setHealth(joueur.getMaxHealth());
            joueur.getFoodData().setFoodLevel(10);
            joueur.getFoodData().setSaturation(5.0f);
        }
    }

    private void diffuserMessage(MinecraftServer serveur, String contenu) {
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            joueur.sendSystemMessage(Component.literal(contenu));
        }
    }

    private void traiterEvenementsJournalisationEnAttente() {
        if (enregistreurDonnees == null || evenementsJournalisationEnAttente.isEmpty()) {
            return;
        }
        for (EvenementJournalisationEnAttente evenement : evenementsJournalisationEnAttente) {
            enregistreurDonnees.enregistrerActionJoueur(evenement.joueur, evenement.action);
        }
        evenementsJournalisationEnAttente.clear();
    }

    // ==================== Suivi divers ====================

    /**
     * Gérer la noyade d'un joueur : agit comme s'il avait perdu tous ses points
     * de vie — fin de la partie pour lui (mort enregistrée + zone spectateur).
     */
    public void gererNoyadeJoueur(ServerPlayer joueur) {
        if (!partieActive || !joueursVivants.contains(joueur.getUUID())) {
            return;
        }

        joueur.sendSystemMessage(Component.literal("§cVous vous êtes noyé ! Téléportation vers la zone spectateur..."));

        enregistrerMortJoueur(joueur.getUUID());
        teleporterVersSpectateur(joueur);
        joueur.setHealth(joueur.getMaxHealth());

        String messageMort = "§e" + joueur.getName().getString() + " §cs'est noyé !";
        for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(joueur.server)) {
            j.sendSystemMessage(Component.literal(messageMort));
        }

        if (obtenirEnregistreurDonnees() != null) {
            obtenirEnregistreurDonnees().enregistrerMortJoueur(joueur);
        }

        if (obtenirJoueursVivants().isEmpty()) {
            MonSubMod.JOURNALISEUR.info("Tous les joueurs sont morts (noyade) - fin du jeu (Sous-mode 3)");
            joueur.server.execute(() -> {
                for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(joueur.server)) {
                    j.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                }
                terminerPartie(joueur.server);
            });
        }
    }

    public void incrementerCompteurBonbons(UUID idJoueur, int montant) {
        compteurBonbonsJoueur.put(idJoueur, compteurBonbonsJoueur.getOrDefault(idJoueur, 0) + montant);
    }

    public void enregistrerMortJoueur(UUID idJoueur) {
        heureMortJoueur.put(idJoueur, System.currentTimeMillis());
    }

    /** Suit un bloc placé (par génération tardive ou par un joueur) pour le nettoyage */
    public void suivreBlocPlace(BlockPos pos) {
        if (generation != null) {
            generation.blocsPlaces.add(pos.immutable());
        }
    }

    // ==================== Getters ====================

    public boolean estPartieActive() {
        return partieActive;
    }

    public boolean estPhaseAttente() {
        return phaseAttente;
    }

    public boolean estDecompteEnCours() {
        return decompteEnCours;
    }

    public boolean estCarteGeneree() {
        return carteGeneree;
    }

    public long obtenirHeureDebutPartie() {
        return heureDebutPartie;
    }

    public boolean estJoueurVivant(UUID idJoueur) {
        return joueursVivants.contains(idJoueur);
    }

    public boolean estJoueurSpectateur(UUID idJoueur) {
        return joueursSpectateurs.contains(idJoueur);
    }

    public boolean estEnAttente(UUID idJoueur) {
        return joueursEnAttente.contains(idJoueur);
    }

    public Set<UUID> obtenirJoueursVivants() {
        return new HashSet<>(joueursVivants);
    }

    public EnregistreurDonneesSousMode3 obtenirEnregistreurDonnees() {
        return enregistreurDonnees;
    }

    public BlockPos obtenirPlateformeSpectateur() {
        return plateformeSpectateur;
    }

    public CarteDonnees obtenirCarte() {
        return carte;
    }

    private static class EvenementJournalisationEnAttente {
        final ServerPlayer joueur;
        final String action;

        EvenementJournalisationEnAttente(ServerPlayer joueur, String action) {
            this.joueur = joueur;
            this.action = action;
        }
    }

    private static class EntreeClassement {
        final String nomJoueur;
        final boolean estVivant;
        final int nombreBonbons;
        final long tempsSurvieMs;

        EntreeClassement(String nomJoueur, boolean estVivant, int nombreBonbons, long tempsSurvieMs) {
            this.nomJoueur = nomJoueur;
            this.estVivant = estVivant;
            this.nombreBonbons = nombreBonbons;
            this.tempsSurvieMs = tempsSurvieMs;
        }
    }
}

package com.example.mysubmod.sousmodes.sousmode2;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import com.example.mysubmod.sousmodes.sousmode2.donnees.EntreeApparitionBonbon;
import com.example.mysubmod.sousmodes.sousmode2.donnees.GestionnaireFichiersApparitionBonbons;
import com.example.mysubmod.sousmodes.sousmode2.donnees.EnregistreurDonneesSousMode2;
import com.example.mysubmod.sousmodes.sousmode2.iles.GenerateurIles;
import com.example.mysubmod.sousmodes.sousmode2.iles.TypeIle;
import com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetListeFichiersBonbons;
import com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSelectionIle;
import com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMinuterieJeu;
import com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetFinPartie;
import com.example.mysubmod.sousmodes.sousmode2.minuterie.MinuterieJeu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GestionnaireSousMode2 {
    private static GestionnaireSousMode2 instance;
    private final Map<UUID, List<ItemStack>> inventairesStockes = new ConcurrentHashMap<>();
    private final Map<UUID, TypeIle> selectionsIleJoueurs = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> selectionManuelleIleJoueur = new ConcurrentHashMap<>(); // Suivre si l'île a été sélectionnée manuellement (true) ou assignée automatiquement (false)
    private final Set<UUID> selectionIleJoueurJournalisee = ConcurrentHashMap.newKeySet(); // Suivre quels joueurs ont eu leur sélection d'île journalisée
    private final Set<UUID> joueursVivants = ConcurrentHashMap.newKeySet();
    private final Set<UUID> joueursSpectateurs = ConcurrentHashMap.newKeySet();
    private final Map<String, InfoDeconnexion> joueursDeconnectes = new ConcurrentHashMap<>(); // Suivre le temps et la position de déconnexion par nom de compte
    private final Map<UUID, Integer> compteurBonbonsJoueur = new ConcurrentHashMap<>(); // Suivre les bonbons collectés
    private final Map<UUID, Long> heureMortJoueur = new ConcurrentHashMap<>(); // Suivre quand les joueurs sont morts
    private final Map<UUID, String> nomsJoueurs = new ConcurrentHashMap<>(); // Suivre les noms des joueurs (pour les joueurs déconnectés dans le classement)
    private final Set<UUID> joueursEnPhaseSelection = ConcurrentHashMap.newKeySet(); // Joueurs présents pendant la sélection
    private final List<net.minecraft.world.entity.decoration.ArmorStand> hologrammes = new ArrayList<>(); // Suivre les entités hologrammes
    private final List<EvenementJournalisationEnAttente> evenementsJournalisationEnAttente = new ArrayList<>(); // Événements avant la création de enregistreurDonnees
    private long heureDebutPartie; // Suivre l'heure de début de partie
    private long heureDebutSelection; // Suivre l'heure de début de la phase de sélection

    // Verrou pour éviter les conditions de course entre la migration UUID (handlePlayerReconnection) et la mort côté serveur (handleDisconnectedPlayerDeath)
    private final Object verrouReconnexion = new Object();

    // Classe interne pour stocker les infos de déconnexion
    private static class InfoDeconnexion {
        UUID ancienUUID; // Stocker l'ancien UUID pour migrer les données lors d'une reconnexion
        long tempsDeconnexion;
        double x, y, z;
        float santeADeconnexion; // Stocker la santé à la déconnexion pour suivre les morts côté serveur
        java.util.List<net.minecraft.world.item.ItemStack> inventaireSauvegarde; // Sauvegarder les items
        boolean estMort; // Flag si le joueur est mort côté serveur pendant la déconnexion

        InfoDeconnexion(UUID ancienUUID, long tempsDeconnexion, double x, double y, double z, float sante, java.util.List<net.minecraft.world.item.ItemStack> inventaire) {
            this.ancienUUID = ancienUUID;
            this.tempsDeconnexion = tempsDeconnexion;
            this.x = x;
            this.y = y;
            this.z = z;
            this.santeADeconnexion = sante;
            this.inventaireSauvegarde = inventaire;
            this.estMort = false; // Initialement vivant
        }
    }

    private boolean partieActive = false;
    private boolean phaseSelection = false;
    private boolean phaseSelectionFichier = false; // L'admin sélectionne le fichier de bonbons
    private boolean finPartieEnCours = false;
    private boolean ilesGenerees = false;
    private Timer minuteurSelection;
    private MinuterieJeu minuteurPartie;
    private EnregistreurDonneesSousMode2 enregistreurDonnees;
    private String fichierApparitionBonbonsSelectionne;
    private List<EntreeApparitionBonbon> configApparitionBonbons;
    private ServerPlayer initiateurPartie; // L'admin qui a démarré la partie

    // Positions des îles
    private BlockPos centreIlePetite;
    private BlockPos centreIleMoyenne;
    private BlockPos centreIleGrande;
    private BlockPos centreIleTresGrande;
    private BlockPos placeCentrale;
    private BlockPos plateformeSpectateur;

    private static final int TEMPS_SELECTION_SECONDES = 30;
    private static final int TEMPS_PARTIE_MINUTES = 15;

    private GestionnaireSousMode2() {}

    public static GestionnaireSousMode2 getInstance() {
        if (instance == null) {
            instance = new GestionnaireSousMode2();
        }
        return instance;
    }

    /**
     * Vérifier si un joueur doit être exclu du SousMode2 (candidats en file d'attente OU joueur protégé/admin non authentifié)
     */
    private static boolean estJoueurRestreintLocal(ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString();
        com.example.mysubmod.authentification.GestionnaireSalleAttente lobbyAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();

        // Vérifier si c'est un candidat de file (nom temporaire)
        if (lobbyAttente.estNomFileTemporaire(nomJoueur)) {
            return true;
        }

        // Vérifier si joueur protégé/admin non authentifié
        com.example.mysubmod.authentification.GestionnaireAuth gestionnaireAuth = com.example.mysubmod.authentification.GestionnaireAuth.getInstance();
        com.example.mysubmod.authentification.GestionnaireAuthAdmin gestionnaireAuthAdmin = com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance();

        com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(nomJoueur);
        boolean estProtegeOuAdmin = (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE ||
                                      typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN);

        if (estProtegeOuAdmin) {
            // Vérifier l'authentification
            if (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN) {
                return !gestionnaireAuthAdmin.estAuthentifie(joueur);
            } else {
                return !gestionnaireAuth.estAuthentifie(joueur.getUUID());
            }
        }

        return false;
    }

    public void activate(MinecraftServer serveur) {
        activate(serveur, null);
    }

    public void activate(MinecraftServer serveur, ServerPlayer initiateur) {
        MonSubMod.JOURNALISEUR.info("Activation du SousMode2");
        this.initiateurPartie = initiateur;

        // Envoyer un message de chargement à tous les joueurs
        UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur).forEach(joueur -> {
            joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e§lChargement du sous-mode 2..."));
        });

        // Nettoyer les bonbons restants des parties précédentes d'abord
        try {
            GestionnaireBonbonsSousMode2.getInstance().retirerTousBonbonsDuMonde(serveur);
            MonSubMod.JOURNALISEUR.info("Nettoyage des bonbons résiduels des parties précédentes");
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors du nettoyage des bonbons résiduels", e);
        }

        // S'assurer que le répertoire d'apparition des bonbons existe
        GestionnaireFichiersApparitionBonbons.getInstance().assurerRepertoireExiste();

        // Initialiser les positions
        initialiserPositions();

        // Générer les îles et la plateforme spectateur
        ServerLevel mondePrincipal = serveur.getLevel(ServerLevel.OVERWORLD);
        MonSubMod.JOURNALISEUR.info("DEBUG: mondePrincipal est null? {}", (mondePrincipal == null));
        if (mondePrincipal != null) {
            MonSubMod.JOURNALISEUR.info("DEBUG: Appel de genererCarte");
            genererCarte(mondePrincipal);
            MonSubMod.JOURNALISEUR.info("DEBUG: genererCarte terminé");
            genererChemins(mondePrincipal);
            genererMursInvisibles(mondePrincipal);
            genererPlateformeSpectateur(mondePrincipal);
        }

        // Définir la lumière du jour permanente
        if (mondePrincipal != null) {
            mondePrincipal.setDayTime(6000); // Définir à midi
        }

        // Nettoyer à nouveau tous les bonbons après la génération des îles (par sécurité)
        try {
            GestionnaireBonbonsSousMode2.getInstance().retirerTousBonbonsDuMonde(serveur);
            MonSubMod.JOURNALISEUR.info("Nettoyage final des bonbons avant la téléportation des joueurs");
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors du nettoyage final des bonbons", e);
        }

        // Nettoyer les hologrammes orphelins des sessions précédentes
        try {
            nettoyerHologrammesOrphelins(serveur.getLevel(ServerLevel.OVERWORLD));
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors du nettoyage des hologrammes orphelins", e);
        }

        // Le journalisateur de données sera initialisé quand le fichier sera sélectionné (dans demarrerSelectionIle)

        // Téléporter les admins vers la plateforme spectateur
        teleporterAdminsVersSpectateur(serveur);

        // Téléporter tous les joueurs non-admin vers la petite île temporairement
        teleporterTousJoueursVersIlePetite(serveur);

        // Afficher la sélection de fichier de bonbons à l'admin initiateur
        if (initiateur != null && GestionnaireSousModes.getInstance().estAdmin(initiateur)) {
            phaseSelectionFichier = true; // Marquer qu'on est en phase de sélection de fichier
            afficherSelectionFichierBonbons(initiateur);
        } else {
            // Sélectionner automatiquement le fichier par défaut si aucun admin n'a initié ou en cas de repli
            String fichierDefaut = GestionnaireFichiersApparitionBonbons.getInstance().obtenirFichierParDefaut();
            definirFichierApparitionBonbons(fichierDefaut);
            demarrerSelectionIle(serveur);
        }
    }

    public void deactivate(MinecraftServer serveur) {
        MonSubMod.JOURNALISEUR.info("Désactivation du SousMode2");

        try {
            // Fermer tous les écrans ouverts pour tous les joueurs
            try {
                for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                    joueur.closeContainer();
                }
                MonSubMod.JOURNALISEUR.info("Fermeture de tous les écrans ouverts pour les joueurs");
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la fermeture des écrans des joueurs", e);
            }

            // Arrêter tous les minuteurs
            try {
                arreterMinuterieSelection();
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'arrêt du minuteur de sélection", e);
            }

            try {
                if (minuteurPartie != null) {
                    minuteurPartie.arreter();
                    minuteurPartie = null;
                }
                // Toujours envoyer le signal de désactivation à tous les clients pour effacer les minuteurs persistants
                GestionnaireReseau.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new PaquetMinuterieJeu(-1)); // -1 signifie désactiver

                // Envoyer des compteurs de bonbons vides pour désactiver le HUD
                GestionnaireReseau.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMiseAJourCompteurBonbons(new java.util.HashMap<>()));

                // Envoyer une liste de fichiers vide pour effacer le stockage côté client (sans ouvrir l'écran)
                GestionnaireReseau.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetListeFichiersBonbons(new java.util.ArrayList<>(), false));

                // Désactiver le HUD de minuterie de pénalité pour tous les clients
                for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                    GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                        new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSynchronisationPenalite(false, joueur.getUUID()));
                }
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'arrêt du minuteur de partie", e);
            }

            // Arrêter la journalisation des données
            try {
                if (enregistreurDonnees != null) {
                    enregistreurDonnees.terminerPartie();
                }
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'arrêt de la journalisation des données", e);
            }

            // Arrêter la dégradation de santé
            try {
                GestionnaireSanteSousMode2.getInstance().arreterDegradationSante();
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'arrêt de la dégradation de santé", e);
            }

            // Séquence de nettoyage des bonbons: arrêter minuteur -> nettoyer bonbons
            // Note: On garde les chunks chargés pour éviter les crashs lors du déchargement
            try {
                GestionnaireBonbonsSousMode2.getInstance().arreterMinuterie(); // Arrêter les apparitions futures
                GestionnaireBonbonsSousMode2.getInstance().retirerTousBonbonsDuMonde(serveur); // Nettoyer pendant que les chunks sont chargés
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'arrêt de l'apparition des bonbons", e);
            }

            // Note: Le changement de mode vers la salle d'attente est géré par le code appelant, pas ici
            // pour éviter les appels récursifs entre deactivate() et changerSousMode()

            // Restaurer les inventaires
            try {
                restaurerTousInventaires(serveur);
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la restauration des inventaires", e);
            }

            // Effacer la carte (îles + plateforme spectateur) APRÈS que les joueurs soient téléportés en sécurité
            try {
                effacerCarte(serveur.getLevel(ServerLevel.OVERWORLD));
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'effacement de la carte", e);
            }

        } finally {
            // Réinitialiser l'état indépendamment des erreurs
            partieActive = false;
            phaseSelection = false;
            phaseSelectionFichier = false;
            finPartieEnCours = false;
            ilesGenerees = false;
            selectionsIleJoueurs.clear();
            selectionManuelleIleJoueur.clear();
            selectionIleJoueurJournalisee.clear();
            joueursVivants.clear();
            joueursSpectateurs.clear();
            joueursDeconnectes.clear(); // Effacer le suivi des déconnexions
            compteurBonbonsJoueur.clear(); // Effacer les compteurs de bonbons
            heureMortJoueur.clear(); // Effacer les heures de mort
            nomsJoueurs.clear(); // Effacer le suivi des noms de joueurs
            joueursEnPhaseSelection.clear(); // Effacer les participants de la phase de sélection
            heureDebutPartie = 0; // Réinitialiser l'heure de début de partie
            heureDebutSelection = 0; // Réinitialiser l'heure de début de sélection
            fichierApparitionBonbonsSelectionne = null; // Effacer le fichier sélectionné
            configApparitionBonbons = null; // Effacer la configuration d'apparition
            initiateurPartie = null; // Effacer l'initiateur de partie
            enregistreurDonnees = null; // Effacer la référence du journalisateur de données
            hologrammes.clear(); // Effacer la liste de suivi des hologrammes

            // Réinitialiser le gestionnaire de spécialisation (efface les pénalités et spécialisations)
            GestionnaireSpecialisation.getInstance().reinitialiser();

            MonSubMod.JOURNALISEUR.info("Désactivation du SousMode2 terminée");
        }
    }

    /**
     * Retirer tous les hologrammes orphelins du monde (pour le nettoyage des hologrammes non suivis)
     */
    public void nettoyerHologrammesOrphelins(net.minecraft.server.level.ServerLevel niveau) {
        int hologrammesRetires = 0;

        for (net.minecraft.world.entity.Entity entite : niveau.getAllEntities()) {
            // Supprimer les armor stands hologrammes (invisibles, sans base, nom personnalisé visible)
            if (entite instanceof net.minecraft.world.entity.decoration.ArmorStand porteurArmure) {
                if (porteurArmure.isInvisible() && porteurArmure.isNoBasePlate() && porteurArmure.isCustomNameVisible()) {
                    porteurArmure.discard();
                    hologrammesRetires++;
                }
            }
        }

        if (hologrammesRetires > 0) {
            MonSubMod.JOURNALISEUR.info("Nettoyage de {} hologrammes orphelins (armor stands)", hologrammesRetires);
        }
    }


    private void initialiserPositions() {
        // Place centrale à l'origine où les joueurs apparaissent initialement
        placeCentrale = new BlockPos(0, 100, 0);

        // 4 îles positionnées autour de la place centrale, chacune à 360 blocs
        // Île nord (PETITE)
        centreIlePetite = new BlockPos(0, 100, -360);
        // Île est (MOYENNE)
        centreIleMoyenne = new BlockPos(360, 100, 0);
        // Île sud (GRANDE)
        centreIleGrande = new BlockPos(0, 100, 360);
        // Île ouest (TRÈS GRANDE)
        centreIleTresGrande = new BlockPos(-360, 100, 0);

        // Plateforme spectateur au-dessus de la place centrale
        plateformeSpectateur = new BlockPos(0, 150, 0);
    }

    private void genererCarte(ServerLevel niveau) {
        GenerateurIles generateur = new GenerateurIles();

        // Générer la place centrale (20x20)
        genererPlaceCentrale(niveau);

        // Générer 4 îles
        generateur.genererIle(niveau, centreIlePetite, TypeIle.PETITE);
        generateur.genererIle(niveau, centreIleMoyenne, TypeIle.MOYENNE);
        generateur.genererIle(niveau, centreIleGrande, TypeIle.GRANDE);
        generateur.genererIle(niveau, centreIleTresGrande, TypeIle.TRES_GRANDE);

        ilesGenerees = true; // Marquer les îles comme générées
    }

    private void genererPlaceCentrale(ServerLevel niveau) {
        MonSubMod.JOURNALISEUR.info("Génération de la place centrale à {}", placeCentrale);

        int demiTaille = 10; // Place 20x20

        // Générer le sol en verre
        for (int x = -demiTaille; x <= demiTaille; x++) {
            for (int z = -demiTaille; z <= demiTaille; z++) {
                niveau.setBlock(placeCentrale.offset(x, -1, z),
                    net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 3);
                // Vider l'air au-dessus
                for (int y = 0; y <= 5; y++) {
                    niveau.setBlock(placeCentrale.offset(x, y, z),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        // Ajouter les panneaux directionnels avec un léger délai pour s'assurer que les chunks sont chargés
        niveau.getServer().execute(() -> ajouterPanneauxDirectionnels(niveau));
    }

    private void ajouterPanneauxDirectionnels(ServerLevel niveau) {
        MonSubMod.JOURNALISEUR.info("Ajout des marqueurs directionnels sur la place centrale");

        // Placer des piliers de laine colorée comme marqueurs directionnels
        // Nord - Tour de laine blanche pour l'île PETITE (3 blocs de haut)
        BlockPos baseNord = new BlockPos(placeCentrale.getX(), placeCentrale.getY(), placeCentrale.getZ() - 8);
        for (int i = 0; i < 3; i++) {
            niveau.setBlock(baseNord.above(i), net.minecraft.world.level.block.Blocks.WHITE_WOOL.defaultBlockState(), 3);
        }
        creerHologramme(niveau, baseNord.above(3), "§f§lP E T I T E   Î L E", "§7§l6 0 x 6 0");

        // Est - Tour de laine verte pour l'île MOYENNE
        BlockPos baseEst = new BlockPos(placeCentrale.getX() + 8, placeCentrale.getY(), placeCentrale.getZ());
        for (int i = 0; i < 3; i++) {
            niveau.setBlock(baseEst.above(i), net.minecraft.world.level.block.Blocks.LIME_WOOL.defaultBlockState(), 3);
        }
        creerHologramme(niveau, baseEst.above(3), "§a§lM O Y E N N E   Î L E", "§7§l9 0 x 9 0");

        // Sud - Tour de laine bleue pour l'île GRANDE
        BlockPos baseSud = new BlockPos(placeCentrale.getX(), placeCentrale.getY(), placeCentrale.getZ() + 8);
        for (int i = 0; i < 3; i++) {
            niveau.setBlock(baseSud.above(i), net.minecraft.world.level.block.Blocks.BLUE_WOOL.defaultBlockState(), 3);
        }
        creerHologramme(niveau, baseSud.above(3), "§9§lG R A N D E   Î L E", "§7§l1 2 0 x 1 2 0");

        // Ouest - Tour de laine orange pour l'île TRÈS GRANDE
        BlockPos baseOuest = new BlockPos(placeCentrale.getX() - 8, placeCentrale.getY(), placeCentrale.getZ());
        for (int i = 0; i < 3; i++) {
            niveau.setBlock(baseOuest.above(i), net.minecraft.world.level.block.Blocks.ORANGE_WOOL.defaultBlockState(), 3);
        }
        creerHologramme(niveau, baseOuest.above(3), "§6§lT R È S   G R A N D E   Î L E", "§7§l1 5 0 x 1 5 0");

        MonSubMod.JOURNALISEUR.info("Marqueurs directionnels placés avec succès (Blanc=Petite, Vert=Moyenne, Bleu=Grande, Orange=T.Grande)");
    }

    private void creerHologramme(ServerLevel niveau, BlockPos pos, String ligne1, String ligne2) {
        // Créer le porte-armure pour la ligne 1
        net.minecraft.world.entity.decoration.ArmorStand hologramme1 = new net.minecraft.world.entity.decoration.ArmorStand(
            net.minecraft.world.entity.EntityType.ARMOR_STAND,
            niveau
        );
        hologramme1.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        hologramme1.setInvisible(true);
        hologramme1.setNoGravity(true);
        hologramme1.setCustomNameVisible(true);
        hologramme1.setCustomName(net.minecraft.network.chat.Component.literal(ligne1));
        hologramme1.setInvulnerable(true);
        hologramme1.setSilent(true);
        hologramme1.setNoBasePlate(true);
        // Ajouter une étiquette personnalisée pour identifier les hologrammes SousMode2
        hologramme1.addTag("HologrammeSousMode2");
        niveau.addFreshEntity(hologramme1);
        hologrammes.add(hologramme1); // Suivre cet hologramme

        // Créer le porte-armure pour la ligne 2 (légèrement en dessous)
        net.minecraft.world.entity.decoration.ArmorStand hologramme2 = new net.minecraft.world.entity.decoration.ArmorStand(
            net.minecraft.world.entity.EntityType.ARMOR_STAND,
            niveau
        );
        hologramme2.setPos(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5);
        hologramme2.setInvisible(true);
        hologramme2.setNoGravity(true);
        hologramme2.setCustomNameVisible(true);
        hologramme2.setCustomName(net.minecraft.network.chat.Component.literal(ligne2));
        hologramme2.setInvulnerable(true);
        hologramme2.setSilent(true);
        hologramme2.setNoBasePlate(true);
        // Ajouter une étiquette personnalisée pour identifier les hologrammes SousMode2
        hologramme2.addTag("HologrammeSousMode2");
        niveau.addFreshEntity(hologramme2);
        hologrammes.add(hologramme2); // Suivre cet hologramme
    }


    private void genererChemins(ServerLevel niveau) {
        MonSubMod.JOURNALISEUR.info("Génération des chemins de la place centrale vers les îles");

        // Chemin de la place centrale vers chaque île (360 blocs chacun)
        genererChemin(niveau, placeCentrale, centreIlePetite, 3);      // Nord
        genererChemin(niveau, placeCentrale, centreIleMoyenne, 3);     // Est
        genererChemin(niveau, placeCentrale, centreIleGrande, 3);      // Sud
        genererChemin(niveau, placeCentrale, centreIleTresGrande, 3); // Ouest
    }

    private void genererChemin(ServerLevel niveau, BlockPos debut, BlockPos fin, int largeur) {
        // Calculer la direction et la longueur du chemin
        int deltaX = fin.getX() - debut.getX();
        int deltaZ = fin.getZ() - debut.getZ();
        int longueur = Math.max(Math.abs(deltaX), Math.abs(deltaZ));

        // Créer un chemin droit
        for (int i = 0; i <= longueur; i++) {
            double progression = (double) i / longueur;
            int cheminX = (int) (debut.getX() + deltaX * progression);
            int cheminY = debut.getY();
            int cheminZ = (int) (debut.getZ() + deltaZ * progression);

            // Générer la largeur du chemin
            for (int w = -largeur/2; w <= largeur/2; w++) {
                for (int l = -largeur/2; l <= largeur/2; l++) {
                    BlockPos posChemin = new BlockPos(cheminX + w, cheminY - 1, cheminZ + l);
                    BlockPos posSurface = new BlockPos(cheminX + w, cheminY, cheminZ + l);

                    // Placer la base en pierre et la surface en briques de pierre
                    niveau.setBlock(posChemin, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                    niveau.setBlock(posSurface, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);

                    // Vider l'espace au-dessus du chemin
                    for (int h = 1; h <= 3; h++) {
                        niveau.setBlock(new BlockPos(cheminX + w, cheminY + h, cheminZ + l),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private void genererMursInvisibles(ServerLevel niveau) {
        MonSubMod.JOURNALISEUR.info("Génération des murs invisibles autour du système d'îles");

        // Générer les barrières autour de la place centrale
        genererBarrieresPlaceCentrale(niveau);

        // Générer les barrières autour de chaque île
        genererBarrieresIle(niveau, centreIlePetite, TypeIle.PETITE);
        genererBarrieresIle(niveau, centreIleMoyenne, TypeIle.MOYENNE);
        genererBarrieresIle(niveau, centreIleGrande, TypeIle.GRANDE);
        genererBarrieresIle(niveau, centreIleTresGrande, TypeIle.TRES_GRANDE);

        // Générer les barrières le long des chemins (empêcher de tomber dans l'eau)
        genererBarrieresChemin(niveau);

        // Générer des barrières supplémentaires à côté des ouvertures de chemin
        genererBarrieresOuvertureChemin(niveau);
    }

    private void genererBarrieresPlaceCentrale(ServerLevel niveau) {
        int demiTaille = 10;
        int hauteurMur = 20;

        // Murs Nord, Sud, Est, Ouest autour de la place centrale
        for (int x = -demiTaille; x <= demiTaille; x++) {
            // Mur Nord
            for (int y = 90; y <= placeCentrale.getY() + hauteurMur; y++) {
                if (!estPointConnexionChemin(placeCentrale, x, placeCentrale.getZ() - demiTaille)) {
                    niveau.setBlock(new BlockPos(placeCentrale.getX() + x, y, placeCentrale.getZ() - demiTaille),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
            // Mur Sud
            for (int y = 90; y <= placeCentrale.getY() + hauteurMur; y++) {
                if (!estPointConnexionChemin(placeCentrale, x, placeCentrale.getZ() + demiTaille)) {
                    niveau.setBlock(new BlockPos(placeCentrale.getX() + x, y, placeCentrale.getZ() + demiTaille),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }

        for (int z = -demiTaille; z <= demiTaille; z++) {
            // Mur Ouest
            for (int y = 90; y <= placeCentrale.getY() + hauteurMur; y++) {
                if (!estPointConnexionChemin(placeCentrale, placeCentrale.getX() - demiTaille, z)) {
                    niveau.setBlock(new BlockPos(placeCentrale.getX() - demiTaille, y, placeCentrale.getZ() + z),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
            // Mur Est
            for (int y = 90; y <= placeCentrale.getY() + hauteurMur; y++) {
                if (!estPointConnexionChemin(placeCentrale, placeCentrale.getX() + demiTaille, z)) {
                    niveau.setBlock(new BlockPos(placeCentrale.getX() + demiTaille, y, placeCentrale.getZ() + z),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }
    }

    private void genererBarrieresIle(ServerLevel niveau, BlockPos centre, TypeIle type) {
        int taille = type.obtenirRayon(); // Barrières au bord exact des îles carrées
        int hauteurMur = 20;

        // Générer des barrières carrées à l'intérieur du périmètre de l'île pour une étanchéité maximale
        // Murs Nord et Sud
        for (int x = centre.getX() - taille; x <= centre.getX() + taille; x++) {
            // Mur Nord (Z négatif) - bord intérieur de l'île carrée
            int nordZ = centre.getZ() - taille;
            if (!estPointConnexionChemin(centre, x, nordZ)) {
                for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                    niveau.setBlock(new BlockPos(x, y, nordZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }

            // Mur Sud (Z positif) - bord intérieur de l'île carrée
            int sudZ = centre.getZ() + taille;
            if (!estPointConnexionChemin(centre, x, sudZ)) {
                for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                    niveau.setBlock(new BlockPos(x, y, sudZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }

        // Murs Est et Ouest
        for (int z = centre.getZ() - taille; z <= centre.getZ() + taille; z++) {
            // Mur Ouest (X négatif) - bord intérieur de l'île carrée
            int ouestX = centre.getX() - taille;
            if (!estPointConnexionChemin(centre, ouestX, z)) {
                for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                    niveau.setBlock(new BlockPos(ouestX, y, z),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }

            // Mur Est (X positif) - bord intérieur de l'île carrée
            int estX = centre.getX() + taille;
            if (!estPointConnexionChemin(centre, estX, z)) {
                for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                    niveau.setBlock(new BlockPos(estX, y, z),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }
    }

    private boolean estPointConnexionChemin(BlockPos centre, int x, int z) {
        int demiLargeurChemin = 1; // Ouverture totale de 3 blocs pour correspondre à la largeur du chemin

        // Pour la place centrale - les chemins se connectent sur les 4 côtés
        if (centre.equals(placeCentrale)) {
            int demiTaille = 10;
            // Chemin Nord vers la petite île
            if (z == placeCentrale.getZ() - demiTaille) {
                return Math.abs(x - placeCentrale.getX()) <= demiLargeurChemin;
            }
            // Chemin Est vers l'île moyenne
            if (x == placeCentrale.getX() + demiTaille) {
                return Math.abs(z - placeCentrale.getZ()) <= demiLargeurChemin;
            }
            // Chemin Sud vers la grande île
            if (z == placeCentrale.getZ() + demiTaille) {
                return Math.abs(x - placeCentrale.getX()) <= demiLargeurChemin;
            }
            // Chemin Ouest vers la très grande île
            if (x == placeCentrale.getX() - demiTaille) {
                return Math.abs(z - placeCentrale.getZ()) <= demiLargeurChemin;
            }
        }

        // Pour la petite île (Nord): le chemin se connecte côté sud vers la place centrale
        if (centre.equals(centreIlePetite)) {
            if (z == centreIlePetite.getZ() + TypeIle.PETITE.obtenirRayon()) {
                return Math.abs(x - centreIlePetite.getX()) <= demiLargeurChemin;
            }
        }

        // Pour l'île moyenne (Est): le chemin se connecte côté ouest vers la place centrale
        if (centre.equals(centreIleMoyenne)) {
            if (x == centreIleMoyenne.getX() - TypeIle.MOYENNE.obtenirRayon()) {
                return Math.abs(z - centreIleMoyenne.getZ()) <= demiLargeurChemin;
            }
        }

        // Pour la grande île (Sud): le chemin se connecte côté nord vers la place centrale
        if (centre.equals(centreIleGrande)) {
            if (z == centreIleGrande.getZ() - TypeIle.GRANDE.obtenirRayon()) {
                return Math.abs(x - centreIleGrande.getX()) <= demiLargeurChemin;
            }
        }

        // Pour la très grande île (Ouest): le chemin se connecte côté est vers la place centrale
        if (centre.equals(centreIleTresGrande)) {
            if (x == centreIleTresGrande.getX() + TypeIle.TRES_GRANDE.obtenirRayon()) {
                return Math.abs(z - centreIleTresGrande.getZ()) <= demiLargeurChemin;
            }
        }

        return false;
    }

    private void genererBarrieresChemin(ServerLevel niveau) {
        // Barrières le long des chemins de la place centrale vers chaque île
        genererBarrieresLateralesChemin(niveau, placeCentrale, centreIlePetite, 3);
        genererBarrieresLateralesChemin(niveau, placeCentrale, centreIleMoyenne, 3);
        genererBarrieresLateralesChemin(niveau, placeCentrale, centreIleGrande, 3);
        genererBarrieresLateralesChemin(niveau, placeCentrale, centreIleTresGrande, 3);
    }

    private void genererBarrieresLateralesChemin(ServerLevel niveau, BlockPos debut, BlockPos fin, int largeurChemin) {
        int deltaX = fin.getX() - debut.getX();
        int deltaZ = fin.getZ() - debut.getZ();

        // Calculer la longueur et la direction réelle du chemin
        double longueurChemin = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double dirX = deltaX / longueurChemin;
        double dirZ = deltaZ / longueurChemin;

        // Perpendiculaire est une rotation de 90 degrés: (x,z) -> (-z, x)
        double perpX = -dirZ;
        double perpZ = dirX;

        int debutBarriere = largeurChemin/2 + 1; // Commencer juste après le bord du chemin
        int rangeesBarrieres = 3; // 3 rangées de barrières de chaque côté

        // Itérer sur chaque bloc le long du chemin
        int etapes = (int) Math.ceil(longueurChemin);
        for (int i = 0; i <= etapes; i++) {
            // Utiliser des nombres flottants pour une progression fluide
            double t = (etapes > 0) ? (double) i / etapes : 0;
            int cheminX = (int) Math.round(debut.getX() + deltaX * t);
            int cheminY = debut.getY();
            int cheminZ = (int) Math.round(debut.getZ() + deltaZ * t);

            // Ignorer les barrières si le point du chemin est sur une île
            if (estPointSurUneIle(cheminX, cheminZ)) {
                continue;
            }

            // Placer plusieurs rangées de barrières de chaque côté
            for (int rangee = 0; rangee < rangeesBarrieres; rangee++) {
                int decalage = debutBarriere + rangee;

                // Barrières côté gauche
                int gaucheX = (int) Math.round(cheminX + decalage * perpX);
                int gaucheZ = (int) Math.round(cheminZ + decalage * perpZ);
                for (int y = 90; y <= cheminY + 20; y++) {
                    niveau.setBlock(new BlockPos(gaucheX, y, gaucheZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }

                // Barrières côté droit
                int droiteX = (int) Math.round(cheminX - decalage * perpX);
                int droiteZ = (int) Math.round(cheminZ - decalage * perpZ);
                for (int y = 90; y <= cheminY + 20; y++) {
                    niveau.setBlock(new BlockPos(droiteX, y, droiteZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }
    }

    private boolean estPointSurUneIle(int x, int z) {
        // Vérifier si le point est dans les limites carrées d'une île ou de la place centrale
        BlockPos[] centresIles = {centreIlePetite, centreIleMoyenne, centreIleGrande, centreIleTresGrande};
        TypeIle[] typesIles = {TypeIle.PETITE, TypeIle.MOYENNE, TypeIle.GRANDE, TypeIle.TRES_GRANDE};

        // Vérifier la place centrale
        int demiTailleCentrale = 10;
        if (x >= placeCentrale.getX() - demiTailleCentrale && x <= placeCentrale.getX() + demiTailleCentrale &&
            z >= placeCentrale.getZ() - demiTailleCentrale && z <= placeCentrale.getZ() + demiTailleCentrale) {
            return true;
        }

        // Vérifier les îles
        for (int i = 0; i < centresIles.length; i++) {
            BlockPos centre = centresIles[i];
            int rayon = typesIles[i].obtenirRayon();

            if (x >= centre.getX() - rayon && x <= centre.getX() + rayon &&
                z >= centre.getZ() - rayon && z <= centre.getZ() + rayon) {
                return true;
            }
        }
        return false;
    }

    private void genererBarrieresOuvertureChemin(ServerLevel niveau) {
        // Ajouter des barrières à côté des ouvertures de chemin pour fermer les brèches
        int hauteurMur = 20;

        // Pour chaque île, ajouter des barrières sur les côtés des ouvertures de chemin
        genererBarrieresOuvertureCheminPourIle(niveau, centreIlePetite, TypeIle.PETITE, hauteurMur);
        genererBarrieresOuvertureCheminPourIle(niveau, centreIleMoyenne, TypeIle.MOYENNE, hauteurMur);
        genererBarrieresOuvertureCheminPourIle(niveau, centreIleGrande, TypeIle.GRANDE, hauteurMur);
        genererBarrieresOuvertureCheminPourIle(niveau, centreIleTresGrande, TypeIle.TRES_GRANDE, hauteurMur);
    }

    private void genererBarrieresOuvertureCheminPourIle(ServerLevel niveau, BlockPos centre, TypeIle type, int hauteurMur) {
        int taille = type.obtenirRayon();

        // Pour l'île moyenne, ajouter des barrières à côté des deux ouvertures de chemin (est et ouest)
        if (centre.equals(centreIleMoyenne)) {
            // Ouverture chemin ouest - barrières au nord et au sud de l'ouverture
            int ouestX = centre.getX() - taille;
            for (int decalageZ = 2; decalageZ <= 4; decalageZ++) { // 3 blocs au nord du chemin
                for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                    niveau.setBlock(new BlockPos(ouestX, y, centre.getZ() - decalageZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                    niveau.setBlock(new BlockPos(ouestX, y, centre.getZ() + decalageZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }

            // Ouverture chemin est - barrières au nord et au sud de l'ouverture
            int estX = centre.getX() + taille;
            for (int decalageZ = 2; decalageZ <= 4; decalageZ++) { // 3 blocs au sud du chemin
                for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                    niveau.setBlock(new BlockPos(estX, y, centre.getZ() - decalageZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                    niveau.setBlock(new BlockPos(estX, y, centre.getZ() + decalageZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }

        // Pour la petite île, ajouter des barrières à côté de l'ouverture chemin est
        if (centre.equals(centreIlePetite)) {
            int estX = centre.getX() + taille;
            for (int decalageZ = 2; decalageZ <= 4; decalageZ++) {
                for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                    niveau.setBlock(new BlockPos(estX, y, centre.getZ() - decalageZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                    niveau.setBlock(new BlockPos(estX, y, centre.getZ() + decalageZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }

        // Pour la grande île, ajouter des barrières à côté de l'ouverture chemin ouest
        if (centre.equals(centreIleGrande)) {
            int ouestX = centre.getX() - taille;
            for (int decalageZ = 2; decalageZ <= 4; decalageZ++) {
                for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                    niveau.setBlock(new BlockPos(ouestX, y, centre.getZ() - decalageZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                    niveau.setBlock(new BlockPos(ouestX, y, centre.getZ() + decalageZ),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }
    }

    private void genererPlateformeSpectateur(ServerLevel niveau) {
        // Générer une plateforme simple pour les spectateurs
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                niveau.setBlock(plateformeSpectateur.offset(x, 0, z),
                    net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 3);
            }
        }

        // Ajouter des barrières invisibles autour de la plateforme
        for (int x = -11; x <= 11; x++) {
            for (int z = -11; z <= 11; z++) {
                // Ignorer la zone intérieure de la plateforme
                if (x >= -10 && x <= 10 && z >= -10 && z <= 10) continue;

                // Placer des barrières de 1 à 3 blocs de haut autour de la plateforme
                for (int y = 1; y <= 3; y++) {
                    niveau.setBlock(plateformeSpectateur.offset(x, y, z),
                        net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }

        // Ajouter un panneau
        niveau.setBlock(plateformeSpectateur.offset(0, 2, 10),
                Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH), 3);
        if (niveau.getBlockEntity(plateformeSpectateur.offset(0, 2, 10)) instanceof SignBlockEntity panneau) {
            Component[] texte1 = new Component[]{
                    Component.literal("Cliquer sur ce"),
                    Component.literal("panneau pour aller"),
                    Component.literal("en mode"),
                    Component.literal("spectateur")
            };
            Component[] texte2 = new Component[]{
                    Component.literal("Cliquer sur ce"),
                    Component.literal("panneau pour aller"),
                    Component.literal("en mode"),
                    Component.literal("spectateur")
            };

            panneau.setText(new SignText(texte1, texte2, DyeColor.BLACK, false), true);
            panneau.setChanged();
        }
    }

    private void effacerCarte(ServerLevel niveau) {
        if (niveau == null) return;

        // Vérifier si les îles existent physiquement dans le monde (en cas de crash serveur/arrêt inattendu)
        boolean ilesExistentPhysiquement = verifierIlesExistentDansMonde(niveau);

        // Effacer seulement si les îles ont été générées OU si des îles résiduelles existent physiquement
        if (!ilesGenerees && !ilesExistentPhysiquement) {
            return;
        }

        if (ilesExistentPhysiquement && !ilesGenerees) {
            MonSubMod.JOURNALISEUR.info("Îles résiduelles d'une session précédente trouvées - nettoyage en cours");
        } else {
            MonSubMod.JOURNALISEUR.info("Effacement de la carte SousMode2 générée");
        }

        // Nettoyer également les bonbons restants
        try {
            GestionnaireBonbonsSousMode2.getInstance().retirerTousBonbonsDuMonde(niveau.getServer());
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors du nettoyage des bonbons restants pendant l'effacement de la carte", e);
        }

        // Retirer les hologrammes EN PREMIER avant d'effacer quoi que ce soit d'autre
        retirerHologrammes(niveau);

        // Effacer la place centrale
        effacerPlaceCentrale(niveau);

        // Effacer les îles d'abord (plus lent)
        GenerateurIles.effacerIle(niveau, centreIlePetite, TypeIle.PETITE);
        GenerateurIles.effacerIle(niveau, centreIleMoyenne, TypeIle.MOYENNE);
        GenerateurIles.effacerIle(niveau, centreIleGrande, TypeIle.GRANDE);
        GenerateurIles.effacerIle(niveau, centreIleTresGrande, TypeIle.TRES_GRANDE);

        // Effacer les chemins entre les îles
        effacerChemins(niveau);

        // Effacer les murs invisibles
        effacerMursInvisibles(niveau);

        // Effacer la plateforme spectateur en dernier (plus rapide)
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                for (int y = -5; y <= 10; y++) {
                    niveau.setBlock(plateformeSpectateur.offset(x, y, z),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        ilesGenerees = false; // Réinitialiser le flag après l'effacement
    }

    private void effacerPlaceCentrale(ServerLevel niveau) {
        int demiTaille = 10;
        // Effacer une zone plus large pour inclure les panneaux
        for (int x = -demiTaille - 5; x <= demiTaille + 5; x++) {
            for (int z = -demiTaille - 5; z <= demiTaille + 5; z++) {
                for (int y = -5; y <= 10; y++) {
                    niveau.setBlock(placeCentrale.offset(x, y, z),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private boolean verifierIlesExistentDansMonde(ServerLevel niveau) {
        // Vérifier si des zones d'îles ont des blocs caractéristiques indiquant qu'une île a été générée
        // On vérifie les blocs solides (pas de l'air) dans une petite zone autour de chaque centre d'île

        try {
            // Initialiser les positions si pas encore fait
            if (centreIlePetite == null) {
                initialiserPositions();
            }

            // Vérifier les blocs solides dans une zone 3x3 autour de chaque centre d'île
            boolean petiteExiste = verifierZoneIle(niveau, centreIlePetite);
            boolean moyenneExiste = verifierZoneIle(niveau, centreIleMoyenne);
            boolean grandeExiste = verifierZoneIle(niveau, centreIleGrande);

            // Si une zone d'île a des blocs solides, on considère que les îles existent
            boolean ilesExistent = petiteExiste || moyenneExiste || grandeExiste;

            if (ilesExistent) {
                MonSubMod.JOURNALISEUR.info("Îles physiques détectées dans le monde - Petite: {}, Moyenne: {}, Grande: {}",
                    petiteExiste, moyenneExiste, grandeExiste);
            }

            return ilesExistent;
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors de la vérification des îles physiques dans le monde", e);
            return false; // Supposer qu'il n'y a pas d'îles si on ne peut pas vérifier
        }
    }

    private boolean verifierZoneIle(ServerLevel niveau, BlockPos centre) {
        // Vérifier une zone 3x3x3 autour du centre de l'île pour trouver des blocs solides
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos posVerif = centre.offset(x, y, z);
                    net.minecraft.world.level.block.state.BlockState etatBloc = niveau.getBlockState(posVerif);

                    // Si on trouve un bloc solide (pas de l'air), l'île existe probablement
                    if (!etatBloc.isAir()) {
                        MonSubMod.JOURNALISEUR.debug("Bloc solide trouvé à {} près du centre de l'île {}: {}",
                            posVerif, centre, etatBloc.getBlock().getName().getString());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void effacerChemins(ServerLevel niveau) {
        // Effacer les chemins de la place centrale vers chaque île
        effacerChemin(niveau, placeCentrale, centreIlePetite, 3);
        effacerChemin(niveau, placeCentrale, centreIleMoyenne, 3);
        effacerChemin(niveau, placeCentrale, centreIleGrande, 3);
        effacerChemin(niveau, placeCentrale, centreIleTresGrande, 3);
    }

    private void effacerChemin(ServerLevel niveau, BlockPos debut, BlockPos fin, int largeur) {
        int deltaX = fin.getX() - debut.getX();
        int deltaZ = fin.getZ() - debut.getZ();
        int longueur = Math.max(Math.abs(deltaX), Math.abs(deltaZ));

        for (int i = 0; i <= longueur; i++) {
            double progression = (double) i / longueur;
            int cheminX = (int) (debut.getX() + deltaX * progression);
            int cheminY = debut.getY();
            int cheminZ = (int) (debut.getZ() + deltaZ * progression);

            for (int w = -largeur/2; w <= largeur/2; w++) {
                for (int l = -largeur/2; l <= largeur/2; l++) {
                    for (int h = -2; h <= 5; h++) {
                        // Utiliser le flag 2 (pas de mises à jour de blocs) pour éviter les mises à jour massives de redstone/chunks qui causent du lag
                        niveau.setBlock(new BlockPos(cheminX + w, cheminY + h, cheminZ + l),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private void effacerMursInvisibles(ServerLevel niveau) {
        MonSubMod.JOURNALISEUR.info("Effacement des murs invisibles (barrières)");

        // Effacer les barrières autour de la place centrale
        effacerBarrieresPlaceCentrale(niveau);

        // Effacer les barrières autour de chaque île
        effacerBarrieresIle(niveau, centreIlePetite, TypeIle.PETITE);
        effacerBarrieresIle(niveau, centreIleMoyenne, TypeIle.MOYENNE);
        effacerBarrieresIle(niveau, centreIleGrande, TypeIle.GRANDE);
        effacerBarrieresIle(niveau, centreIleTresGrande, TypeIle.TRES_GRANDE);

        // Effacer les barrières de chemin
        effacerBarrieresChemin(niveau);
    }

    private void effacerBarrieresPlaceCentrale(ServerLevel niveau) {
        int demiTaille = 10;
        int hauteurMur = 20;

        // Effacer les barrières sur tous les côtés de la place centrale
        for (int x = -demiTaille; x <= demiTaille; x++) {
            // Mur Nord
            for (int y = 90; y <= placeCentrale.getY() + hauteurMur; y++) {
                niveau.setBlock(new BlockPos(placeCentrale.getX() + x, y, placeCentrale.getZ() - demiTaille),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            // Mur Sud
            for (int y = 90; y <= placeCentrale.getY() + hauteurMur; y++) {
                niveau.setBlock(new BlockPos(placeCentrale.getX() + x, y, placeCentrale.getZ() + demiTaille),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }

        for (int z = -demiTaille; z <= demiTaille; z++) {
            // Mur Ouest
            for (int y = 90; y <= placeCentrale.getY() + hauteurMur; y++) {
                niveau.setBlock(new BlockPos(placeCentrale.getX() - demiTaille, y, placeCentrale.getZ() + z),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            // Mur Est
            for (int y = 90; y <= placeCentrale.getY() + hauteurMur; y++) {
                niveau.setBlock(new BlockPos(placeCentrale.getX() + demiTaille, y, placeCentrale.getZ() + z),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private void effacerBarrieresIle(ServerLevel niveau, BlockPos centre, TypeIle type) {
        int rayon = type.obtenirRayon() - 2; // Même rayon plus serré que la génération
        int hauteurMur = 20;

        // Effacer les barrières à l'intérieur du périmètre de l'île (mêmes positions que la génération)
        // Murs Nord et Sud
        for (int x = centre.getX() - rayon; x <= centre.getX() + rayon; x++) {
            // Mur Nord
            int nordZ = centre.getZ() - rayon;
            for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                niveau.setBlock(new BlockPos(x, y, nordZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }

            // Mur Sud
            int sudZ = centre.getZ() + rayon;
            for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                niveau.setBlock(new BlockPos(x, y, sudZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }

        // Murs Est et Ouest
        for (int z = centre.getZ() - rayon; z <= centre.getZ() + rayon; z++) {
            // Mur Ouest
            int ouestX = centre.getX() - rayon;
            for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                niveau.setBlock(new BlockPos(ouestX, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }

            // Mur Est
            int estX = centre.getX() + rayon;
            for (int y = 90; y <= centre.getY() + hauteurMur; y++) {
                niveau.setBlock(new BlockPos(estX, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private void effacerBarrieresChemin(ServerLevel niveau) {
        effacerBarrieresLateralesChemin(niveau, placeCentrale, centreIlePetite, 3);
        effacerBarrieresLateralesChemin(niveau, placeCentrale, centreIleMoyenne, 3);
        effacerBarrieresLateralesChemin(niveau, placeCentrale, centreIleGrande, 3);
        effacerBarrieresLateralesChemin(niveau, placeCentrale, centreIleTresGrande, 3);
    }

    private void effacerBarrieresLateralesChemin(ServerLevel niveau, BlockPos debut, BlockPos fin, int largeurChemin) {
        int deltaX = fin.getX() - debut.getX();
        int deltaZ = fin.getZ() - debut.getZ();

        // Calculer la longueur et la direction réelle du chemin
        double longueurChemin = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double dirX = deltaX / longueurChemin;
        double dirZ = deltaZ / longueurChemin;

        // Perpendiculaire est une rotation de 90 degrés: (x,z) -> (-z, x)
        double perpX = -dirZ;
        double perpZ = dirX;

        int debutBarriere = largeurChemin/2 + 1;
        int rangeesBarrieres = 3; // Correspondre à la génération - 3 rangées de chaque côté

        // Itérer sur chaque bloc le long du chemin
        int etapes = (int) Math.ceil(longueurChemin);
        for (int i = 0; i <= etapes; i++) {
            // Utiliser des nombres flottants pour une progression fluide
            double t = (etapes > 0) ? (double) i / etapes : 0;
            int cheminX = (int) Math.round(debut.getX() + deltaX * t);
            int cheminY = debut.getY();
            int cheminZ = (int) Math.round(debut.getZ() + deltaZ * t);

            // Ignorer l'effacement si le point du chemin était sur une île (pas de barrières placées là)
            if (estPointSurUneIle(cheminX, cheminZ)) {
                continue;
            }

            // Effacer plusieurs rangées de barrières de chaque côté
            for (int rangee = 0; rangee < rangeesBarrieres; rangee++) {
                int decalage = debutBarriere + rangee;

                // Effacer les barrières côté gauche
                int gaucheX = (int) Math.round(cheminX + decalage * perpX);
                int gaucheZ = (int) Math.round(cheminZ + decalage * perpZ);
                for (int y = 90; y <= cheminY + 20; y++) {
                    // Utiliser le flag 2 (pas de mises à jour de blocs) pour éviter les mises à jour massives de redstone/chunks qui causent du lag
                    niveau.setBlock(new BlockPos(gaucheX, y, gaucheZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }

                // Effacer les barrières côté droit
                int droiteX = (int) Math.round(cheminX - decalage * perpX);
                int droiteZ = (int) Math.round(cheminZ - decalage * perpZ);
                for (int y = 90; y <= cheminY + 20; y++) {
                    // Utiliser le flag 2 (pas de mises à jour de blocs) pour éviter les mises à jour massives de redstone/chunks qui causent du lag
                    niveau.setBlock(new BlockPos(droiteX, y, droiteZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private void teleporterAdminsVersSpectateur(MinecraftServer serveur) {
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                teleporterVersSpectateur(joueur);
            }
        }
    }

    private void teleporterTousJoueursVersIlePetite(MinecraftServer serveur) {
        ServerLevel mondePrincipal = serveur.getLevel(ServerLevel.OVERWORLD);
        if (mondePrincipal == null) return;

        // Suivre tous les joueurs non-admin participant (même s'ils se déconnectent plus tard)
        joueursEnPhaseSelection.clear();

        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            if (!GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                // Vérifier si le joueur protégé est authentifié
                com.example.mysubmod.authentification.GestionnaireAuth gestionnaireAuth = com.example.mysubmod.authentification.GestionnaireAuth.getInstance();
                com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(joueur.getName().getString());
                boolean estJoueurProtege = (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE);
                boolean estAuthentifie = gestionnaireAuth.estAuthentifie(joueur.getUUID());

                if (estJoueurProtege && !estAuthentifie) {
                    // Joueur protégé non authentifié - envoyer en spectateur
                    MonSubMod.JOURNALISEUR.info("Joueur protégé {} non authentifié - envoi en spectateur", joueur.getName().getString());
                    teleporterVersSpectateur(joueur);
                    joueur.sendSystemMessage(Component.literal("§c§lVous devez être authentifié pour participer au jeu\n\n§7Veuillez vous authentifier et rejoindre le serveur."));
                    continue;
                }

                // Ajouter au suivi de la phase de sélection
                joueursEnPhaseSelection.add(joueur.getUUID());

                // Stocker et vider l'inventaire
                stockerInventaireJoueur(joueur);
                viderInventaireJoueur(joueur);

                // Téléporter temporairement vers la place centrale (ils choisiront leur île finale)
                BlockPos posSpawn = placeCentrale;
                teleportationSecurisee(joueur, mondePrincipal, posSpawn.getX() + 0.5, posSpawn.getY() + 1, posSpawn.getZ() + 0.5);
                joueursVivants.add(joueur.getUUID());
                nomsJoueurs.put(joueur.getUUID(), joueur.getName().getString());
            }
        }
    }

    public void demarrerSelectionIle(MinecraftServer serveur) {
        phaseSelectionFichier = false; // Sélection de fichier terminée
        phaseSelection = true;
        heureDebutSelection = System.currentTimeMillis(); // Enregistrer quand la sélection a commencé

        // Initialiser l'enregistreur de données MAINTENANT (quand le fichier est sélectionné et que le jeu commence vraiment)
        if (enregistreurDonnees == null) {
            enregistreurDonnees = new EnregistreurDonneesSousMode2();
            enregistreurDonnees.demarrerNouvellePartie();
            MonSubMod.JOURNALISEUR.info("Journalisation des données démarrée pour la partie avec le fichier: {}", fichierApparitionBonbonsSelectionne);

            // Traiter les événements en attente qui se sont produits avant la création de l'enregistreur
            if (!evenementsJournalisationEnAttente.isEmpty()) {
                MonSubMod.JOURNALISEUR.info("Traitement de {} événements en attente rétroactivement", evenementsJournalisationEnAttente.size());
                for (EvenementJournalisationEnAttente evenement : evenementsJournalisationEnAttente) {
                    enregistreurDonnees.enregistrerActionJoueur(evenement.joueur, evenement.action);
                }
                evenementsJournalisationEnAttente.clear();
            }
        }

        // Forcer le chargement de tous les chunks des îles et retirer les pissenlits une fois
        ServerLevel mondePrincipal = serveur.getLevel(ServerLevel.OVERWORLD);
        if (mondePrincipal != null) {
            forcerChargementChunksIlesPourSelection(mondePrincipal);
            // Attendre quelques ticks pour que les chunks se chargent, puis retirer les pissenlits
            serveur.tell(new net.minecraft.server.TickTask(serveur.getTickCount() + 5, () -> {
                retirerItemsPissenlit(mondePrincipal);
            }));
        }

        // Ajouter les joueurs non-admin nouvellement connectés (ne pas effacer - garder les joueurs de la phase de sélection de fichier)
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            if (!GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                joueursEnPhaseSelection.add(joueur.getUUID());
            }
        }

        // Envoyer l'interface de sélection d'île à tous les joueurs non-admin
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            if (!GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                stockerInventaireJoueur(joueur);
                viderInventaireJoueur(joueur);
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new PaquetSelectionIle(TEMPS_SELECTION_SECONDES));
            }
        }

        // Démarrer le minuteur de 30 secondes
        minuteurSelection = new Timer();
        minuteurSelection.schedule(new TimerTask() {
            @Override
            public void run() {
                terminerPhaseSelection(serveur);
            }
        }, TEMPS_SELECTION_SECONDES * 1000);

        // Diffuser le compte à rebours
        diffuserMessage(serveur, "§eChoisissez votre île ! Temps restant: " + TEMPS_SELECTION_SECONDES + " secondes");
    }

    public void selectionnerIle(ServerPlayer joueur, TypeIle ile) {
        if (!phaseSelection) return;

        // Ignorer les comptes candidats file temporaires
        if (estJoueurRestreintLocal(joueur)) {
            return;
        }

        selectionsIleJoueurs.put(joueur.getUUID(), ile);
        selectionManuelleIleJoueur.put(joueur.getUUID(), true); // Sélection manuelle
        joueur.sendSystemMessage(Component.literal("§aVous avez sélectionné: " + ile.obtenirNomAffichage()));

        // Journaliser la sélection d'île
        if (enregistreurDonnees != null) {
            enregistreurDonnees.enregistrerSelectionIle(joueur, ile);
            selectionIleJoueurJournalisee.add(joueur.getUUID());
        }
    }

    private void terminerPhaseSelection(MinecraftServer serveur) {
        phaseSelection = false;

        // Assigner des îles aléatoires aux joueurs qui n'ont pas sélectionné (y compris les joueurs déconnectés)
        TypeIle[] iles = TypeIle.values();
        Random aleatoire = new Random();

        // Traiter TOUS les joueurs qui étaient en phase de sélection (connectés ou déconnectés)
        for (UUID idJoueur : joueursEnPhaseSelection) {
            if (!selectionsIleJoueurs.containsKey(idJoueur)) {
                TypeIle ileAleatoire = iles[aleatoire.nextInt(iles.length)];
                selectionsIleJoueurs.put(idJoueur, ileAleatoire);
                selectionManuelleIleJoueur.put(idJoueur, false); // Assignation automatique

                // Les marquer comme vivants pour qu'ils soient gérés correctement à la reconnexion
                joueursVivants.add(idJoueur);

                // Notifier si le joueur est actuellement connecté et stocker son nom
                ServerPlayer joueur = serveur.getPlayerList().getPlayer(idJoueur);
                if (joueur != null) {
                    nomsJoueurs.put(idJoueur, joueur.getName().getString());
                    joueur.sendSystemMessage(Component.literal("§eÎle assignée automatiquement: " + ileAleatoire.obtenirNomAffichage()));
                } else {
                    MonSubMod.JOURNALISEUR.info("Île aléatoire {} assignée au joueur déconnecté {} et marqué comme vivant",
                        ileAleatoire.name(), idJoueur);
                }

                // Journaliser l'assignation automatique d'île
                if (enregistreurDonnees != null && joueur != null) {
                    enregistreurDonnees.enregistrerSelectionIle(joueur, ileAleatoire, "AUTOMATIQUE");
                    selectionIleJoueurJournalisee.add(idJoueur);
                }
            }
        }

        // Téléporter les joueurs vers leurs îles
        teleporterJoueursVersIles(serveur);

        // Démarrer la partie
        demarrerPartie(serveur);
    }

    private void teleporterJoueursVersIles(MinecraftServer serveur) {
        ServerLevel surMonde = serveur.getLevel(ServerLevel.OVERWORLD);
        if (surMonde == null) return;

        // Retirer les pissenlits une dernière fois avant la téléportation (ils peuvent avoir réapparu pendant la sélection)
        retirerItemsPissenlit(surMonde);

        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) continue;

            // Vérifier si le joueur protégé est authentifié
            com.example.mysubmod.authentification.GestionnaireAuth gestionnaireAuth = com.example.mysubmod.authentification.GestionnaireAuth.getInstance();
            com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(joueur.getName().getString());
            boolean estJoueurProtege = (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE);
            boolean estAuthentifie = gestionnaireAuth.estAuthentifie(joueur.getUUID());

            if (estJoueurProtege && !estAuthentifie) {
                // Joueur protégé a perdu l'authentification - envoyer en spectateur
                MonSubMod.JOURNALISEUR.warn("Joueur protégé {} a perdu l'authentification - envoi en spectateur", joueur.getName().getString());
                teleporterVersSpectateur(joueur);
                joueur.sendSystemMessage(Component.literal("§c§lAuthentification perdue\n\n§7Vous avez été placé en spectateur."));
                continue;
            }

            TypeIle ileSelectionnee = selectionsIleJoueurs.get(joueur.getUUID());
            if (ileSelectionnee != null) {
                BlockPos posSpawn = obtenirPositionSpawnIle(ileSelectionnee);
                teleportationSecurisee(joueur, surMonde, posSpawn.getX() + 0.5, posSpawn.getY() + 1, posSpawn.getZ() + 0.5);
                joueursVivants.add(joueur.getUUID());
                nomsJoueurs.put(joueur.getUUID(), joueur.getName().getString());
            }
        }
    }

    private BlockPos obtenirPositionSpawnIle(TypeIle ile) {
        switch (ile) {
            case PETITE: return centreIlePetite;
            case MOYENNE: return centreIleMoyenne;
            case GRANDE: return centreIleGrande;
            case TRES_GRANDE: return centreIleTresGrande;
            default: return placeCentrale;
        }
    }

    private void demarrerPartie(MinecraftServer serveur) {
        partieActive = true;
        heureDebutPartie = System.currentTimeMillis(); // Enregistrer l'heure de début pour le classement

        // Nettoyage final: Retirer tous les bonbons une dernière fois avant de démarrer
        try {
            GestionnaireBonbonsSousMode2.getInstance().retirerTousBonbonsDuMonde(serveur);
            MonSubMod.JOURNALISEUR.info("Nettoyage final de tous les bonbons avant le démarrage de la partie");
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors du nettoyage final des bonbons avant le démarrage", e);
        }

        // Réinitialiser la santé de tous les joueurs à 100%
        reinitialiserSanteTousJoueurs(serveur);

        // Démarrer le minuteur de partie
        minuteurPartie = new MinuterieJeu(TEMPS_PARTIE_MINUTES, serveur);
        minuteurPartie.demarrer();

        // Démarrer la dégradation de santé
        GestionnaireSanteSousMode2.getInstance().demarrerDegradationSante(serveur);

        // Démarrer l'apparition des bonbons
        GestionnaireBonbonsSousMode2.getInstance().demarrerApparitionBonbons(serveur);

        diffuserMessage(serveur, "§aLa partie commence ! Survivez " + TEMPS_PARTIE_MINUTES + " minutes !");
    }


    private void retirerItemsPissenlit(net.minecraft.server.level.ServerLevel niveau) {
        int nombreRetires = 0;
        int totalPissenlits = 0;

        for (net.minecraft.world.entity.Entity entite : niveau.getAllEntities()) {
            if (entite instanceof net.minecraft.world.entity.item.ItemEntity itemEntite) {
                if (itemEntite.getItem().getItem() == net.minecraft.world.item.Items.DANDELION) {
                    totalPissenlits++;
                    // Retirer seulement si proche d'une île ou d'un chemin
                    BlockPos posItem = itemEntite.blockPosition();
                    if (estProcheIleOuChemin(posItem)) {
                        itemEntite.discard();
                        nombreRetires++;
                    }
                }
            }
        }

        MonSubMod.JOURNALISEUR.info("Nettoyage des pissenlits: {} trouvés au total, {} retirés des îles/chemins", totalPissenlits, nombreRetires);
    }

    public boolean estProcheIleOuChemin(BlockPos pos) {
        // Vérifier si dans les limites carrées de la petite île (60x60, donc demi-taille = 30, ajouter marge = 5)
        if (estDansLimitesCarrees(pos, centreIlePetite, 30 + 5)) {
            return true;
        }

        // Vérifier si dans les limites carrées de l'île moyenne (90x90, donc demi-taille = 45, ajouter marge = 5)
        if (estDansLimitesCarrees(pos, centreIleMoyenne, 45 + 5)) {
            return true;
        }

        // Vérifier si dans les limites carrées de la grande île (120x120, donc demi-taille = 60, ajouter marge = 5)
        if (estDansLimitesCarrees(pos, centreIleGrande, 60 + 5)) {
            return true;
        }

        // Vérifier si dans les limites carrées de l'île très grande (150x150, donc demi-taille = 75, ajouter marge = 5)
        if (estDansLimitesCarrees(pos, centreIleTresGrande, 75 + 5)) {
            return true;
        }

        // Vérifier si dans les limites carrées de la place centrale (20x20, donc demi-taille = 10, ajouter marge = 5)
        if (estDansLimitesCarrees(pos, placeCentrale, 10 + 5)) {
            return true;
        }

        // Vérifier si sur un chemin entre le centre et les îles
        // Les chemins vont du centre (0,0) vers le centre de chaque île
        if (estSurChemin(pos, placeCentrale, centreIlePetite, 5)) return true;
        if (estSurChemin(pos, placeCentrale, centreIleMoyenne, 5)) return true;
        if (estSurChemin(pos, placeCentrale, centreIleGrande, 5)) return true;
        if (estSurChemin(pos, placeCentrale, centreIleTresGrande, 5)) return true;

        return false;
    }

    /**
     * Vérifier si la position est dans les limites carrées (pas circulaires)
     * Cela couvre correctement tous les coins des îles carrées
     */
    private boolean estDansLimitesCarrees(BlockPos pos, BlockPos centre, int demiTaille) {
        if (centre == null) return false;

        int distX = Math.abs(pos.getX() - centre.getX());
        int distZ = Math.abs(pos.getZ() - centre.getZ());

        return distX <= demiTaille && distZ <= demiTaille;
    }

    private boolean estSurChemin(BlockPos pos, BlockPos debut, BlockPos fin, int largeurChemin) {
        if (debut == null || fin == null) return false;

        // Vérifier si la position est proche de la ligne entre début et fin
        // En utilisant la formule de distance d'un point à une ligne
        double longueurLigne = Math.sqrt(Math.pow(fin.getX() - debut.getX(), 2) +
                                     Math.pow(fin.getZ() - debut.getZ(), 2));
        if (longueurLigne == 0) return false;

        // Calculer la distance perpendiculaire de pos à la ligne
        double distance = Math.abs(
            (fin.getZ() - debut.getZ()) * pos.getX() -
            (fin.getX() - debut.getX()) * pos.getZ() +
            fin.getX() * debut.getZ() -
            fin.getZ() * debut.getX()
        ) / longueurLigne;

        // Vérifier si dans la largeur du chemin et entre les points de début et de fin
        if (distance <= largeurChemin) {
            int minX = Math.min(debut.getX(), fin.getX()) - largeurChemin;
            int maxX = Math.max(debut.getX(), fin.getX()) + largeurChemin;
            int minZ = Math.min(debut.getZ(), fin.getZ()) - largeurChemin;
            int maxZ = Math.max(debut.getZ(), fin.getZ()) + largeurChemin;

            return pos.getX() >= minX && pos.getX() <= maxX &&
                   pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        return false;
    }

    private boolean estDansRayon(BlockPos pos1, BlockPos pos2, int rayon) {
        if (pos2 == null) return false;
        double distance = Math.sqrt(Math.pow(pos1.getX() - pos2.getX(), 2) +
                                  Math.pow(pos1.getZ() - pos2.getZ(), 2));
        return distance <= rayon;
    }

    private void forcerChargementChunksIlesPourSelection(ServerLevel niveau) {
        // Forcer le chargement des chunks pour toutes les îles pour s'assurer que les pissenlits peuvent être détectés
        forcerChargementChunkA(niveau, centreIlePetite);
        forcerChargementChunkA(niveau, centreIleMoyenne);
        forcerChargementChunkA(niveau, centreIleGrande);
        forcerChargementChunkA(niveau, centreIleTresGrande);
        forcerChargementChunkA(niveau, placeCentrale);
        MonSubMod.JOURNALISEUR.info("Chunks des îles chargés de force pour le nettoyage des pissenlits");
    }

    private void forcerChargementChunkA(ServerLevel niveau, BlockPos pos) {
        if (pos != null) {
            niveau.getChunkAt(pos);
        }
    }

    private void retirerHologrammes(net.minecraft.server.level.ServerLevel niveau) {
        int hologrammesRetires = 0;

        // Premièrement, retirer les hologrammes suivis directement
        for (net.minecraft.world.entity.decoration.ArmorStand hologramme : hologrammes) {
            if (hologramme != null && hologramme.isAlive()) {
                hologramme.discard();
                hologrammesRetires++;
            }
        }
        hologrammes.clear(); // Vider la liste de suivi

        // Deuxièmement, rechercher les hologrammes orphelins avec notre tag (nettoyage de secours)
        // Cela attrape les hologrammes qui n'étaient pas dans la liste de suivi
        int orphelinsRetires = 0;
        for (net.minecraft.world.entity.Entity entite : niveau.getAllEntities()) {
            if (entite instanceof net.minecraft.world.entity.decoration.ArmorStand supportArmure) {
                if (supportArmure.getTags().contains("HologrammeSousMode2") && supportArmure.isAlive()) {
                    supportArmure.discard();
                    orphelinsRetires++;
                }
            }
        }

        if (hologrammesRetires > 0 || orphelinsRetires > 0) {
            MonSubMod.JOURNALISEUR.info("{} hologrammes suivis + {} orphelins retirés du monde",
                hologrammesRetires, orphelinsRetires);
        }
    }

    private void retirerToutesFleursDansZone(net.minecraft.server.level.ServerLevel niveau, BlockPos centre, int rayon) {
        if (centre == null) return;

        int pissenlitsRetires = 0;
        for (int x = centre.getX() - rayon; x <= centre.getX() + rayon; x++) {
            for (int z = centre.getZ() - rayon; z <= centre.getZ() + rayon; z++) {
                for (int y = centre.getY() - 5; y < centre.getY() + 10; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    net.minecraft.world.level.block.state.BlockState etat = niveau.getBlockState(pos);

                    // Retirer SEULEMENT les blocs de pissenlit, pas les autres fleurs
                    if (etat.is(net.minecraft.world.level.block.Blocks.DANDELION)) {
                        // Utiliser le flag 2 (pas de mises à jour de bloc) pour éviter les mises à jour massives de redstone/chunk qui causent du lag
                        niveau.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                        pissenlitsRetires++;
                    }
                }
            }
        }

        if (pissenlitsRetires > 0) {
            MonSubMod.JOURNALISEUR.info("{} pissenlits retirés de l'île à {}", pissenlitsRetires, centre);
        }
    }

    public void terminerPartie(MinecraftServer serveur) {
        // Empêcher les appels multiples
        if (finPartieEnCours) {
            return;
        }
        finPartieEnCours = true;
        partieActive = false;

        MonSubMod.JOURNALISEUR.info("Fin de la partie SousMode2");

        // Notifier les joueurs authentifiés que la partie est terminée
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur), new PaquetFinPartie());
        }

        // Arrêter les systèmes immédiatement pour empêcher d'autres opérations
        try {
            GestionnaireSanteSousMode2.getInstance().arreterDegradationSante();
            // Séquence de nettoyage des bonbons: arrêter le minuteur -> nettoyer les bonbons
            // Note: On garde les chunks chargés de force pour éviter les crashes lors du déchargement
            GestionnaireBonbonsSousMode2.getInstance().arreterMinuterie(); // Arrêter les apparitions futures
            GestionnaireBonbonsSousMode2.getInstance().retirerTousBonbonsDuMonde(serveur); // Nettoyer pendant que les chunks sont chargés

            // Arrêter le minuteur de partie pour empêcher les appels en double
            if (minuteurPartie != null) {
                minuteurPartie.arreter();
            }

            // Réinitialiser le gestionnaire de spécialisation (sera réinitialisé à nouveau dans deactivate, mais sûr d'appeler deux fois)
            GestionnaireSpecialisation.getInstance().reinitialiser();
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors de l'arrêt des systèmes de jeu pendant terminerPartie", e);
        }

        // Afficher le classement
        afficherClassement(serveur);

        // Afficher les félicitations
        afficherFelicitations(serveur);

        // Changer vers la salle d'attente et désactiver après un court délai
        Timer minuteurDelai = new Timer("SousMode2-FinPartie-minuterie");
        minuteurDelai.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    serveur.execute(() -> {
                        try {
                            // Changer vers la salle d'attente d'abord (cela appellera deactivate automatiquement)
                            GestionnaireSousModes.getInstance().changerSousMode(com.example.mysubmod.sousmodes.SousMode.SALLE_ATTENTE, null, serveur);
                        } catch (Exception e) {
                            MonSubMod.JOURNALISEUR.error("Erreur lors du changement vers la salle d'attente pendant la fin de partie", e);
                        } finally {
                            minuteurDelai.cancel(); // Nettoyer le minuteur
                        }
                    });
                } catch (Exception e) {
                    MonSubMod.JOURNALISEUR.error("Erreur lors de la planification du changement de mode", e);
                    minuteurDelai.cancel();
                }
            }
        }, 5000); // 5 secondes de délai
    }

    private void afficherFelicitations(MinecraftServer serveur) {
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            joueur.sendSystemMessage(Component.literal("§6§l=== FÉLICITATIONS ==="));
            joueur.sendSystemMessage(Component.literal("§eMerci d'avoir participé à cette expérience !"));
            joueur.sendSystemMessage(Component.literal("§aRetour à la salle d'attente dans 5 secondes..."));
        }
    }

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
            restaurerInventaireJoueur(joueur);
        }
    }

    private void restaurerInventaireJoueur(ServerPlayer joueur) {
        // D'abord, vider l'inventaire actuel pour retirer les bonbons collectés pendant la partie
        joueur.getInventory().clearContent();

        // Ensuite, restaurer l'inventaire sauvegardé d'avant la partie
        List<ItemStack> inventaire = inventairesStockes.remove(joueur.getUUID());
        if (inventaire != null) {
            for (int i = 0; i < Math.min(inventaire.size(), joueur.getInventory().getContainerSize()); i++) {
                joueur.getInventory().setItem(i, inventaire.get(i));
            }
        }
    }

    public void teleporterVersSpectateur(ServerPlayer joueur) {
        ServerLevel mondePrincipal = joueur.server.getLevel(ServerLevel.OVERWORLD);
        if (mondePrincipal != null) {
            teleportationSecurisee(joueur, mondePrincipal,
                plateformeSpectateur.getX() + 0.5,
                plateformeSpectateur.getY() + 1,
                plateformeSpectateur.getZ() + 0.5);
            joueur.setGameMode(GameType.SURVIVAL);
            joueursSpectateurs.add(joueur.getUUID());
            joueursVivants.remove(joueur.getUUID());
        }
    }

    private void teleportationSecurisee(ServerPlayer joueur, ServerLevel niveau, double x, double y, double z) {
        // Forcer le chargement du chunk à la destination avant la téléportation
        BlockPos destination = new BlockPos((int)x, (int)y, (int)z);
        niveau.getChunkAt(destination); // Forcer le chargement du chunk

        // Forcer la synchronisation de la position du joueur pour éviter "Invalid move player packet received"
        joueur.connection.resetPosition();

        // Définir la position du joueur directement en premier
        joueur.moveTo(x, y, z, 0.0f, 0.0f);

        // Puis utiliser teleportTo pour une synchronisation correcte du monde
        joueur.teleportTo(niveau, x, y, z, 0.0f, 0.0f);

        // Forcer la mise à jour de la position du joueur sur le client avec le drapeau relatif effacé
        joueur.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket(
            x, y, z, 0.0f, 0.0f, java.util.Collections.emptySet(), 0));
    }

    private void arreterMinuterieSelection() {
        if (minuteurSelection != null) {
            minuteurSelection.cancel();
            minuteurSelection = null;
        }
    }

    private void diffuserMessage(MinecraftServer serveur, String message) {
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            joueur.sendSystemMessage(Component.literal(message));
        }
    }

    private void reinitialiserSanteTousJoueurs(MinecraftServer serveur) {
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            // Réinitialiser la santé au maximum (20.0f = 10 cœurs)
            joueur.setHealth(joueur.getMaxHealth());

            // Définir la faim à 50% (10 sur 20)
            joueur.getFoodData().setFoodLevel(10);
            joueur.getFoodData().setSaturation(5.0f);
        }

        MonSubMod.JOURNALISEUR.info("Santé réinitialisée à 100% et faim à 50% pour tous les joueurs au démarrage du jeu");
    }

    // Gestion du fichier d'apparition de bonbons
    private void afficherSelectionFichierBonbons(ServerPlayer joueur) {
        List<String> fichiersDisponibles = GestionnaireFichiersApparitionBonbons.getInstance().obtenirFichiersDisponibles();

        if (fichiersDisponibles.isEmpty()) {
            joueur.sendSystemMessage(Component.literal("§cAucun fichier de spawn de bonbons trouvé. Utilisation de la configuration par défaut."));
            definirFichierApparitionBonbons(null);
            demarrerSelectionIle(joueur.getServer());
            return;
        }

        // Envoyer la liste des fichiers sans ouvrir l'écran automatiquement (ouvrirEcran = false)
        GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
            new PaquetListeFichiersBonbons(fichiersDisponibles, false));

        joueur.sendSystemMessage(Component.literal("§eAppuyez sur N pour ouvrir le menu de sélection de fichier"));
    }

    public void definirFichierApparitionBonbons(String nomFichier) {
        this.fichierApparitionBonbonsSelectionne = nomFichier;

        if (nomFichier != null) {
            this.configApparitionBonbons = GestionnaireFichiersApparitionBonbons.getInstance().chargerConfigApparition(nomFichier);
            MonSubMod.JOURNALISEUR.info("Fichier de spawn bonbons sélectionné: {} avec {} entrées", nomFichier, configApparitionBonbons.size());

            // Notifier l'initiateur de partie (mais NE PAS démarrer la sélection d'île - l'appelant le fera)
            if (initiateurPartie != null) {
                initiateurPartie.sendSystemMessage(Component.literal("§aFichier de spawn sélectionné: " + nomFichier));
            }
        } else {
            this.configApparitionBonbons = new ArrayList<>();
            MonSubMod.JOURNALISEUR.warn("Aucun fichier de spawn bonbons sélectionné, utilisation d'une configuration vide");
        }
    }

    public List<EntreeApparitionBonbon> obtenirConfigApparitionBonbons() {
        return configApparitionBonbons != null ? new ArrayList<>(configApparitionBonbons) : new ArrayList<>();
    }

    public String obtenirFichierApparitionBonbonsSelectionne() {
        return fichierApparitionBonbonsSelectionne;
    }

    public void envoyerListeFichiersBonbonsAuJoueur(ServerPlayer joueur) {
        List<String> fichiersDisponibles = GestionnaireFichiersApparitionBonbons.getInstance().obtenirFichiersDisponibles();
        GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
            new PaquetListeFichiersBonbons(fichiersDisponibles));
    }

    /**
     * Gérer la déconnexion d'un joueur - suivre quand et où il est parti
     */
    public void gererDeconnexionJoueur(ServerPlayer joueur) {
        MonSubMod.JOURNALISEUR.info("DEBUG: gererDeconnexionJoueur appelé pour {} (UUID: {})",
            joueur.getName().getString(), joueur.getUUID());

        // Ignorer les comptes candidats temporaires de la file d'attente
        // NOTE: Ne pas utiliser estJoueurRestreintLocal() ici car authenticatedUsers peut déjà être vidé lors de la déconnexion
        String nomJoueur = joueur.getName().getString();
        com.example.mysubmod.authentification.GestionnaireSalleAttente lobbyAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();

        if (lobbyAttente.estNomFileTemporaire(nomJoueur)) {
            MonSubMod.JOURNALISEUR.info("DEBUG: Joueur {} est un candidat de file, ignorer le suivi de déconnexion",
                nomJoueur);
            return;
        }

        // IMPORTANT: Ne jamais écraser les InfoDeconnexion existantes
        // C'est crucial pour le système de file où plusieurs personnes partagent le même compte:
        // - Indiv1 se déconnecte du jeu → InfoDeconnexion sauvegardée avec position île
        // - Indiv1 se reconnecte mais ne s'authentifie pas → va au lobby d'attente
        // - Indiv1 se déconnecte du lobby d'attente → on ne doit PAS écraser avec la position du lobby
        // - Indiv2 obtient sa place dans la file et s'authentifie → doit restaurer la position île d'Indiv1
        if (joueursDeconnectes.containsKey(nomJoueur)) {
            MonSubMod.JOURNALISEUR.info("DEBUG: Joueur {} a déjà des infos de déconnexion - PAS d'écrasement (garde la position originale pour le système de file)",
                nomJoueur);
            return;
        }

        // Sauvegarder l'inventaire du joueur (copier tous les items)
        java.util.List<net.minecraft.world.item.ItemStack> inventaireSauvegarde = new java.util.ArrayList<>();
        for (int i = 0; i < joueur.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack pile = joueur.getInventory().getItem(i);
            if (!pile.isEmpty()) {
                inventaireSauvegarde.add(pile.copy()); // Faire une copie pour préserver l'item
            } else {
                inventaireSauvegarde.add(net.minecraft.world.item.ItemStack.EMPTY);
            }
        }

        InfoDeconnexion info = new InfoDeconnexion(
            joueur.getUUID(),
            System.currentTimeMillis(),
            joueur.getX(),
            joueur.getY(),
            joueur.getZ(),
            joueur.getHealth(),
            inventaireSauvegarde
        );
        joueursDeconnectes.put(nomJoueur, info);
        MonSubMod.JOURNALISEUR.info("Joueur {} (UUID: {}) déconnecté pendant SousMode2 à ({}, {}, {}) avec {} PV - sauvegardé {} items d'inventaire - suivi déconnexion (joueursDeconnectes taille: {})",
            nomJoueur, joueur.getUUID(), joueur.getX(), joueur.getY(), joueur.getZ(), joueur.getHealth(), inventaireSauvegarde.stream().filter(s -> !s.isEmpty()).count(), joueursDeconnectes.size());

        // Journaliser la déconnexion (si le logger existe, sinon mettre en file d'attente)
        if (enregistreurDonnees != null) {
            enregistreurDonnees.enregistrerActionJoueur(joueur, "DECONNECTE");
        } else {
            // Mettre en file d'attente pour journalisation rétroactive quand enregistreurDonnees sera créé
            evenementsJournalisationEnAttente.add(new EvenementJournalisationEnAttente(joueur, "DECONNECTE"));
        }
    }

    /**
     * Vérifier si le joueur était déconnecté pendant la partie (par nom de compte)
     */
    public boolean etaitJoueurDeconnecte(String nomJoueur) {
        boolean resultat = joueursDeconnectes.containsKey(nomJoueur);
        MonSubMod.JOURNALISEUR.info("DEBUG: etaitJoueurDeconnecte({}) = {} (joueursDeconnectes size: {})",
            nomJoueur, resultat, joueursDeconnectes.size());
        return resultat;
    }

    /**
     * Effacer les infos de déconnexion pour un joueur (appelé quand il se reconnecte au serveur)
     * Cela empêche une deuxième personne d'être téléportée à la position de la première personne
     * si elle s'authentifie sur le même compte
     */
    public void effacerInfoDeconnexion(String nomJoueur) {
        InfoDeconnexion supprime = joueursDeconnectes.remove(nomJoueur);
        if (supprime != null) {
            MonSubMod.JOURNALISEUR.info("Cleared disconnect info for player {} on server reconnection", nomJoueur);
        }
    }

    /**
     * Vérifier si on est en phase de sélection de fichier (admin choisissant le fichier bonbons)
     */
    public boolean estPhaseSelectionFichier() {
        return phaseSelectionFichier;
    }

    /**
     * Obtenir le temps de sélection restant en secondes
     */
    public int obtenirTempsSelectionRestant() {
        if (!phaseSelection || heureDebutSelection == 0) {
            return 0;
        }
        long ecoule = (System.currentTimeMillis() - heureDebutSelection) / 1000;
        int restant = (int) (TEMPS_SELECTION_SECONDES - ecoule);
        return Math.max(0, restant);
    }

    /**
     * Gérer la reconnexion d'un joueur - restaurer son état et appliquer la pénalité de santé
     */
    public void gererReconnexionJoueur(ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString();
        UUID idJoueur = joueur.getUUID();

        MonSubMod.JOURNALISEUR.info("DEBUG: gererReconnexionJoueur appelé pour {} (UUID: {})",
            nomJoueur, idJoueur);

        // Ignorer les comptes candidats temporaires de la file d'attente
        if (estJoueurRestreintLocal(joueur)) {
            MonSubMod.JOURNALISEUR.info("DEBUG: Joueur {} est restreint, abandon de la reconnexion",
                nomJoueur);
            return;
        }

        // SECTION CRITIQUE: Synchroniser l'accès à joueursDeconnectes et la migration d'UUID
        // pour éviter la condition de course avec gererMortJoueurDeconnecte() dans le thread minuterie
        InfoDeconnexion infoDeconnexion;
        UUID ancienUUID;
        UUID nouvelUUID = joueur.getUUID();
        boolean joueurEstMort = false;

        synchronized (verrouReconnexion) {
            // Obtenir les infos de déconnexion par nom de compte (pas UUID, pour permettre le partage de compte)
            infoDeconnexion = joueursDeconnectes.remove(nomJoueur);

            if (infoDeconnexion == null) {
                MonSubMod.JOURNALISEUR.warn("Pas d'info de déconnexion trouvée pour le joueur reconnecté {} - joueursDeconnectes taille: {}",
                    nomJoueur, joueursDeconnectes.size());
                return;
            }

            // Vérifier si le joueur est mort côté serveur pendant la déconnexion
            joueurEstMort = infoDeconnexion.estMort;
        } // Fin du bloc synchronisé - libérer le verrou AVANT de téléporter

        // Gérer le joueur mort EN DEHORS du bloc synchronisé pour éviter les deadlocks
        if (joueurEstMort) {
            MonSubMod.JOURNALISEUR.info("Joueur {} est mort côté serveur pendant sa déconnexion - envoi vers spectateur", nomJoueur);
            teleporterVersSpectateur(joueur);
            joueur.sendSystemMessage(Component.literal(
                "§c§lVous êtes mort pendant votre déconnexion\n\n" +
                "§7Vous avez été téléporté en zone spectateur."
            ));
            // Pas besoin de migrer l'UUID car ils sont déjà dans joueursSpectateurs
            return;
        }

        synchronized (verrouReconnexion) {

            MonSubMod.JOURNALISEUR.info("DEBUG: Info de déconnexion trouvée pour {}, procédure de reconnexion en cours",
                joueur.getName().getString());

            // Migrer les données basées sur l'UUID de l'ancien UUID vers le nouveau
            // Ceci est nécessaire quand plusieurs personnes partagent le même compte (ex: système de file d'attente)
            ancienUUID = infoDeconnexion.ancienUUID;

            MonSubMod.JOURNALISEUR.info("DEBUG: ancienUUID from InfoDeconnexion: {}", ancienUUID);
            MonSubMod.JOURNALISEUR.info("DEBUG: nouvelUUID from player: {}", nouvelUUID);
            MonSubMod.JOURNALISEUR.info("DEBUG: Are they equal? {}", ancienUUID.equals(nouvelUUID));

            if (!ancienUUID.equals(nouvelUUID)) {
                MonSubMod.JOURNALISEUR.info("Migrating player data from old UUID {} to new UUID {} for account {}",
                    ancienUUID, nouvelUUID, nomJoueur);

                // Migrer joueursVivants
                MonSubMod.JOURNALISEUR.info("DEBUG: joueursVivants avant migration: {}", joueursVivants);
                MonSubMod.JOURNALISEUR.info("DEBUG: joueursVivants contient ancienUUID? {}", joueursVivants.contains(ancienUUID));
                if (joueursVivants.remove(ancienUUID)) {
                    joueursVivants.add(nouvelUUID);
                    MonSubMod.JOURNALISEUR.info("  - Migration de joueursVivants");
                } else {
                    MonSubMod.JOURNALISEUR.warn("DEBUG: Échec de retrait de ancienUUID de joueursVivants!");
                }
                MonSubMod.JOURNALISEUR.info("DEBUG: joueursVivants après migration: {}", joueursVivants);

                // Migrer joueursSpectateurs
                if (joueursSpectateurs.remove(ancienUUID)) {
                    joueursSpectateurs.add(nouvelUUID);
                    MonSubMod.JOURNALISEUR.info("  - Migration de joueursSpectateurs");
                }

                // Migrer joueursEnPhaseSelection
                if (joueursEnPhaseSelection.remove(ancienUUID)) {
                    joueursEnPhaseSelection.add(nouvelUUID);
                    MonSubMod.JOURNALISEUR.info("  - Migration de joueursEnPhaseSelection");
                }

                // Migrer selectionsIleJoueurs
                if (selectionsIleJoueurs.containsKey(ancienUUID)) {
                    TypeIle ile = selectionsIleJoueurs.remove(ancienUUID);
                    selectionsIleJoueurs.put(nouvelUUID, ile);
                    MonSubMod.JOURNALISEUR.info("  - Migration de selectionsIleJoueurs: {}", ile);
                }

                // Migrer selectionManuelleIleJoueur
                if (selectionManuelleIleJoueur.containsKey(ancienUUID)) {
                    Boolean manuelle = selectionManuelleIleJoueur.remove(ancienUUID);
                    selectionManuelleIleJoueur.put(nouvelUUID, manuelle);
                    MonSubMod.JOURNALISEUR.info("  - Migration de selectionManuelleIleJoueur");
                }

                // Migrer compteurBonbonsJoueur
                if (compteurBonbonsJoueur.containsKey(ancienUUID)) {
                    Integer bonbons = compteurBonbonsJoueur.remove(ancienUUID);
                    compteurBonbonsJoueur.put(nouvelUUID, bonbons);
                    MonSubMod.JOURNALISEUR.info("  - Migration de compteurBonbonsJoueur: {}", bonbons);
                }

                // Migrer nomsJoueurs (utilise le nom actuel du joueur pour le nouvel UUID)
                if (nomsJoueurs.containsKey(ancienUUID)) {
                    nomsJoueurs.remove(ancienUUID);
                }
                nomsJoueurs.put(nouvelUUID, joueur.getName().getString());
                MonSubMod.JOURNALISEUR.info("  - Migration/mise à jour de nomsJoueurs");

                // Migrer selectionIleJoueurJournalisee
                if (selectionIleJoueurJournalisee.remove(ancienUUID)) {
                    selectionIleJoueurJournalisee.add(nouvelUUID);
                    MonSubMod.JOURNALISEUR.info("  - Migration de selectionIleJoueurJournalisee");
                }

                // Migrer les données de spécialisation (spécialisation et pénalité)
                GestionnaireSpecialisation.getInstance().migrerDonneesJoueur(ancienUUID, nouvelUUID);

                MonSubMod.JOURNALISEUR.info("Migration UUID complétée pour le compte {}", nomJoueur);
            } else {
                MonSubMod.JOURNALISEUR.info("Aucune migration UUID nécessaire - même UUID qui se reconnecte");
            }
        } // Fin du bloc synchronisé

        long tempsDeconnexion = infoDeconnexion.tempsDeconnexion;

        // Calculer la perte de santé basée sur le nombre de ticks de dégradation de santé survenus pendant la déconnexion
        // Les ticks de santé se produisent à intervalles fixes: heureDebutPartie + 10s, +20s, +30s, etc.
        long tempsActuel = System.currentTimeMillis();

        // Calculer seulement si le jeu est actif et que le joueur s'est déconnecté pendant/après le début du jeu
        int ticksSanteManques = 0;
        float perteSante = 0.0f;

        if (partieActive && heureDebutPartie > 0) {
            long tempsDeconnexionEffectif = Math.max(tempsDeconnexion, heureDebutPartie);

            // Calculer à quel numéro de tick de santé nous sommes pour les moments de déconnexion et reconnexion
            // Tick 0 se produit à heureDebutPartie, tick 1 à heureDebutPartie+10s, etc.
            long msDepuisDebutJeuADeconnexion = tempsDeconnexionEffectif - heureDebutPartie;
            long msDepuisDebutJeuAReconnexion = tempsActuel - heureDebutPartie;

            // Calculer les numéros de tick (quel intervalle de 10 secondes)
            long numeroTickADeconnexion = msDepuisDebutJeuADeconnexion / 10000;
            long numeroTickAReconnexion = msDepuisDebutJeuAReconnexion / 10000;

            // Ticks manqués = ticks qui se sont produits entre la déconnexion et la reconnexion
            ticksSanteManques = (int) (numeroTickAReconnexion - numeroTickADeconnexion);

            if (ticksSanteManques > 0) {
                perteSante = (float) ticksSanteManques * 1.0f; // 1 PV par tick
            }
        }

        MonSubMod.JOURNALISEUR.info("Joueur {} reconnecté - {} ticks de santé manqués, perte de santé: {} PV",
            joueur.getName().getString(), ticksSanteManques, perteSante);

        // Vérifier si le joueur protégé est authentifié avant d'autoriser la reconnexion au jeu
        com.example.mysubmod.authentification.GestionnaireAuth gestionnaireAuth = com.example.mysubmod.authentification.GestionnaireAuth.getInstance();
        com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(joueur.getName().getString());
        boolean estJoueurProtege = (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE);
        boolean estAuthentifie = gestionnaireAuth.estAuthentifie(joueur.getUUID());

        if (estJoueurProtege && !estAuthentifie) {
            // Joueur protégé non authentifié - envoyer en spectateur
            MonSubMod.JOURNALISEUR.warn("Joueur protégé {} tente de se reconnecter sans authentification - envoi en spectateur", joueur.getName().getString());
            teleporterVersSpectateur(joueur);
            joueur.sendSystemMessage(Component.literal("§c§lVous devez être authentifié pour participer au jeu\n\n§7Veuillez vous authentifier et rejoindre le serveur."));
            // Retirer de joueursVivants s'il y était
            joueursVivants.remove(idJoueur);
            return;
        }

        // Gérer la reconnexion pendant la phase de sélection de fichier (AVANT de vérifier joueursVivants)
        if (!partieActive && phaseSelectionFichier) {
            // Ajouter le joueur aux participants de la phase de sélection pour qu'il ne soit pas traité comme spectateur
            joueursEnPhaseSelection.add(idJoueur);

            // Restaurer à la position de déconnexion
            MinecraftServer serveur = joueur.getServer();
            if (serveur != null) {
                ServerLevel mondePrincipal = serveur.getLevel(ServerLevel.OVERWORLD);
                if (mondePrincipal != null) {
                    teleportationSecurisee(joueur, mondePrincipal, infoDeconnexion.x, infoDeconnexion.y, infoDeconnexion.z);
                    MonSubMod.JOURNALISEUR.info("Joueur {} téléporté à la position de déconnexion pendant la phase de sélection de fichier", joueur.getName().getString());
                }
            }
            joueur.sendSystemMessage(Component.literal("§eVous avez été reconnecté. En attente de la sélection de fichier par l'admin..."));

            // Journaliser la reconnexion (mettre en file d'attente car enregistreurDonnees n'existe pas encore)
            evenementsJournalisationEnAttente.add(new EvenementJournalisationEnAttente(joueur, "RECONNECTED (file selection phase)"));
            return;
        }

        // Gérer la reconnexion pendant la phase de sélection d'île (AVANT de vérifier joueursVivants)
        if (!partieActive && phaseSelection) {
            // Restaurer à la position de déconnexion
            MinecraftServer serveur = joueur.getServer();
            if (serveur != null) {
                ServerLevel mondePrincipal = serveur.getLevel(ServerLevel.OVERWORLD);
                if (mondePrincipal != null) {
                    teleportationSecurisee(joueur, mondePrincipal, infoDeconnexion.x, infoDeconnexion.y, infoDeconnexion.z);
                    MonSubMod.JOURNALISEUR.info("Joueur {} téléporté à la position de déconnexion pendant la phase de sélection d'île", joueur.getName().getString());
                }
            }

            // Vérifier si le joueur n'a pas encore sélectionné d'île
            if (!selectionsIleJoueurs.containsKey(idJoueur)) {
                // Envoyer l'écran de sélection d'île avec le temps restant
                int tempsRestant = obtenirTempsSelectionRestant();
                if (tempsRestant > 0) {
                    // Planifier l'envoi du paquet après 10 ticks (0.5 secondes) pour s'assurer que le client est prêt
                    final int tempsAEnvoyer = tempsRestant;
                    java.util.Timer minuterie = new java.util.Timer();
                    minuterie.schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            MinecraftServer serveurJeu = joueur.getServer();
                            if (serveurJeu != null && joueur.isAlive()) {
                                serveurJeu.execute(() -> {
                                    GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                                        new PaquetSelectionIle(tempsAEnvoyer));
                                    MonSubMod.JOURNALISEUR.info("Paquet de sélection d'île envoyé au joueur reconnecté {} avec {} secondes restantes",
                                        joueur.getName().getString(), tempsAEnvoyer);
                                });
                            }
                        }
                    }, 500); // délai de 500ms
                    joueur.sendSystemMessage(Component.literal("§eVous avez été reconnecté pendant la phase de sélection. Temps restant: " + tempsRestant + "s"));
                } else {
                    joueur.sendSystemMessage(Component.literal("§eVous avez été reconnecté. La phase de sélection se termine..."));
                }
            } else {
                joueur.sendSystemMessage(Component.literal("§eVous avez été reconnecté. Île déjà sélectionnée: " +
                    selectionsIleJoueurs.get(idJoueur).obtenirNomAffichage()));
            }

            // Journaliser la reconnexion (si l'enregistreur existe, sinon mettre en file d'attente)
            if (enregistreurDonnees != null) {
                enregistreurDonnees.enregistrerActionJoueur(joueur, "RECONNECTE (phase selection ile)");
            } else {
                evenementsJournalisationEnAttente.add(new EvenementJournalisationEnAttente(joueur, "RECONNECTE (phase selection ile)"));
            }
            return;
        }

        // Gérer la reconnexion quand le jeu a démarré pendant que le joueur était en phase de sélection
        // MAIS seulement si le joueur n'était PAS en jeu actif (si dans joueursVivants, gérer ci-dessous avec pénalité de santé)
        if (partieActive && joueursEnPhaseSelection.contains(idJoueur) && !joueursVivants.contains(idJoueur)) {
            // Vérifier si le joueur s'est déconnecté AVANT le début de la phase de sélection d'île
            // S'il s'est déconnecté pendant la phase de sélection de fichier, il devrait être spectateur
            // ou si le joueur est mort et s'est reconnecté en spectateur
            if ((heureDebutSelection > 0 && tempsDeconnexion < heureDebutSelection) || joueursSpectateurs.contains(idJoueur)) {
                if(heureDebutSelection > 0 && tempsDeconnexion < heureDebutSelection){
                    joueursSpectateurs.add(idJoueur);
                    joueur.sendSystemMessage(Component.literal("§cVous vous êtes déconnecté avant la phase de sélection des îles.\n§7Vous êtes maintenant spectateur."));
                }
                teleporterVersSpectateur(joueur);
                MonSubMod.JOURNALISEUR.info("Joueur {} déconnecté pendant la phase de sélection de fichier (avant le début de la sélection d'île) - envoi en spectateur", joueur.getName().getString());

                // Journaliser la reconnexion en tant que spectateur
                if (enregistreurDonnees != null) {
                    enregistreurDonnees.enregistrerActionJoueur(joueur, "RECONNECTE (spectateur - selection ile manquee ou reconnecte en spectateur)");
                }
                return;
            }

            // Le joueur était en phase de sélection d'île quand il s'est déconnecté, mais le jeu a démarré pendant qu'il était hors ligne
            MonSubMod.JOURNALISEUR.info("Joueur {} était en phase de sélection d'île mais le jeu a démarré pendant qu'il était hors ligne - ajout au jeu", joueur.getName().getString());

            joueursVivants.add(idJoueur);
            nomsJoueurs.put(idJoueur, joueur.getName().getString());

            // Vérifier s'il a sélectionné une île
            TypeIle ileSelectionnee = selectionsIleJoueurs.get(idJoueur);
            if (ileSelectionnee == null) {
                // Assigner une île aléatoire
                TypeIle[] iles = TypeIle.values();
                Random aleatoire = new Random();
                ileSelectionnee = iles[aleatoire.nextInt(iles.length)];
                selectionsIleJoueurs.put(idJoueur, ileSelectionnee);
                selectionManuelleIleJoueur.put(idJoueur, false);
                MonSubMod.JOURNALISEUR.info("Île aléatoire {} assignée au joueur {}", ileSelectionnee.name(), joueur.getName().getString());
            }

            // Téléporter à la position de déconnexion (devrait être la place centrale)
            MinecraftServer serveur = joueur.getServer();
            if (serveur != null) {
                ServerLevel mondePrincipal = serveur.getLevel(ServerLevel.OVERWORLD);
                if (mondePrincipal != null) {
                    teleportationSecurisee(joueur, mondePrincipal, infoDeconnexion.x, infoDeconnexion.y, infoDeconnexion.z);
                }
            }

            joueur.sendSystemMessage(Component.literal(String.format(
                "§eVous avez été reconnecté. Le jeu a démarré pendant votre absence.\n§7Île assignée: %s",
                ileSelectionnee.obtenirNomAffichage())));

            // Journaliser la reconnexion
            if (enregistreurDonnees != null) {
                enregistreurDonnees.enregistrerActionJoueur(joueur, "RECONNECTE (debut partie manque)");
            }
            return;
        }

        // Restaurer le joueur à l'état de jeu (pendant le jeu actif)
        MonSubMod.JOURNALISEUR.info("DEBUG: Vérification si joueur {} est dans joueursVivants - résultat: {}", idJoueur, joueursVivants.contains(idJoueur));
        MonSubMod.JOURNALISEUR.info("DEBUG: contenu joueursVivants: {}", joueursVivants);
        MonSubMod.JOURNALISEUR.info("DEBUG: partieActive: {}, phaseSelection: {}, phaseSelectionFichier: {}", partieActive, phaseSelection, phaseSelectionFichier);

        if (joueursVivants.contains(idJoueur)) {
            MonSubMod.JOURNALISEUR.info("DEBUG: Joueur {} est dans joueursVivants, procédant à la restauration de l'état de jeu", idJoueur);
            // Le joueur était vivant quand il s'est déconnecté

            // Obtenir sa sélection d'île
            TypeIle ileSelectionnee = selectionsIleJoueurs.get(idJoueur);
            boolean ileAssigneePendantReconnexion = false;

            // Si le joueur s'est déconnecté pendant la sélection et se reconnecte après le début du jeu sans île
            if (ileSelectionnee == null && partieActive && joueursEnPhaseSelection.contains(idJoueur)) {
                // Assigner une île aléatoire au joueur qui a manqué la sélection
                TypeIle[] iles = TypeIle.values();
                Random aleatoire = new Random();
                ileSelectionnee = iles[aleatoire.nextInt(iles.length)];
                selectionsIleJoueurs.put(idJoueur, ileSelectionnee);
                selectionManuelleIleJoueur.put(idJoueur, false); // Assignation automatique
                ileAssigneePendantReconnexion = true;

                MonSubMod.JOURNALISEUR.info("Île aléatoire {} assignée au joueur {} qui s'est reconnecté après la phase de sélection",
                    ileSelectionnee.name(), joueur.getName().getString());

                joueur.sendSystemMessage(Component.literal("§eÎle assignée automatiquement (reconnexion tardive): " + ileSelectionnee.obtenirNomAffichage()));
            }

            if (ileSelectionnee != null && partieActive) {
                // Déterminer si le joueur s'est déconnecté avant le début du jeu
                boolean deconnecteAvantDebutJeu = (heureDebutPartie > 0 && tempsDeconnexion < heureDebutPartie);
                MonSubMod.JOURNALISEUR.info("DEBUG: ileSelectionnee={}, partieActive={}, deconnecteAvantDebutJeu={}",
                    ileSelectionnee, partieActive, deconnecteAvantDebutJeu);
                MonSubMod.JOURNALISEUR.info("DEBUG: heureDebutPartie={}, tempsDeconnexion={}", heureDebutPartie, tempsDeconnexion);

                // Logique de téléportation
                MinecraftServer serveur = joueur.getServer();
                if (serveur != null) {
                    ServerLevel mondePrincipal = serveur.getLevel(ServerLevel.OVERWORLD);
                    if (mondePrincipal != null) {
                        if (deconnecteAvantDebutJeu) {
                            // Le joueur s'est déconnecté avant le début du jeu - téléporter au centre de l'île (il n'est jamais allé sur son île)
                            BlockPos positionSpawn = obtenirPositionSpawnIle(ileSelectionnee);
                            MonSubMod.JOURNALISEUR.info("DEBUG: Téléportation au centre de l'île: ({}, {}, {})",
                                positionSpawn.getX() + 0.5, positionSpawn.getY() + 1, positionSpawn.getZ() + 0.5);
                            teleportationSecurisee(joueur, mondePrincipal, positionSpawn.getX() + 0.5, positionSpawn.getY() + 1, positionSpawn.getZ() + 0.5);
                            MonSubMod.JOURNALISEUR.info("Joueur {} téléporté au centre de l'île (déconnecté avant le début du jeu)",
                                joueur.getName().getString());
                        } else {
                            // Le joueur s'est déconnecté pendant le jeu actif - téléporter à la position exacte de déconnexion
                            MonSubMod.JOURNALISEUR.info("DEBUG: Téléportation à la position de déconnexion: ({}, {}, {})",
                                infoDeconnexion.x, infoDeconnexion.y, infoDeconnexion.z);
                            teleportationSecurisee(joueur, mondePrincipal, infoDeconnexion.x, infoDeconnexion.y, infoDeconnexion.z);
                            MonSubMod.JOURNALISEUR.info("Joueur {} téléporté à la position exacte de déconnexion ({}, {}, {})",
                                joueur.getName().getString(), infoDeconnexion.x, infoDeconnexion.y, infoDeconnexion.z);
                        }
                    } else {
                        MonSubMod.JOURNALISEUR.error("DEBUG: mondePrincipal est null!");
                    }
                } else {
                    MonSubMod.JOURNALISEUR.error("DEBUG: serveur est null!");
                }

                // Restaurer l'inventaire sauvegardé depuis la déconnexion
                if (infoDeconnexion.inventaireSauvegarde != null && !infoDeconnexion.inventaireSauvegarde.isEmpty()) {
                    joueur.getInventory().clearContent(); // Vider l'inventaire actuel d'abord
                    for (int i = 0; i < Math.min(infoDeconnexion.inventaireSauvegarde.size(), joueur.getInventory().getContainerSize()); i++) {
                        joueur.getInventory().setItem(i, infoDeconnexion.inventaireSauvegarde.get(i).copy());
                    }
                    int objetsRestaures = (int) infoDeconnexion.inventaireSauvegarde.stream().filter(s -> !s.isEmpty()).count();
                    MonSubMod.JOURNALISEUR.info("{} objets d'inventaire restaurés pour le joueur reconnecté {}", objetsRestaures, joueur.getName().getString());
                } else {
                    // Pas d'inventaire sauvegardé, s'assurer qu'il est vidé
                    if (!joueur.getInventory().isEmpty()) {
                        MonSubMod.JOURNALISEUR.warn("Joueur {} reconnecté sans inventaire sauvegardé mais avec un inventaire actuel non vide - vidage", joueur.getName().getString());
                        viderInventaireJoueur(joueur);
                    }
                }

                // Réinitialiser la santé à 100% SEULEMENT si le joueur s'est déconnecté AVANT le début du jeu
                // (Les joueurs qui se déconnectent pendant le jeu actif gardent leur santé actuelle moins la pénalité)
                float santeActuelle = joueur.getHealth();

                if (deconnecteAvantDebutJeu) {
                    // Le joueur était déconnecté avant le début du jeu - réinitialiser à 100% PUIS appliquer la pénalité
                    joueur.setHealth(joueur.getMaxHealth());
                    joueur.getFoodData().setFoodLevel(10);
                    joueur.getFoodData().setSaturation(5.0f);
                    santeActuelle = joueur.getMaxHealth(); // Mettre à jour vers la nouvelle valeur de santé
                    MonSubMod.JOURNALISEUR.info("Santé réinitialisée à 100% pour le joueur {} (était déconnecté avant le début du jeu)", joueur.getName().getString());
                } else {
                    // Le joueur s'est déconnecté pendant le jeu actif - garder la santé actuelle
                    MonSubMod.JOURNALISEUR.info("Joueur {} déconnecté pendant le jeu actif - conservation de la santé actuelle {}",
                        joueur.getName().getString(), santeActuelle);
                }

                // Journaliser la sélection d'île si:
                // 1. L'île a été assignée pendant la reconnexion (phase de sélection manquée)
                // 2. Le joueur s'est déconnecté avant le début du jeu (jamais téléporté sur l'île) ET pas encore journalisé
                if (enregistreurDonnees != null && !selectionIleJoueurJournalisee.contains(idJoueur)) {
                    if (ileAssigneePendantReconnexion) {
                        enregistreurDonnees.enregistrerSelectionIle(joueur, ileSelectionnee, "AUTOMATIQUE");
                        selectionIleJoueurJournalisee.add(idJoueur);
                        MonSubMod.JOURNALISEUR.info("Sélection d'île journalisée pour le joueur {} (assignée pendant la reconnexion)", joueur.getName().getString());
                    } else if (deconnecteAvantDebutJeu) {
                        // Vérifier si l'île a été sélectionnée manuellement ou assignée automatiquement
                        Boolean etaitSelectionManuelle = selectionManuelleIleJoueur.get(idJoueur);
                        String typeSelection = (etaitSelectionManuelle != null && etaitSelectionManuelle) ? "MANUAL" : "AUTOMATIC";
                        enregistreurDonnees.enregistrerSelectionIle(joueur, ileSelectionnee, typeSelection);
                        selectionIleJoueurJournalisee.add(idJoueur);
                        MonSubMod.JOURNALISEUR.info("Sélection d'île journalisée pour le joueur {} (première téléportation vers l'île après reconnexion, type: {})",
                            joueur.getName().getString(), typeSelection);
                    }
                }

                // TOUJOURS appliquer la pénalité de santé pour le temps de déconnexion (que ce soit avant ou pendant le jeu)
                float nouvelleSante = Math.max(0.5f, santeActuelle - perteSante); // Minimum 0.5 PV pour éviter la mort instantanée
                joueur.setHealth(nouvelleSante);

                joueur.sendSystemMessage(Component.literal(String.format(
                    "§eVous avez été reconnecté. Perte de santé: %.1f cœurs (%d ticks de dégradation manqués)",
                    perteSante / 2.0f, ticksSanteManques)));

                MonSubMod.JOURNALISEUR.info("Santé du joueur {} réduite de {} (de {} à {})",
                    joueur.getName().getString(), perteSante, santeActuelle, nouvelleSante);

                // Journaliser la reconnexion (si l'enregistreur existe, sinon mettre en file d'attente)
                if (enregistreurDonnees != null) {
                    enregistreurDonnees.enregistrerActionJoueur(joueur, String.format("RECONNECTE (-%d ticks, -%.1f PV)",
                        ticksSanteManques, perteSante));
                } else {
                    // Mettre l'événement en file d'attente pour journalisation rétroactive quand enregistreurDonnees sera créé
                    evenementsJournalisationEnAttente.add(new EvenementJournalisationEnAttente(joueur, String.format("RECONNECTE (-%d ticks, -%.1f PV)",
                        ticksSanteManques, perteSante)));
                }

                // Synchroniser l'état de pénalité avec le client pour réactiver le HUD si la pénalité est toujours active
                GestionnaireSpecialisation.getInstance().synchroniserPenaliteAvecClient(joueur);

                // Synchroniser la spécialisation avec le client pour restaurer l'affichage HUD
                GestionnaireSpecialisation.getInstance().synchroniserSpecialisationAvecClient(joueur);

                // Vérifier si la pénalité de santé a tué le joueur
                if (nouvelleSante <= 0.5f) {
                    joueur.sendSystemMessage(Component.literal("§cVous êtes mort pendant votre déconnexion !"));

                    // Enregistrer l'heure de mort pour le classement
                    enregistrerMortJoueur(idJoueur);

                    GestionnaireSanteSousMode2.getInstance().arreterDegradationSante();
                    teleporterVersSpectateur(joueur);

                    // Diffuser le message de mort
                    String messageMort = "§e" + joueur.getName().getString() + " §cest mort pendant sa déconnexion !";
                    MinecraftServer serveurJeu = joueur.getServer();
                    if (serveurJeu != null) {
                        for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveurJeu)) {
                            j.sendSystemMessage(Component.literal(messageMort));
                        }

                        // Vérifier si tous les joueurs sont morts (plus aucun joueur vivant)
                        if (obtenirJoueursVivants().isEmpty()) {
                            MonSubMod.JOURNALISEUR.info("Tous les joueurs sont morts après la mort lors de la reconnexion - fin de la partie");
                            serveurJeu.execute(() -> {
                                for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveurJeu)) {
                                    j.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                                }
                                terminerPartie(serveurJeu);
                            });
                        }
                    }
                }
            }
        } else if (joueursSpectateurs.contains(idJoueur)) {
            MonSubMod.JOURNALISEUR.info("DEBUG: Joueur {} est dans joueursSpectateurs, téléportation en spectateur", idJoueur);
            // Le joueur était spectateur quand il s'est déconnecté
            teleporterVersSpectateur(joueur);
            joueur.sendSystemMessage(Component.literal("§eVous avez été reconnecté en mode spectateur"));
        } else {
            MonSubMod.JOURNALISEUR.warn("DEBUG: Joueur {} n'est ni dans joueursVivants ni dans joueursSpectateurs après migration!", idJoueur);
        }
    }

    /**
     * Incrémenter le compteur de bonbons pour un joueur
     */
    public void incrementerCompteurBonbons(UUID idJoueur, int montant) {
        compteurBonbonsJoueur.put(idJoueur, compteurBonbonsJoueur.getOrDefault(idJoueur, 0) + montant);
    }

    /**
     * Enregistrer quand un joueur est mort pour le classement
     */
    public void enregistrerMortJoueur(UUID idJoueur) {
        heureMortJoueur.put(idJoueur, System.currentTimeMillis());
    }

    /**
     * Afficher le classement à la fin de la partie
     */
    private void afficherClassement(MinecraftServer serveur) {
        MonSubMod.JOURNALISEUR.info("Affichage du classement");

        // Créer les entrées de classement pour tous les joueurs qui ont participé
        List<EntreeClassement> entrees = new ArrayList<>();

        // Itérer sur TOUS les joueurs qui ont sélectionné une île (ont participé), pas seulement ceux connectés
        for (Map.Entry<UUID, TypeIle> entreeMap : selectionsIleJoueurs.entrySet()) {
            UUID idJoueur = entreeMap.getKey();

            // Obtenir le nom du joueur - essayer d'abord de trouver le joueur en ligne, puis utiliser le nom stocké
            String nomJoueur = null;
            ServerPlayer joueurEnLigne = serveur.getPlayerList().getPlayer(idJoueur);
            if (joueurEnLigne != null) {
                nomJoueur = joueurEnLigne.getName().getString();
            } else if (nomsJoueurs.containsKey(idJoueur)) {
                nomJoueur = nomsJoueurs.get(idJoueur);
            } else {
                // Repli: essayer d'obtenir depuis GameProfile
                com.mojang.authlib.GameProfile profil = serveur.getProfileCache().get(idJoueur).orElse(null);
                if (profil != null) {
                    nomJoueur = profil.getName();
                } else {
                    MonSubMod.JOURNALISEUR.warn("Impossible de trouver le nom pour l'UUID du joueur {} - exclusion du classement", idJoueur);
                    continue;
                }
            }

            boolean estVivant = joueursVivants.contains(idJoueur);
            int nombreBonbons = compteurBonbonsJoueur.getOrDefault(idJoueur, 0);
            long tempsSurvie = 0;

            if (!estVivant && heureMortJoueur.containsKey(idJoueur)) {
                // Calculer le temps de survie pour les joueurs morts
                tempsSurvie = heureMortJoueur.get(idJoueur) - heureDebutPartie;
            }

            entrees.add(new EntreeClassement(nomJoueur, estVivant, nombreBonbons, tempsSurvie));
            MonSubMod.JOURNALISEUR.debug("Joueur {} ajouté au classement - vivant: {}, bonbons: {}, survie: {}ms",
                nomJoueur, estVivant, nombreBonbons, tempsSurvie);
        }

        // Trier le classement:
        // 1. Joueurs vivants en premier (par nombre de bonbons décroissant)
        // 2. Joueurs morts ensuite (par temps de survie décroissant)
        entrees.sort((e1, e2) -> {
            // Les joueurs vivants sont toujours classés plus haut que les joueurs morts
            if (e1.estVivant && !e2.estVivant) return -1;
            if (!e1.estVivant && e2.estVivant) return 1;

            // Les deux vivants: trier par nombre de bonbons (décroissant)
            if (e1.estVivant && e2.estVivant) {
                return Integer.compare(e2.nombreBonbons, e1.nombreBonbons);
            }

            // Les deux morts: trier par temps de survie (décroissant)
            return Long.compare(e2.tempsSurvieMs, e1.tempsSurvieMs);
        });

        // Afficher le classement à tous les joueurs
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            joueur.sendSystemMessage(Component.literal(""));
            joueur.sendSystemMessage(Component.literal("§6§l========== CLASSEMENT FINAL =========="));
            joueur.sendSystemMessage(Component.literal(""));

            for (int i = 0; i < entrees.size(); i++) {
                EntreeClassement entree = entrees.get(i);
                int rang = i + 1;

                String couleurRang;
                if (rang == 1) couleurRang = "§6"; // Or pour le 1er
                else if (rang == 2) couleurRang = "§7"; // Argent pour le 2ème
                else if (rang == 3) couleurRang = "§c"; // Bronze pour le 3ème
                else couleurRang = "§f"; // Blanc pour les autres

                String infoStatut;
                if (entree.estVivant) {
                    infoStatut = String.format("§a✓ Vivant §7- §e%d bonbons", entree.nombreBonbons);
                } else {
                    long secondesSurvie = entree.tempsSurvieMs / 1000;
                    long minutes = secondesSurvie / 60;
                    long secondes = secondesSurvie % 60;
                    infoStatut = String.format("§c✗ Mort §7- Survie: %dm%ds", minutes, secondes);
                }

                joueur.sendSystemMessage(Component.literal(
                    String.format("%s#%d §f- %s %s", couleurRang, rang, entree.nomJoueur, infoStatut)
                ));
            }

            joueur.sendSystemMessage(Component.literal(""));
            joueur.sendSystemMessage(Component.literal("§6§l======================================="));
            joueur.sendSystemMessage(Component.literal(""));
        }

        // Journaliser le classement dans l'enregistreur de données
        if (enregistreurDonnees != null) {
            StringBuilder journalClassement = new StringBuilder("\n=== CLASSEMENT FINAL ===\n");
            for (int i = 0; i < entrees.size(); i++) {
                EntreeClassement entree = entrees.get(i);
                journalClassement.append(String.format("#%d - %s: %s\n",
                    i + 1,
                    entree.nomJoueur,
                    entree.estVivant ?
                        String.format("Vivant (%d bonbons)", entree.nombreBonbons) :
                        String.format("Mort (survécu %dms)", entree.tempsSurvieMs)
                ));
            }
            try {
                java.io.File fichierEvenements = new java.io.File(enregistreurDonnees.obtenirIdSessionPartie() != null ?
                    new java.io.File(".", "donnees_monsubmod/sousmode2_partie_" + enregistreurDonnees.obtenirIdSessionPartie()) :
                    new java.io.File(".", "donnees_monsubmod"), "evenements_partie.txt");
                java.io.FileWriter enregistreurEvenements = new java.io.FileWriter(fichierEvenements, true);
                enregistreurEvenements.write(journalClassement.toString());
                enregistreurEvenements.close();
            } catch (java.io.IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la journalisation du classement", e);
            }
        }
    }

    /**
     * Classe interne pour stocker les événements de journalisation qui se produisent avant la création de enregistreurDonnees
     */
    private static class EvenementJournalisationEnAttente {
        final ServerPlayer joueur;
        final String action;
        final long horodatage;

        EvenementJournalisationEnAttente(ServerPlayer joueur, String action) {
            this.joueur = joueur;
            this.action = action;
            this.horodatage = System.currentTimeMillis();
        }
    }

    /**
     * Classe interne pour représenter une entrée du classement
     */
    private static class EntreeClassement {
        String nomJoueur;
        boolean estVivant;
        int nombreBonbons;
        long tempsSurvieMs;

        EntreeClassement(String nomJoueur, boolean estVivant, int nombreBonbons, long tempsSurvieMs) {
            this.nomJoueur = nomJoueur;
            this.estVivant = estVivant;
            this.nombreBonbons = nombreBonbons;
            this.tempsSurvieMs = tempsSurvieMs;
        }
    }

    // Getters
    public boolean estPartieActive() { return partieActive; }
    public long obtenirHeureDebutPartie() { return heureDebutPartie; }

    public void ajouterJoueurEnPhaseSelection(ServerPlayer joueur) {
        joueursEnPhaseSelection.add(joueur.getUUID());

        // Téléporter vers la place centrale
        MinecraftServer serveur = joueur.getServer();
        if (serveur != null) {
            ServerLevel surMonde = serveur.getLevel(ServerLevel.OVERWORLD);
            if (surMonde != null) {
                teleportationSecurisee(joueur, surMonde,
                    placeCentrale.getX() + 0.5,
                    placeCentrale.getY() + 1,
                    placeCentrale.getZ() + 0.5);
                joueur.setGameMode(GameType.SURVIVAL);
            }
        }
    }
    public boolean estPhaseSelection() { return phaseSelection; }
    public boolean estJoueurVivant(UUID idJoueur) { return joueursVivants.contains(idJoueur); }
    public boolean estJoueurSpectateur(UUID idJoueur) { return joueursSpectateurs.contains(idJoueur); }
    public boolean estEnPhaseSelection(UUID idJoueur) { return joueursEnPhaseSelection.contains(idJoueur); }
    public Set<UUID> obtenirJoueursVivants() { return new HashSet<>(joueursVivants); }
    public EnregistreurDonneesSousMode2 obtenirEnregistreurDonnees() { return enregistreurDonnees; }

    /**
     * Obtenir les infos des joueurs déconnectés pour le suivi de santé côté serveur
     * Retourne une map des noms de joueurs vers leurs infos de déconnexion
     */
    public Map<String, InfoDeconnexion> obtenirInfoJoueursDeconnectes() {
        // Synchroniser pour empêcher la lecture pendant la migration d'UUID
        synchronized (verrouReconnexion) {
            return new HashMap<>(joueursDeconnectes);
        }
    }

    /**
     * Gérer la mort d'un joueur déconnecté (suivi côté serveur)
     * Appelé quand la santé d'un joueur déconnecté atteint 0 selon le temps écoulé
     */
    public void gererMortJoueurDeconnecte(String nomJoueur, UUID idJoueur) {
        // SECTION CRITIQUE: Synchroniser avec gererReconnexionJoueur() pour éviter les conditions de course
        // pendant la migration d'UUID
        synchronized (verrouReconnexion) {
            // Double vérification: Si le joueur s'est reconnecté entre la vérification de santé et maintenant, InfoDeconnexion sera parti
            if (!joueursDeconnectes.containsKey(nomJoueur)) {
                MonSubMod.JOURNALISEUR.info("Le joueur {} s'est reconnecté avant que la mort ne soit traitée - mort ignorée", nomJoueur);
                return;
            }

            // Vérifier que l'UUID correspond à ce qui est dans InfoDeconnexion (au cas où une migration a eu lieu)
            InfoDeconnexion info = joueursDeconnectes.get(nomJoueur);
            if (!info.ancienUUID.equals(idJoueur)) {
                MonSubMod.JOURNALISEUR.info("L'UUID du joueur {} a changé (reconnexion) avant la mort - mort ignorée", nomJoueur);
                return;
            }

            if (!joueursVivants.contains(idJoueur)) {
                return; // Déjà mort ou spectateur
            }

            MonSubMod.JOURNALISEUR.info("Le joueur déconnecté {} est mort côté serveur à cause de la dégradation de santé", nomJoueur);

            // Retirer des joueurs vivants
            joueursVivants.remove(idJoueur);

            // Ajouter aux spectateurs
            joueursSpectateurs.add(idJoueur);

            // Enregistrer l'heure de mort pour le classement
            enregistrerMortJoueur(idJoueur);

            // Marquer comme mort dans InfoDeconnexion au lieu de le supprimer
            // De cette façon, si quelqu'un se reconnecte sur ce compte, il sera envoyé en spectateur
            info.estMort = true;
            // Vider l'inventaire puisque le joueur est mort
            info.inventaireSauvegarde = new ArrayList<>();
            MonSubMod.JOURNALISEUR.info("InfoDeconnexion marqué comme mort pour {} - la reconnexion enverra en spectateur", nomJoueur);
        }
    }
    public BlockPos obtenirCentreIlePetite() { return centreIlePetite; }
    public BlockPos obtenirCentreIleMoyenne() { return centreIleMoyenne; }
    public BlockPos obtenirCentreIleGrande() { return centreIleGrande; }
    public BlockPos obtenirCentreIleTresGrande() { return centreIleTresGrande; }
    public BlockPos obtenirPlaceCentrale() { return placeCentrale; }
}
package com.example.mysubmod.authentification;

import com.example.mysubmod.MonSubMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Gère les joueurs dans le lobby de stationnement en attente d'authentification
 * - Délai d'expiration de 60 secondes avant expulsion
 * - Système de file d'attente pour les connexions en attente (par IP)
 * - Protections anti-monopolisation
 */
public class GestionnaireSalleAttente {
    private static GestionnaireSalleAttente instance;

    private final Map<UUID, SessionAuth> sessionsActives = new ConcurrentHashMap<>();
    private final Map<String, Queue<EntreeFile>> filesAttente = new ConcurrentHashMap<>(); // nomCompte -> file
    private final Map<String, Set<UUID>> candidatsFile = new ConcurrentHashMap<>(); // nomCompte -> ensemble d'UUIDs en attente de vérification du mot de passe
    private final Map<String, String> listeBlancheTemporaire = new ConcurrentHashMap<>(); // nomCompte -> ipAutorisee
    private final Map<String, Long> expirationListeBlanche = new ConcurrentHashMap<>(); // nomCompte -> tempsExpiration
    private final Map<String, Boolean> ipsAutoriseesDepuisFile = new ConcurrentHashMap<>(); // Suivi des IPs autorisées depuis la file (nomCompte -> true)
    private final Map<String, String> nomTemporaireVersCompte = new ConcurrentHashMap<>(); // nomTemporaire -> nomCompte original
    private final Map<String, String> jetonsActifs = new ConcurrentHashMap<>(); // nomCompte -> jeton actif pour la fenêtre de monopole actuelle
    private final Map<UUID, String> ipsCandidats = new ConcurrentHashMap<>(); // UUID -> adresse IP (pour le suivi de protection DoS)
    private final Map<UUID, Long> tempJointureCandidats = new ConcurrentHashMap<>(); // UUID -> horodatage de connexion (pour éviction basée sur l'âge)
    private final Set<UUID> verificationJetonEchouee = ConcurrentHashMap.newKeySet(); // Joueurs ayant échoué la vérification du jeton (ne pas appeler autoriserSuivantDansFile pour eux)
    private final Timer minuterieExpiration = new Timer("ParkingLobby-Timeout", true);

    private static final long DELAI_EXPIRATION_AUTH_MS = 60 * 1000; // 60 secondes (par défaut)
    private static final long DELAI_EXPIRATION_AUTH_DEPUIS_FILE_MS = 30 * 1000; // 30 secondes (depuis la file)
    private static final long EXPIRATION_LISTE_BLANCHE_MS = 45 * 1000; // 45 secondes pour se reconnecter (fenêtre de monopole)
    private static final long EXPIRATION_ENTREE_FILE_MS = 5 * 60 * 1000; // 5 minutes maximum dans la file
    private static final int TAILLE_MAX_FILE = 1; // Maximum 1 personne en attente dans la file par compte
    private static final int MAX_CANDIDATS_PAR_COMPTE_PAR_IP = 4; // Maximum 4 tentatives parallèles par compte depuis la même IP (protection DoS)
    private static final int MAX_CANDIDATS_PAR_IP_GLOBAL = 10; // Maximum 10 comptes _Q_ depuis la même IP à travers tous les comptes (protection DoS)
    private static final long AGE_MIN_CANDIDAT_POUR_EVICTION_MS = 20 * 1000; // 20 secondes - âge minimum avant qu'un candidat puisse être évincé

    private GestionnaireSalleAttente() {
        // Démarrer la tâche de nettoyage pour les entrées de file et listes blanches expirées
        minuterieExpiration.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                nettoyerEntreesExpirees();
            }
        }, 30000, 30000); // Toutes les 30 secondes
    }

    public static GestionnaireSalleAttente getInstance() {
        if (instance == null) {
            instance = new GestionnaireSalleAttente();
        }
        return instance;
    }

    /**
     * Informations de session pour un joueur en attente d'authentification
     */
    private static class SessionAuth {
        final long heureDebut;
        final long delaiExpirationMs; // Durée réelle d'expiration pour cette session
        final String typeCompte; // "ADMINISTRATEUR" ou "JOUEUR_PROTEGE"
        final String nomJoueur; // Suivre le nom du joueur pour la notification de file
        final String adresseIp; // Suivre l'adresse IP
        TimerTask tacheExpiration;

        SessionAuth(String typeCompte, String nomJoueur, String adresseIp, long delaiExpirationMs) {
            this.heureDebut = System.currentTimeMillis();
            this.delaiExpirationMs = delaiExpirationMs;
            this.typeCompte = typeCompte;
            this.nomJoueur = nomJoueur;
            this.adresseIp = adresseIp;
        }

        long obtenirTempsFinExpirationMs() {
            return heureDebut + delaiExpirationMs;
        }
    }

    /**
     * Entrée de file pour connexion en attente
     */
    private static class EntreeFile {
        final String adresseIp;
        final long horodatage;
        final String jeton; // Jeton unique pour cette entrée de file
        long monopoleDebutMs; // Heure de début garantie pour la fenêtre de monopole
        long monopoleFinMs;   // Heure de fin garantie pour la fenêtre de monopole

        EntreeFile(String adresseIp, long monopoleDebutMs, long monopoleFinMs) {
            this.adresseIp = adresseIp;
            this.horodatage = System.currentTimeMillis();
            this.monopoleDebutMs = monopoleDebutMs;
            this.monopoleFinMs = monopoleFinMs;
            this.jeton = genererJeton();
        }

        boolean estExpiree() {
            return System.currentTimeMillis() - horodatage > EXPIRATION_ENTREE_FILE_MS;
        }

        private static String genererJeton() {
            // Générer un jeton alphanumérique de 6 caractères
            String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            Random aleatoire = new Random();
            StringBuilder jeton = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                jeton.append(caracteres.charAt(aleatoire.nextInt(caracteres.length())));
            }
            return jeton.toString();
        }
    }

    /**
     * Ajouter un joueur au lobby de stationnement et démarrer le délai d'expiration
     */
    public void ajouterJoueur(ServerPlayer joueur, String typeCompte) {
        UUID idJoueur = joueur.getUUID();
        String nomJoueur = joueur.getName().getString();
        String adresseIp = joueur.getIpAddress();

        // Vérifier si le joueur vient de la file (délai plus court) en vérifiant si son compte+IP a été autorisé
        String cleCompteIp = nomJoueur.toLowerCase() + ":" + adresseIp;
        boolean depuisFile = ipsAutoriseesDepuisFile.remove(cleCompteIp) != null;
        long delaiExpiration = depuisFile ? DELAI_EXPIRATION_AUTH_DEPUIS_FILE_MS : DELAI_EXPIRATION_AUTH_MS;
        int secondesExpiration = (int)(delaiExpiration / 1000);

        // Créer la session
        SessionAuth session = new SessionAuth(typeCompte, nomJoueur, adresseIp, delaiExpiration);
        sessionsActives.put(idJoueur, session);

        // Planifier l'expulsion par expiration
        session.tacheExpiration = new TimerTask() {
            @Override
            public void run() {
                // Exécuter sur le thread du serveur
                if (joueur.server != null) {
                    joueur.server.execute(() -> {
                        if (estDansLobbyStationnement(idJoueur)) {
                            MonSubMod.JOURNALISEUR.warn("Le joueur {} a expiré pendant l'authentification ({}s)", nomJoueur, secondesExpiration);
                            joueur.connection.disconnect(Component.literal(
                                "§c§lTemps d'authentification écoulé\n\n" +
                                "§7Vous aviez " + secondesExpiration + " secondes pour vous authentifier."
                            ));
                            retirerJoueur(idJoueur);

                            // Notifier la file : autoriser la prochaine personne en attente (0ms restants car expiration)
                            autoriserSuivantDansFile(nomJoueur, 0);
                        }
                    });
                }
            }
        };

        minuterieExpiration.schedule(session.tacheExpiration, delaiExpiration);

        // Créer une barrière invisible autour de la position du lobby de stationnement
        creerBarrieresLobbyStationnement(joueur.serverLevel());

        MonSubMod.JOURNALISEUR.info("Joueur {} ajouté au lobby de stationnement en tant que {} ({}s d'expiration, depuisFile: {})",
            nomJoueur, typeCompte, secondesExpiration, depuisFile);
    }

    /**
     * Créer des barrières invisibles (blocs de barrière) autour de la position du lobby de stationnement
     * pour empêcher tout mouvement
     * Crée uniquement des barrières si elles n'existent pas déjà (plusieurs joueurs peuvent être dans le lobby)
     */
    private void creerBarrieresLobbyStationnement(ServerLevel monde) {
        BlockPos centre = new BlockPos(10000, 200, 10000);
        int rayon = 3; // rayon de 3 blocs autour du joueur
        int hauteur = 5; // 5 blocs de haut

        // Créer une boîte de barrières autour du joueur
        for (int x = -rayon; x <= rayon; x++) {
            for (int y = -1; y <= hauteur; y++) {
                for (int z = -rayon; z <= rayon; z++) {
                    // Placer uniquement des barrières sur les bords (boîte creuse)
                    boolean estBord = Math.abs(x) == rayon || Math.abs(z) == rayon || y == -1 || y == hauteur;

                    if (estBord) {
                        BlockPos posBarriere = centre.offset(x, y, z);
                        // Placer uniquement si ce n'est pas déjà une barrière (peut provenir d'un autre joueur)
                        if (!monde.getBlockState(posBarriere).is(Blocks.BARRIER)) {
                            monde.setBlock(posBarriere, Blocks.BARRIER.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    /**
     * Supprimer les barrières autour de la position du lobby de stationnement
     */
    private void supprimerBarrieresLobbyStationnement(ServerLevel monde) {
        BlockPos centre = new BlockPos(10000, 200, 10000);
        int rayon = 3;
        int hauteur = 5;

        for (int x = -rayon; x <= rayon; x++) {
            for (int y = -1; y <= hauteur; y++) {
                for (int z = -rayon; z <= rayon; z++) {
                    boolean estBord = Math.abs(x) == rayon || Math.abs(z) == rayon || y == -1 || y == hauteur;

                    if (estBord) {
                        BlockPos posBarriere = centre.offset(x, y, z);
                        // Supprimer uniquement si c'est un bloc de barrière
                        if (monde.getBlockState(posBarriere).is(Blocks.BARRIER)) {
                            monde.setBlock(posBarriere, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    /**
     * Retirer un joueur du lobby de stationnement (authentifié ou expulsé)
     */
    public void retirerJoueur(UUID idJoueur) {
        SessionAuth session = sessionsActives.remove(idJoueur);
        if (session != null && session.tacheExpiration != null) {
            session.tacheExpiration.cancel();
        }
    }

    /**
     * Retirer un joueur du lobby de stationnement et nettoyer les barrières
     */
    public void retirerJoueur(UUID idJoueur, ServerLevel monde) {
        SessionAuth session = sessionsActives.remove(idJoueur);
        if (session != null && session.tacheExpiration != null) {
            session.tacheExpiration.cancel();
        }

        // Nettoyer le suivi des candidats si ce joueur était un candidat de file
        boolean etaitCandidat = false;
        String nomCompte = null;

        // Trouver à quel compte appartient ce candidat
        for (Map.Entry<String, Set<UUID>> entree : candidatsFile.entrySet()) {
            if (entree.getValue().contains(idJoueur)) {
                nomCompte = entree.getKey();
                etaitCandidat = true;
                break;
            }
        }

        // Si le joueur était un candidat de file, nettoyer tout le suivi
        if (etaitCandidat && nomCompte != null) {
            retirerCandidatFile(nomCompte, idJoueur);
            MonSubMod.JOURNALISEUR.info("Nettoyage du suivi du candidat de file pour le joueur {} du compte {} lors de la déconnexion",
                idJoueur, nomCompte);
        }

        // Supprimer les barrières uniquement si aucun autre joueur n'est dans le lobby de stationnement
        if (sessionsActives.isEmpty()) {
            supprimerBarrieresLobbyStationnement(monde);
        }
    }

    /**
     * Effacer la file pour un compte (appelé lorsque l'authentification réussit)
     */
    public void effacerFilePourCompte(String nomCompte) {
        Queue<EntreeFile> file = filesAttente.remove(nomCompte);
        if (file != null) {
            int effaces = file.size();
            MonSubMod.JOURNALISEUR.info("File effacée pour {} ({} entrées supprimées après authentification réussie)", nomCompte, effaces);
        }

        // Également effacer tous les candidats de file s'il y en a
        Set<UUID> candidats = candidatsFile.remove(nomCompte);
        if (candidats != null && !candidats.isEmpty()) {
            MonSubMod.JOURNALISEUR.info("Effacement de {} candidat(s) de file pour {} après authentification réussie", candidats.size(), nomCompte);
            // Nettoyer le suivi IP et le temps de connexion pour ces candidats
            for (UUID idCandidat : candidats) {
                ipsCandidats.remove(idCandidat);
                tempJointureCandidats.remove(idCandidat);
            }
        }

        // Également supprimer toute liste blanche temporaire
        listeBlancheTemporaire.remove(nomCompte);
        expirationListeBlanche.remove(nomCompte);
    }

    /**
     * Vérifier si une file existe pour ce compte (soit file d'attente, soit autorisation IP active)
     */
    public boolean aUneFile(String nomCompte) {
        // Vérifier s'il y a des personnes en attente dans la file
        Queue<EntreeFile> file = filesAttente.get(nomCompte);
        if (file != null && !file.isEmpty()) {
            return true;
        }

        // Vérifier s'il y a une autorisation IP active (fenêtre de monopole)
        if (listeBlancheTemporaire.containsKey(nomCompte)) {
            // Vérifier que l'autorisation n'a pas expiré
            Long tempsExpiration = expirationListeBlanche.get(nomCompte);
            if (tempsExpiration != null && System.currentTimeMillis() < tempsExpiration) {
                return true; // Fenêtre de monopole active
            }
        }

        return false;
    }

    /**
     * Vérifier s'il y a de la place dans la file pour ce compte
     * Compte uniquement les entrées de file réelles (personnes ayant entré le bon mot de passe)
     * Les candidats de file (en attente de mot de passe) NE comptent PAS - ils peuvent être multiples
     * Retourne true si taille file < TAILLE_MAX_FILE
     */
    public boolean aPlaceDansFile(String nomCompte) {
        // Vérifier la taille actuelle de la file (uniquement les personnes ayant entré le mot de passe)
        Queue<EntreeFile> file = filesAttente.get(nomCompte);
        int tailleFile = (file != null) ? file.size() : 0;

        // Place dans la file si la taille réelle de la file est en dessous de la limite
        // Les candidats ne comptent pas - plusieurs personnes peuvent essayer d'entrer le mot de passe
        return tailleFile < TAILLE_MAX_FILE;
    }

    /**
     * Marquer un joueur comme candidat de file (en attente de vérification du mot de passe)
     * Plusieurs candidats peuvent exister pour le même compte
     * Protection DoS : Applique des limites par IP avec éviction basée sur l'âge
     * @param nomCompte Le nom du compte
     * @param idJoueur L'UUID du joueur
     * @param adresseIp L'adresse IP du joueur
     * @param serveur L'instance du serveur (pour expulser les anciens candidats si nécessaire)
     * @return true si ajouté avec succès, false si les limites sont dépassées et aucun candidat évictable n'a été trouvé
     */
    public boolean ajouterCandidatFile(String nomCompte, UUID idJoueur, String adresseIp, net.minecraft.server.MinecraftServer serveur) {
        String ipSeule = extraireIpSansPort(adresseIp);
        long maintenant = System.currentTimeMillis();

        // Vérifier limite 1 : Max candidats par compte depuis la même IP
        int compteDepuisCetteIp = 0;
        UUID plusVieuxEvictablePourCompte = null;
        long plusVieuxTempsPourCompte = Long.MAX_VALUE;

        Set<UUID> candidats = candidatsFile.get(nomCompte);
        if (candidats != null) {
            for (UUID idCandidat : candidats) {
                String ipCandidat = ipsCandidats.get(idCandidat);
                if (ipCandidat != null && extraireIpSansPort(ipCandidat).equals(ipSeule)) {
                    compteDepuisCetteIp++;

                    // Suivre le candidat évictable le plus vieux (≥20s)
                    Long tempsJointure = tempJointureCandidats.get(idCandidat);
                    if (tempsJointure != null) {
                        long age = maintenant - tempsJointure;
                        if (age >= AGE_MIN_CANDIDAT_POUR_EVICTION_MS && tempsJointure < plusVieuxTempsPourCompte) {
                            plusVieuxTempsPourCompte = tempsJointure;
                            plusVieuxEvictablePourCompte = idCandidat;
                        }
                    }
                }
            }
        }

        // Appliquer la limite : si l'ajout de ce candidat dépasserait la limite, nous devons évincer avant d'ajouter
        if (compteDepuisCetteIp >= MAX_CANDIDATS_PAR_COMPTE_PAR_IP) {
            // Essayer d'évincer le candidat le plus vieux si disponible
            if (plusVieuxEvictablePourCompte != null) {
                evincerCandidat(plusVieuxEvictablePourCompte, nomCompte, serveur, "evicted_for_new_candidate_account");
                MonSubMod.JOURNALISEUR.info("Protection DoS : Ancien candidat {} évincé du compte {} pour faire place au nouveau candidat (âge : {}s)",
                    plusVieuxEvictablePourCompte, nomCompte, (maintenant - plusVieuxTempsPourCompte) / 1000);
                compteDepuisCetteIp--; // Décrémenter le compte après éviction
            } else {
                MonSubMod.JOURNALISEUR.warn("Protection DoS : L'IP {} a dépassé le max de candidats par compte pour {} ({}/{}) - aucun candidat évictable",
                    ipSeule, nomCompte, compteDepuisCetteIp, MAX_CANDIDATS_PAR_COMPTE_PAR_IP);
                return false;
            }
        }

        // Vérifier limite 2 : Max total de candidats _Q_ depuis cette IP à travers tous les comptes
        int totalCandidatsDepuisIp = 0;
        UUID plusVieuxEvictableGlobal = null;
        long plusVieuxTempsGlobal = Long.MAX_VALUE;
        String compteEvictablePlusVieux = null;

        for (Map.Entry<String, Set<UUID>> entree : candidatsFile.entrySet()) {
            for (UUID idCandidat : entree.getValue()) {
                String ipCandidat = ipsCandidats.get(idCandidat);
                if (ipCandidat != null && extraireIpSansPort(ipCandidat).equals(ipSeule)) {
                    totalCandidatsDepuisIp++;

                    // Suivre le candidat évictable le plus vieux globalement
                    Long tempsJointure = tempJointureCandidats.get(idCandidat);
                    if (tempsJointure != null) {
                        long age = maintenant - tempsJointure;
                        if (age >= AGE_MIN_CANDIDAT_POUR_EVICTION_MS && tempsJointure < plusVieuxTempsGlobal) {
                            plusVieuxTempsGlobal = tempsJointure;
                            plusVieuxEvictableGlobal = idCandidat;
                            compteEvictablePlusVieux = entree.getKey();
                        }
                    }
                }
            }
        }

        // Appliquer la limite : si nous sommes à la limite, nous devons évincer avant d'ajouter
        if (totalCandidatsDepuisIp >= MAX_CANDIDATS_PAR_IP_GLOBAL) {
            // Essayer d'évincer le candidat le plus vieux globalement si disponible
            if (plusVieuxEvictableGlobal != null) {
                evincerCandidat(plusVieuxEvictableGlobal, compteEvictablePlusVieux, serveur, "evicted_for_new_candidate_global");
                MonSubMod.JOURNALISEUR.info("Protection DoS : Ancien candidat {} évincé globalement (compte : {}) pour faire place au nouveau candidat (âge : {}s)",
                    plusVieuxEvictableGlobal, compteEvictablePlusVieux, (maintenant - plusVieuxTempsGlobal) / 1000);
                totalCandidatsDepuisIp--; // Décrémenter le compte après éviction
            } else {
                MonSubMod.JOURNALISEUR.warn("Protection DoS : L'IP {} a dépassé le max global de candidats ({}/{}) - aucun candidat évictable",
                    ipSeule, totalCandidatsDepuisIp, MAX_CANDIDATS_PAR_IP_GLOBAL);
                return false;
            }
        }

        // Ajouter le candidat
        candidatsFile.computeIfAbsent(nomCompte, k -> ConcurrentHashMap.newKeySet()).add(idJoueur);
        ipsCandidats.put(idJoueur, adresseIp);
        tempJointureCandidats.put(idJoueur, maintenant);

        // Logger avec des comptes précis (après éviction et ajout)
        MonSubMod.JOURNALISEUR.info("Joueur {} marqué comme candidat de file pour {} (IP : {}, candidats de compte depuis IP : {}, candidats globaux depuis IP : {})",
            idJoueur, nomCompte, ipSeule, compteDepuisCetteIp + 1, totalCandidatsDepuisIp + 1);
        return true;
    }

    /**
     * Évincer un candidat (les expulser pour faire place à un nouveau candidat)
     */
    private void evincerCandidat(UUID idCandidat, String nomCompte, net.minecraft.server.MinecraftServer serveur, String raison) {
        // Retirer du suivi
        retirerCandidatFile(nomCompte, idCandidat);

        // Expulser le joueur
        net.minecraft.server.level.ServerPlayer joueur = serveur.getPlayerList().getPlayer(idCandidat);
        if (joueur != null) {
            String message;
            if ("evicted_for_new_candidate_account".equals(raison)) {
                message = "§c§lConnexion remplacée\n\n" +
                          "§eUn nouveau candidat a pris votre place.\n" +
                          "§7Vous étiez en attente depuis trop longtemps (>20s).\n\n" +
                          "§7Veuillez réessayer.";
            } else if ("evicted_for_new_candidate_global".equals(raison)) {
                message = "§c§lConnexion remplacée\n\n" +
                          "§eTrop de tentatives depuis votre IP.\n" +
                          "§7Un nouveau candidat a pris votre place.\n\n" +
                          "§7Veuillez réessayer.";
            } else {
                message = "§c§lConnexion remplacée\n\n" +
                          "§7" + raison;
            }

            joueur.connection.disconnect(Component.literal(message));
            MonSubMod.JOURNALISEUR.info("Candidat {} évincé du compte {} - raison : {}", idCandidat, nomCompte, raison);
        }
    }

    /**
     * Vérifier si un joueur est un candidat de file
     */
    public boolean estCandidatFile(UUID idJoueur) {
        for (Set<UUID> candidats : candidatsFile.values()) {
            if (candidats.contains(idJoueur)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retirer un candidat de file spécifique par UUID (mot de passe échoué ou expulsé)
     */
    public void retirerCandidatFilePublic(String nomCompte, UUID idJoueur) {
        Set<UUID> candidats = candidatsFile.get(nomCompte);
        if (candidats != null) {
            boolean retire = candidats.remove(idJoueur);
            if (retire) {
                MonSubMod.JOURNALISEUR.info("Candidat de file retiré pour {} : {}", nomCompte, idJoueur);
                // Nettoyer les ensembles vides
                if (candidats.isEmpty()) {
                    candidatsFile.remove(nomCompte);
                }
            }
        }

        // Nettoyer le suivi IP et le temps de jointure
        ipsCandidats.remove(idJoueur);
        tempJointureCandidats.remove(idJoueur);
    }

    /**
     * Promouvoir un candidat de file en entrée de file réelle (après vérification réussie du mot de passe)
     * Retourne la position dans la file, ou -1 si la file est pleine
     * Thread-safe : utilise un bloc synchronized pour éviter les conditions de course
     */
    public synchronized int promouvoirCandidatFileVersFile(String nomCompte, UUID idJoueur, String adresseIp) {
        Set<UUID> candidats = candidatsFile.get(nomCompte);
        if (candidats == null || !candidats.remove(idJoueur)) {
            MonSubMod.JOURNALISEUR.warn("Tentative de promotion d'un candidat de file inexistant {} pour {}", idJoueur, nomCompte);
            return -1;
        }

        // Nettoyer les ensembles vides
        if (candidats.isEmpty()) {
            candidatsFile.remove(nomCompte);
        }

        // Vérifier si la file est pleine AVANT d'ajouter (vérification thread-safe)
        Queue<EntreeFile> file = filesAttente.get(nomCompte);
        int tailleActuelle = (file != null) ? file.size() : 0;

        if (tailleActuelle >= TAILLE_MAX_FILE) {
            // File pleine - ré-ajouter comme candidat et rejeter
            candidats = candidatsFile.computeIfAbsent(nomCompte, k -> ConcurrentHashMap.newKeySet());
            candidats.add(idJoueur);
            MonSubMod.JOURNALISEUR.warn("File pleine pour {} - candidat {} rejeté (taille actuelle : {})",
                nomCompte, idJoueur, tailleActuelle);
            return -1;
        }

        // Ajouter à la file réelle (mais NE PAS utiliser la fenêtre future calculée - commencer immédiatement)
        int position = ajouterDansFileImmediatement(nomCompte, adresseIp);
        MonSubMod.JOURNALISEUR.info("Candidat de file promu à la file pour {} à la position {}", nomCompte, position);

        return position;
    }

    /**
     * Ajouter une IP à la file avec fenêtre de monopole IMMÉDIATE (commence MAINTENANT, pas dans le futur)
     * Utilisé quand quelqu'un entre le mot de passe pendant que indiv1 s'authentifie
     * @return position dans la file (toujours 1 pour la première entrée)
     */
    private synchronized int ajouterDansFileImmediatement(String nomCompte, String adresseIp) {
        // Extraire l'IP sans port
        String ipSeule = extraireIpSansPort(adresseIp);

        // Obtenir ou créer la file pour ce compte
        Queue<EntreeFile> file = filesAttente.computeIfAbsent(nomCompte, k -> new ConcurrentLinkedQueue<>());

        // Vérifier si la file est pleine
        if (file.size() >= TAILLE_MAX_FILE) {
            MonSubMod.JOURNALISEUR.warn("File pleine pour {} - rejet de l'IP {}", nomCompte, ipSeule);
            return -1;
        }

        // Calculer la fenêtre de monopole IMMÉDIATE (commence MAINTENANT)
        long monopoleDebutMs = System.currentTimeMillis();
        long monopoleFinMs = monopoleDebutMs + EXPIRATION_LISTE_BLANCHE_MS; // 45 secondes à partir de MAINTENANT

        // Ajouter à la file avec fenêtre immédiate
        EntreeFile nouvelleEntree = new EntreeFile(ipSeule, monopoleDebutMs, monopoleFinMs);
        file.add(nouvelleEntree);
        int position = file.size();

        MonSubMod.JOURNALISEUR.info("IP {} ajoutée à la file pour {} à la position {} avec fenêtre de monopole IMMÉDIATE (commence MAINTENANT, se termine dans 45s, jeton : {})",
            ipSeule, nomCompte, position, nouvelleEntree.jeton);

        return position;
    }

    /**
     * Expulser le joueur en train de s'authentifier sur ce compte
     * Appelé quand quelqu'un de la file est promu (indiv2 prend le relais)
     * @param nomCompte Le nom du compte
     * @param serveur L'instance du serveur Minecraft
     */
    public void expulserJoueurEnAuthentification(String nomCompte, net.minecraft.server.MinecraftServer serveur) {
        // Trouver le joueur qui s'authentifie actuellement sur ce compte
        for (Map.Entry<UUID, SessionAuth> entree : sessionsActives.entrySet()) {
            if (entree.getValue().nomJoueur.equalsIgnoreCase(nomCompte)) {
                UUID uuidJoueur = entree.getKey();
                net.minecraft.server.level.ServerPlayer joueur = serveur.getPlayerList().getPlayer(uuidJoueur);

                if (joueur != null) {
                    MonSubMod.JOURNALISEUR.info("Expulsion du joueur en authentification {} - la file prend le relais", nomCompte);

                    // Expulser le joueur en authentification
                    joueur.connection.disconnect(Component.literal(
                        "§c§lConnexion interrompue\n\n" +
                        "§eUn joueur en file d'attente a priorité.\n" +
                        "§7Vous avez été déconnecté pour libérer la place."
                    ));

                    // Retirer du lobby de stationnement
                    retirerJoueur(uuidJoueur);
                }
                break;
            }
        }
    }

    /**
     * Expulser tous les candidats de file restants pour un compte
     * Appelé quand quelqu'un entre avec succès dans la file OU quand l'authentification réussit
     * @param nomCompte Le nom du compte
     * @param serveur L'instance du serveur Minecraft
     * @param raison La raison de l'expulsion (pour message personnalisé)
     */
    public void expulserCandidatsFileRestants(String nomCompte, net.minecraft.server.MinecraftServer serveur, String raison) {
        MonSubMod.JOURNALISEUR.info("DEBUG : expulserCandidatsFileRestants appelé pour le compte : {}, raison : {}", nomCompte, raison);
        MonSubMod.JOURNALISEUR.info("DEBUG : contenu de candidatsFile : {}", candidatsFile.keySet());

        Set<UUID> candidats = candidatsFile.remove(nomCompte);
        if (candidats == null || candidats.isEmpty()) {
            MonSubMod.JOURNALISEUR.warn("DEBUG : Aucun candidat de file trouvé pour le compte : {}", nomCompte);
            return;
        }

        MonSubMod.JOURNALISEUR.info("Expulsion de {} candidat(s) de file restant(s) pour {} - raison : {}",
            candidats.size(), nomCompte, raison);

        // Trouver et expulser tous les joueurs qui sont encore candidats de file
        for (UUID uuidCandidat : candidats) {
            net.minecraft.server.level.ServerPlayer joueur = serveur.getPlayerList().getPlayer(uuidCandidat);
            if (joueur != null) {
                // Trouver le nom du joueur depuis les sessions actives
                SessionAuth session = sessionsActives.get(uuidCandidat);
                String nomCandidat = session != null ? session.nomJoueur : "inconnu";

                MonSubMod.JOURNALISEUR.info("Expulsion du candidat de file {} (nom temp : {}) - {}",
                    uuidCandidat, nomCandidat, raison);

                // Expulser le joueur avec le message approprié
                String message;
                if ("queue_full".equals(raison)) {
                    message = "§c§lFile d'attente complète\n\n" +
                              "§eUn autre joueur a entré le mot de passe avant vous.\n" +
                              "§7La file d'attente est limitée à §e1 personne§7.";
                } else if ("auth_success".equals(raison)) {
                    message = "§c§lFile d'attente annulée\n\n" +
                              "§eLe joueur s'est authentifié avec succès.\n" +
                              "§7La file d'attente n'est plus nécessaire.";
                } else {
                    message = "§c§lFile d'attente annulée\n\n" +
                              "§7" + raison;
                }

                joueur.connection.disconnect(Component.literal(message));
            }

            // Nettoyer le suivi IP et le temps de jointure
            ipsCandidats.remove(uuidCandidat);
            tempJointureCandidats.remove(uuidCandidat);
        }
    }

    /**
     * Expulser tous les candidats de file restants (méthode de commodité avec raison par défaut)
     */
    public void expulserCandidatsFileRestants(String nomCompte, net.minecraft.server.MinecraftServer serveur) {
        expulserCandidatsFileRestants(nomCompte, serveur, "queue_full");
    }

    /**
     * Vérifier si le joueur est dans le lobby de stationnement
     */
    public boolean estDansLobbyStationnement(UUID idJoueur) {
        return sessionsActives.containsKey(idJoueur);
    }

    /**
     * Obtenir le type de compte pour le joueur dans le lobby de stationnement
     */
    public String obtenirTypeCompte(UUID idJoueur) {
        SessionAuth session = sessionsActives.get(idJoueur);
        return session != null ? session.typeCompte : null;
    }

    /**
     * Obtenir le temps restant avant expiration (en secondes)
     */
    public int obtenirTempsRestant(UUID idJoueur) {
        SessionAuth session = sessionsActives.get(idJoueur);
        if (session == null) return 0;

        long ecoule = System.currentTimeMillis() - session.heureDebut;
        long restant = DELAI_EXPIRATION_AUTH_MS - ecoule;
        return Math.max(0, (int)(restant / 1000));
    }

    /**
     * Vérifier si une IP s'authentifie actuellement sur un compte
     */
    public boolean estIpEnCoursAuthentificationSurCompte(String nomCompte, String adresseIp) {
        String ipSeule = extraireIpSansPort(adresseIp);
        for (SessionAuth session : sessionsActives.values()) {
            String ipSession = extraireIpSansPort(session.adresseIp);
            if (session.nomJoueur.equalsIgnoreCase(nomCompte) && ipSession.equals(ipSeule)) {
                MonSubMod.JOURNALISEUR.info("L'IP {} s'authentifie actuellement sur {}", ipSeule, nomCompte);
                return true;
            }
        }
        return false;
    }

    /**
     * Vérifier si quelqu'un s'authentifie actuellement sur un compte (peu importe l'IP)
     */
    public boolean estCompteEnCoursAuthentification(String nomCompte) {
        for (SessionAuth session : sessionsActives.values()) {
            if (session.nomJoueur.equalsIgnoreCase(nomCompte)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Obtenir les sessions actives (pour vérifier les candidats de file)
     */
    public Map<UUID, String> obtenirJoueursSessionsActives() {
        Map<UUID, String> resultat = new java.util.HashMap<>();
        for (Map.Entry<UUID, SessionAuth> entree : sessionsActives.entrySet()) {
            resultat.put(entree.getKey(), entree.getValue().nomJoueur);
        }
        return resultat;
    }

    /**
     * Enregistrer un mappage de nom temporaire pour candidat de file
     */
    public void enregistrerNomTemporaire(String nomTemporaire, String nomCompteOriginal) {
        nomTemporaireVersCompte.put(nomTemporaire, nomCompteOriginal);
        MonSubMod.JOURNALISEUR.info("Nom temporaire enregistré : {} -> {}", nomTemporaire, nomCompteOriginal);
    }

    /**
     * Obtenir le nom de compte original à partir du nom temporaire
     */
    public String obtenirNomCompteOriginal(String nomTemporaire) {
        return nomTemporaireVersCompte.get(nomTemporaire);
    }

    /**
     * Supprimer le mappage de nom temporaire
     */
    public void supprimerNomTemporaire(String nomTemporaire) {
        nomTemporaireVersCompte.remove(nomTemporaire);
    }

    /**
     * Vérifier si un nom de joueur est un nom de candidat de file temporaire
     */
    public boolean estNomFileTemporaire(String nomJoueur) {
        // Vérifier si le nom existe dans notre mappage de noms temporaires
        // Cela empêche les exploits où un joueur libre crée un compte commençant par "_Q_"
        return nomJoueur != null && nomTemporaireVersCompte.containsKey(nomJoueur);
    }

    /**
     * Obtenir tous les UUIDs de joueurs avec la même IP s'authentifiant sur le même compte
     * Utilisé pour diffuser les mises à jour du nombre de tentatives
     * Inclut à la fois les joueurs en authentification normale ET les candidats de file
     */
    public List<UUID> obtenirJoueursMemeIpEtCompte(String nomCompte, String adresseIp, UUID exclureUuid) {
        List<UUID> resultat = new java.util.ArrayList<>();
        String ipSeule = extraireIpSansPort(adresseIp);

        // Vérifier les sessions actives (joueurs en authentification normale)
        for (Map.Entry<UUID, SessionAuth> entree : sessionsActives.entrySet()) {
            UUID uuid = entree.getKey();
            SessionAuth session = entree.getValue();

            // Ignorer le joueur que nous voulons exclure (celui qui vient d'essayer)
            if (uuid.equals(exclureUuid)) {
                continue;
            }

            // Vérifier si même compte et même IP
            String ipSession = extraireIpSansPort(session.adresseIp);
            if (session.nomJoueur.equalsIgnoreCase(nomCompte) && ipSession.equals(ipSeule)) {
                resultat.add(uuid);
            }
        }

        // Vérifier également les candidats de file pour ce compte (recherche insensible à la casse)
        for (Map.Entry<String, Set<UUID>> entree : candidatsFile.entrySet()) {
            if (entree.getKey().equalsIgnoreCase(nomCompte)) {
                for (UUID uuidCandidat : entree.getValue()) {
                    // Ignorer le joueur que nous voulons exclure
                    if (uuidCandidat.equals(exclureUuid)) {
                        continue;
                    }

                    // Vérifier si même IP
                    String ipCandidat = ipsCandidats.get(uuidCandidat);
                    if (ipCandidat != null && extraireIpSansPort(ipCandidat).equals(ipSeule)) {
                        // Éviter les doublons (au cas où le joueur serait dans les deux maps)
                        if (!resultat.contains(uuidCandidat)) {
                            resultat.add(uuidCandidat);
                            MonSubMod.JOURNALISEUR.debug("Candidat de file {} trouvé avec la même IP {} pour le compte {}",
                                uuidCandidat, ipSeule, nomCompte);
                        }
                    }
                }
            }
        }

        MonSubMod.JOURNALISEUR.debug("obtenirJoueursMemeIpEtCompte({}, {}) a trouvé {} autre(s) joueur(s)",
            nomCompte, ipSeule, resultat.size());

        return resultat;
    }

    /**
     * Extraire l'adresse IP sans port et sans barre oblique initiale
     * Gère les formats IPv4 (/127.0.0.1:port) et IPv6 (/[::1]:port)
     */
    private String extraireIpSansPort(String adresseIp) {
        if (adresseIp == null) return adresseIp;

        String resultat = adresseIp;

        // Supprimer la barre oblique initiale si présente
        if (resultat.startsWith("/")) {
            resultat = resultat.substring(1);
        }

        // Vérifier le format IPv6 : [adresse]:port
        int indexCrochet = resultat.lastIndexOf(']');
        if (indexCrochet > 0) {
            // IPv6 - extraire tout jusqu'au crochet fermant inclus
            return resultat.substring(0, indexCrochet + 1);
        }

        // Format IPv4 : adresse:port
        int indexDeuxPoints = resultat.lastIndexOf(':');
        if (indexDeuxPoints > 0) {
            return resultat.substring(0, indexDeuxPoints);
        }

        return resultat;
    }

    /**
     * Ajouter une IP à la file pour un compte
     * Plusieurs entrées avec la même IP sont autorisées (distinguées par des jetons uniques)
     * @return position dans la file (base 1), ou -1 si rejetée
     */
    public int ajouterAFile(String nomCompte, String adresseIp) {
        // Extraire l'IP sans port pour comparaison
        String ipSeule = extraireIpSansPort(adresseIp);

        // Note : Nous NE vérifions PAS si l'IP s'authentifie actuellement
        // Le système de jetons permet à plusieurs clients de la même IP de faire la file
        // Chacun recevra un jeton unique pour les distinguer

        // Obtenir ou créer la file pour ce compte
        Queue<EntreeFile> file = filesAttente.computeIfAbsent(nomCompte, k -> new ConcurrentLinkedQueue<>());

        // Vérifier si la file est pleine (TAILLE_MAX_FILE = 1 personne maximum)
        if (file.size() >= TAILLE_MAX_FILE) {
            MonSubMod.JOURNALISEUR.warn("File pleine pour {} - rejet de l'IP {}", nomCompte, ipSeule);
            return -1; // File pleine
        }

        // Note : Nous autorisons plusieurs entrées de file avec la même IP (pour différents comptes)
        // Chaque entrée aura un jeton unique pour distinguer différents clients
        // Cela supporte plusieurs joueurs de la même IP (par ex., même foyer, même NAT)

        // Calculer la fenêtre de monopole garantie pour cette nouvelle entrée
        long[] fenetre = calculerFenetreMonopoleGarantie(nomCompte, file.size() + 1);

        // Ajouter à la file avec fenêtre garantie en utilisant l'IP normalisée (sans port, pour comparaison cohérente)
        EntreeFile nouvelleEntree = new EntreeFile(ipSeule, fenetre[0], fenetre[1]);
        file.add(nouvelleEntree);
        int position = file.size();

        MonSubMod.JOURNALISEUR.info("IP {} ajoutée à la file pour {} à la position {} avec jeton {} (fenêtre garantie {}-{})",
            ipSeule, nomCompte, position, nouvelleEntree.jeton, fenetre[0], fenetre[1]);
        return position;
    }

    /**
     * Calculer la fenêtre de monopole garantie basée sur le pire scénario
     * Cette fenêtre est GARANTIE d'être valide peu importe ce qui arrive
     */
    private long[] calculerFenetreMonopoleGarantie(String nomCompte, int position) {
        // Obtenir la session active pour ce compte pour obtenir le délai exact
        SessionAuth sessionActive = null;
        for (SessionAuth session : sessionsActives.values()) {
            if (session.nomJoueur.equalsIgnoreCase(nomCompte)) {
                sessionActive = session;
                break;
            }
        }

        long monopoleDebutMs;
        if (sessionActive != null && position == 1) {
            // Premier dans la file - garanti de commencer quand la session actuelle expire AU PLUS TARD
            monopoleDebutMs = sessionActive.obtenirTempsFinExpirationMs();
        } else if (position == 1) {
            // Premier dans la file mais aucune session active - immédiat
            monopoleDebutMs = System.currentTimeMillis();
        } else {
            // Pas premier dans la file - calculer basé sur le pire cas (tout le monde avant expire complètement)
            long tempsBase = sessionActive != null ? sessionActive.obtenirTempsFinExpirationMs() : System.currentTimeMillis();
            // Chaque personne devant obtient son délai complet (pire cas)
            // Position 2 = tempsBase + 1*60s, Position 3 = tempsBase + 2*60s, etc.
            monopoleDebutMs = tempsBase + ((position - 1) * DELAI_EXPIRATION_AUTH_MS);
        }

        long monopoleFinMs = monopoleDebutMs + EXPIRATION_LISTE_BLANCHE_MS;

        return new long[]{monopoleDebutMs, monopoleFinMs};
    }

    /**
     * Obtenir la fenêtre de monopole pour une IP en file (retourne la fenêtre garantie stockée)
     * Retourne un tableau : [tempsDebutMs, tempsFinMs] ou null si pas dans la file
     */
    public long[] obtenirFenetreMonopole(String nomCompte, String adresseIp) {
        Queue<EntreeFile> file = filesAttente.get(nomCompte);
        if (file == null || file.isEmpty()) {
            return null;
        }

        // Extraire l'IP sans port pour comparaison
        String ipSeule = extraireIpSansPort(adresseIp);

        // Trouver l'entrée et retourner sa fenêtre garantie
        for (EntreeFile entree : file) {
            if (extraireIpSansPort(entree.adresseIp).equals(ipSeule)) {
                return new long[]{entree.monopoleDebutMs, entree.monopoleFinMs};
            }
        }

        return null;
    }

    /**
     * Obtenir le jeton pour une IP en file
     * Retourne le jeton ou null si pas dans la file
     */
    public String obtenirJetonFile(String nomCompte, String adresseIp) {
        Queue<EntreeFile> file = filesAttente.get(nomCompte);
        if (file == null || file.isEmpty()) {
            return null;
        }

        // Extraire l'IP sans port pour comparaison
        String ipSeule = extraireIpSansPort(adresseIp);

        // Trouver l'entrée et retourner son jeton
        for (EntreeFile entree : file) {
            if (extraireIpSansPort(entree.adresseIp).equals(ipSeule)) {
                return entree.jeton;
            }
        }

        return null;
    }

    /**
     * Vérifier si l'IP est autorisée pour le compte (liste blanche temporaire)
     * Note : Cela vérifie uniquement l'IP, pas le jeton. Utiliser estAutoriseeAvecJeton() pour une vérification complète.
     */
    public boolean estAutorisee(String nomCompte, String adresseIp) {
        String ipAutorisee = listeBlancheTemporaire.get(nomCompte);
        if (ipAutorisee == null) {
            return false;
        }

        // Comparer les IPs sans port (permettre la reconnexion avec un port différent)
        String ipSeule = extraireIpSansPort(adresseIp);
        String ipAutoriseeSeule = extraireIpSansPort(ipAutorisee);
        if (!ipAutoriseeSeule.equals(ipSeule)) {
            return false;
        }

        // Vérifier l'expiration
        Long expiration = expirationListeBlanche.get(nomCompte);
        if (expiration == null || System.currentTimeMillis() > expiration) {
            // Expiré - supprimer
            listeBlancheTemporaire.remove(nomCompte);
            expirationListeBlanche.remove(nomCompte);
            jetonsActifs.remove(nomCompte);
            MonSubMod.JOURNALISEUR.info("Liste blanche expirée pour {} (IP : {})", nomCompte, adresseIp);
            return false;
        }

        return true;
    }

    /**
     * Vérifier si la combinaison IP + jeton est autorisée pour le compte
     */
    public boolean estAutoriseeAvecJeton(String nomCompte, String adresseIp, String jeton) {
        // D'abord vérifier si l'IP est autorisée
        if (!estAutorisee(nomCompte, adresseIp)) {
            return false;
        }

        // Ensuite vérifier le jeton
        String jetonAttendu = jetonsActifs.get(nomCompte);
        if (jetonAttendu == null || !jetonAttendu.equals(jeton)) {
            MonSubMod.JOURNALISEUR.warn("Jeton incompatible pour {} - attendu : {}, reçu : {}",
                nomCompte, jetonAttendu, jeton);
            return false;
        }

        return true;
    }

    /**
     * Obtenir le jeton actif pour un compte (pour affichage au joueur)
     */
    public String obtenirJetonActif(String nomCompte) {
        return jetonsActifs.get(nomCompte);
    }

    /**
     * Consommer l'autorisation (retirer de la liste blanche après utilisation et marquer l'IP comme provenant de la file)
     */
    public void consommerAutorisation(String nomCompte, String adresseIp) {
        listeBlancheTemporaire.remove(nomCompte);
        expirationListeBlanche.remove(nomCompte);
        jetonsActifs.remove(nomCompte); // Nettoyer le jeton

        // Marquer cette combinaison compte+IP comme provenant de la file
        String cleCompteIp = nomCompte.toLowerCase() + ":" + adresseIp;
        ipsAutoriseesDepuisFile.put(cleCompteIp, true);

        // Retirer de la file également (comparer les IPs sans port)
        Queue<EntreeFile> file = filesAttente.get(nomCompte);
        if (file != null) {
            String ipSeule = extraireIpSansPort(adresseIp);
            file.removeIf(entree -> extraireIpSansPort(entree.adresseIp).equals(ipSeule));
        }

        MonSubMod.JOURNALISEUR.info("Autorisation consommée pour {} (IP : {}, marquée comme depuisFile)", nomCompte, adresseIp);
    }

    /**
     * Marquer un joueur comme ayant échoué la vérification du jeton
     * Utilisé pour empêcher autoriserSuivantDansFile d'être appelé quand ils se déconnectent
     */
    public void marquerJoueurVerificationJetonEchouee(UUID idJoueur) {
        verificationJetonEchouee.add(idJoueur);
        MonSubMod.JOURNALISEUR.info("Joueur {} marqué comme ayant échoué la vérification du jeton", idJoueur);
    }

    /**
     * Vérifier si un joueur a échoué la vérification du jeton
     */
    public boolean aEchoueVerificationJeton(UUID idJoueur) {
        return verificationJetonEchouee.contains(idJoueur);
    }

    /**
     * Effacer la marque d'échec de vérification du jeton (appelé lors de la déconnexion)
     */
    public void effacerEchecVerificationJeton(UUID idJoueur) {
        verificationJetonEchouee.remove(idJoueur);
    }

    /**
     * Obtenir le nom du joueur depuis la session du lobby de stationnement
     */
    public String obtenirNomJoueur(UUID idJoueur) {
        SessionAuth session = sessionsActives.get(idJoueur);
        return session != null ? session.nomJoueur : null;
    }

    /**
     * Autoriser immédiatement un candidat de file promu
     * Crée l'entrée de liste blanche tout de suite (n'attend pas onPlayerLogout)
     * Appelé depuis PaquetAuthAdmin quand un candidat de file entre le bon mot de passe
     * @param nomCompte Le nom du compte
     * @param adresseIp L'adresse IP à autoriser
     */
    public void autoriserCandidatFileImmediatement(String nomCompte, String adresseIp) {
        String ipSeule = extraireIpSansPort(adresseIp);

        // Obtenir l'entrée de file pour obtenir le jeton et la fenêtre de monopole
        Queue<EntreeFile> file = filesAttente.get(nomCompte);
        if (file == null || file.isEmpty()) {
            MonSubMod.JOURNALISEUR.warn("autoriserCandidatFileImmediatement : Aucune entrée de file trouvée pour {}", nomCompte);
            return;
        }

        // Trouver l'entrée pour cette IP
        EntreeFile entree = null;
        for (EntreeFile e : file) {
            if (extraireIpSansPort(e.adresseIp).equals(ipSeule)) {
                entree = e;
                break;
            }
        }

        if (entree == null) {
            MonSubMod.JOURNALISEUR.warn("autoriserCandidatFileImmediatement : Aucune entrée de file trouvée pour l'IP {} sur le compte {}", ipSeule, nomCompte);
            return;
        }

        // Créer l'entrée de liste blanche immédiatement
        listeBlancheTemporaire.put(nomCompte, entree.adresseIp);
        expirationListeBlanche.put(nomCompte, entree.monopoleFinMs);
        jetonsActifs.put(nomCompte, entree.jeton);

        // Effacer la file - le candidat a été promu et autorisé
        filesAttente.remove(nomCompte);

        MonSubMod.JOURNALISEUR.info("IP {} immédiatement autorisée pour le compte {} avec jeton {} (fenêtre jusqu'à {}). File effacée.",
            ipSeule, nomCompte, entree.jeton, entree.monopoleFinMs);
    }

    /**
     * Autoriser la prochaine personne dans la file (public pour utilisation par les gestionnaires d'événements)
     * @param nomCompte Le nom du compte
     * @param tempsRestantMs Temps restant de la session précédente (0 si expiration, >0 si déconnexion)
     */
    public void autoriserProchainDansFile(String nomCompte, long tempsRestantMs) {
        Queue<EntreeFile> file = filesAttente.get(nomCompte);
        if (file == null || file.isEmpty()) {
            MonSubMod.JOURNALISEUR.info("Personne en attente dans la file pour {}", nomCompte);
            return;
        }

        // Retirer d'abord les entrées expirées
        file.removeIf(EntreeFile::estExpiree);

        // Obtenir le suivant dans la file
        EntreeFile suivant = file.poll();
        if (suivant == null) {
            MonSubMod.JOURNALISEUR.info("File vide après nettoyage pour {}", nomCompte);
            return;
        }

        // Calculer la fenêtre de monopole réelle (peut être plus tôt que garantie)
        long debutReelMs = System.currentTimeMillis();
        long dureeMonopole = tempsRestantMs + EXPIRATION_LISTE_BLANCHE_MS;
        long finReelleMs = debutReelMs + dureeMonopole;

        // La fin réelle doit être au moins la fin garantie (étendre si déconnexion précoce donne plus de temps)
        if (finReelleMs > suivant.monopoleFinMs) {
            suivant.monopoleFinMs = finReelleMs;
            MonSubMod.JOURNALISEUR.info("Fenêtre de monopole étendue pour {} en raison de déconnexion précoce", suivant.adresseIp);
        }

        // Ajouter à la liste blanche temporaire - utiliser le temps de fin garanti pour honorer la promesse
        listeBlancheTemporaire.put(nomCompte, suivant.adresseIp);
        expirationListeBlanche.put(nomCompte, suivant.monopoleFinMs);
        jetonsActifs.put(nomCompte, suivant.jeton); // Stocker le jeton pour vérification

        // Effacer la file - pendant la fenêtre de monopole, seule l'IP autorisée peut se connecter
        // Pas de raison de garder les autres en file puisqu'ils ne peuvent pas rejoindre pendant cette fenêtre
        filesAttente.remove(nomCompte);

        MonSubMod.JOURNALISEUR.info("IP {} autorisée pour le compte {} avec jeton {} (fenêtre jusqu'à {}). File effacée - seule l'IP autorisée peut se connecter pendant la fenêtre de monopole.",
            suivant.adresseIp, nomCompte, suivant.jeton, suivant.monopoleFinMs);
    }

    /**
     * Mettre à jour les fenêtres de monopole de la file après qu'un joueur soit autorisé tôt
     */
    private void mettreAJourFenetresFileApresAutorisation(Queue<EntreeFile> file, long nouveauTempsBase) {
        if (file.isEmpty()) return;

        int position = 1;
        for (EntreeFile entree : file) {
            // Calculer le nouveau temps de début potentiel (si tout le monde avant obtient son tour tôt)
            long nouveauDebutMs = nouveauTempsBase + ((position - 1) * DELAI_EXPIRATION_AUTH_MS);

            // Mettre à jour uniquement si le nouveau temps est PLUS TÔT (nous ne retardons jamais la fenêtre garantie)
            // Mais nous gardons le temps de fin garanti (ils obtiennent toujours leurs 30s complètes depuis le début garanti)
            if (nouveauDebutMs < entree.monopoleDebutMs) {
                long dureeGarantie = entree.monopoleFinMs - entree.monopoleDebutMs;
                entree.monopoleDebutMs = nouveauDebutMs;
                entree.monopoleFinMs = nouveauDebutMs + dureeGarantie;
                MonSubMod.JOURNALISEUR.info("Fenêtre de monopole mise à jour pour la position {} pour commencer à {} (plus tôt)",
                    position, nouveauDebutMs);
            }
            position++;
        }
    }

    /**
     * Obtenir le temps restant pour la session active sur le compte (en millisecondes)
     */
    public long obtenirTempsRestantPourCompte(String nomCompte) {
        for (SessionAuth session : sessionsActives.values()) {
            if (session.nomJoueur.equalsIgnoreCase(nomCompte)) {
                long ecoule = System.currentTimeMillis() - session.heureDebut;
                long restant = session.delaiExpirationMs - ecoule;
                return Math.max(0, restant);
            }
        }
        return 0;
    }

    /**
     * Compter le nombre total d'entrées de file pour une IP à travers tous les comptes
     */
    private int compterEntreesFilePourIp(String adresseIp) {
        String ipSeule = extraireIpSansPort(adresseIp);
        int compte = 0;
        for (Queue<EntreeFile> file : filesAttente.values()) {
            for (EntreeFile entree : file) {
                String ipEntree = extraireIpSansPort(entree.adresseIp);
                if (ipEntree.equals(ipSeule) && !entree.estExpiree()) {
                    compte++;
                }
            }
        }
        return compte;
    }

    /**
     * Obtenir la position d'une IP dans la file
     */
    private int obtenirPositionDansFile(Queue<EntreeFile> file, String adresseIp) {
        String ipSeule = extraireIpSansPort(adresseIp);
        int position = 1;
        for (EntreeFile entree : file) {
            if (extraireIpSansPort(entree.adresseIp).equals(ipSeule)) {
                return position;
            }
            position++;
        }
        return -1;
    }

    /**
     * Nettoyer les entrées de file et listes blanches expirées
     */
    private void nettoyerEntreesExpirees() {
        // Nettoyer les entrées de file expirées
        for (Map.Entry<String, Queue<EntreeFile>> entree : filesAttente.entrySet()) {
            Queue<EntreeFile> file = entree.getValue();
            file.removeIf(EntreeFile::estExpiree);

            // Supprimer les files vides
            if (file.isEmpty()) {
                filesAttente.remove(entree.getKey());
            }
        }

        // Nettoyer les listes blanches expirées
        long maintenant = System.currentTimeMillis();
        expirationListeBlanche.entrySet().removeIf(entree -> {
            if (maintenant > entree.getValue()) {
                listeBlancheTemporaire.remove(entree.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Obtenir la taille de la file pour un compte
     */
    public int obtenirTailleFile(String nomCompte) {
        Queue<EntreeFile> file = filesAttente.get(nomCompte);
        return file != null ? file.size() : 0;
    }

    /**
     * Nettoyage lors de l'arrêt du serveur
     */
    public void arreter() {
        minuterieExpiration.cancel();
        sessionsActives.clear();
        filesAttente.clear();
        listeBlancheTemporaire.clear();
        expirationListeBlanche.clear();
        ipsAutoriseesDepuisFile.clear();
        ipsCandidats.clear();
        tempJointureCandidats.clear();
    }

    /**
     * Retirer un candidat de file
     */
    private void retirerCandidatFile(String nomCompte, UUID idJoueur) {
        retirerCandidatFilePublic(nomCompte, idJoueur);
    }

    /**
     * Autoriser le suivant dans la file
     */
    private void autoriserSuivantDansFile(String nomCompte, long tempsRestantMs) {
        autoriserProchainDansFile(nomCompte, tempsRestantMs);
    }
}

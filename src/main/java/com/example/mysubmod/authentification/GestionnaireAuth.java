package com.example.mysubmod.authentification;

import com.example.mysubmod.MonSubMod;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire d'authentification unifié pour les admins et les joueurs protégés
 * Gère le hachage des mots de passe, la mise sur liste noire et la limitation du taux
 */
public class GestionnaireAuth {
    private static GestionnaireAuth instance;

    // État d'exécution
    private final Set<UUID> utilisateursAuthentifies = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> protectionAuthentificationParCompte = new ConcurrentHashMap<>(); // nomCompte -> heure de fin de protection

    private static final int TENTATIVES_MAX = 3;
    private static final long DUREE_LISTE_NOIRE_COMPTE = 3 * 60 * 1000; // 3 minutes
    private static final long DUREE_LISTE_NOIRE_IP_BASE = 3 * 60 * 1000; // 3 minutes (base pour l'escalade)
    private static final long TEMPS_REINITIALISATION_ECHECS = 24 * 60 * 60 * 1000; // 24 heures
    private static final long DUREE_PROTECTION_AUTH = 30 * 1000; // 30 secondes

    public enum TypeCompte {
        ADMIN,
        JOUEUR_PROTEGE,
        JOUEUR_LIBRE
    }

    private GestionnaireAuth() {
        // Aucune initialisation nécessaire - utilise StockageIdentifiants
    }

    private JsonObject obtenirInformationsIdentification() {
        return StockageIdentifiants.getInstance().obtenirIdentifiants();
    }

    public static GestionnaireAuth getInstance() {
        if (instance == null) {
            instance = new GestionnaireAuth();
        }
        return instance;
    }


    /**
     * Obtenir le type de compte pour un nom de joueur
     */
    public TypeCompte obtenirTypeCompte(String nomJoueur) {
        if (GestionnaireAuthAdmin.getInstance().estCompteAdmin(nomJoueur)) {
            return TypeCompte.ADMIN;
        }

        JsonObject joueursProtèges = obtenirInformationsIdentification().getAsJsonObject("protected_players");
        if (joueursProtèges.has(nomJoueur)) {
            return TypeCompte.JOUEUR_PROTEGE;
        }

        return TypeCompte.JOUEUR_LIBRE;
    }

    /**
     * Vérifier si le compte nécessite une authentification
     */
    public boolean necessiteAuthentification(String nomJoueur) {
        TypeCompte typeCompte = obtenirTypeCompte(nomJoueur);
        return typeCompte == TypeCompte.ADMIN || typeCompte == TypeCompte.JOUEUR_PROTEGE;
    }

    /**
     * Ajouter un compte de joueur protégé
     */
    public boolean ajouterJoueurProtege(String nomJoueur, String motDePasse) {
        if (nomJoueur == null || motDePasse == null || motDePasse.isEmpty()) {
            return false;
        }

        JsonObject joueursProtèges = obtenirInformationsIdentification().getAsJsonObject("protected_players");
        if (joueursProtèges.has(nomJoueur)) {
            MonSubMod.JOURNALISEUR.warn("Le joueur protégé {} existe déjà", nomJoueur);
            return false;
        }

        // Générer le sel et le hachage
        String sel = genererSel();
        String motDePasseHache = hacherMotDePasse(motDePasse, sel);

        JsonObject donneesJoueur = new JsonObject();
        donneesJoueur.addProperty("salt", sel);
        donneesJoueur.addProperty("password", motDePasseHache);
        donneesJoueur.addProperty("failures", 0);
        donneesJoueur.addProperty("lastFailure", 0);

        joueursProtèges.add(nomJoueur, donneesJoueur);
        StockageIdentifiants.getInstance().sauvegarder();

        MonSubMod.JOURNALISEUR.info("Joueur protégé ajouté: {}", nomJoueur);
        return true;
    }

    /**
     * Retirer un compte de joueur protégé
     */
    public boolean retirerJoueurProtege(String nomJoueur) {
        JsonObject joueursProtèges = obtenirInformationsIdentification().getAsJsonObject("protected_players");
        if (!joueursProtèges.has(nomJoueur)) {
            return false;
        }

        joueursProtèges.remove(nomJoueur);
        StockageIdentifiants.getInstance().sauvegarder();

        MonSubMod.JOURNALISEUR.info("Joueur protégé retiré: {}", nomJoueur);
        return true;
    }

    /**
     * Lister tous les joueurs protégés
     */
    public List<String> listerJoueursProteges() {
        JsonObject joueursProtèges = obtenirInformationsIdentification().getAsJsonObject("protected_players");
        return new ArrayList<>(joueursProtèges.keySet());
    }

    /**
     * Définir le mot de passe pour un joueur protégé
     */
    public boolean definirMotDePasseJoueurProtege(String nomJoueur, String nouveauMotDePasse) {
        if (nouveauMotDePasse == null || nouveauMotDePasse.isEmpty()) {
            return false;
        }

        JsonObject joueursProtèges = obtenirInformationsIdentification().getAsJsonObject("protected_players");
        if (!joueursProtèges.has(nomJoueur)) {
            return false;
        }

        // Générer un nouveau sel et hachage
        String sel = genererSel();
        String motDePasseHache = hacherMotDePasse(nouveauMotDePasse, sel);

        JsonObject donneesJoueur = joueursProtèges.getAsJsonObject(nomJoueur);
        donneesJoueur.addProperty("salt", sel);
        donneesJoueur.addProperty("password", motDePasseHache);
        donneesJoueur.addProperty("failures", 0);
        donneesJoueur.addProperty("lastFailure", 0);

        StockageIdentifiants.getInstance().sauvegarder();

        MonSubMod.JOURNALISEUR.info("Mot de passe mis à jour pour le joueur protégé: {}", nomJoueur);
        return true;
    }

    /**
     * Vérifier le mot de passe pour un joueur protégé
     */
    public boolean verifierMotDePasseJoueurProtege(String nomJoueur, String motDePasse) {
        JsonObject joueursProtèges = obtenirInformationsIdentification().getAsJsonObject("protected_players");

        // Essayer d'abord une correspondance exacte, puis en minuscules (pour compatibilité)
        JsonObject donneesJoueur = null;
        if (joueursProtèges.has(nomJoueur)) {
            donneesJoueur = joueursProtèges.getAsJsonObject(nomJoueur);
        } else if (joueursProtèges.has(nomJoueur.toLowerCase())) {
            donneesJoueur = joueursProtèges.getAsJsonObject(nomJoueur.toLowerCase());
        }

        if (donneesJoueur == null) {
            return false;
        }

        String sel = donneesJoueur.get("salt").getAsString();
        String hachageStocke = donneesJoueur.get("password").getAsString();
        String hachageFourni = hacherMotDePasse(motDePasse, sel);

        return hachageStocke.equals(hachageFourni);
    }

    /**
     * Tenter la connexion pour un joueur protégé avec suivi de liste noire basé sur IP
     * Retourne: 0 = succès, -1 = mauvais mot de passe, -2 = IP sur liste noire pour ce compte
     */
    public int tenterConnexionJoueurProtege(ServerPlayer joueur, String motDePasse) {
        String nomJoueur = joueur.getName().getString(); // Conserver la casse originale pour correspondre aux clés JSON
        String adresseIp = joueur.getIpAddress();

        // Vérifier si cette IP est sur liste noire pour ce compte
        if (estIpSurListeNoirePourCompte(nomJoueur, adresseIp)) {
            return -2; // IP sur liste noire pour ce compte
        }

        // Obtenir le nombre de tentatives actuel pour cette IP sur ce compte
        int tentatives = obtenirTentativesIpPourCompte(nomJoueur, adresseIp);
        tentatives++;

        // Vérifier le mot de passe
        if (verifierMotDePasseJoueurProtege(nomJoueur, motDePasse)) {
            // Succès - effacer les tentatives pour cette IP et authentifier
            effacerTentativesIpPourCompte(nomJoueur, adresseIp);
            utilisateursAuthentifies.add(joueur.getUUID());
            effacerProtectionAuthentification(nomJoueur);

            // Gérer la transition de joueur restreint à normal
            gererTransitionAuthentification(joueur.getUUID());

            MonSubMod.JOURNALISEUR.info("Joueur protégé {} authentifié avec succès depuis l'IP {}", nomJoueur, adresseIp);
            return 0;
        } else {
            // Mauvais mot de passe
            MonSubMod.JOURNALISEUR.warn("Tentative de connexion échouée {}/{} pour le joueur protégé {} depuis l'IP {}", tentatives, TENTATIVES_MAX, nomJoueur, adresseIp);

            if (tentatives >= TENTATIVES_MAX) {
                // Nombre de tentatives max atteint - mettre cette IP sur liste noire pour ce compte (3 minutes)
                mettreIpSurListeNoirePourCompte(nomJoueur, adresseIp);
                return -2;
            } else {
                // Sauvegarder les tentatives actuelles pour cette IP
                sauvegarderTentativesIpPourCompte(nomJoueur, adresseIp, tentatives);
            }

            return -1;
        }
    }

    /**
     * Générer une clé pour la combinaison IP+compte
     */
    private String obtenirCleIpCompte(String nomJoueur, String adresseIp) {
        return nomJoueur.toLowerCase() + ":" + extraireIpSansPort(adresseIp);
    }

    /**
     * Extraire l'adresse IP sans port et sans barre oblique initiale
     * Gère les formats IPv4 (/127.0.0.1:port) et IPv6 (/[::1]:port)
     */
    private String extraireIpSansPort(String adresseIp) {
        if (adresseIp == null) return "";

        String resultat = adresseIp;

        // Retirer la barre oblique initiale si présente
        if (resultat.startsWith("/")) {
            resultat = resultat.substring(1);
        }

        // Vérifier le format IPv6: [adresse]:port
        int indexCrochet = resultat.lastIndexOf(']');
        if (indexCrochet > 0) {
            // IPv6 - extraire tout jusqu'au crochet fermant inclus
            return resultat.substring(0, indexCrochet + 1);
        }

        // Format IPv4: adresse:port
        int indexDeuxPoints = resultat.lastIndexOf(':');
        if (indexDeuxPoints > 0) {
            return resultat.substring(0, indexDeuxPoints);
        }

        return resultat;
    }

    /**
     * Vérifier si une IP est sur liste noire pour un compte spécifique
     */
    public boolean estIpSurListeNoirePourCompte(String nomJoueur, String adresseIp) {
        JsonObject listeNoireCompte = obtenirInformationsIdentification().getAsJsonObject("account_blacklist");
        String cle = obtenirCleIpCompte(nomJoueur, adresseIp);

        if (!listeNoireCompte.has(cle)) {
            return false;
        }

        JsonObject entree = listeNoireCompte.getAsJsonObject(cle);
        if (!entree.has("until")) {
            return false;
        }

        long jusqua = entree.get("until").getAsLong();

        if (System.currentTimeMillis() < jusqua) {
            return true;
        } else {
            // Liste noire expirée - retirer complètement l'entrée (réinitialise les tentatives à 3)
            listeNoireCompte.remove(cle);
            StockageIdentifiants.getInstance().sauvegarder();
            return false;
        }
    }

    /**
     * Obtenir le temps de liste noire restant pour une IP sur un compte spécifique (en millisecondes)
     */
    public long obtenirTempsRestantListeNoireIpPourCompte(String nomJoueur, String adresseIp) {
        JsonObject listeNoireCompte = obtenirInformationsIdentification().getAsJsonObject("account_blacklist");
        String cle = obtenirCleIpCompte(nomJoueur, adresseIp);

        if (!listeNoireCompte.has(cle)) {
            return 0;
        }

        JsonObject entree = listeNoireCompte.getAsJsonObject(cle);
        if (!entree.has("until")) {
            return 0;
        }

        long jusqua = entree.get("until").getAsLong();
        long restant = jusqua - System.currentTimeMillis();

        return Math.max(0, restant);
    }

    /**
     * Obtenir les tentatives actuelles pour une IP sur un compte spécifique
     * Efface automatiquement les entrées expirées (après la période de liste noire de 3 minutes)
     */
    private int obtenirTentativesIpPourCompte(String nomJoueur, String adresseIp) {
        JsonObject listeNoireCompte = obtenirInformationsIdentification().getAsJsonObject("account_blacklist");
        String cle = obtenirCleIpCompte(nomJoueur, adresseIp);

        if (!listeNoireCompte.has(cle)) {
            return 0;
        }

        JsonObject entree = listeNoireCompte.getAsJsonObject(cle);

        // Vérifier si cette entrée avait une liste noire qui a maintenant expiré
        if (entree.has("until")) {
            long jusqua = entree.get("until").getAsLong();
            if (System.currentTimeMillis() >= jusqua) {
                // Liste noire expirée - retirer complètement l'entrée (réinitialise les tentatives à 0)
                listeNoireCompte.remove(cle);
                StockageIdentifiants.getInstance().sauvegarder();
                MonSubMod.JOURNALISEUR.info("Liste noire expirée effacée pour l'IP {} sur le compte {}", adresseIp, nomJoueur);
                return 0;
            }
        }

        // Vérifier si les tentatives doivent être réinitialisées en fonction du temps (3 minutes depuis la dernière tentative)
        if (entree.has("lastAttempt") && !entree.has("until")) {
            long derniereTentative = entree.get("lastAttempt").getAsLong();
            if (System.currentTimeMillis() - derniereTentative > DUREE_LISTE_NOIRE_COMPTE) {
                // Plus de 3 minutes depuis la dernière tentative - réinitialiser
                listeNoireCompte.remove(cle);
                StockageIdentifiants.getInstance().sauvegarder();
                MonSubMod.JOURNALISEUR.info("Tentatives obsolètes effacées pour l'IP {} sur le compte {} (3+ minutes d'inactivité)", adresseIp, nomJoueur);
                return 0;
            }
        }

        if (entree.has("currentAttempts")) {
            return entree.get("currentAttempts").getAsInt();
        }
        return 0;
    }

    /**
     * Obtenir les tentatives restantes pour une IP sur un compte spécifique
     */
    public int obtenirTentativesRestantesJoueurProtege(String nomJoueur, String adresseIp) {
        int tentatives = obtenirTentativesIpPourCompte(nomJoueur, adresseIp);
        return TENTATIVES_MAX - tentatives;
    }

    /**
     * Sauvegarder les tentatives actuelles pour une IP sur un compte spécifique
     */
    private void sauvegarderTentativesIpPourCompte(String nomJoueur, String adresseIp, int tentatives) {
        JsonObject listeNoireCompte = obtenirInformationsIdentification().getAsJsonObject("account_blacklist");
        String cle = obtenirCleIpCompte(nomJoueur, adresseIp);
        JsonObject entree;

        if (listeNoireCompte.has(cle)) {
            entree = listeNoireCompte.getAsJsonObject(cle);
        } else {
            entree = new JsonObject();
            listeNoireCompte.add(cle, entree);
        }

        entree.addProperty("currentAttempts", tentatives);
        entree.addProperty("lastAttempt", System.currentTimeMillis());
        StockageIdentifiants.getInstance().sauvegarder();
    }

    /**
     * Effacer les tentatives pour une IP sur un compte spécifique (lors d'une authentification réussie)
     */
    private void effacerTentativesIpPourCompte(String nomJoueur, String adresseIp) {
        JsonObject listeNoireCompte = obtenirInformationsIdentification().getAsJsonObject("account_blacklist");
        String cle = obtenirCleIpCompte(nomJoueur, adresseIp);

        if (listeNoireCompte.has(cle)) {
            listeNoireCompte.remove(cle);
            StockageIdentifiants.getInstance().sauvegarder();
        }
    }

    /**
     * Version publique de effacerTentativesIpPourCompte pour utilisation par les candidats en file d'attente
     */
    public void effacerTentativesIpPourComptePublic(String nomJoueur, String adresseIp) {
        effacerTentativesIpPourCompte(nomJoueur, adresseIp);
    }

    /**
     * Tenter la connexion pour un candidat en file d'attente (suit les tentatives par IP+compte sans authentifier)
     * Retourne: 0 = mot de passe correct, -1 = mauvais mot de passe, -2 = IP sur liste noire pour ce compte
     */
    public int tenterConnexionCandidatFile(String nomCompte, String adresseIp, String motDePasse) {
        // Vérifier si cette IP est sur liste noire pour ce compte
        if (estIpSurListeNoirePourCompte(nomCompte, adresseIp)) {
            return -2; // IP sur liste noire pour ce compte
        }

        // Obtenir le nombre de tentatives actuel pour cette IP sur ce compte
        int tentatives = obtenirTentativesIpPourCompte(nomCompte, adresseIp);
        tentatives++;

        // Vérifier le mot de passe (vérification en lecture seule)
        if (verifierMotDePasseJoueurProtege(nomCompte, motDePasse)) {
            // Succès - ne pas effacer les tentatives ici, laisser l'appelant le faire après la promotion de la file d'attente
            MonSubMod.JOURNALISEUR.info("Mot de passe de candidat en file d'attente correct pour {} depuis l'IP {}", nomCompte, adresseIp);
            return 0;
        } else {
            // Mauvais mot de passe
            MonSubMod.JOURNALISEUR.warn("Tentative de connexion de candidat en file d'attente échouée {}/{} pour {} depuis l'IP {}", tentatives, TENTATIVES_MAX, nomCompte, adresseIp);

            if (tentatives >= TENTATIVES_MAX) {
                // Nombre de tentatives max atteint - mettre cette IP sur liste noire pour ce compte (3 minutes)
                mettreIpSurListeNoirePourCompte(nomCompte, adresseIp);
                return -2;
            } else {
                // Sauvegarder les tentatives actuelles pour cette IP
                sauvegarderTentativesIpPourCompte(nomCompte, adresseIp, tentatives);
            }

            return -1;
        }
    }

    /**
     * Mettre une IP sur liste noire pour un compte spécifique pendant 3 minutes
     */
    private void mettreIpSurListeNoirePourCompte(String nomJoueur, String adresseIp) {
        JsonObject listeNoireCompte = obtenirInformationsIdentification().getAsJsonObject("account_blacklist");
        String cle = obtenirCleIpCompte(nomJoueur, adresseIp);

        // Liste noire fixe de 3 minutes
        long jusqua = System.currentTimeMillis() + DUREE_LISTE_NOIRE_COMPTE;

        JsonObject entree = new JsonObject();
        entree.addProperty("until", jusqua);
        entree.addProperty("lastAttempt", System.currentTimeMillis());

        listeNoireCompte.add(cle, entree);
        StockageIdentifiants.getInstance().sauvegarder();

        MonSubMod.JOURNALISEUR.warn("IP {} mise sur liste noire pendant 3 minutes sur le compte {}", adresseIp, nomJoueur);
    }

    /**
     * Définir la protection d'authentification à l'heure actuelle + 30 secondes
     * Utilisé lorsque quelqu'un se connecte pendant la fenêtre de monopole (avec IP autorisée)
     * Cela leur donne 30 secondes pour s'authentifier sans que d'autres ne se connectent
     */
    public void mettreAJourProtectionAuthentification(String nomCompte) {
        long heureFinProtection = System.currentTimeMillis() + DUREE_PROTECTION_AUTH;
        protectionAuthentificationParCompte.put(nomCompte, heureFinProtection);
        MonSubMod.JOURNALISEUR.info("Protection d'authentification de 30 secondes définie pour {} (heure de fin: {})", nomCompte, heureFinProtection);
    }

    /**
     * Vérifier si le compte a une protection d'authentification active
     * Retourne le temps restant en millisecondes, ou 0 si aucune protection
     */
    public long obtenirTempsRestantProtection(String nomCompte) {
        Long heureFinProtection = protectionAuthentificationParCompte.get(nomCompte);
        if (heureFinProtection == null) {
            return 0;
        }

        long restant = heureFinProtection - System.currentTimeMillis();
        if (restant <= 0) {
            // Protection expirée
            protectionAuthentificationParCompte.remove(nomCompte);
            return 0;
        }

        return restant;
    }

    /**
     * Effacer la protection d'authentification pour un compte
     */
    public void effacerProtectionAuthentification(String nomCompte) {
        protectionAuthentificationParCompte.remove(nomCompte);
        MonSubMod.JOURNALISEUR.info("Protection d'authentification effacée pour {}", nomCompte);
    }


    /**
     * Marquer l'utilisateur comme authentifié
     */
    public void marquerAuthentifie(UUID idJoueur) {
        utilisateursAuthentifies.add(idJoueur);

        // Effacer la protection en trouvant le nom de compte du joueur
        net.minecraft.server.MinecraftServer serveur = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (serveur != null) {
            net.minecraft.server.level.ServerPlayer joueur = serveur.getPlayerList().getPlayer(idJoueur);
            if (joueur != null) {
                effacerProtectionAuthentification(joueur.getName().getString());
            }
        }

        // Gérer la transition de joueur restreint à normal
        gererTransitionAuthentification(idJoueur);
    }

    /**
     * Authentifier le joueur automatiquement via jeton (aucun mot de passe requis)
     * Appelé lorsque le joueur se reconnecte pendant la fenêtre de monopole avec un jeton valide
     */
    public void authentifierParJeton(net.minecraft.server.level.ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString();
        MonSubMod.JOURNALISEUR.info("Auto-authentification du joueur protégé {} via jeton", nomJoueur);

        utilisateursAuthentifies.add(joueur.getUUID());
        effacerProtectionAuthentification(nomJoueur);

        // Gérer la transition de joueur restreint à normal
        gererTransitionAuthentification(joueur.getUUID());
    }

    /**
     * Gérer la transition du joueur de non authentifié (restreint) à authentifié (normal)
     * Traite le joueur comme s'il venait de se connecter pour la première fois
     */
    private void gererTransitionAuthentification(UUID idJoueur) {
        // Trouver le joueur par UUID
        net.minecraft.server.MinecraftServer serveur = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (serveur == null) return;

        net.minecraft.server.level.ServerPlayer joueur = serveur.getPlayerList().getPlayer(idJoueur);
        if (joueur == null) return;

        com.example.mysubmod.MonSubMod.JOURNALISEUR.info("Gestion de la transition d'authentification pour le joueur protégé: {}", joueur.getName().getString());

        // Vérifier si le serveur est plein et expulser un JOUEUR_LIBRE si nécessaire
        // Doit être appelé AVANT de retirer du lobby de stationnement pour que le compte soit précis
        com.example.mysubmod.GestionnaireEvenementsServeur.verifierEtExpulserPourPriorite(joueur, TypeCompte.JOUEUR_PROTEGE);

        // Retirer l'invisibilité
        joueur.setInvisible(false);

        // Retirer du lobby de stationnement
        com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance().retirerJoueur(idJoueur, joueur.serverLevel());

        // Envoyer les informations du joueur à tous les clients MAINTENANT qu'ils sont authentifiés (n'a pas été envoyé lors de la connexion initiale)
        net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket paquetAjout =
            net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(java.util.List.of(joueur));
        serveur.getPlayerList().broadcastAll(paquetAjout);
        com.example.mysubmod.MonSubMod.JOURNALISEUR.info("Diffusion du joueur authentifié {} à tous les clients", joueur.getName().getString());

        // Vérifier si le joueur a été déconnecté pendant un sous-mode actif - si oui, restaurer son état directement
        com.example.mysubmod.sousmodes.GestionnaireSousModes gestionnaireSousModes = com.example.mysubmod.sousmodes.GestionnaireSousModes.getInstance();
        com.example.mysubmod.sousmodes.SousMode modeActuel = gestionnaireSousModes.obtenirModeActuel();

        if (modeActuel == com.example.mysubmod.sousmodes.SousMode.SOUS_MODE_1) {
            com.example.mysubmod.sousmodes.sousmode1.GestionnaireSousMode1 gestionnaireSousMode1 =
                com.example.mysubmod.sousmodes.sousmode1.GestionnaireSousMode1.getInstance();

            if (gestionnaireSousMode1.etaitJoueurDeconnecte(joueur.getName().getString())) {
                // Le joueur a été déconnecté pendant le jeu - restaurer sa position et son état
                com.example.mysubmod.MonSubMod.JOURNALISEUR.info("Le joueur {} a été déconnecté pendant le Sous-mode 1, restauration de l'état", joueur.getName().getString());
                gestionnaireSousMode1.gererReconnexionJoueur(joueur);
                return;
            }
        } else if (modeActuel == com.example.mysubmod.sousmodes.SousMode.SOUS_MODE_2) {
            com.example.mysubmod.sousmodes.sousmode2.GestionnaireSousMode2 gestionnaireSousMode2 =
                com.example.mysubmod.sousmodes.sousmode2.GestionnaireSousMode2.getInstance();

            if (gestionnaireSousMode2.etaitJoueurDeconnecte(joueur.getName().getString())) {
                // Le joueur a été déconnecté pendant le jeu - restaurer sa position et son état
                com.example.mysubmod.MonSubMod.JOURNALISEUR.info("Le joueur {} a été déconnecté pendant le Sous-mode 2, restauration de l'état", joueur.getName().getString());
                gestionnaireSousMode2.gererReconnexionJoueur(joueur);
                return;
            }
        }

        // Le joueur s'authentifie pour la première fois cette session - traiter comme une nouvelle connexion
        // Appeler le gestionnaire d'événements du sous-mode approprié
        if (modeActuel == com.example.mysubmod.sousmodes.SousMode.SOUS_MODE_1) {
            com.example.mysubmod.sousmodes.sousmode1.GestionnaireEvenementsSousMode1.onPlayerJoin(
                new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(joueur)
            );
        } else if (modeActuel == com.example.mysubmod.sousmodes.SousMode.SOUS_MODE_2) {
            com.example.mysubmod.sousmodes.sousmode2.GestionnaireEvenementsSousMode2.onPlayerJoin(
                new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(joueur)
            );
        } else if (modeActuel == com.example.mysubmod.sousmodes.SousMode.SALLE_ATTENTE) {
            com.example.mysubmod.sousmodes.salleattente.GestionnaireEvenementsSalleAttente.onPlayerJoin(
                new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(joueur)
            );
        } else {
            // Aucun sous-mode actif - téléporter au spawn
            net.minecraft.server.level.ServerLevel overworld = serveur.overworld();
            if (overworld != null) {
                net.minecraft.core.BlockPos positionSpawn = overworld.getSharedSpawnPos();
                net.minecraft.core.BlockPos positionSecurisee = overworld.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, positionSpawn);
                joueur.teleportTo(overworld, positionSecurisee.getX() + 0.5, positionSecurisee.getY(), positionSecurisee.getZ() + 0.5, 0, 0);
            }
        }

        com.example.mysubmod.MonSubMod.JOURNALISEUR.info("Le joueur protégé {} a réussi la transition de restreint à normal", joueur.getName().getString());
    }

    /**
     * Vérifier si l'utilisateur est authentifié
     */
    public boolean estAuthentifie(UUID idJoueur) {
        return utilisateursAuthentifies.contains(idJoueur);
    }

    /**
     * Gérer la déconnexion du joueur
     */
    public void gererDeconnexion(ServerPlayer joueur) {
        utilisateursAuthentifies.remove(joueur.getUUID());
        // Note: Nous n'effaçons intentionnellement PAS protectionAuthentificationParCompte ici
        // La protection doit persister même après la déconnexion pour empêcher les reconnexions rapides
    }

    // Utilitaires de hachage et de sel
    private String genererSel() {
        SecureRandom aleatoire = new SecureRandom();
        byte[] sel = new byte[16];
        aleatoire.nextBytes(sel);
        return Base64.getEncoder().encodeToString(sel);
    }

    private String hacherMotDePasse(String motDePasse, String sel) {
        try {
            MessageDigest empreinte = MessageDigest.getInstance("SHA-256");
            String combine = motDePasse + sel;
            byte[] hachage = empreinte.digest(combine.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hachage);
        } catch (NoSuchAlgorithmException e) {
            MonSubMod.JOURNALISEUR.error("SHA-256 non disponible", e);
            return "";
        }
    }

}

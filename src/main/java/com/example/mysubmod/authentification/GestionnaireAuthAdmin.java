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

public class GestionnaireAuthAdmin {
    private static GestionnaireAuthAdmin instance;

    // État à l'exécution
    private final Set<UUID> adminsAuthentifies = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> protectionAuthentificationParCompte = new ConcurrentHashMap<>(); // nomCompte -> heure de fin de protection

    private static final int TENTATIVES_MAX = 3;
    private static final long DUREE_LISTE_NOIRE_COMPTE = 3 * 60 * 1000; // 3 minutes en ms (fixe pour les comptes)
    private static final long DUREE_BASE_LISTE_NOIRE_IP = 3 * 60 * 1000; // 3 minutes en ms (base pour l'escalade IP)
    private static final long TEMPS_REINITIALISATION_ECHECS = 24 * 60 * 60 * 1000; // 24 heures en ms
    private static final long DUREE_PROTECTION_AUTH = 30 * 1000; // 30 secondes de protection

    private GestionnaireAuthAdmin() {
        // Aucune initialisation nécessaire - utilise StockageIdentifiants
    }

    private JsonObject obtenirCredentiels() {
        return StockageIdentifiants.getInstance().obtenirIdentifiants();
    }

    public static GestionnaireAuthAdmin getInstance() {
        if (instance == null) {
            instance = new GestionnaireAuthAdmin();
        }
        return instance;
    }


    /**
     * Vérifie si un nom de joueur est enregistré comme admin (a des identifiants)
     */
    public boolean estCompteAdmin(String nomJoueur) {
        JsonObject admins = obtenirCredentiels().getAsJsonObject("admins");
        return admins.has(nomJoueur.toLowerCase());
    }

    /**
     * Vérifie si un joueur est actuellement authentifié
     */
    public boolean estAuthentifie(ServerPlayer joueur) {
        return adminsAuthentifies.contains(joueur.getUUID());
    }

    /**
     * Vérifie si un joueur est actuellement sur liste noire
     */
    public boolean estSurListeNoire(String nomJoueur) {
        JsonObject blacklist = obtenirCredentiels().getAsJsonObject("blacklist");
        if (!blacklist.has(nomJoueur.toLowerCase())) {
            return false;
        }

        JsonObject entree = blacklist.getAsJsonObject(nomJoueur.toLowerCase());

        // Vérifie si cette entrée a un champ "until" (blacklist réelle)
        if (!entree.has("until")) {
            // C'est juste un suivi de tentatives, pas une blacklist
            return false;
        }

        long jusqua = entree.get("until").getAsLong();

        if (System.currentTimeMillis() < jusqua) {
            return true;
        } else {
            // Liste noire expirée, la retirer
            blacklist.remove(nomJoueur.toLowerCase());
            StockageIdentifiants.getInstance().sauvegarder();
            return false;
        }
    }

    /**
     * Obtient le temps de liste noire restant en millisecondes
     */
    public long obtenirTempsRestantListeNoire(String nomJoueur) {
        JsonObject blacklist = obtenirCredentiels().getAsJsonObject("blacklist");
        if (!blacklist.has(nomJoueur.toLowerCase())) {
            return 0;
        }

        JsonObject entree = blacklist.getAsJsonObject(nomJoueur.toLowerCase());
        long jusqua = entree.get("until").getAsLong();
        long restant = jusqua - System.currentTimeMillis();

        return Math.max(0, restant);
    }

    /**
     * Tente d'authentifier un joueur
     * Retourne: 0 = succès, -1 = mauvais mot de passe, -2 = tentatives max atteintes (compte), -3 = IP sur liste noire
     */
    public int tenterConnexion(ServerPlayer joueur, String motDePasse) {
        String nomJoueur = joueur.getName().getString().toLowerCase();
        UUID idJoueur = joueur.getUUID();
        String adresseIP = joueur.getIpAddress();

        // Vérifie si l'IP est sur liste noire
        if (estIPSurListeNoire(adresseIP)) {
            return -3;
        }

        // Vérifie si le compte est sur liste noire
        if (estSurListeNoire(nomJoueur)) {
            return -2;
        }

        // Obtient le nombre de tentatives actuel depuis l'entrée de liste noire (persistant après reconnexion)
        JsonObject blacklist = obtenirCredentiels().getAsJsonObject("blacklist");
        int tentatives = 0;
        long derniereTentative = 0;

        if (blacklist.has(nomJoueur)) {
            JsonObject entree = blacklist.getAsJsonObject(nomJoueur);
            if (entree.has("currentAttempts")) {
                tentatives = entree.get("currentAttempts").getAsInt();
            }
            if (entree.has("lastAttempt")) {
                derniereTentative = entree.get("lastAttempt").getAsLong();
            }

            // Réinitialise les tentatives si 24h écoulées
            if (System.currentTimeMillis() - derniereTentative > TEMPS_REINITIALISATION_ECHECS) {
                tentatives = 0;
            }
        }

        tentatives++;

        // Vérifie le mot de passe
        if (verifierMotDePasse(nomJoueur, motDePasse)) {
            // Succès - effacer les tentatives et authentifier
            effacerTentatives(nomJoueur);
            effacerTentativesIP(adresseIP);
            adminsAuthentifies.add(idJoueur);
            effacerProtectionAuthentification(nomJoueur);

            // Gère la transition de restreint à joueur normal
            gererTransitionAuthentification(joueur);

            MonSubMod.JOURNALISEUR.info("Admin {} authentifié avec succès", nomJoueur);
            return 0;
        } else {
            // Mauvais mot de passe
            MonSubMod.JOURNALISEUR.warn("Échec de tentative de connexion {}/{} pour admin {} depuis IP {}", tentatives, TENTATIVES_MAX, nomJoueur, adresseIP);

            if (tentatives >= TENTATIVES_MAX) {
                // Tentatives max atteintes - mettre sur liste noire le compte (3 minutes fixe) et l'IP (avec escalade)
                mettreJoueurSurListeNoire(nomJoueur);
                mettreIPSurListeNoire(adresseIP);
                return -2;
            } else {
                // Sauvegarder les tentatives actuelles pour persister après reconnexion
                sauvegarderTentatives(nomJoueur, tentatives);
            }

            return -1;
        }
    }

    /**
     * Obtient les tentatives restantes pour un joueur par nom
     */
    public int obtenirTentativesRestantesParNom(String nomJoueur) {
        JsonObject blacklist = obtenirCredentiels().getAsJsonObject("blacklist");
        int tentatives = 0;

        if (blacklist.has(nomJoueur.toLowerCase())) {
            JsonObject entree = blacklist.getAsJsonObject(nomJoueur.toLowerCase());
            if (entree.has("currentAttempts")) {
                tentatives = entree.get("currentAttempts").getAsInt();
            }
        }

        return TENTATIVES_MAX - tentatives;
    }

    /**
     * Sauvegarde les tentatives actuelles dans JSON (persiste après reconnexions)
     */
    private void sauvegarderTentatives(String nomJoueur, int tentatives) {
        JsonObject listeNoire = obtenirCredentiels().getAsJsonObject("blacklist");
        JsonObject entree;

        if (listeNoire.has(nomJoueur)) {
            entree = listeNoire.getAsJsonObject(nomJoueur);
        } else {
            entree = new JsonObject();
            listeNoire.add(nomJoueur, entree);
        }

        entree.addProperty("currentAttempts", tentatives);
        entree.addProperty("lastAttempt", System.currentTimeMillis());
        StockageIdentifiants.getInstance().sauvegarder();
    }

    /**
     * Efface les tentatives après connexion réussie
     */
    private void effacerTentatives(String nomJoueur) {
        JsonObject listeNoire = obtenirCredentiels().getAsJsonObject("blacklist");

        if (listeNoire.has(nomJoueur)) {
            listeNoire.remove(nomJoueur);
            StockageIdentifiants.getInstance().sauvegarder();
        }
    }

    private void mettreJoueurSurListeNoire(String nomJoueur) {
        JsonObject listeNoire = obtenirCredentiels().getAsJsonObject("blacklist");

        // Liste noire de 3 minutes fixe pour les comptes
        long jusqua = System.currentTimeMillis() + DUREE_LISTE_NOIRE_COMPTE;

        // Créer l'entrée de liste noire
        JsonObject entree = new JsonObject();
        entree.addProperty("until", jusqua);
        entree.addProperty("lastAttempt", System.currentTimeMillis());

        listeNoire.add(nomJoueur.toLowerCase(), entree);
        StockageIdentifiants.getInstance().sauvegarder();

        MonSubMod.JOURNALISEUR.warn("Compte {} mis sur liste noire pour 3 minutes", nomJoueur);
    }

    private void mettreIPSurListeNoire(String adresseIP) {
        JsonObject listeNoireIP = obtenirCredentiels().getAsJsonObject("ipBlacklist");

        // Obtient le nombre d'échecs actuel pour l'IP
        int nombreEchecs = 0;
        long derniereTentative = System.currentTimeMillis();

        if (listeNoireIP.has(adresseIP)) {
            JsonObject entree = listeNoireIP.getAsJsonObject(adresseIP);
            if (entree.has("failureCount")) {
                nombreEchecs = entree.get("failureCount").getAsInt();
            }
            if (entree.has("lastAttempt")) {
                derniereTentative = entree.get("lastAttempt").getAsLong();
            }
        }

        // Vérifie si on doit réinitialiser le compte d'échecs (24h depuis dernière tentative)
        if (System.currentTimeMillis() - derniereTentative > TEMPS_REINITIALISATION_ECHECS) {
            nombreEchecs = 0;
            MonSubMod.JOURNALISEUR.info("Réinitialisation du compte d'échecs IP pour {} (24h écoulées)", adresseIP);
        }

        nombreEchecs++;

        // Calcule la durée de liste noire avec escalade (3min * 10^(nombreEchecs-1))
        long duree = (long) (DUREE_BASE_LISTE_NOIRE_IP * Math.pow(10, nombreEchecs - 1));
        long jusqua = System.currentTimeMillis() + duree;

        // Créer l'entrée de liste noire IP
        JsonObject entree = new JsonObject();
        entree.addProperty("until", jusqua);
        entree.addProperty("failureCount", nombreEchecs);
        entree.addProperty("lastAttempt", System.currentTimeMillis());

        listeNoireIP.add(adresseIP, entree);
        StockageIdentifiants.getInstance().sauvegarder();

        MonSubMod.JOURNALISEUR.warn("IP {} mise sur liste noire pour {} minutes (nombre d'échecs: {})",
            adresseIP, duree / 60000, nombreEchecs);
    }

    /**
     * Vérifie si une IP est sur liste noire
     */
    public boolean estIPSurListeNoire(String adresseIP) {
        JsonObject listeNoireIP = obtenirCredentiels().getAsJsonObject("ipBlacklist");
        if (!listeNoireIP.has(adresseIP)) {
            return false;
        }

        JsonObject entree = listeNoireIP.getAsJsonObject(adresseIP);

        // Vérifie si cette entrée a un champ "until"
        if (!entree.has("until")) {
            return false;
        }

        long jusqua = entree.get("until").getAsLong();

        if (System.currentTimeMillis() < jusqua) {
            return true;
        } else {
            // Liste noire expirée, la retirer
            listeNoireIP.remove(adresseIP);
            StockageIdentifiants.getInstance().sauvegarder();
            return false;
        }
    }

    /**
     * Obtient le temps de liste noire IP restant en millisecondes
     */
    public long obtenirTempsRestantListeNoireIP(String adresseIP) {
        JsonObject listeNoireIP = obtenirCredentiels().getAsJsonObject("ipBlacklist");
        if (!listeNoireIP.has(adresseIP)) {
            return 0;
        }

        JsonObject entree = listeNoireIP.getAsJsonObject(adresseIP);
        if (!entree.has("until")) {
            return 0;
        }

        long jusqua = entree.get("until").getAsLong();
        long restant = jusqua - System.currentTimeMillis();

        return Math.max(0, restant);
    }

    /**
     * Efface les tentatives IP après connexion réussie
     */
    private void effacerTentativesIP(String adresseIP) {
        JsonObject listeNoireIP = obtenirCredentiels().getAsJsonObject("ipBlacklist");

        if (listeNoireIP.has(adresseIP)) {
            listeNoireIP.remove(adresseIP);
            StockageIdentifiants.getInstance().sauvegarder();
        }
    }

    /**
     * Vérifie un mot de passe contre les identifiants stockés
     */
    private boolean verifierMotDePasse(String nomJoueur, String motDePasse) {
        JsonObject admins = obtenirCredentiels().getAsJsonObject("admins");
        if (!admins.has(nomJoueur.toLowerCase())) {
            return false;
        }

        JsonObject admin = admins.getAsJsonObject(nomJoueur.toLowerCase());
        String hachageStocke = admin.get("passwordHash").getAsString();
        String sel = admin.get("salt").getAsString();

        String hachageEntree = hacherMotDePasse(motDePasse, sel);
        return hachageStocke.equals(hachageEntree);
    }

    /**
     * Vérifie le mot de passe sans suivre les tentatives (pour les candidats de file)
     * Retourne true si le mot de passe est correct, false sinon
     */
    public boolean verifierMotDePasseSeul(String nomJoueur, String motDePasse) {
        return verifierMotDePasse(nomJoueur, motDePasse);
    }

    /**
     * Définit ou met à jour le mot de passe admin
     */
    public void definirMotDePasseAdmin(String nomJoueur, String motDePasse) {
        JsonObject admins = obtenirCredentiels().getAsJsonObject("admins");

        // Génère le sel
        String sel = genererSel();
        String hash = hacherMotDePasse(motDePasse, sel);

        JsonObject admin = new JsonObject();
        admin.addProperty("passwordHash", hash);
        admin.addProperty("salt", sel);

        admins.add(nomJoueur.toLowerCase(), admin);
        StockageIdentifiants.getInstance().sauvegarder();

        MonSubMod.JOURNALISEUR.info("Mot de passe défini pour admin {}", nomJoueur);
    }

    /**
     * Réinitialise la liste noire pour un joueur
     */
    public void reinitialiserListeNoire(String nomJoueur) {
        JsonObject listeNoire = obtenirCredentiels().getAsJsonObject("blacklist");
        listeNoire.remove(nomJoueur.toLowerCase());
        StockageIdentifiants.getInstance().sauvegarder();
        MonSubMod.JOURNALISEUR.info("Liste noire réinitialisée pour {}", nomJoueur);
    }

    /**
     * Réinitialise le compte d'échecs pour un joueur (conserve la liste noire active)
     */
    public void reinitialiserCompteurEchecs(String nomJoueur) {
        JsonObject listeNoire = obtenirCredentiels().getAsJsonObject("blacklist");
        if (listeNoire.has(nomJoueur.toLowerCase())) {
            JsonObject entree = listeNoire.getAsJsonObject(nomJoueur.toLowerCase());
            entree.addProperty("failureCount", 0);
            entree.addProperty("lastAttempt", System.currentTimeMillis());
            StockageIdentifiants.getInstance().sauvegarder();
            MonSubMod.JOURNALISEUR.info("Compte d'échecs réinitialisé pour {}", nomJoueur);
        }
    }

    /**
     * Réinitialise la liste noire IP
     */
    public void reinitialiserListeNoireIP(String adresseIP) {
        JsonObject listeNoireIP = obtenirCredentiels().getAsJsonObject("ipBlacklist");
        listeNoireIP.remove(adresseIP);
        StockageIdentifiants.getInstance().sauvegarder();
        MonSubMod.JOURNALISEUR.info("Liste noire IP réinitialisée pour {}", adresseIP);
    }

    /**
     * Enregistre un échec IP et met sur liste noire si nécessaire (pour les joueurs protégés)
     */
    public void enregistrerEchecIP(String adresseIP) {
        mettreIPSurListeNoire(adresseIP);
    }

    /**
     * Retire un compte admin
     */
    public void retirerAdmin(String nomJoueur) {
        JsonObject admins = obtenirCredentiels().getAsJsonObject("admins");
        admins.remove(nomJoueur.toLowerCase());
        StockageIdentifiants.getInstance().sauvegarder();
        MonSubMod.JOURNALISEUR.info("Compte admin retiré pour {}", nomJoueur);
    }

    /**
     * Gère la déconnexion du joueur
     */
    public void gererDeconnexion(ServerPlayer joueur) {
        adminsAuthentifies.remove(joueur.getUUID());
        // Note: On n'efface intentionnellement PAS protectionAuthentificationParCompte ici
        // La protection doit persister même après déconnexion pour éviter les reconnexions rapides
    }

    /**
     * Authentifie un admin automatiquement via jeton (pas de mot de passe requis)
     * Appelé quand l'admin se reconnecte pendant la fenêtre de monopole avec un jeton valide
     */
    public void authentifierParJeton(ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString();
        MonSubMod.JOURNALISEUR.info("Auto-authentification de l'admin {} via jeton", nomJoueur);

        adminsAuthentifies.add(joueur.getUUID());
        effacerProtectionAuthentification(nomJoueur);

        // Gère la transition de restreint à joueur normal
        gererTransitionAuthentification(joueur);
    }

    /**
     * Définit la protection d'authentification à l'heure actuelle + 30 secondes
     * Utilisé quand quelqu'un se connecte pendant la fenêtre de monopole (avec IP autorisée)
     * Cela lui donne 30 secondes pour s'authentifier sans que d'autres se connectent
     */
    public void mettreAJourProtectionAuthentification(String nomCompte) {
        long heureFinProtection = System.currentTimeMillis() + DUREE_PROTECTION_AUTH;
        protectionAuthentificationParCompte.put(nomCompte, heureFinProtection);
        MonSubMod.JOURNALISEUR.info("Protection d'authentification de 30 secondes définie pour {} (heure de fin: {})", nomCompte, heureFinProtection);
    }

    /**
     * Vérifie si le compte a une protection d'authentification active
     * Retourne le temps restant en millisecondes, ou 0 si pas de protection
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
     * Efface la protection d'authentification pour un compte
     */
    public void effacerProtectionAuthentification(String nomCompte) {
        protectionAuthentificationParCompte.remove(nomCompte);
        MonSubMod.JOURNALISEUR.info("Protection d'authentification effacée pour {}", nomCompte);
    }


    /**
     * Génère un sel aléatoire
     */
    private String genererSel() {
        SecureRandom aleatoire = new SecureRandom();
        byte[] sel = new byte[16];
        aleatoire.nextBytes(sel);
        return Base64.getEncoder().encodeToString(sel);
    }

    /**
     * Hache un mot de passe avec sel en utilisant SHA-256
     */
    private String hacherMotDePasse(String motDePasse, String sel) {
        try {
            MessageDigest empreinte = MessageDigest.getInstance("SHA-256");
            String motDePasseSale = motDePasse + sel;
            byte[] hachage = empreinte.digest(motDePasseSale.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hachage);
        } catch (NoSuchAlgorithmException e) {
            MonSubMod.JOURNALISEUR.error("Algorithme SHA-256 non trouvé", e);
            return "";
        }
    }

    /**
     * Obtient la liste de tous les comptes admin
     */
    public Set<String> obtenirComptesAdmin() {
        JsonObject admins = obtenirCredentiels().getAsJsonObject("admins");
        Set<String> comptes = new HashSet<>();
        admins.keySet().forEach(comptes::add);
        return comptes;
    }

    /**
     * Gère la transition du joueur de non authentifié (restreint) à authentifié (normal)
     * Traite le joueur comme s'il venait juste de se connecter pour la première fois
     */
    private void gererTransitionAuthentification(ServerPlayer joueur) {
        MonSubMod.JOURNALISEUR.info("Gestion de la transition d'authentification pour admin: {}", joueur.getName().getString());

        // Les admins ne comptent pas dans la limite de joueurs et ne KICKENT JAMAIS personne
        // Donc on saute complètement la vérification de kick prioritaire

        // Retire l'invisibilité
        joueur.setInvisible(false);

        // Retire du parking lobby
        com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance().retirerJoueur(joueur.getUUID(), joueur.serverLevel());

        // Vérifie si le joueur a été déconnecté pendant un sous-mode actif - si oui, restaure son état directement
        com.example.mysubmod.sousmodes.GestionnaireSousModes gestionnaireSousModes = com.example.mysubmod.sousmodes.GestionnaireSousModes.getInstance();
        com.example.mysubmod.sousmodes.SousMode modeActuel = gestionnaireSousModes.obtenirModeActuel();

        if (modeActuel == com.example.mysubmod.sousmodes.SousMode.SOUS_MODE_1) {
            com.example.mysubmod.sousmodes.sousmode1.GestionnaireSousMode1 gestionnaireSousMode1 =
                com.example.mysubmod.sousmodes.sousmode1.GestionnaireSousMode1.getInstance();

            if (gestionnaireSousMode1.etaitJoueurDeconnecte(joueur.getName().getString())) {
                // Le joueur a été déconnecté pendant le jeu - restaure sa position et son état
                MonSubMod.JOURNALISEUR.info("Admin {} a été déconnecté pendant le Sous-mode 1, restauration de l'état", joueur.getName().getString());
                gestionnaireSousMode1.gererReconnexionJoueur(joueur);
                return;
            }
        } else if (modeActuel == com.example.mysubmod.sousmodes.SousMode.SOUS_MODE_2) {
            com.example.mysubmod.sousmodes.sousmode2.GestionnaireSousMode2 gestionnaireSousMode2 =
                com.example.mysubmod.sousmodes.sousmode2.GestionnaireSousMode2.getInstance();

            if (gestionnaireSousMode2.etaitJoueurDeconnecte(joueur.getName().getString())) {
                // Le joueur a été déconnecté pendant le jeu - restaure sa position et son état
                MonSubMod.JOURNALISEUR.info("Admin {} a été déconnecté pendant le Sous-mode 2, restauration de l'état", joueur.getName().getString());
                gestionnaireSousMode2.gererReconnexionJoueur(joueur);
                return;
            }
        }

        // Le joueur s'authentifie pour la première fois cette session - traite comme nouvelle connexion
        // Appelle le gestionnaire d'événement du sous-mode approprié
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
            // Pas de sous-mode actif - téléporte au spawn
            net.minecraft.server.MinecraftServer serveur = joueur.getServer();
            if (serveur != null) {
                net.minecraft.server.level.ServerLevel mondePrincipal = serveur.overworld();
                if (mondePrincipal != null) {
                    net.minecraft.core.BlockPos positionSpawn = mondePrincipal.getSharedSpawnPos();
                    net.minecraft.core.BlockPos positionSecurisee = mondePrincipal.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, positionSpawn);
                    joueur.teleportTo(mondePrincipal, positionSecurisee.getX() + 0.5, positionSecurisee.getY(), positionSecurisee.getZ() + 0.5, 0, 0);
                }
            }
        }

        MonSubMod.JOURNALISEUR.info("Admin {} a réussi la transition de restreint à normal", joueur.getName().getString());
    }
}

package com.example.mysubmod.sousmodes;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.reseau.PaquetChangementSousMode;
import com.example.mysubmod.sousmodes.salleattente.GestionnaireSalleAttente;
import com.example.mysubmod.sousmodes.sousmode1.GestionnaireSousMode1;
import com.example.mysubmod.sousmodes.sousmode2.GestionnaireSousMode2;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

public class GestionnaireSousModes {
    private static GestionnaireSousModes instance;
    private SousMode modeActuel = SousMode.SALLE_ATTENTE;
    private final Set<String> admins = new HashSet<>();
    private boolean changementEnCours = false; // Verrou pour empêcher les changements de mode simultanés
    private long dernierChangementMode = 0; // Suivre l'horodatage du dernier changement de mode
    private static final long DELAI_CHANGEMENT_MODE_MS = 5000; // 5 secondes de délai

    private GestionnaireSousModes() {}

    public static GestionnaireSousModes getInstance() {
        if (instance == null) {
            instance = new GestionnaireSousModes();
        }
        return instance;
    }

    public void forcerDesactivationTousSousModes(MinecraftServer serveur) {
        MonSubMod.JOURNALISEUR.info("Désactivation forcée de tous les sous-modes au démarrage du serveur");

        try {
            // Forcer la désactivation de tous les sous-modes possibles pour nettoyer tout état résiduel
            // Note: GestionnaireSousMode1.deactivate() inclut maintenant clearMap() avec détection améliorée
            GestionnaireSalleAttente.getInstance().deactivate(serveur);
            GestionnaireSousMode1.getInstance().deactivate(serveur);
            GestionnaireSousMode2.getInstance().deactivate(serveur);

            // Réinitialiser à un état propre
            modeActuel = SousMode.SALLE_ATTENTE; // Sera correctement activé ensuite

            MonSubMod.JOURNALISEUR.info("Tous les sous-modes ont été désactivés avec succès");
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors de la désactivation forcée des sous-modes", e);
        }
    }

    public void demarrerSalleAttente() {
        changerSousMode(SousMode.SALLE_ATTENTE, null);
        MonSubMod.JOURNALISEUR.info("Salle d'attente démarrée automatiquement");
    }

    public boolean changerSousMode(SousMode nouveauMode, ServerPlayer joueurDemandeur) {
        return changerSousMode(nouveauMode, joueurDemandeur, joueurDemandeur != null ? joueurDemandeur.server : null);
    }

    public boolean changerSousMode(SousMode nouveauMode, ServerPlayer joueurDemandeur, MinecraftServer serveur) {
        if (joueurDemandeur != null && !estAdmin(joueurDemandeur)) {
            MonSubMod.JOURNALISEUR.warn("Le joueur {} a tenté de changer de sous-mode sans privilèges d'administrateur", joueurDemandeur.getName().getString());
            return false;
        }

        // Vérifier le délai - empêcher les changements de mode rapides
        long tempsActuel = System.currentTimeMillis();
        long tempsDepuisDernierChangement = tempsActuel - dernierChangementMode;
        if (dernierChangementMode > 0 && tempsDepuisDernierChangement < DELAI_CHANGEMENT_MODE_MS) {
            long delaiRestant = (DELAI_CHANGEMENT_MODE_MS - tempsDepuisDernierChangement) / 1000;
            MonSubMod.JOURNALISEUR.warn("Délai de changement de sous-mode actif, rejet de la demande de {}. {} secondes restantes",
                joueurDemandeur != null ? joueurDemandeur.getName().getString() : "SERVEUR", delaiRestant);
            if (joueurDemandeur != null) {
                joueurDemandeur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cChangement de sous-mode trop rapide ! Veuillez attendre " + delaiRestant + " seconde(s)..."));
            }
            return false;
        }

        // Vérifier si un changement de mode est déjà en cours
        if (changementEnCours) {
            MonSubMod.JOURNALISEUR.warn("Changement de sous-mode déjà en cours, rejet de la nouvelle demande de {}",
                joueurDemandeur != null ? joueurDemandeur.getName().getString() : "SERVEUR");
            if (joueurDemandeur != null) {
                joueurDemandeur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cChangement de sous-mode déjà en cours, veuillez attendre..."));
            }
            return false;
        }

        // Verrouiller le changement de mode
        changementEnCours = true;

        try {
            SousMode modePrecedent = modeActuel;

            // Désactiver le mode précédent
            if (serveur != null) {
                switch (modePrecedent) {
                    case SALLE_ATTENTE:
                        GestionnaireSalleAttente.getInstance().deactivate(serveur);
                        break;
                    case SOUS_MODE_1:
                        GestionnaireSousMode1.getInstance().deactivate(serveur);
                        break;
                    case SOUS_MODE_2:
                        GestionnaireSousMode2.getInstance().deactivate(serveur);
                        break;
                }
            }

            modeActuel = nouveauMode;

            // Activer le nouveau mode
            if (serveur != null) {
                switch (nouveauMode) {
                    case SALLE_ATTENTE:
                        GestionnaireSalleAttente.getInstance().activate(serveur);
                        break;
                    case SOUS_MODE_1:
                        GestionnaireSousMode1.getInstance().activate(serveur, joueurDemandeur);
                        break;
                    case SOUS_MODE_2:
                        GestionnaireSousMode2.getInstance().activate(serveur, joueurDemandeur);
                        break;
                }
            }

            MonSubMod.JOURNALISEUR.info("Sous-mode changé de {} à {} par {}",
                modePrecedent.obtenirNomAffichage(),
                nouveauMode.obtenirNomAffichage(),
                joueurDemandeur != null ? joueurDemandeur.getName().getString() : "SERVEUR");

            GestionnaireReseau.INSTANCE.send(PacketDistributor.ALL.noArg(), new PaquetChangementSousMode(nouveauMode));

            // Mettre à jour l'horodatage du dernier changement de mode pour le suivi du délai
            dernierChangementMode = System.currentTimeMillis();

            return true;
        } finally {
            // Toujours déverrouiller, même si une exception se produit
            changementEnCours = false;
        }
    }

    public SousMode obtenirModeActuel() {
        return modeActuel;
    }

    public void ajouterAdmin(String nomJoueur, MinecraftServer serveur) {
        admins.add(nomJoueur.toLowerCase());
        MonSubMod.JOURNALISEUR.info("{} ajouté en tant qu'administrateur", nomJoueur);

        // Déconnecter le joueur pour forcer le rafraîchissement des privilèges
        deconnecterJoueur(nomJoueur, serveur, "§aVos privilèges ont été modifiés. Veuillez vous reconnecter.");
    }

    public void retirerAdmin(String nomJoueur, MinecraftServer serveur) {
        admins.remove(nomJoueur.toLowerCase());
        MonSubMod.JOURNALISEUR.info("{} retiré des administrateurs", nomJoueur);

        // Déconnecter le joueur pour forcer le rafraîchissement des privilèges
        deconnecterJoueur(nomJoueur, serveur, "§cVos privilèges ont été modifiés. Veuillez vous reconnecter.");
    }

    private void deconnecterJoueur(String nomJoueur, MinecraftServer serveur, String raison) {
        if (serveur != null) {
            ServerPlayer joueur = serveur.getPlayerList().getPlayerByName(nomJoueur);
            if (joueur != null) {
                joueur.connection.disconnect(net.minecraft.network.chat.Component.literal(raison));
                MonSubMod.JOURNALISEUR.info("Joueur {} déconnecté en raison d'un changement de privilège", nomJoueur);
            }
        }
    }

    public boolean estAdmin(ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString().toLowerCase();
        com.example.mysubmod.authentification.GestionnaireAuthAdmin gestAuth = com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance();

        // Vérifier si le joueur est dans la liste des admins
        if (admins.contains(nomJoueur)) {
            // Les comptes admin DOIVENT être authentifiés
            boolean authentifie = gestAuth.estAuthentifie(joueur);
            MonSubMod.JOURNALISEUR.info("Vérification estAdmin (liste admin): {} -> authentifié={}", nomJoueur, authentifie);
            return authentifie;
        }

        // Les opérateurs du serveur (niveau de permission 2+) sont également admins mais doivent s'authentifier
        if (joueur.hasPermissions(2)) {
            if (gestAuth.estCompteAdmin(nomJoueur)) {
                // Ils ont un mot de passe défini - exiger l'authentification
                boolean authentifie = gestAuth.estAuthentifie(joueur);
                MonSubMod.JOURNALISEUR.info("Vérification estAdmin (op avec mot de passe): {} -> authentifié={}", nomJoueur, authentifie);
                return authentifie;
            }
            // Pas encore de mot de passe défini - autoriser l'accès (ils peuvent définir leur mot de passe)
            MonSubMod.JOURNALISEUR.info("Vérification estAdmin (op sans mot de passe): {} -> true", nomJoueur);
            return true;
        }

        // Vérifier si le joueur a un compte admin (même s'il n'est pas op et pas dans la liste admin)
        if (gestAuth.estCompteAdmin(nomJoueur)) {
            boolean authentifie = gestAuth.estAuthentifie(joueur);
            MonSubMod.JOURNALISEUR.info("Vérification estAdmin (compte admin): {} -> authentifié={}", nomJoueur, authentifie);
            return authentifie;
        }

        MonSubMod.JOURNALISEUR.info("Vérification estAdmin: {} -> false (pas op, pas dans la liste admin, pas de compte admin)", nomJoueur);
        return false;
    }

    public Set<String> obtenirAdmins() {
        return new HashSet<>(admins);
    }
}

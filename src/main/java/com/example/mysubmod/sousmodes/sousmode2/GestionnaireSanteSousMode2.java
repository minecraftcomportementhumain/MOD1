package com.example.mysubmod.sousmodes.sousmode2;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * Gestionnaire de santé pour Sous-mode 2 avec support des pénalités de spécialisation
 * Applique des multiplicateurs de santé (75% pendant pénalité, 100% sinon) lorsque les joueurs consomment des ressources
 */
public class GestionnaireSanteSousMode2 {
    private static GestionnaireSanteSousMode2 instance;
    private Timer minuterieSante;
    private static final float PERTE_SANTE_PAR_TIC = 1.0f; // 1 coeur toutes les 10 secondes
    private static final int INTERVALLE_TIC = 10000; // 10 secondes

    private GestionnaireSanteSousMode2() {}

    public static GestionnaireSanteSousMode2 getInstance() {
        if (instance == null) {
            instance = new GestionnaireSanteSousMode2();
        }
        return instance;
    }

    public void demarrerDegradationSante(MinecraftServer serveur) {
        MonSubMod.JOURNALISEUR.info("Démarrage de la dégradation de santé pour Sous-mode 2");

        minuterieSante = new Timer();
        minuterieSante.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                serveur.execute(() -> {
                    // Dégrader la santé seulement si le jeu est actif (pas pendant la sélection d'île)
                    if (!GestionnaireSousMode2.getInstance().estPartieActive()) {
                        return;
                    }

                    // Dégrader la santé des joueurs connectés
                    for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                        if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueur.getUUID())) {
                            degraderSanteJoueur(joueur);
                        }
                    }

                    // Vérifier les joueurs déconnectés et gérer les morts côté serveur
                    verifierSanteJoueursDeconnectes(serveur);
                });
            }
        }, INTERVALLE_TIC, INTERVALLE_TIC);
    }

    public void arreterDegradationSante() {
        if (minuterieSante != null) {
            minuterieSante.cancel();
            minuterieSante = null;
            MonSubMod.JOURNALISEUR.info("Arrêt de la dégradation de santé pour Sous-mode 2");
        }
    }

    private void degraderSanteJoueur(ServerPlayer joueur) {
        float santeActuelle = joueur.getHealth();
        float nouvelleSante = Math.max(0.0f, santeActuelle - PERTE_SANTE_PAR_TIC);

        joueur.setHealth(nouvelleSante);

        // Avertir le joueur quand la santé est basse
        if (nouvelleSante <= 2.0f && nouvelleSante > 0.0f) {
            joueur.sendSystemMessage(Component.literal("§c⚠ Santé critique ! Trouvez un bonbon !"));
        }

        // Gérer la mort du joueur
        if (nouvelleSante <= 0.0f) {
            gererMortJoueur(joueur);
        }

        // Journaliser le changement de santé (journalisation basique sans contexte de ressource)
        if (GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees() != null) {
            // Utiliser la journalisation basique pour la dégradation
            // La journalisation complète avec le type de ressource se fait dans gererConsommationBonbon
        }
    }

    /**
     * Gérer la consommation de bonbons avec le système de spécialisation
     * Retourne la quantité de santé réellement restaurée
     */
    public float gererConsommationBonbon(ServerPlayer joueur, TypeRessource typeRessource) {
        float santeActuelle = joueur.getHealth();
        float santeMax = joueur.getMaxHealth();

        // Obtenir le multiplicateur de santé depuis le GestionnaireSpecialisation (1.0 ou 0.75)
        float multiplicateurSante = GestionnaireSpecialisation.getInstance().gererCollecteRessource(joueur, typeRessource);

        // Restauration de santé de base: 1 coeur (2.0 points de santé)
        float restaurationBase = 2.0f;
        float restaurationReelle = restaurationBase * multiplicateurSante;

        // Calculer la nouvelle santé (plafonnée au maximum)
        float nouvelleSante = Math.min(santeMax, santeActuelle + restaurationReelle);
        joueur.setHealth(nouvelleSante);

        // Vérifier si le joueur a une pénalité pour le retour d'information
        boolean aPenalite = GestionnaireSpecialisation.getInstance().aPenalite(joueur.getUUID());

        // Fournir un retour au joueur
        if (aPenalite) {
            long msRestants = GestionnaireSpecialisation.getInstance().obtenirTempsRestantPenalite(joueur.getUUID());
            String texteTemps = GestionnaireSpecialisation.formaterTemps(msRestants);
            joueur.sendSystemMessage(Component.literal(
                String.format("§e+%.2f ❤ §c(Pénalité: 25%% - %s restant)", restaurationReelle / 2.0f, texteTemps)
            ));
        } else {
            joueur.sendSystemMessage(Component.literal(
                String.format("§a+%.1f ❤ §7(%s)", restaurationReelle / 2.0f, typeRessource.obtenirNomAffichage())
            ));
        }

        // Journaliser le changement de santé avec le contexte du type de ressource
        if (GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees() != null) {
            GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees().enregistrerChangementSante(
                joueur, santeActuelle, nouvelleSante, typeRessource, aPenalite
            );
        }

        MonSubMod.JOURNALISEUR.info("Joueur {} a consommé {} - restauré {:.1f} santé (multiplicateur: {:.1f}x)",
            joueur.getName().getString(), typeRessource.obtenirNomAffichage(), restaurationReelle, multiplicateurSante);

        return restaurationReelle;
    }

    /**
     * Vérifier la santé des joueurs déconnectés côté serveur et gérer les morts
     * Cela garantit que le jeu se termine même si les joueurs déconnectés meurent hors ligne
     */
    private void verifierSanteJoueursDeconnectes(MinecraftServer serveur) {
        GestionnaireSousMode2 gestionnaire = GestionnaireSousMode2.getInstance();

        // Obtenir un instantané des joueurs déconnectés
        java.util.Map<String, ?> joueursDeconnectes = gestionnaire.obtenirInfoJoueursDeconnectes();
        long tempsActuel = System.currentTimeMillis();
        long tempsDebutJeu = gestionnaire.obtenirHeureDebutPartie();

        if (tempsDebutJeu <= 0) return; // Le jeu n'a pas encore commencé

        for (java.util.Map.Entry<String, ?> entree : joueursDeconnectes.entrySet()) {
            String nomJoueur = entree.getKey();
            Object objetInfo = entree.getValue();

            // Utiliser la réflexion pour accéder aux champs de DisconnectInfo car c'est privé
            try {
                java.lang.reflect.Field champUuid = objetInfo.getClass().getDeclaredField("oldUUID");
                java.lang.reflect.Field champTempsDeconnexion = objetInfo.getClass().getDeclaredField("disconnectTime");
                java.lang.reflect.Field champSante = objetInfo.getClass().getDeclaredField("healthAtDisconnect");
                java.lang.reflect.Field champEstMort = objetInfo.getClass().getDeclaredField("isDead");

                champUuid.setAccessible(true);
                champTempsDeconnexion.setAccessible(true);
                champSante.setAccessible(true);
                champEstMort.setAccessible(true);

                UUID idJoueur = (UUID) champUuid.get(objetInfo);
                long tempsDeconnexion = champTempsDeconnexion.getLong(objetInfo);
                float santeADeconnexion = champSante.getFloat(objetInfo);
                boolean estMort = champEstMort.getBoolean(objetInfo);

                // Ignorer les joueurs déjà marqués comme morts
                if (estMort) {
                    continue;
                }

                // Vérifier uniquement les joueurs encore marqués comme vivants
                if (!gestionnaire.estJoueurVivant(idJoueur)) {
                    continue;
                }

                // Calculer la santé basée sur le temps déconnecté
                long tempsEffectifDeconnexion = Math.max(tempsDeconnexion, tempsDebutJeu);
                long msDepuisDebutJeuADeconnexion = tempsEffectifDeconnexion - tempsDebutJeu;
                long msDepuisDebutJeuMaintenant = tempsActuel - tempsDebutJeu;

                long numeroTicADeconnexion = msDepuisDebutJeuADeconnexion / 10000;
                long numeroTicMaintenant = msDepuisDebutJeuMaintenant / 10000;

                int ticsSanteManques = (int) (numeroTicMaintenant - numeroTicADeconnexion);
                float santeActuelle = santeADeconnexion - (ticsSanteManques * PERTE_SANTE_PAR_TIC);

                // Le joueur est mort pendant qu'il était déconnecté
                if (santeActuelle <= 0.0f) {
                    MonSubMod.JOURNALISEUR.info("Sous-mode 2: Joueur déconnecté {} mort côté serveur (santé: {} -> {}, {} tics manqués)",
                        nomJoueur, santeADeconnexion, santeActuelle, ticsSanteManques);

                    // Gérer la mort côté serveur
                    gestionnaire.gererMortJoueurDeconnecte(nomJoueur, idJoueur);

                    // Diffuser le message de mort
                    String messageMort = "§e" + nomJoueur + " §cest mort pendant sa déconnexion !";
                    for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                        j.sendSystemMessage(Component.literal(messageMort));
                    }

                    // Vérifier si tous les joueurs sont morts
                    if (gestionnaire.obtenirJoueursVivants().isEmpty()) {
                        MonSubMod.JOURNALISEUR.info("Sous-mode 2: Tous les joueurs sont morts (y compris les déconnectés) - fin du jeu");
                        serveur.execute(() -> {
                            for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                                j.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                            }
                            gestionnaire.terminerPartie(serveur);
                        });
                        return; // Arrêter la vérification, le jeu se termine
                    }
                }
            } catch (Exception e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la vérification de la santé d'un joueur déconnecté dans Sous-mode 2: {}", e.getMessage());
            }
        }
    }

    private void gererMortJoueur(ServerPlayer joueur) {
        joueur.sendSystemMessage(Component.literal("§cVous êtes mort ! Téléportation vers la zone spectateur..."));

        // Enregistrer l'heure de mort pour le classement
        GestionnaireSousMode2.getInstance().enregistrerMortJoueur(joueur.getUUID());

        // Téléporter vers la zone spectateur
        GestionnaireSousMode2.getInstance().teleporterVersSpectateur(joueur);

        // Restaurer la santé pour le mode spectateur
        joueur.setHealth(joueur.getMaxHealth());

        // Diffuser le message de mort
        String messageMort = "§e" + joueur.getName().getString() + " §cest mort !";
        for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(joueur.server)) {
            j.sendSystemMessage(Component.literal(messageMort));
        }

        // Journaliser la mort
        if (GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees() != null) {
            GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees().enregistrerMortJoueur(joueur);
        }

        // Vérifier si tous les joueurs sont morts (aucun joueur vivant restant)
        if (GestionnaireSousMode2.getInstance().obtenirJoueursVivants().isEmpty()) {
            MonSubMod.JOURNALISEUR.info("Sous-mode 2: Tous les joueurs sont morts - fin du jeu");
            joueur.server.execute(() -> {
                for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(joueur.server)) {
                    j.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                }
                GestionnaireSousMode2.getInstance().terminerPartie(joueur.server);
            });
        }
    }
}

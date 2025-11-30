package com.example.mysubmod.sousmodes.sousmode1;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class GestionnaireSanteSousMode1 {
    private static GestionnaireSanteSousMode1 instance;
    private Timer minuterieSante;
    private static final float PERTE_SANTE_PAR_TICK = 1.0f; // 1 cœur toutes les 10 secondes
    private static final int INTERVALLE_TICK = 10000; // 10 secondes

    private GestionnaireSanteSousMode1() {}

    public static GestionnaireSanteSousMode1 getInstance() {
        if (instance == null) {
            instance = new GestionnaireSanteSousMode1();
        }
        return instance;
    }

    public void demarrerDegradationSante(MinecraftServer serveur) {
        MonSubMod.JOURNALISEUR.info("Démarrage de la dégradation de santé pour Sous-mode 1");

        minuterieSante = new Timer();
        minuterieSante.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                serveur.execute(() -> {
                    // Dégrader la santé uniquement si le jeu est actif (pas pendant la sélection d'île)
                    if (!GestionnaireSousMode1.getInstance().estPartieActive()) {
                        return;
                    }

                    // Dégrader la santé pour les joueurs connectés
                    for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                        if (GestionnaireSousMode1.getInstance().estJoueurVivant(joueur.getUUID())) {
                            degraderSanteJoueur(joueur);
                        }
                    }

                    // Vérifier la santé des joueurs déconnectés et gérer les morts côté serveur
                    verifierSanteJoueursDeconnectes(serveur);
                });
            }
        }, INTERVALLE_TICK, INTERVALLE_TICK);
    }

    public void arreterDegradationSante() {
        if (minuterieSante != null) {
            minuterieSante.cancel();
            minuterieSante = null;
            MonSubMod.JOURNALISEUR.info("Arrêt de la dégradation de santé pour Sous-mode 1");
        }
    }

    private void degraderSanteJoueur(ServerPlayer joueur) {
        float santeActuelle = joueur.getHealth();
        float nouvelleSante = Math.max(0.0f, santeActuelle - PERTE_SANTE_PAR_TICK);

        joueur.setHealth(nouvelleSante);

        // Avertir le joueur quand la santé est faible
        if (nouvelleSante <= 2.0f && nouvelleSante > 0.0f) {
            joueur.sendSystemMessage(Component.literal("§c⚠ Santé critique ! Trouvez un bonbon !"));
        }

        // Gérer la mort du joueur
        if (nouvelleSante <= 0.0f) {
            gererMortJoueur(joueur);
        }

        // Journaliser le changement de santé
        if (GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees() != null) {
            GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees().enregistrerChangementSante(joueur, santeActuelle, nouvelleSante);
        }
    }

    /**
     * Vérifier la santé des joueurs déconnectés côté serveur et gérer les morts
     * Cela garantit que le jeu se termine même si les joueurs déconnectés meurent hors ligne
     */
    private void verifierSanteJoueursDeconnectes(MinecraftServer serveur) {
        GestionnaireSousMode1 gestionnaire = GestionnaireSousMode1.getInstance();

        // Obtenir un instantané des joueurs déconnectés
        java.util.Map<String, ?> joueursDeconnectes = gestionnaire.obtenirInfoJoueursDeconnectes();
        long tempsActuel = System.currentTimeMillis();
        long tempsDebutJeu = gestionnaire.obtenirHeureDebutPartie();

        if (tempsDebutJeu <= 0) return; // Le jeu n'a pas encore commencé

        for (java.util.Map.Entry<String, ?> entree : joueursDeconnectes.entrySet()) {
            String nomJoueur = entree.getKey();
            Object objetInfo = entree.getValue();

            // Utiliser la réflexion pour accéder aux champs de DisconnectInfo car il est privé
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
                float santeALaDeconnexion = champSante.getFloat(objetInfo);
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
                long tempsDeconnexionEffectif = Math.max(tempsDeconnexion, tempsDebutJeu);
                long msDepuisDebutJeuALaDeconnexion = tempsDeconnexionEffectif - tempsDebutJeu;
                long msDepuisDebutJeuMaintenant = tempsActuel - tempsDebutJeu;

                long numeroTickALaDeconnexion = msDepuisDebutJeuALaDeconnexion / 10000;
                long numeroTickMaintenant = msDepuisDebutJeuMaintenant / 10000;

                int ticksSanteManques = (int) (numeroTickMaintenant - numeroTickALaDeconnexion);
                float santeActuelle = santeALaDeconnexion - (ticksSanteManques * PERTE_SANTE_PAR_TICK);

                // Le joueur est mort pendant sa déconnexion
                if (santeActuelle <= 0.0f) {
                    MonSubMod.JOURNALISEUR.info("Le joueur déconnecté {} est mort côté serveur (santé: {} -> {}, {} ticks manqués)",
                        nomJoueur, santeALaDeconnexion, santeActuelle, ticksSanteManques);

                    // Gérer la mort côté serveur
                    gestionnaire.gererMortJoueurDeconnecte(nomJoueur, idJoueur);

                    // Diffuser le message de mort
                    String messageMort = "§e" + nomJoueur + " §cest mort pendant sa déconnexion !";
                    for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                        j.sendSystemMessage(Component.literal(messageMort));
                    }

                    // Vérifier si tous les joueurs sont morts
                    if (gestionnaire.obtenirJoueursVivants().isEmpty()) {
                        MonSubMod.JOURNALISEUR.info("Tous les joueurs sont morts (y compris les déconnectés) - fin du jeu");
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
                MonSubMod.JOURNALISEUR.error("Erreur lors de la vérification de la santé du joueur déconnecté: {}", e.getMessage());
            }
        }
    }

    private void gererMortJoueur(ServerPlayer joueur) {
        joueur.sendSystemMessage(Component.literal("§cVous êtes mort ! Téléportation vers la zone spectateur..."));

        // Enregistrer l'heure de mort pour le classement
        GestionnaireSousMode1.getInstance().enregistrerMortJoueur(joueur.getUUID());

        // Téléporter vers la zone spectateur
        GestionnaireSousMode1.getInstance().teleporterVersSpectateur(joueur);

        // Restaurer la santé pour le mode spectateur
        joueur.setHealth(joueur.getMaxHealth());

        // Diffuser le message de mort
        String messageMort = "§e" + joueur.getName().getString() + " §cest mort !";
        for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(joueur.server)) {
            j.sendSystemMessage(Component.literal(messageMort));
        }

        // Journaliser la mort
        if (GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees() != null) {
            GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees().enregistrerMortJoueur(joueur);
        }

        // Vérifier si tous les joueurs sont morts (aucun joueur vivant restant)
        if (GestionnaireSousMode1.getInstance().obtenirJoueursVivants().isEmpty()) {
            MonSubMod.JOURNALISEUR.info("Tous les joueurs sont morts - fin du jeu");
            joueur.server.execute(() -> {
                for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(joueur.server)) {
                    j.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                }
                GestionnaireSousMode1.getInstance().terminerPartie(joueur.server);
            });
        }
    }
}

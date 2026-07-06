package com.example.mysubmod.sousmodes.sousmode3;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * Dégradation de santé du Sous-mode 3 (reprend la base du Sous-mode 1) :
 * 1 cœur perdu toutes les 10 secondes ; les bonbons redonnent de la vie.
 * Gère aussi la mort côté serveur des joueurs déconnectés.
 */
public class GestionnaireSanteSousMode3 {
    private static GestionnaireSanteSousMode3 instance;
    private Timer minuterieSante;
    private static final float PERTE_SANTE_PAR_TICK = 1.0f;
    private static final int INTERVALLE_TICK = 10000; // 10 secondes

    private GestionnaireSanteSousMode3() {
    }

    public static GestionnaireSanteSousMode3 getInstance() {
        if (instance == null) {
            instance = new GestionnaireSanteSousMode3();
        }
        return instance;
    }

    public void demarrerDegradationSante(MinecraftServer serveur) {
        MonSubMod.JOURNALISEUR.info("Démarrage de la dégradation de santé pour Sous-mode 3");

        minuterieSante = new Timer("SousMode3-Sante", true);
        minuterieSante.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                serveur.execute(() -> {
                    if (!GestionnaireSousMode3.getInstance().estPartieActive()) {
                        return;
                    }

                    for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                        if (GestionnaireSousMode3.getInstance().estJoueurVivant(joueur.getUUID())) {
                            degraderSanteJoueur(joueur);
                        }
                    }

                    verifierSanteJoueursDeconnectes(serveur);
                });
            }
        }, INTERVALLE_TICK, INTERVALLE_TICK);
    }

    public void arreterDegradationSante() {
        if (minuterieSante != null) {
            minuterieSante.cancel();
            minuterieSante = null;
            MonSubMod.JOURNALISEUR.info("Arrêt de la dégradation de santé pour Sous-mode 3");
        }
    }

    private void degraderSanteJoueur(ServerPlayer joueur) {
        float santeActuelle = joueur.getHealth();
        float nouvelleSante = Math.max(0.0f, santeActuelle - PERTE_SANTE_PAR_TICK);

        joueur.setHealth(nouvelleSante);

        if (nouvelleSante <= 2.0f && nouvelleSante > 0.0f) {
            joueur.sendSystemMessage(Component.literal("§c⚠ Santé critique ! Trouvez un bonbon !"));
        }

        if (nouvelleSante <= 0.0f) {
            gererMortJoueur(joueur);
        }

        if (GestionnaireSousMode3.getInstance().obtenirEnregistreurDonnees() != null) {
            GestionnaireSousMode3.getInstance().obtenirEnregistreurDonnees()
                .enregistrerChangementSante(joueur, santeActuelle, nouvelleSante);
        }
    }

    /**
     * Vérifie la santé des joueurs déconnectés côté serveur et gère leur mort
     * pour que la partie se termine même s'ils restent hors ligne.
     */
    private void verifierSanteJoueursDeconnectes(MinecraftServer serveur) {
        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();

        Map<String, GestionnaireSousMode3.InfoDeconnexion> joueursDeconnectes =
            gestionnaire.obtenirInfoJoueursDeconnectes();
        long tempsActuel = System.currentTimeMillis();
        long tempsDebutJeu = gestionnaire.obtenirHeureDebutPartie();

        if (tempsDebutJeu <= 0) {
            return;
        }

        for (Map.Entry<String, GestionnaireSousMode3.InfoDeconnexion> entree : joueursDeconnectes.entrySet()) {
            String nomJoueur = entree.getKey();
            GestionnaireSousMode3.InfoDeconnexion info = entree.getValue();

            if (info.estMort) {
                continue;
            }
            UUID idJoueur = info.ancienUUID;
            if (!gestionnaire.estJoueurVivant(idJoueur)) {
                continue;
            }

            long tempsDeconnexionEffectif = Math.max(info.tempsDeconnexion, tempsDebutJeu);
            long numeroTickALaDeconnexion = (tempsDeconnexionEffectif - tempsDebutJeu) / 10000;
            long numeroTickMaintenant = (tempsActuel - tempsDebutJeu) / 10000;
            int ticksSanteManques = (int) (numeroTickMaintenant - numeroTickALaDeconnexion);
            float santeActuelle = info.santeADeconnexion - (ticksSanteManques * PERTE_SANTE_PAR_TICK);

            if (santeActuelle <= 0.0f) {
                MonSubMod.JOURNALISEUR.info("Le joueur déconnecté {} est mort côté serveur (santé: {} -> {}, {} ticks manqués)",
                    nomJoueur, info.santeADeconnexion, santeActuelle, ticksSanteManques);

                gestionnaire.gererMortJoueurDeconnecte(nomJoueur, idJoueur);

                String messageMort = "§e" + nomJoueur + " §cest mort pendant sa déconnexion !";
                for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                    j.sendSystemMessage(Component.literal(messageMort));
                }

                if (gestionnaire.obtenirJoueursVivants().isEmpty()) {
                    serveur.execute(() -> {
                        for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                            j.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                        }
                        gestionnaire.terminerPartie(serveur);
                    });
                    return;
                }
            }
        }
    }

    private void gererMortJoueur(ServerPlayer joueur) {
        joueur.sendSystemMessage(Component.literal("§cVous êtes mort ! Téléportation vers la zone spectateur..."));

        GestionnaireSousMode3.getInstance().enregistrerMortJoueur(joueur.getUUID());
        GestionnaireSousMode3.getInstance().teleporterVersSpectateur(joueur);
        joueur.setHealth(joueur.getMaxHealth());

        String messageMort = "§e" + joueur.getName().getString() + " §cest mort !";
        for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(joueur.server)) {
            j.sendSystemMessage(Component.literal(messageMort));
        }

        if (GestionnaireSousMode3.getInstance().obtenirEnregistreurDonnees() != null) {
            GestionnaireSousMode3.getInstance().obtenirEnregistreurDonnees().enregistrerMortJoueur(joueur);
        }

        if (GestionnaireSousMode3.getInstance().obtenirJoueursVivants().isEmpty()) {
            MonSubMod.JOURNALISEUR.info("Tous les joueurs sont morts - fin du jeu (Sous-mode 3)");
            joueur.server.execute(() -> {
                for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(joueur.server)) {
                    j.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                }
                GestionnaireSousMode3.getInstance().terminerPartie(joueur.server);
            });
        }
    }
}

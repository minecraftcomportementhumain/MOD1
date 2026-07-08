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
 * Dégradation de santé du Sous-mode 3 (reprend la base du Sous-mode 1).
 *
 * <p>Le rythme, l'intensité et l'activation de la dégradation proviennent désormais de
 * {@link ConfigPartieSousMode3} (choisie par l'admin au menu N). Par défaut : 1 point de vie
 * perdu toutes les 10 secondes ; les bonbons redonnent de la vie.</p>
 *
 * <p>Gère aussi la mort côté serveur des joueurs déconnectés.</p>
 */
public class GestionnaireSanteSousMode3 {
    private static GestionnaireSanteSousMode3 instance;
    private Timer minuterieSante;

    private GestionnaireSanteSousMode3() {
    }

    public static GestionnaireSanteSousMode3 getInstance() {
        if (instance == null) {
            instance = new GestionnaireSanteSousMode3();
        }
        return instance;
    }

    private static ConfigPartieSousMode3 config() {
        return GestionnaireSousMode3.getInstance().obtenirConfig();
    }

    public void demarrerDegradationSante(MinecraftServer serveur) {
        ConfigPartieSousMode3 config = config();
        if (!config.degradationSante) {
            MonSubMod.JOURNALISEUR.info("Dégradation de santé désactivée par la configuration (Sous-mode 3)");
            return;
        }

        final int intervalleMs = config.intervalleDegradationSecondes * 1000;
        MonSubMod.JOURNALISEUR.info("Démarrage de la dégradation de santé pour Sous-mode 3 (perte {} PV / {} ms)",
            config.perteSanteParTick, intervalleMs);

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
        }, intervalleMs, intervalleMs);
    }

    public void arreterDegradationSante() {
        if (minuterieSante != null) {
            minuterieSante.cancel();
            minuterieSante = null;
            MonSubMod.JOURNALISEUR.info("Arrêt de la dégradation de santé pour Sous-mode 3");
        }
    }

    private void degraderSanteJoueur(ServerPlayer joueur) {
        float perte = config().perteSanteParTick;
        float santeActuelle = joueur.getHealth();
        float nouvelleSante = Math.max(0.0f, santeActuelle - perte);

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
        ConfigPartieSousMode3 config = config();

        // Réapparition autorisée : la mort n'est jamais définitive, donc inutile (et incohérent)
        // de tuer les déconnectés côté serveur — ils réapparaîtront à la reconnexion, comme
        // un joueur en ligne (verifierMortApresReconnexion -> reapparaitreApresMort).
        if (config.reapparitionAutorisee) {
            return;
        }

        Map<String, GestionnaireSousMode3.InfoDeconnexion> joueursDeconnectes =
            gestionnaire.obtenirInfoJoueursDeconnectes();
        long tempsActuel = System.currentTimeMillis();
        long tempsDebutJeu = gestionnaire.obtenirHeureDebutPartie();

        if (tempsDebutJeu <= 0) {
            return;
        }

        final long intervalleMs = Math.max(1L, (long) config.intervalleDegradationSecondes * 1000L);

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
            long numeroTickALaDeconnexion = (tempsDeconnexionEffectif - tempsDebutJeu) / intervalleMs;
            long numeroTickMaintenant = (tempsActuel - tempsDebutJeu) / intervalleMs;
            int ticksSanteManques = (int) (numeroTickMaintenant - numeroTickALaDeconnexion);
            float santeActuelle = info.santeADeconnexion - (ticksSanteManques * config.perteSanteParTick);

            if (santeActuelle <= 0.0f) {
                MonSubMod.JOURNALISEUR.info("Le joueur déconnecté {} est mort côté serveur (santé: {} -> {}, {} ticks manqués)",
                    nomJoueur, info.santeADeconnexion, santeActuelle, ticksSanteManques);

                gestionnaire.gererMortJoueurDeconnecte(nomJoueur, idJoueur, serveur);

                String messageMort = "§e" + nomJoueur + " §cest mort pendant sa déconnexion !";
                for (ServerPlayer j : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                    j.sendSystemMessage(Component.literal(messageMort));
                }

                if (gestionnaire.verifierFinParElimination(serveur)) {
                    return;
                }
            }
        }
    }

    /**
     * Consommation d'un bonbon typé Bleu/Rouge (option « Spécialisation » du menu N) :
     * la spécialisation gère le multiplicateur (1.0, ou réduit sous pénalité / changement
     * de type), le soin est plafonné à la vie max. Retourne la santé restaurée.
     */
    public float gererConsommationBonbonTypee(ServerPlayer joueur,
                                              com.example.mysubmod.sousmodes.sousmode2.TypeRessource typeRessource) {
        float santeActuelle = joueur.getHealth();
        float santeMax = joueur.getMaxHealth();

        float multiplicateur = GestionnaireSpecialisationSousMode3.getInstance()
            .gererCollecteRessource(joueur, typeRessource);
        float restaurationReelle = 2.0f * multiplicateur;

        float nouvelleSante = Math.min(santeMax, santeActuelle + restaurationReelle);
        joueur.setHealth(nouvelleSante);

        boolean aPenalite = GestionnaireSpecialisationSousMode3.getInstance().aPenalite(joueur.getUUID());
        if (aPenalite) {
            long msRestants = GestionnaireSpecialisationSousMode3.getInstance()
                .obtenirTempsRestantPenalite(joueur.getUUID());
            joueur.sendSystemMessage(Component.literal(String.format(
                "§e+%.2f ❤ §c(Pénalité - %s restant)", restaurationReelle / 2.0f,
                GestionnaireSpecialisationSousMode3.formaterTemps(msRestants))));
        } else {
            joueur.sendSystemMessage(Component.literal(String.format(
                "§a+%.1f ❤ §7(%s)", restaurationReelle / 2.0f, typeRessource.obtenirNomAffichage())));
        }

        if (GestionnaireSousMode3.getInstance().obtenirEnregistreurDonnees() != null) {
            GestionnaireSousMode3.getInstance().obtenirEnregistreurDonnees()
                .enregistrerChangementSante(joueur, santeActuelle, nouvelleSante);
        }
        return restaurationReelle;
    }

    private void gererMortJoueur(ServerPlayer joueur) {
        GestionnaireSousMode3.getInstance().deposerInventaireALaMort(joueur);
        // Réapparition éventuelle : le joueur reste vivant, aucune conséquence.
        if (GestionnaireSousMode3.getInstance().reapparaitreApresMort(joueur)) {
            return;
        }

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

        GestionnaireSousMode3.getInstance().verifierFinParElimination(joueur.server);
    }
}

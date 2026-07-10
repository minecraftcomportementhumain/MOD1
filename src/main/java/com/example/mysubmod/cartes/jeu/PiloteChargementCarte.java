package com.example.mysubmod.cartes.jeu;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.cartes.reseau.PaquetProgressionChargement;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode3.GenerateurCarteSousMode3;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Pilote la génération d'une carte étalée sur plusieurs ticks serveur, au lieu de figer
 * un tick entier. Chaque tick, la {@link GenerateurCarteSousMode3.Tache} avance dans son
 * budget de temps et la progression est diffusée aux joueurs (barre de chargement).
 *
 * <p>Un seul chargement à la fois (garanti par le verrou de changement de sous-mode).
 * Une fois la carte entièrement posée, le callback fourni est exécuté sur le thread
 * serveur pour enchaîner la suite de l'activation (bonbons, zones, téléportations...).</p>
 */
@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PiloteChargementCarte {
    /** Temps maximal consacré à la génération par tick (30 ms sur ~50 ms/tick). */
    private static final long BUDGET_NANOS = 30_000_000L;

    private static GenerateurCarteSousMode3.Tache tache;
    private static Runnable apresGeneration;
    private static String nomCarte;
    private static MinecraftServer serveur;
    private static int dernierPourcentEnvoye = -1;

    private PiloteChargementCarte() {
    }

    public static boolean estEnCours() {
        return tache != null;
    }

    /**
     * Démarre une génération étalée. {@code apresGeneration} est exécuté sur le thread
     * serveur une fois la carte entièrement posée (peut être null).
     */
    public static void demarrer(MinecraftServer s, GenerateurCarteSousMode3.Tache t,
                                String nom, Runnable apres) {
        serveur = s;
        tache = t;
        nomCarte = nom != null ? nom : "";
        apresGeneration = apres;
        dernierPourcentEnvoye = -1;
        envoyerProgression(0, true);
        MonSubMod.JOURNALISEUR.info("Démarrage de la génération étalée de la carte « {} »", nomCarte);
    }

    /**
     * Annule la génération en cours (changement de mode / arrêt). Ne nettoie pas les blocs
     * déjà posés : l'appelant efface la bande de la cage via le résultat de génération.
     */
    public static void annuler() {
        if (tache == null) {
            return;
        }
        MonSubMod.JOURNALISEUR.info("Annulation de la génération étalée de la carte « {} »", nomCarte);
        envoyerProgression(0, false); // masquer la barre
        tache.abandonner(); // libérer les tickets de préchargement de chunks
        tache = null;
        apresGeneration = null;
        serveur = null;
        nomCarte = null;
        dernierPourcentEnvoye = -1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || tache == null) {
            return;
        }

        boolean termine;
        try {
            termine = tache.avancer(BUDGET_NANOS);
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur pendant la génération étalée de la carte", e);
            termine = true; // abandonner proprement : on enchaîne quand même la suite
        }

        int pourcent = Math.round(tache.progression() * 100);
        if (pourcent != dernierPourcentEnvoye) {
            envoyerProgression(pourcent, true);
            dernierPourcentEnvoye = pourcent;
        }

        if (termine) {
            Runnable apres = apresGeneration;
            envoyerProgression(100, false); // 100 % puis barre masquée
            // Réinitialiser l'état AVANT le callback (celui-ci peut relancer une génération)
            tache = null;
            apresGeneration = null;
            serveur = null;
            nomCarte = null;
            dernierPourcentEnvoye = -1;
            if (apres != null) {
                try {
                    apres.run();
                } catch (Exception e) {
                    MonSubMod.JOURNALISEUR.error("Erreur dans le callback de fin de génération de carte", e);
                }
            }
        }
    }

    private static void envoyerProgression(int pourcent, boolean actif) {
        if (serveur == null) {
            return;
        }
        PaquetProgressionChargement paquet = new PaquetProgressionChargement(actif, pourcent, nomCarte);
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
        }
    }
}

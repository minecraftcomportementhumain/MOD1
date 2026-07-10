package com.example.mysubmod.sousmodes.sousmode3;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.cartes.reseau.PaquetProgressionChargement;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Efface la bande de la cage du Sous-mode 3 en l'étalant sur plusieurs ticks serveur.
 * Un effacement synchrone chargerait (voire générerait) des milliers de chunks dans un
 * seul tick sur une grande carte : le watchdog tuerait le serveur (tick > 60 s).
 *
 * <p>Deux modes : effacement de la carte désactivée (limité aux chunks réellement
 * écrits par la génération + un anneau pour le feuillage, avec le masque du périmètre),
 * et nettoyage de secours de toute une région après un arrêt brutal. Les chunks sont
 * préchargés en asynchrone comme pour la génération, avec la même contre-pression.
 * Le fichier de région n'est supprimé qu'une fois le balayage terminé.</p>
 */
@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EffaceurCarteSousMode3 {

    private static final int MAX_CHUNKS_PAR_TICK = 24;
    private static final int FENETRE_PRECHARGEMENT = 32;
    private static final int MARGE_CHUNKS_CHARGES = 2000;
    private static final int TICKS_PAUSE_MAX = 100;
    /** Temps maximal consacré à l'effacement par tick */
    private static final long BUDGET_NANOS = 30_000_000L;

    private static ServerLevel niveau;
    private static List<ChunkPos> cibles;
    private static int index;
    private static int prochainTicket;
    // Masque du périmètre (null = essuyer toute la bande des chunks ciblés)
    private static Set<Long> cellulesInterieur;
    private static int origineX;
    private static int origineZ;
    // Emprise de la salle d'attente (Y 100, dans la bande) : jamais essuyée — la
    // plateforme est reconstruite par l'activation de la salle d'attente AVANT la fin
    // du balayage, et l'effacer ferait tomber les joueurs dans le vide
    private static int[] exclusionSalleAttente;
    private static long blocsEffaces;
    private static int chunksChargesReference = -1;
    private static int ticksEnPause;
    /** Ticks consécutifs passés à attendre le chargement du même chunk (anti-blocage) */
    private static int ticksBloque;
    /** Au-delà de ce nombre de ticks bloqué sur un chunk, on le saute pour ne pas figer
     *  l'effaceur à jamais (sinon estEnCours() resterait vrai → deadlock de changement de mode) */
    private static final int TICKS_BLOCAGE_MAX = 200;
    private static String nomCarte = "";
    private static int dernierPourcentEnvoye = -1;
    /** Tâches à exécuter une fois l'effacement terminé (ex. génération suivante) */
    private static final List<Runnable> tachesApres = new ArrayList<>();

    private EffaceurCarteSousMode3() {
    }

    /** Arrêt du serveur (dont serveur intégré/LAN) : libérer les tickets restants et
     *  réinitialiser l'état statique pour ne pas pointer un monde déchargé. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (niveau != null && cibles != null) {
            for (int i = Math.max(index, 0); i < prochainTicket && i < cibles.size(); i++) {
                try {
                    ChunkPos pos = cibles.get(i);
                    niveau.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, 0, pos);
                } catch (Exception ignore) {
                    // arrêt en cours : best effort
                }
            }
        }
        niveau = null;
        cibles = null;
        cellulesInterieur = null;
        index = 0;
        prochainTicket = 0;
        tachesApres.clear();
    }

    public static boolean estEnCours() {
        return niveau != null;
    }

    /**
     * Exécute la tâche après la fin de l'effacement en cours, ou immédiatement s'il
     * n'y en a pas. Sert à enchaîner la génération d'une nouvelle carte sans écrire
     * dans des chunks en train d'être essuyés.
     */
    public static void puisExecuter(Runnable tache) {
        if (estEnCours()) {
            tachesApres.add(tache);
        } else {
            tache.run();
        }
    }

    /**
     * Effacement de la carte désactivée : uniquement les chunks réellement écrits par la
     * génération (plus un anneau d'un chunk pour le feuillage débordant), avec le masque
     * du périmètre. Une génération annulée tôt n'a presque rien à effacer.
     */
    public static void demarrer(ServerLevel monde, GenerateurCarteSousMode3.ResultatGeneration generation,
                                String nom) {
        if (monde == null || generation == null) {
            return;
        }
        if (estEnCours()) {
            // Un effacement est déjà en cours : mettre celui-ci en file
            tachesApres.add(() -> demarrer(monde, generation, nom));
            return;
        }
        Set<ChunkPos> ensemble = new HashSet<>();
        for (ChunkPos pos : generation.chunksModifies) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    ensemble.add(new ChunkPos(pos.x + dx, pos.z + dz));
                }
            }
        }
        demarrerInterne(monde, ordonner(ensemble), generation.cellulesInterieur,
            generation.origineX, generation.origineZ, nom);
    }

    /** Nettoyage de secours : toute la bande de la région enregistrée (masque nul) */
    public static void demarrerRegion(ServerLevel monde, int minX, int minZ, int maxX, int maxZ) {
        if (monde == null) {
            return;
        }
        if (estEnCours()) {
            tachesApres.add(() -> demarrerRegion(monde, minX, minZ, maxX, maxZ));
            return;
        }
        int marge = GenerateurCarteSousMode3.MARGE_EFFACEMENT;
        Set<ChunkPos> ensemble = new HashSet<>();
        for (int chunkZ = (minZ - marge) >> 4; chunkZ <= (maxZ + marge) >> 4; chunkZ++) {
            for (int chunkX = (minX - marge) >> 4; chunkX <= (maxX + marge) >> 4; chunkX++) {
                ensemble.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        demarrerInterne(monde, ordonner(ensemble), null, 0, 0, "");
    }

    private static List<ChunkPos> ordonner(Set<ChunkPos> ensemble) {
        List<ChunkPos> liste = new ArrayList<>(ensemble);
        liste.sort((a, b) -> a.z != b.z ? Integer.compare(a.z, b.z) : Integer.compare(a.x, b.x));
        return liste;
    }

    private static void demarrerInterne(ServerLevel monde, List<ChunkPos> chunks,
                                        Set<Long> interieur, int origX, int origZ, String nom) {
        niveau = monde;
        cibles = chunks;
        index = 0;
        prochainTicket = 0;
        cellulesInterieur = interieur;
        origineX = origX;
        origineZ = origZ;
        blocsEffaces = 0;
        chunksChargesReference = -1;
        ticksEnPause = 0;
        ticksBloque = 0;
        nomCarte = nom != null ? nom : "";
        dernierPourcentEnvoye = -1;
        exclusionSalleAttente = com.example.mysubmod.sousmodes.salleattente.GestionnaireSalleAttente
            .obtenirEmpriseProtegee();
        MonSubMod.JOURNALISEUR.info("Effacement de la carte du Sous-mode 3 : {} chunks à balayer", chunks.size());
        if (chunks.isEmpty()) {
            terminer();
        } else {
            envoyerProgression(0, true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || niveau == null) {
            return;
        }

        try {
            long debut = System.nanoTime();
            int chunksCeTick = 0;
            while (index < cibles.size() && chunksCeTick < MAX_CHUNKS_PAR_TICK
                && System.nanoTime() - debut < BUDGET_NANOS) {
                // Même contre-pression que la génération : borne la croissance des chunks
                // chargés par rapport au niveau observé au démarrage de l'effacement
                int charges = niveau.getChunkSource().getLoadedChunksCount();
                if (chunksChargesReference < 0) {
                    chunksChargesReference = charges;
                }
                if (charges > chunksChargesReference + MARGE_CHUNKS_CHARGES) {
                    if (++ticksEnPause > TICKS_PAUSE_MAX) {
                        chunksChargesReference = charges;
                        ticksEnPause = 0;
                    }
                    return;
                }
                ticksEnPause = 0;

                precharger();
                ChunkPos pos = cibles.get(index);
                if (!niveau.getChunkSource().hasChunk(pos.x, pos.z)) {
                    // Filet anti-blocage : si un chunk ne se charge jamais (worldgen calée),
                    // le sauter après un délai plutôt que de figer l'effaceur pour toujours.
                    if (++ticksBloque > TICKS_BLOCAGE_MAX) {
                        MonSubMod.JOURNALISEUR.warn("Effacement : chunk {} jamais chargé, ignoré", pos);
                        libererTicket(index);
                        index++;
                        ticksBloque = 0;
                        continue;
                    }
                    return; // chargement/génération en cours sur les threads de worldgen
                }
                ticksBloque = 0;
                blocsEffaces += GenerateurCarteSousMode3.essuyerBandeChunk(
                    niveau, niveau.getChunk(pos.x, pos.z), cellulesInterieur, origineX, origineZ,
                    exclusionSalleAttente);
                libererTicket(index);
                index++;
                chunksCeTick++;
            }
            int pourcent = Math.round(100.0f * index / cibles.size());
            if (pourcent != dernierPourcentEnvoye) {
                envoyerProgression(pourcent, true);
                dernierPourcentEnvoye = pourcent;
            }
            if (index >= cibles.size()) {
                terminer();
            }
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur pendant l'effacement étalé de la carte", e);
            terminer(); // abandonner proprement (le fichier de région est tout de même supprimé)
        }
    }

    private static void precharger() {
        int fin = Math.min(cibles.size(), index + FENETRE_PRECHARGEMENT);
        while (prochainTicket < fin) {
            ChunkPos pos = cibles.get(prochainTicket);
            niveau.getChunkSource().addRegionTicket(TicketType.FORCED, pos, 0, pos);
            prochainTicket++;
        }
    }

    private static void libererTicket(int indexCible) {
        if (indexCible < prochainTicket) {
            ChunkPos pos = cibles.get(indexCible);
            niveau.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, 0, pos);
        }
    }

    private static void terminer() {
        for (int i = Math.max(index, 0); i < prochainTicket; i++) {
            ChunkPos pos = cibles.get(i);
            niveau.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, 0, pos);
        }
        MonSubMod.JOURNALISEUR.info("Carte Sous-mode 3 effacée : {} blocs supprimés ({} chunks balayés)",
            blocsEffaces, index);
        GenerateurCarteSousMode3.supprimerFichierRegion();
        envoyerProgression(100, false); // 100 % puis barre masquée

        niveau = null;
        cibles = null;
        cellulesInterieur = null;
        index = 0;
        prochainTicket = 0;
        chunksChargesReference = -1;
        ticksEnPause = 0;

        if (!tachesApres.isEmpty()) {
            List<Runnable> aExecuter = new ArrayList<>(tachesApres);
            tachesApres.clear();
            for (Runnable tache : aExecuter) {
                try {
                    tache.run();
                } catch (Exception e) {
                    MonSubMod.JOURNALISEUR.error("Erreur dans une tâche après effacement de carte", e);
                }
            }
        }
    }

    /** Barre de progression du nettoyage, diffusée aux joueurs authentifiés */
    private static void envoyerProgression(int pourcent, boolean actif) {
        if (niveau == null) {
            return;
        }
        PaquetProgressionChargement paquet = new PaquetProgressionChargement(
            actif, pourcent, nomCarte, PaquetProgressionChargement.TITRE_NETTOYAGE);
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(niveau.getServer())) {
            GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
        }
    }
}

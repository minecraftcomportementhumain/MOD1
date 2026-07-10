package com.example.mysubmod.sousmodes.sousmode3;

import com.example.mysubmod.MonSubMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

    private static final int MAX_CHUNKS_PAR_TICK = 6;
    private static final int FENETRE_PRECHARGEMENT = 16;
    private static final int MARGE_CHUNKS_CHARGES = 2000;
    private static final int TICKS_PAUSE_MAX = 100;

    private static ServerLevel niveau;
    private static List<ChunkPos> cibles;
    private static int index;
    private static int prochainTicket;
    // Masque du périmètre (null = essuyer toute la bande des chunks ciblés)
    private static Set<Long> cellulesInterieur;
    private static int origineX;
    private static int origineZ;
    private static long blocsEffaces;
    private static int chunksChargesReference = -1;
    private static int ticksEnPause;
    /** Tâches à exécuter une fois l'effacement terminé (ex. génération suivante) */
    private static final List<Runnable> tachesApres = new ArrayList<>();

    private EffaceurCarteSousMode3() {
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
    public static void demarrer(ServerLevel monde, GenerateurCarteSousMode3.ResultatGeneration generation) {
        if (monde == null || generation == null) {
            return;
        }
        if (estEnCours()) {
            // Un effacement est déjà en cours : mettre celui-ci en file
            tachesApres.add(() -> demarrer(monde, generation));
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
            generation.origineX, generation.origineZ);
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
        demarrerInterne(monde, ordonner(ensemble), null, 0, 0);
    }

    private static List<ChunkPos> ordonner(Set<ChunkPos> ensemble) {
        List<ChunkPos> liste = new ArrayList<>(ensemble);
        liste.sort((a, b) -> a.z != b.z ? Integer.compare(a.z, b.z) : Integer.compare(a.x, b.x));
        return liste;
    }

    private static void demarrerInterne(ServerLevel monde, List<ChunkPos> chunks,
                                        Set<Long> interieur, int origX, int origZ) {
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
        MonSubMod.JOURNALISEUR.info("Effacement de la carte du Sous-mode 3 : {} chunks à balayer", chunks.size());
        if (chunks.isEmpty()) {
            terminer();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || niveau == null) {
            return;
        }

        try {
            int chunksCeTick = 0;
            while (index < cibles.size() && chunksCeTick < MAX_CHUNKS_PAR_TICK) {
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
                    return; // chargement/génération en cours sur les threads de worldgen
                }
                blocsEffaces += GenerateurCarteSousMode3.essuyerBandeChunk(
                    niveau, niveau.getChunk(pos.x, pos.z), cellulesInterieur, origineX, origineZ);
                libererTicket(index);
                index++;
                chunksCeTick++;
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
}

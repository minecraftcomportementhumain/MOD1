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
    private static final int MAX_CHUNKS_PAR_TICK_RAPIDE = 64;
    /** Fenêtre large : les threads de worldgen doivent toujours avoir du travail d'avance */
    private static final int FENETRE_PRECHARGEMENT = 128;
    /** Distance des tickets (priorité de worldgen) — voir la constante homonyme du
     *  générateur : add et remove doivent utiliser la même valeur. */
    private static final int DISTANCE_TICKET = GenerateurCarteSousMode3.Tache.DISTANCE_TICKET_PRECHARGEMENT;
    private static final int MARGE_CHUNKS_CHARGES = 2000;
    private static final int TICKS_PAUSE_MAX = 100;
    /** Temps maximal consacré à l'effacement par tick */
    private static final long BUDGET_NANOS = 30_000_000L;
    /** Budget relevé quand le serveur est détendu */
    private static final long BUDGET_NANOS_RAPIDE = 45_000_000L;
    /** Hystérésis du mode rapide (la mesure inclut notre propre budget — voir
     *  PiloteChargementCarte pour le raisonnement) */
    private static final float MSPT_ENTREE_RAPIDE = 40.0f;
    private static final float MSPT_SORTIE_RAPIDE = 48.0f;
    private static boolean rapide;

    private static ServerLevel niveau;
    private static List<ChunkPos> cibles;
    /** Plus petit index NON traité (préfixe) : borne basse de la fenêtre de tickets ;
     *  les chunks se traitent hors ordre à l'intérieur de la fenêtre */
    private static int index;
    private static java.util.BitSet traites;
    private static int nbTraites;
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
    /** Objets résiduels (bonbons, objets estampillés d'une session) retirés au passage */
    private static long objetsResiduelsRetires;
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
                    niveau.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, DISTANCE_TICKET, pos);
                } catch (Exception ignore) {
                    // arrêt en cours : best effort
                }
            }
        }
        niveau = null;
        cibles = null;
        traites = null;
        nbTraites = 0;
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
        traites = new java.util.BitSet(chunks.size());
        nbTraites = 0;
        prochainTicket = 0;
        cellulesInterieur = interieur;
        origineX = origX;
        origineZ = origZ;
        blocsEffaces = 0;
        objetsResiduelsRetires = 0;
        chunksChargesReference = -1;
        ticksEnPause = 0;
        ticksBloque = 0;
        rapide = false;
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
            // Budget adaptatif : serveur détendu → budget et plafond de chunks relevés
            float tickMoyen = niveau.getServer().getAverageTickTime();
            if (!rapide && tickMoyen < MSPT_ENTREE_RAPIDE) {
                rapide = true;
            } else if (rapide && tickMoyen > MSPT_SORTIE_RAPIDE) {
                rapide = false;
            }
            long budget = rapide ? BUDGET_NANOS_RAPIDE : BUDGET_NANOS;
            int maxChunks = rapide ? MAX_CHUNKS_PAR_TICK_RAPIDE : MAX_CHUNKS_PAR_TICK;

            long debut = System.nanoTime();
            int chunksCeTick = 0;
            while (nbTraites < cibles.size() && chunksCeTick < maxChunks
                && System.nanoTime() - debut < budget) {
                // Même contre-pression que la génération : sous pression, plus aucun
                // NOUVEAU ticket — mais les chunks déjà chargés de la fenêtre restent
                // essuyés (cela n'aggrave rien et libère leurs tickets)
                int charges = niveau.getChunkSource().getLoadedChunksCount();
                if (chunksChargesReference < 0) {
                    chunksChargesReference = charges;
                }
                boolean pressionHaute = charges > chunksChargesReference + MARGE_CHUNKS_CHARGES;
                if (!pressionHaute) {
                    precharger();
                }
                // Premier chunk PRÊT de la fenêtre, pas forcément le prochain de l'ordre
                // (les chunks sont indépendants) : ne pas gaspiller le tick sur un chunk lent
                int i = chercherChunkPret();
                if (i < 0) {
                    if (pressionHaute) {
                        if (++ticksEnPause > TICKS_PAUSE_MAX) {
                            chunksChargesReference = charges;
                            ticksEnPause = 0;
                        }
                    } else if (++ticksBloque > TICKS_BLOCAGE_MAX) {
                        // Filet anti-blocage : si plus rien ne se charge (worldgen calée),
                        // sauter le premier non traité plutôt que de figer l'effaceur
                        MonSubMod.JOURNALISEUR.warn("Effacement : chunk {} jamais chargé, ignoré",
                            cibles.get(index));
                        libererTicket(index);
                        marquerTraite(index);
                        ticksBloque = 0;
                        continue;
                    }
                    break; // rien de prêt ce tick
                }
                ticksEnPause = 0;
                ticksBloque = 0;
                ChunkPos pos = cibles.get(i);
                blocsEffaces += GenerateurCarteSousMode3.essuyerBandeChunk(
                    niveau, niveau.getChunk(pos.x, pos.z), cellulesInterieur, origineX, origineZ,
                    exclusionSalleAttente);
                purgerObjetsResiduels(pos);
                libererTicket(i);
                marquerTraite(i);
                chunksCeTick++;
            }
            int pourcent = Math.round(100.0f * nbTraites / cibles.size());
            if (pourcent != dernierPourcentEnvoye) {
                envoyerProgression(pourcent, true);
                dernierPourcentEnvoye = pourcent;
            }
            if (nbTraites >= cibles.size()) {
                terminer();
            }
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur pendant l'effacement étalé de la carte", e);
            // Abandon en cours de balayage : NE PAS supprimer le fichier de région, sinon le
            // terrain généré non essuyé subsisterait à jamais. On le conserve pour qu'un
            // balayage de secours (nettoyerRegionResiduelle) reprenne au prochain démarrage.
            terminer(false);
        }
    }

    private static void precharger() {
        int fin = Math.min(cibles.size(), index + FENETRE_PRECHARGEMENT);
        while (prochainTicket < fin) {
            ChunkPos pos = cibles.get(prochainTicket);
            niveau.getChunkSource().addRegionTicket(TicketType.FORCED, pos, DISTANCE_TICKET, pos);
            prochainTicket++;
        }
    }

    private static void libererTicket(int indexCible) {
        if (indexCible < prochainTicket) {
            ChunkPos pos = cibles.get(indexCible);
            niveau.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, DISTANCE_TICKET, pos);
        }
    }

    /** Premier chunk non traité et déjà chargé dans la fenêtre de tickets, ou -1. */
    private static int chercherChunkPret() {
        int fin = Math.min(cibles.size(), prochainTicket);
        for (int i = index; i < fin; i++) {
            if (traites.get(i)) {
                continue;
            }
            ChunkPos pos = cibles.get(i);
            if (niveau.getChunkSource().hasChunk(pos.x, pos.z)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Retire du chunk balayé les objets résiduels de la session terminée : bonbons et
     * objets estampillés (jetés/déposés). Les balayages synchrones de la désactivation ne
     * voient que les chunks alors chargés — un objet d'un chunk déchargé (joueurs regroupés
     * sur la plateforme en fin de partie) a été sauvé sur disque et ne réapparaîtrait qu'au
     * prochain chargement. L'effaceur repasse justement sur chaque chunk écrit : purge au
     * passage. Les entités ne sont interrogeables que si leur section est déjà chargée ;
     * pour les autres, le tueur de résidus du filtre d'apparition reste le filet.
     */
    private static void purgerObjetsResiduels(ChunkPos pos) {
        net.minecraft.world.phys.AABB boite = new net.minecraft.world.phys.AABB(
            pos.getMinBlockX(), GenerateurCarteSousMode3.Y_PLANCHER_BARRIER - 2, pos.getMinBlockZ(),
            pos.getMaxBlockX() + 1, GenerateurCarteSousMode3.Y_PLAFOND_BARRIER + 3, pos.getMaxBlockZ() + 1);
        for (net.minecraft.world.entity.item.ItemEntity objet : niveau.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, boite,
                o -> GestionnaireBonbonsSousMode3.estObjetBonbon(o.getItem())
                    || o.getPersistentData().contains(GestionnaireSousMode3.TAG_SESSION_CARTE))) {
            objet.discard();
            objetsResiduelsRetires++;
        }
    }

    /** Marque un chunk traité et avance le préfixe (borne basse de la fenêtre). */
    private static void marquerTraite(int i) {
        traites.set(i);
        nbTraites++;
        while (index < cibles.size() && traites.get(index)) {
            index++;
        }
    }

    private static void terminer() {
        terminer(true);
    }

    /**
     * @param succes vrai si le balayage est allé jusqu'au bout. Le fichier de région (record de
     *   reprise après crash) n'est supprimé que dans ce cas. En cas d'abandon (exception en
     *   cours de balayage), on le conserve pour qu'un balayage de secours reprenne au prochain
     *   démarrage — sinon les chunks générés non encore essuyés subsisteraient définitivement.
     */
    private static void terminer(boolean succes) {
        // Les chunks déjà traités de la plage ont libéré leur ticket au traitement :
        // un retrait redondant est sans effet
        for (int i = Math.max(index, 0); i < prochainTicket; i++) {
            ChunkPos pos = cibles.get(i);
            niveau.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, DISTANCE_TICKET, pos);
        }
        if (succes) {
            MonSubMod.JOURNALISEUR.info("Carte Sous-mode 3 effacée : {} blocs supprimés ({} chunks balayés, "
                + "{} objets résiduels retirés)", blocsEffaces, nbTraites, objetsResiduelsRetires);
            GenerateurCarteSousMode3.supprimerFichierRegion();
        } else {
            MonSubMod.JOURNALISEUR.warn("Effacement de la carte Sous-mode 3 interrompu ({}/{} chunks balayés) : "
                + "fichier de région conservé pour un nouveau balayage au prochain démarrage",
                nbTraites, cibles != null ? cibles.size() : 0);
        }
        envoyerProgression(100, false); // barre masquée

        niveau = null;
        cibles = null;
        traites = null;
        nbTraites = 0;
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

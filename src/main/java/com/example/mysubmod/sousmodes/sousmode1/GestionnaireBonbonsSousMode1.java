package com.example.mysubmod.sousmodes.sousmode1;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.objets.ItemsMod;
import com.example.mysubmod.sousmodes.sousmode1.donnees.EntreeApparitionBonbon;
import com.example.mysubmod.sousmodes.sousmode1.iles.TypeIle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GestionnaireBonbonsSousMode1 {
    private static GestionnaireBonbonsSousMode1 instance;
    private Timer minuterieApparitionBonbons;
    private final Map<ItemEntity, Long> tempsApparitionBonbons = new ConcurrentHashMap<>();
    private final Set<net.minecraft.world.level.ChunkPos> chunksForceCharges = new HashSet<>();
    private MinecraftServer serveurJeu; // Garder une référence au serveur

    private GestionnaireBonbonsSousMode1() {}

    public static GestionnaireBonbonsSousMode1 obtenirInstance() {
        if (instance == null) {
            instance = new GestionnaireBonbonsSousMode1();
        }
        return instance;
    }

    public void demarrerApparitionBonbons(MinecraftServer serveur) {
        MonSubMod.JOURNALISEUR.info("Démarrage de l'apparition de bonbons pour Sous-mode 1");

        // Stocker la référence du serveur
        this.serveurJeu = serveur;

        // Forcer le chargement uniquement si ce n'est pas déjà fait (éviter les appels répétés et les plantages)
        if (chunksForceCharges.isEmpty()) {
            forceChargerChunksIles(serveur);
        }

        // Obtenir la configuration d'apparition de GestionnaireSousMode1
        List<EntreeApparitionBonbon> configApparition = GestionnaireSousMode1.getInstance().obtenirConfigApparitionBonbons();

        if (configApparition == null || configApparition.isEmpty()) {
            MonSubMod.JOURNALISEUR.error("Aucune configuration d'apparition de bonbons chargée !");
            return;
        }

        MonSubMod.JOURNALISEUR.info("Chargé {} entrées d'apparition de bonbons depuis la configuration", configApparition.size());

        // Planifier les apparitions de bonbons basées sur le fichier de configuration
        planifierApparitionsBonbonsDepuisConfig(serveur, configApparition);
    }

    private void planifierApparitionsBonbonsDepuisConfig(MinecraftServer serveur, List<EntreeApparitionBonbon> configApparition) {
        minuterieApparitionBonbons = new Timer();

        for (EntreeApparitionBonbon entree : configApparition) {
            minuterieApparitionBonbons.schedule(new TimerTask() {
                @Override
                public void run() {
                    serveur.execute(() -> faireApparaitreBonbonsDepuisEntree(serveur, entree));
                }
            }, entree.obtenirTempsMs());
        }
    }

    public void arreterApparitionBonbons() {
        if (minuterieApparitionBonbons != null) {
            minuterieApparitionBonbons.cancel();
            minuterieApparitionBonbons = null;
        }

        // Retirer tous les bonbons existants
        retirerTousLesBonbons();

        // Note: Nous gardons les chunks forcés chargés pour éviter les plantages dus au déchargement

        MonSubMod.JOURNALISEUR.info("Arrêt de l'apparition de bonbons pour Sous-mode 1");
    }

    private void faireApparaitreBonbonsDepuisEntree(MinecraftServer serveur, EntreeApparitionBonbon entree) {
        ServerLevel overworld = serveur.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        // Vérifier si nous avons encore des joueurs vivants
        Set<UUID> joueursVivants = GestionnaireSousMode1.getInstance().obtenirJoueursVivants();
        if (joueursVivants.isEmpty()) return;

        BlockPos centreApparition = entree.obtenirPosition();
        int nombreBonbons = entree.obtenirNombreBonbons();

        // Suivre les positions utilisées pour éviter les coordonnées en double
        Set<BlockPos> positionsUtilisees = new HashSet<>();
        Random aleatoire = new Random();

        // Faire apparaître plusieurs bonbons autour de la position spécifiée
        for (int i = 0; i < nombreBonbons; i++) {
            BlockPos positionApparition = trouverPositionApparitionValidePresPoint(overworld, centreApparition, 3, positionsUtilisees); // rayon de 3 blocs autour du point d'apparition
            if (positionApparition != null) {
                positionsUtilisees.add(positionApparition); // Marquer cette position comme utilisée

                // Ajouter un petit décalage aléatoire pour empêcher l'empilement exact
                double decalageX = 0.3 + aleatoire.nextDouble() * 0.4; // Aléatoire entre 0.3 et 0.7
                double decalageZ = 0.3 + aleatoire.nextDouble() * 0.4; // Aléatoire entre 0.3 et 0.7

                ItemStack pileBonbons = new ItemStack(ItemsMod.BONBON.get());
                ItemEntity entiteBonbon = new ItemEntity(overworld,
                    positionApparition.getX() + decalageX,
                    positionApparition.getY() - 0.9,
                    positionApparition.getZ() + decalageZ,
                    pileBonbons);

                // Empêcher le ramassage pendant 3 secondes
                entiteBonbon.setPickUpDelay(60); // 3 secondes (60 ticks)

                // Empêcher le bonbon de disparaître
                entiteBonbon.setUnlimitedLifetime();

                overworld.addFreshEntity(entiteBonbon);
                tempsApparitionBonbons.put(entiteBonbon, System.currentTimeMillis());

                // Journaliser l'apparition de bonbon
                if (GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees() != null) {
                    GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees().enregistrerApparitionBonbon(positionApparition);
                }
            }
        }

        MonSubMod.JOURNALISEUR.info("Fait apparaître {} bonbons au temps {}s", nombreBonbons, entree.obtenirTempsSecondes());
    }


    private BlockPos trouverPositionApparitionValidePresPoint(ServerLevel niveau, BlockPos pointApparition, int rayon, Set<BlockPos> positionsUtilisees) {
        Random aleatoire = new Random();

        for (int tentatives = 0; tentatives < 100; tentatives++) {
            int x = pointApparition.getX() + aleatoire.nextInt(rayon * 2 + 1) - rayon;
            int z = pointApparition.getZ() + aleatoire.nextInt(rayon * 2 + 1) - rayon;

            // Trouver le niveau du sol
            for (int y = pointApparition.getY() + 5; y >= pointApparition.getY() - 5; y--) {
                BlockPos positionVerifiee = new BlockPos(x, y, z);
                BlockPos positionAuDessus = positionVerifiee.above();

                // Vérifier si la position est déjà utilisée
                if (positionsUtilisees.contains(positionAuDessus)) {
                    continue;
                }

                if (!niveau.getBlockState(positionVerifiee).isAir() &&
                    niveau.getBlockState(positionAuDessus).isAir() &&
                    niveau.getBlockState(positionAuDessus.above()).isAir() &&
                    estSurSurfaceIle(niveau, positionVerifiee)) {
                    return positionAuDessus;
                }
            }
        }

        // Si aucune position valide n'a été trouvée à proximité, retourner le point d'apparition lui-même (avec un petit décalage pour éviter le chevauchement exact)
        return pointApparition;
    }

    private boolean estSurSurfaceIle(ServerLevel niveau, BlockPos pos) {
        // Vérifier si le bloc est de l'herbe (surface d'île) et non des briques de pierre (chemin)
        net.minecraft.world.level.block.state.BlockState etat = niveau.getBlockState(pos);
        return etat.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
    }


    private void retirerTousLesBonbons() {
        for (ItemEntity bonbon : tempsApparitionBonbons.keySet()) {
            if (bonbon.isAlive()) {
                bonbon.discard();
            }
        }
        tempsApparitionBonbons.clear();
    }

    public void retirerTousBonbonsDuMonde(MinecraftServer serveur) {
        ServerLevel overworld = serveur.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        int nombreRetire = 0;

        // Forcer le chargement des chunks des îles pour s'assurer d'attraper tous les bonbons
        GestionnaireSousMode1 gestionnaire = GestionnaireSousMode1.getInstance();
        if (gestionnaire.obtenirCentreIlePetite() != null) {
            forceChargerChunk(overworld, gestionnaire.obtenirCentreIlePetite());
            forceChargerChunk(overworld, gestionnaire.obtenirCentreIleMoyenne());
            forceChargerChunk(overworld, gestionnaire.obtenirCentreIleGrande());
            forceChargerChunk(overworld, gestionnaire.obtenirCentreIleTresGrande());
        }

        // Retirer TOUS les objets bonbons du monde
        for (net.minecraft.world.entity.Entity entite : overworld.getAllEntities()) {
            if (entite instanceof ItemEntity entiteObjet) {
                if (entiteObjet.getItem().getItem() == ItemsMod.BONBON.get()) {
                    entiteObjet.discard();
                    nombreRetire++;
                }
            }
        }

        tempsApparitionBonbons.clear();
        MonSubMod.JOURNALISEUR.info("Retiré {} objets bonbons du monde", nombreRetire);
    }

    private void forceChargerChunk(ServerLevel niveau, BlockPos pos) {
        if (pos != null) {
            niveau.getChunkAt(pos);
        }
    }

    /**
     * Obtenir le nombre de bonbons disponibles par île en scannant tous les objets bonbons dans le monde
     * Compte CHAQUE ItemEntity de bonbon individuellement, même si plusieurs bonbons partagent la même position
     */
    public Map<TypeIle, Integer> obtenirBonbonsDisponiblesParIle(MinecraftServer serveur) {
        Map<TypeIle, Integer> compteurs = new HashMap<>();
        compteurs.put(TypeIle.PETITE, 0);
        compteurs.put(TypeIle.MOYENNE, 0);
        compteurs.put(TypeIle.GRANDE, 0);
        compteurs.put(TypeIle.TRES_GRANDE, 0);

        ServerLevel overworld = serveur.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return compteurs;

        GestionnaireSousMode1 gestionnaire = GestionnaireSousMode1.getInstance();
        int totalCompte = 0;
        int pasSurIle = 0;

        // Scanner TOUS les objets ItemEntity de bonbons dans le monde - compter CHAQUE entité séparément
        for (net.minecraft.world.entity.Entity entite : overworld.getAllEntities()) {
            if (entite instanceof ItemEntity entiteObjet) {
                if (entiteObjet.getItem().getItem() == ItemsMod.BONBON.get()) {
                    if (entiteObjet.isAlive() && !entiteObjet.isRemoved()) {
                        // Compter le bonbon basé sur la taille de la pile (au cas où plusieurs bonbons sont dans une ItemEntity)
                        int quantiteBonbons = entiteObjet.getItem().getCount();

                        // Déterminer l'île par position actuelle
                        TypeIle ile = determinerIleDepuisPosition(entiteObjet.position(), gestionnaire);
                        if (ile != null) {
                            totalCompte += quantiteBonbons;
                            compteurs.put(ile, compteurs.get(ile) + quantiteBonbons);
                        } else {
                            // Bonbon pas sur une île
                            pasSurIle += quantiteBonbons;
                            MonSubMod.JOURNALISEUR.warn("Bonbon non compté - pas sur une île à {} (ID entité: {})",
                                entiteObjet.position(), entite.getId());
                        }
                    }
                }
            }
        }

        MonSubMod.JOURNALISEUR.info("Compteur de bonbons: {} total trouvés ({} pas sur les îles) - Petit:{} Moyen:{} Grand:{} TresGrand:{}",
            totalCompte, pasSurIle, compteurs.get(TypeIle.PETITE), compteurs.get(TypeIle.MOYENNE),
            compteurs.get(TypeIle.GRANDE), compteurs.get(TypeIle.TRES_GRANDE));

        return compteurs;
    }

    private void forceChargerChunksIles(MinecraftServer serveur) {
        ServerLevel overworld = serveur.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return;

        GestionnaireSousMode1 gestionnaire = GestionnaireSousMode1.getInstance();

        // Forcer le chargement des chunks pour chaque île (charger 3x3 chunks autour du centre pour couvrir toute l'île)
        forceChargerZoneIle(overworld, gestionnaire.obtenirCentreIlePetite(), 2); // Petit: 60x60 = ~4 chunks
        forceChargerZoneIle(overworld, gestionnaire.obtenirCentreIleMoyenne(), 3); // Moyen: 90x90 = ~6 chunks
        forceChargerZoneIle(overworld, gestionnaire.obtenirCentreIleGrande(), 4); // Grand: 120x120 = ~8 chunks
        forceChargerZoneIle(overworld, gestionnaire.obtenirCentreIleTresGrande(), 5); // Très Grand: 150x150 = ~10 chunks

        MonSubMod.JOURNALISEUR.info("Chargé forcé {} chunks pour toutes les îles", chunksForceCharges.size());
    }

    private void forceChargerZoneIle(ServerLevel niveau, BlockPos centre, int rayonChunk) {
        if (centre == null) return;

        net.minecraft.world.level.ChunkPos chunkCentre = new net.minecraft.world.level.ChunkPos(centre);

        // Charger rayonChunk x rayonChunk chunks autour du centre de l'île
        for (int x = -rayonChunk; x <= rayonChunk; x++) {
            for (int z = -rayonChunk; z <= rayonChunk; z++) {
                net.minecraft.world.level.ChunkPos posChunk = new net.minecraft.world.level.ChunkPos(
                    chunkCentre.x + x,
                    chunkCentre.z + z
                );

                // Forcer le chargement uniquement s'il n'est pas déjà suivi
                if (!chunksForceCharges.contains(posChunk)) {
                    try {
                        niveau.setChunkForced(posChunk.x, posChunk.z, true);
                        chunksForceCharges.add(posChunk);
                    } catch (Exception e) {
                        MonSubMod.JOURNALISEUR.warn("Échec du chargement forcé du chunk à {}, {}: {}", posChunk.x, posChunk.z, e.getMessage());
                    }
                }
            }
        }
    }

    private void dechargerChunksForces() {
        if (serveurJeu == null || chunksForceCharges.isEmpty()) return;

        ServerLevel overworld = serveurJeu.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            chunksForceCharges.clear();
            serveurJeu = null;
            return;
        }

        try {
            for (net.minecraft.world.level.ChunkPos posChunk : chunksForceCharges) {
                try {
                    overworld.setChunkForced(posChunk.x, posChunk.z, false);
                } catch (Exception e) {
                    MonSubMod.JOURNALISEUR.warn("Échec du déchargement forcé du chunk à {}, {}: {}", posChunk.x, posChunk.z, e.getMessage());
                }
            }
        } finally {
            chunksForceCharges.clear();
            serveurJeu = null;
        }
    }

    /**
     * Déterminer à quelle île appartient une position en vérifiant si elle est dans les limites de l'île
     */
    private TypeIle determinerIleDepuisPosition(net.minecraft.world.phys.Vec3 pos, GestionnaireSousMode1 gestionnaire) {
        BlockPos centrePetit = gestionnaire.obtenirCentreIlePetite();
        BlockPos centreMoyen = gestionnaire.obtenirCentreIleMoyenne();
        BlockPos centreGrand = gestionnaire.obtenirCentreIleGrande();
        BlockPos centreTresGrand = gestionnaire.obtenirCentreIleTresGrande();

        if (centrePetit == null || centreMoyen == null || centreGrand == null || centreTresGrand == null) {
            return null;
        }

        // Vérifier les limites de chaque île (zone carrée définie par le rayon)
        // Petite île: 60x60 (rayon 30)
        if (estDansLimitesIle(pos, centrePetit, TypeIle.PETITE.obtenirRayon())) {
            return TypeIle.PETITE;
        }

        // Île moyenne: 90x90 (rayon 45)
        if (estDansLimitesIle(pos, centreMoyen, TypeIle.MOYENNE.obtenirRayon())) {
            return TypeIle.MOYENNE;
        }

        // Grande île: 120x120 (rayon 60)
        if (estDansLimitesIle(pos, centreGrand, TypeIle.GRANDE.obtenirRayon())) {
            return TypeIle.GRANDE;
        }

        // Île très grande: 150x150 (rayon 75)
        if (estDansLimitesIle(pos, centreTresGrand, TypeIle.TRES_GRANDE.obtenirRayon())) {
            return TypeIle.TRES_GRANDE;
        }

        return null; // Pas sur une île
    }

    /**
     * Vérifier si une position est dans les limites carrées d'une île
     */
    private boolean estDansLimitesIle(net.minecraft.world.phys.Vec3 pos, BlockPos centre, int rayon) {
        // Vérifier si la position est dans la zone carrée (pas circulaire)
        // Les îles sont carrées avec longueur de côté = 2 * rayon
        double distX = Math.abs(pos.x - centre.getX());
        double distZ = Math.abs(pos.z - centre.getZ());

        return distX <= rayon && distZ <= rayon;
    }

    public void surRamassageBonbon(ItemEntity bonbon) {
        tempsApparitionBonbons.remove(bonbon);
    }
}

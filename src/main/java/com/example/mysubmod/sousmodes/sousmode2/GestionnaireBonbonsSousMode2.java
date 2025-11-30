package com.example.mysubmod.sousmodes.sousmode2;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.objets.ItemsMod;
import com.example.mysubmod.sousmodes.sousmode2.donnees.EntreeApparitionBonbon;
import com.example.mysubmod.sousmodes.sousmode2.iles.TypeIle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de bonbons Sous-mode 2 avec support pour les types de ressources doubles (A et B)
 * Utilise des tags NBT pour différencier les types de bonbons
 */
public class GestionnaireBonbonsSousMode2 {
    private static GestionnaireBonbonsSousMode2 instance;
    private Timer minuterieApparitionBonbons;
    private final Map<ItemEntity, Long> tempsApparitionBonbons = new ConcurrentHashMap<>();
    private final Set<net.minecraft.world.level.ChunkPos> chunksForceCharges = new HashSet<>();
    private MinecraftServer serveurJeu;

    // Clé de tag NBT pour le type de ressource
    private static final String NBT_TYPE_RESSOURCE = "TypeRessourceSousMode2";

    private GestionnaireBonbonsSousMode2() {}

    public static GestionnaireBonbonsSousMode2 getInstance() {
        if (instance == null) {
            instance = new GestionnaireBonbonsSousMode2();
        }
        return instance;
    }

    public void demarrerApparitionBonbons(MinecraftServer serveur) {
        MonSubMod.JOURNALISEUR.info("Démarrage de l'apparition de bonbons pour Sous-mode 2");

        this.serveurJeu = serveur;

        // Forcer le chargement seulement si pas déjà fait (éviter les appels répétés)
        if (chunksForceCharges.isEmpty()) {
            forceChargerChunksIles(serveur);
        }

        List<EntreeApparitionBonbon> configApparition = GestionnaireSousMode2.getInstance().obtenirConfigApparitionBonbons();

        if (configApparition == null || configApparition.isEmpty()) {
            MonSubMod.JOURNALISEUR.error("Aucune configuration d'apparition de bonbons chargée pour Sous-mode 2!");
            return;
        }

        MonSubMod.JOURNALISEUR.info("Chargé {} entrées d'apparition de bonbons depuis la configuration Sous-mode 2", configApparition.size());
        planifierApparitionsBonbonsDepuisConfig(serveur, configApparition);
    }

    private void planifierApparitionsBonbonsDepuisConfig(MinecraftServer serveur, List<EntreeApparitionBonbon> configApparition) {
        minuterieApparitionBonbons = new Timer();

        for (EntreeApparitionBonbon entree : configApparition) {
            minuterieApparitionBonbons.schedule(new TimerTask() {
                @Override
                public void run() {
                    serveur.execute(() -> fairApparaitreBonbonsDepuisEntree(serveur, entree));
                }
            }, entree.obtenirTempsMs());
        }
    }

    /**
     * Arrêter seulement la minuterie d'apparition de bonbons (ne nettoie pas les bonbons ni ne décharge les chunks)
     */
    public void arreterMinuterie() {
        if (minuterieApparitionBonbons != null) {
            minuterieApparitionBonbons.cancel();
            minuterieApparitionBonbons = null;
            MonSubMod.JOURNALISEUR.info("Arrêt de la minuterie d'apparition de bonbons pour Sous-mode 2");
        }
    }

    /**
     * Nettoyage complet: arrête la minuterie et retire les bonbons
     * Note: Nous n'utilisons plus le chargement forcé permanent des chunks pour éviter les crashes
     */
    public void arreterApparitionBonbons() {
        arreterMinuterie();
        retirerTousLesBonbons();

        MonSubMod.JOURNALISEUR.info("Arrêt de l'apparition de bonbons pour Sous-mode 2");
    }

    private void fairApparaitreBonbonsDepuisEntree(MinecraftServer serveur, EntreeApparitionBonbon entree) {
        ServerLevel surMonde = serveur.getLevel(ServerLevel.OVERWORLD);
        if (surMonde == null) return;

        Set<UUID> joueursVivants = GestionnaireSousMode2.getInstance().obtenirJoueursVivants();
        if (joueursVivants.isEmpty()) return;

        BlockPos centreApparition = entree.obtenirPosition();
        int nombreBonbons = entree.obtenirNombreBonbons();
        TypeRessource typeRessource = entree.obtenirType();

        Set<BlockPos> positionsUtilisees = new HashSet<>();
        Random aleatoire = new Random();

        for (int i = 0; i < nombreBonbons; i++) {
            BlockPos posApparition = trouverPositionApparitionValidePresPoint(surMonde, centreApparition, 3, positionsUtilisees);
            if (posApparition != null) {
                positionsUtilisees.add(posApparition);

                double decalageX = 0.3 + aleatoire.nextDouble() * 0.4;
                double decalageZ = 0.3 + aleatoire.nextDouble() * 0.4;

                // Utiliser différents items selon le type de ressource
                ItemStack pileBonbon;
                if (typeRessource == TypeRessource.BONBON_BLEU) {
                    pileBonbon = new ItemStack(ItemsMod.BONBON_BLEU.get());
                } else {
                    pileBonbon = new ItemStack(ItemsMod.BONBON_ROUGE.get());
                }

                ItemEntity entiteBonbon = new ItemEntity(surMonde,
                    posApparition.getX() + decalageX,
                    posApparition.getY() - 0.9,
                    posApparition.getZ() + decalageZ,
                    pileBonbon);

                entiteBonbon.setPickUpDelay(60); // 3 secondes
                entiteBonbon.setUnlimitedLifetime();

                surMonde.addFreshEntity(entiteBonbon);
                tempsApparitionBonbons.put(entiteBonbon, System.currentTimeMillis());

                // Journaliser l'apparition de bonbon avec le type
                if (GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees() != null) {
                    GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees().enregistrerApparitionBonbon(posApparition, typeRessource);
                }
            }
        }

        MonSubMod.JOURNALISEUR.info("Apparu {} bonbons (Type {}) au temps {}s", nombreBonbons, typeRessource.name(), entree.obtenirTempsSecondes());
    }

    private BlockPos trouverPositionApparitionValidePresPoint(ServerLevel niveau, BlockPos pointApparition, int rayon, Set<BlockPos> positionsUtilisees) {
        Random aleatoire = new Random();

        for (int tentatives = 0; tentatives < 100; tentatives++) {
            int x = pointApparition.getX() + aleatoire.nextInt(rayon * 2 + 1) - rayon;
            int z = pointApparition.getZ() + aleatoire.nextInt(rayon * 2 + 1) - rayon;

            for (int y = pointApparition.getY() + 5; y >= pointApparition.getY() - 5; y--) {
                BlockPos posVerif = new BlockPos(x, y, z);
                BlockPos posAuDessus = posVerif.above();

                if (positionsUtilisees.contains(posAuDessus)) {
                    continue;
                }

                if (!niveau.getBlockState(posVerif).isAir() &&
                    niveau.getBlockState(posAuDessus).isAir() &&
                    niveau.getBlockState(posAuDessus.above()).isAir() &&
                    estSurSurfaceIle(niveau, posVerif)) {
                    return posAuDessus;
                }
            }
        }

        return pointApparition;
    }

    private boolean estSurSurfaceIle(ServerLevel niveau, BlockPos pos) {
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
        ServerLevel surMonde = serveur.getLevel(ServerLevel.OVERWORLD);
        if (surMonde == null) return;

        int nombreRetires = 0;

        GestionnaireSousMode2 gestionnaire = GestionnaireSousMode2.getInstance();
        if (gestionnaire.obtenirCentreIlePetite() != null) {
            forceChargerChunk(surMonde, gestionnaire.obtenirCentreIlePetite());
            forceChargerChunk(surMonde, gestionnaire.obtenirCentreIleMoyenne());
            forceChargerChunk(surMonde, gestionnaire.obtenirCentreIleGrande());
            forceChargerChunk(surMonde, gestionnaire.obtenirCentreIleTresGrande());
        }

        for (net.minecraft.world.entity.Entity entite : surMonde.getAllEntities()) {
            if (entite instanceof ItemEntity entiteItem) {
                // Retirer les bonbons Sous-mode 2 (bleus et rouges)
                if (entiteItem.getItem().getItem() == ItemsMod.BONBON_BLEU.get() ||
                    entiteItem.getItem().getItem() == ItemsMod.BONBON_ROUGE.get()) {
                    entiteItem.discard();
                    nombreRetires++;
                }
            }
        }

        tempsApparitionBonbons.clear();
        MonSubMod.JOURNALISEUR.info("Retiré {} objets de bonbons Sous-mode 2 du monde", nombreRetires);
    }

    private void forceChargerChunk(ServerLevel niveau, BlockPos pos) {
        if (pos != null) {
            niveau.getChunkAt(pos);
        }
    }

    /**
     * Obtenir le compte de bonbons disponibles par île ET par type de ressource
     * Retourne une carte imbriquée: TypeIle -> TypeRessource -> Compte
     */
    public Map<TypeIle, Map<TypeRessource, Integer>> obtenirBonbonsDisponiblesParIle(MinecraftServer serveur) {
        Map<TypeIle, Map<TypeRessource, Integer>> comptes = new HashMap<>();

        // Initialiser la structure
        for (TypeIle ile : TypeIle.values()) {
            Map<TypeRessource, Integer> comptesTypes = new HashMap<>();
            comptesTypes.put(TypeRessource.BONBON_BLEU, 0);
            comptesTypes.put(TypeRessource.BONBON_ROUGE, 0);
            comptes.put(ile, comptesTypes);
        }

        ServerLevel surMonde = serveur.getLevel(ServerLevel.OVERWORLD);
        if (surMonde == null) return comptes;

        GestionnaireSousMode2 gestionnaire = GestionnaireSousMode2.getInstance();
        int totalCompte = 0;
        int pasSurIle = 0;

        for (net.minecraft.world.entity.Entity entite : surMonde.getAllEntities()) {
            if (entite instanceof ItemEntity entiteItem) {
                TypeRessource typeRessource = obtenirTypeRessourceDepuisBonbon(entiteItem.getItem());

                // Compter seulement les bonbons Sous-mode 2 (bleus et rouges)
                if (typeRessource != null && entiteItem.isAlive() && !entiteItem.isRemoved()) {
                    int quantiteBonbon = entiteItem.getItem().getCount();

                    TypeIle ile = determinerIleDepuisPosition(entiteItem.position(), gestionnaire);
                    if (ile != null) {
                        totalCompte += quantiteBonbon;
                        Map<TypeRessource, Integer> comptesTypes = comptes.get(ile);
                        comptesTypes.put(typeRessource, comptesTypes.get(typeRessource) + quantiteBonbon);
                    } else {
                        pasSurIle += quantiteBonbon;
                        MonSubMod.JOURNALISEUR.warn("Bonbon Sous-mode 2 non compté - pas sur une île à {} (ID entité: {})",
                            entiteItem.position(), entite.getId());
                    }
                }
            }
        }

        MonSubMod.JOURNALISEUR.info("Compte de bonbons Sous-mode 2: {} total trouvés ({} pas sur les îles)", totalCompte, pasSurIle);

        return comptes;
    }

    /**
     * Extraire le TypeRessource depuis le ItemStack de bonbon basé sur le type d'item
     */
    public static TypeRessource obtenirTypeRessourceDepuisBonbon(ItemStack pileBonbon) {
        if (pileBonbon.getItem() == ItemsMod.BONBON_BLEU.get()) {
            return TypeRessource.BONBON_BLEU;
        } else if (pileBonbon.getItem() == ItemsMod.BONBON_ROUGE.get()) {
            return TypeRessource.BONBON_ROUGE;
        }
        return null;
    }

    private void forceChargerChunksIles(MinecraftServer serveur) {
        ServerLevel surMonde = serveur.getLevel(ServerLevel.OVERWORLD);
        if (surMonde == null) return;

        GestionnaireSousMode2 gestionnaire = GestionnaireSousMode2.getInstance();

        forceChargerZoneIle(surMonde, gestionnaire.obtenirCentreIlePetite(), 2);
        forceChargerZoneIle(surMonde, gestionnaire.obtenirCentreIleMoyenne(), 3);
        forceChargerZoneIle(surMonde, gestionnaire.obtenirCentreIleGrande(), 4);
        forceChargerZoneIle(surMonde, gestionnaire.obtenirCentreIleTresGrande(), 5);

        MonSubMod.JOURNALISEUR.info("Chargé de force {} chunks pour toutes les îles Sous-mode 2", chunksForceCharges.size());
    }

    private void forceChargerZoneIle(ServerLevel niveau, BlockPos centre, int rayonChunk) {
        if (centre == null) return;

        net.minecraft.world.level.ChunkPos chunkCentre = new net.minecraft.world.level.ChunkPos(centre);

        for (int x = -rayonChunk; x <= rayonChunk; x++) {
            for (int z = -rayonChunk; z <= rayonChunk; z++) {
                net.minecraft.world.level.ChunkPos posChunk = new net.minecraft.world.level.ChunkPos(
                    chunkCentre.x + x,
                    chunkCentre.z + z
                );

                // Forcer le chargement seulement si pas déjà suivi
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

    public void annulerChargementForceChunks() {
        if (serveurJeu == null || chunksForceCharges.isEmpty()) return;

        ServerLevel surMonde = serveurJeu.getLevel(ServerLevel.OVERWORLD);
        if (surMonde == null) {
            chunksForceCharges.clear();
            serveurJeu = null;
            return;
        }

        try {
            for (net.minecraft.world.level.ChunkPos posChunk : chunksForceCharges) {
                try {
                    surMonde.setChunkForced(posChunk.x, posChunk.z, false);
                } catch (Exception e) {
                    MonSubMod.JOURNALISEUR.warn("Échec du déchargement forcé du chunk à {}, {}: {}", posChunk.x, posChunk.z, e.getMessage());
                }
            }
        } finally {
            chunksForceCharges.clear();
            serveurJeu = null;
        }
    }

    private TypeIle determinerIleDepuisPosition(net.minecraft.world.phys.Vec3 pos, GestionnaireSousMode2 gestionnaire) {
        BlockPos centrePetite = gestionnaire.obtenirCentreIlePetite();
        BlockPos centreMoyenne = gestionnaire.obtenirCentreIleMoyenne();
        BlockPos centreGrande = gestionnaire.obtenirCentreIleGrande();
        BlockPos centreTresGrande = gestionnaire.obtenirCentreIleTresGrande();

        if (centrePetite == null || centreMoyenne == null || centreGrande == null || centreTresGrande == null) {
            return null;
        }

        if (estDansLimitesIle(pos, centrePetite, TypeIle.PETITE.obtenirRayon())) {
            return TypeIle.PETITE;
        }

        if (estDansLimitesIle(pos, centreMoyenne, TypeIle.MOYENNE.obtenirRayon())) {
            return TypeIle.MOYENNE;
        }

        if (estDansLimitesIle(pos, centreGrande, TypeIle.GRANDE.obtenirRayon())) {
            return TypeIle.GRANDE;
        }

        if (estDansLimitesIle(pos, centreTresGrande, TypeIle.TRES_GRANDE.obtenirRayon())) {
            return TypeIle.TRES_GRANDE;
        }

        return null;
    }

    private boolean estDansLimitesIle(net.minecraft.world.phys.Vec3 pos, BlockPos centre, int rayon) {
        double distX = Math.abs(pos.x - centre.getX());
        double distZ = Math.abs(pos.z - centre.getZ());

        return distX <= rayon && distZ <= rayon;
    }

    public void quandBonbonRamasse(ItemEntity bonbon) {
        tempsApparitionBonbons.remove(bonbon);
    }
}

package com.example.mysubmod;

import com.example.mysubmod.objets.ItemsMod;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("mysubmod")
public class MonSubMod {
    public static final String ID_MOD = "mysubmod";
    /** @deprecated Utilisez ID_MOD à la place */
    @Deprecated
    public static final String MOD_ID = ID_MOD;
    public static final Logger JOURNALISEUR = LogManager.getLogger();

    public static ResourceLocation id(String chemin) {
        return ResourceLocation.fromNamespaceAndPath(ID_MOD, chemin);
    }

    public MonSubMod() {
        IEventBus busEvenementsMod = FMLJavaModLoadingContext.get().getModEventBus();

        ItemsMod.register(busEvenementsMod);
        busEvenementsMod.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GestionnaireReseau.init();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        JOURNALISEUR.info("Démarrage des fonctionnalités côté serveur de MonSubMod");

        // Forcer la désactivation de tous les sous-modes d'abord pour nettoyer tout état résiduel
        GestionnaireSousModes.getInstance().forcerDesactivationTousSousModes(event.getServer());

        // Ensuite démarrer la salle d'attente
        GestionnaireSousModes.getInstance().demarrerSalleAttente();

        // Activer la salle d'attente et nettoyer les hologrammes après que le serveur soit complètement chargé
        event.getServer().execute(() -> {
            net.minecraft.server.level.ServerLevel overworld = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (overworld != null) {
                // Nettoyer AVANT d'activer la salle d'attente
                nettoyerHologrammesOrphelins(overworld);
            }
            com.example.mysubmod.sousmodes.salleattente.GestionnaireSalleAttente.getInstance().activate(event.getServer());
        });
    }

    private void nettoyerHologrammesOrphelins(net.minecraft.server.level.ServerLevel niveau) {
        // La place centrale est à (0, 100, 0) où les hologrammes du Sous-mode 1 apparaissent
        net.minecraft.core.BlockPos placeCentrale = new net.minecraft.core.BlockPos(0, 100, 0);
        net.minecraft.world.level.ChunkPos positionChunk = new net.minecraft.world.level.ChunkPos(placeCentrale);

        JOURNALISEUR.info("Chargement forcé du chunk à {} pour le nettoyage des hologrammes...", positionChunk);

        // Forcer le chargement du chunk avec un ticket de chargement pour s'assurer que les entités sont chargées
        niveau.setChunkForced(positionChunk.x, positionChunk.z, true);

        // Attendre un peu pour que les entités se chargent réellement
        try {
            Thread.sleep(500); // Délai de 500ms pour laisser les entités se charger
        } catch (InterruptedException e) {
            JOURNALISEUR.error("Interruption pendant l'attente du chargement du chunk", e);
        }

        int supprimes = 0;
        JOURNALISEUR.info("Recherche d'hologrammes orphelins à la place centrale (0, 100, 0)...");

        // Créer une boîte englobante autour de la zone de la place centrale
        net.minecraft.world.phys.AABB boiteRecherche = new net.minecraft.world.phys.AABB(
            placeCentrale.getX() - 15, 100, placeCentrale.getZ() - 15,
            placeCentrale.getX() + 15, 105, placeCentrale.getZ() + 15
        );

        // Obtenir tous les supports d'armure dans cette zone
        java.util.List<net.minecraft.world.entity.decoration.ArmorStand> supportsArmure = niveau.getEntitiesOfClass(
            net.minecraft.world.entity.decoration.ArmorStand.class,
            boiteRecherche,
            supportArmure -> supportArmure.isInvisible() && supportArmure.isCustomNameVisible()
        );

        JOURNALISEUR.info("Trouvé {} supports d'armure dans la zone de recherche", supportsArmure.size());

        // Supprimer tous les hologrammes trouvés
        for (net.minecraft.world.entity.decoration.ArmorStand supportArmure : supportsArmure) {
            JOURNALISEUR.info("Hologramme orphelin trouvé à {} avec le nom : {}",
                supportArmure.blockPosition(),
                supportArmure.getCustomName() != null ? supportArmure.getCustomName().getString() : "pas de nom");
            supportArmure.discard();
            supprimes++;
        }

        // Retirer le chargement forcé du chunk
        niveau.setChunkForced(positionChunk.x, positionChunk.z, false);

        if (supprimes > 0) {
            JOURNALISEUR.info("Nettoyé {} entités hologrammes orphelines de la session précédente", supprimes);
        } else {
            JOURNALISEUR.info("Aucun hologramme orphelin trouvé à la place centrale");
        }
    }
}

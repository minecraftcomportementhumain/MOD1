package com.example.mysubmod.sousmodes.salleattente;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GestionnaireEvenementsSalleAttente {
    private static boolean hologrammesNettoyes = false;

    @SubscribeEvent
    public static void onPlayerAttack(LivingAttackEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SALLE_ATTENTE) {
            return;
        }

        if (event.getSource().getEntity() instanceof ServerPlayer attaquant) {
            // Ignorer les joueurs restreints (non authentifiés)
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(attaquant)) {
                event.setCanceled(true);
                return;
            }

            // Tous les joueurs authentifiés en salle d'attente ne peuvent pas attaquer
            event.setCanceled(true);
            attaquant.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en salle d'attente"));
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SALLE_ATTENTE) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les joueurs restreints (non authentifiés)
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }

            // Tous les joueurs authentifiés en salle d'attente ne peuvent pas interagir avec les blocs
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en salle d'attente"));
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SALLE_ATTENTE) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les joueurs restreints (non authentifiés)
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }

            // Tous les joueurs authentifiés en salle d'attente ne peuvent pas utiliser d'objets
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser d'objets en salle d'attente"));
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SALLE_ATTENTE) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer joueur) {
            // Ignorer les joueurs restreints (non authentifiés)
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                return;
            }

            // Tous les joueurs authentifiés en salle d'attente ne peuvent pas casser de blocs
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en salle d'attente"));
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SALLE_ATTENTE) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les joueurs restreints (non authentifiés)
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                return;
            }

            // Tous les joueurs authentifiés en salle d'attente ne peuvent pas placer de blocs
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en salle d'attente"));
        }
    }

    @SubscribeEvent
    public static void onPlayerCrafting(PlayerEvent.ItemCraftedEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SALLE_ATTENTE) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les joueurs restreints (non authentifiés)
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                return;
            }

            // Tous les joueurs authentifiés en salle d'attente ne peuvent pas fabriquer
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en salle d'attente"));
        }
    }

    @SubscribeEvent
    public static void onPlayerDropItem(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SALLE_ATTENTE) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer joueur) {
            // Ignorer les joueurs restreints (non authentifiés)
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                return;
            }

            // Tous les joueurs authentifiés en salle d'attente ne peuvent pas jeter d'objets
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en salle d'attente"));
        }
    }

    @SubscribeEvent
    public static void onPlayerPickupItem(PlayerEvent.ItemPickupEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SALLE_ATTENTE) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les joueurs restreints (non authentifiés)
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                return;
            }

            // Tous les joueurs authentifiés en salle d'attente ne peuvent pas ramasser d'objets
            // Retirer les objets de l'inventaire immédiatement car le ramassage ne peut pas être annulé
            joueur.getInventory().removeItem(event.getStack());
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() == SousMode.SALLE_ATTENTE) {
            if (event.getEntity() instanceof ServerPlayer joueur) {
                MonSubMod.JOURNALISEUR.info("DEBUG: GestionnaireEvenementsSalleAttente.onPlayerJoin appelé pour {}", joueur.getName().getString());

                // Ignorer les joueurs restreints (non authentifiés et candidats en file d'attente)
                if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                    MonSubMod.JOURNALISEUR.info("DEBUG: Le joueur {} est restreint, ignorer la téléportation vers la SalleAttente", joueur.getName().getString());
                    return;
                }

                MonSubMod.JOURNALISEUR.info("DEBUG: Le joueur {} n'est PAS restreint, téléportation vers la SalleAttente", joueur.getName().getString());

                // Nettoyer les hologrammes orphelins lors de la première connexion d'un joueur (quand les entités sont réellement chargées)
                if (!hologrammesNettoyes) {
                    joueur.server.execute(() -> {
                        nettoyerHologrammesOrphelins(joueur.serverLevel());
                        hologrammesNettoyes = true;
                    });
                }

                GestionnaireSalleAttente.getInstance().teleporterJoueurVersSalleAttente(joueur);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer joueur) {
            GestionnaireSalleAttente.getInstance().restaurerInventaireJoueur(joueur);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Réinitialiser le drapeau de nettoyage pour le prochain démarrage du serveur
        hologrammesNettoyes = false;

        if (GestionnaireSousModes.getInstance().obtenirModeActuel() == SousMode.SALLE_ATTENTE) {
            GestionnaireSalleAttente.getInstance().deactivate(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Actif uniquement en mode salle d'attente
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SALLE_ATTENTE) {
            return;
        }

        // Bloquer les monstres hostiles près de la plateforme de la salle d'attente
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster) {
            BlockPos positionApparition = event.getEntity().blockPosition();

            // Vérifier si l'apparition est près de la plateforme de la salle d'attente
            if (estProcheDelaPlateformeSalleAttente(positionApparition)) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean estProcheDelaPlateformeSalleAttente(BlockPos pos) {
        GestionnaireSalleAttente gestionnaire = GestionnaireSalleAttente.getInstance();
        BlockPos centrePlateforme = gestionnaire.obtenirCentrePlateforme();
        int taillePlateforme = gestionnaire.obtenirTaillePlateforme();

        // Vérifier si dans un rayon plus large autour de la plateforme pour la sécurité
        // La plateforme est de 20x20, donc nous ajoutons un tampon supplémentaire (30 blocs de rayon au total)
        int rayonSecurite = taillePlateforme + 10;

        return estDansLeRayon(pos, centrePlateforme, rayonSecurite);
    }

    private static boolean estDansLeRayon(BlockPos pos1, BlockPos pos2, int rayon) {
        if (pos2 == null) return false;
        double distance = Math.sqrt(Math.pow(pos1.getX() - pos2.getX(), 2) +
                                  Math.pow(pos1.getZ() - pos2.getZ(), 2));
        return distance <= rayon;
    }

    public static void verifierLimitesJoueur(ServerPlayer joueur) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SALLE_ATTENTE) {
            return;
        }

        if (!GestionnaireSalleAttente.getInstance().estJoueurDansSalleAttente(joueur)) {
            return;
        }

        BlockPos centre = GestionnaireSalleAttente.getInstance().obtenirCentrePlateforme();
        int taillePlateforme = GestionnaireSalleAttente.getInstance().obtenirTaillePlateforme();
        Vec3 positionJoueur = joueur.position();

        double distanceX = Math.abs(positionJoueur.x - centre.getX());
        double distanceZ = Math.abs(positionJoueur.z - centre.getZ());

        if (distanceX > taillePlateforme/2.0 - 1 || distanceZ > taillePlateforme/2.0 - 1) {
            Vec3 positionTeleportation = new Vec3(centre.getX() + 0.5, centre.getY() + 1, centre.getZ() + 0.5);
            joueur.teleportTo(positionTeleportation.x, positionTeleportation.y, positionTeleportation.z);
            joueur.sendSystemMessage(Component.literal("§eVous ne pouvez pas quitter la plateforme de la salle d'attente"));
        }
    }

    private static void nettoyerHologrammesOrphelins(net.minecraft.server.level.ServerLevel niveau) {
        int nombreSupprime = 0;

        // Le carré central est à (0, 100, 0) où les hologrammes de Sous-mode 1 apparaissent
        BlockPos carreCentral = new BlockPos(0, 100, 0);

        MonSubMod.JOURNALISEUR.info("Nettoyage des hologrammes orphelins au carré central (premier joueur connecté)...");

        // Créer une boîte englobante autour de la zone du carré central
        net.minecraft.world.phys.AABB boiteRecherche = new net.minecraft.world.phys.AABB(
            carreCentral.getX() - 15, 100, carreCentral.getZ() - 15,
            carreCentral.getX() + 15, 105, carreCentral.getZ() + 15
        );

        // Obtenir tous les présentoirs d'armures dans cette zone
        java.util.List<net.minecraft.world.entity.decoration.ArmorStand> presentoirs = niveau.getEntitiesOfClass(
            net.minecraft.world.entity.decoration.ArmorStand.class,
            boiteRecherche,
            presentoir -> presentoir.isInvisible() && presentoir.isCustomNameVisible()
        );

        MonSubMod.JOURNALISEUR.info("Trouvé {} présentoirs d'armures dans la zone de recherche", presentoirs.size());

        // Supprimer tous les hologrammes trouvés
        for (net.minecraft.world.entity.decoration.ArmorStand presentoir : presentoirs) {
            MonSubMod.JOURNALISEUR.info("Suppression de l'hologramme orphelin à {} avec le nom: {}",
                presentoir.blockPosition(),
                presentoir.getCustomName() != null ? presentoir.getCustomName().getString() : "pas de nom");
            presentoir.discard();
            nombreSupprime++;
        }

        if (nombreSupprime > 0) {
            MonSubMod.JOURNALISEUR.info("Nettoyé {} entités d'hologrammes orphelins", nombreSupprime);
        } else {
            MonSubMod.JOURNALISEUR.info("Aucun hologramme orphelin trouvé");
        }
    }
}

package com.example.mysubmod.sousmodes.sousmode1;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.objets.ItemsMod;
import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.client.telemetry.TelemetryProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;

@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GestionnaireEvenementsSousMode1 {
    private static int ticksJournalisationPosition = 0;
    private static final int INTERVALLE_JOURNALISATION_POSITION = 100; // Journaliser toutes les 5 secondes (100 ticks)
    private static int ticksMiseAJourCompteurBonbons = 0;
    private static final int INTERVALLE_MISE_A_JOUR_COMPTEUR_BONBONS = 40; // Mettre à jour toutes les 2 secondes (40 ticks)

    @SubscribeEvent
    public static void onPlayerAttack(LivingAttackEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_1) {
            return;
        }

        if (event.getSource().getEntity() instanceof ServerPlayer attaquant) {
            // Ignorer les comptes candidats temporaires de la file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(attaquant)) {
                event.setCanceled(true);
                return;
            }

            // En Sous-mode 1, TOUS les joueurs non-admin ne peuvent pas attaquer
            if (!GestionnaireSousModes.getInstance().estAdmin(attaquant)) {
                event.setCanceled(true);
                if (GestionnaireSousMode1.getInstance().estJoueurVivant(attaquant.getUUID())) {
                    attaquant.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en sous-mode 1"));
                } else if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(attaquant.getUUID())) {
                    attaquant.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en tant que spectateur"));
                } else if (GestionnaireSousMode1.getInstance().estEnPhaseSelection(attaquant.getUUID())) {
                    attaquant.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en sous-mode 1"));
                } else {
                    attaquant.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en sous-mode 1"));
                }
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_1) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de la file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }

            // Les admins sur la plateforme spectateur ont les mêmes restrictions que les spectateurs
            // Ils ne peuvent interagir qu'avec le panneau pour passer en mode spectateur
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                    // Admin sur plateforme spectateur - mêmes restrictions que les spectateurs
                    if (event.getLevel().getBlockEntity(event.getPos()) instanceof SignBlockEntity) {
                        joueur.setGameMode(GameType.SPECTATOR);
                    } else {
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.FAIL);
                        joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs sur la plateforme spectateur"));
                    }
                }
                return;
            }

            // Autoriser l'utilisation de bonbons même lorsqu'on cible des blocs
            ItemStack objetTenu = joueur.getItemInHand(event.getHand());
            if (objetTenu.is(ItemsMod.BONBON.get())) {
                return; // Autoriser l'utilisation de bonbons
            }

            if (GestionnaireSousMode1.getInstance().estJoueurVivant(joueur.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en sous-mode 1"));
            } else if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                if (event.getLevel().getBlockEntity(event.getPos()) instanceof SignBlockEntity) {
                        joueur.setGameMode(GameType.SPECTATOR);
                }else{
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en tant que spectateur"));
                }
            } else if (GestionnaireSousMode1.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en sous-mode 1"));
            } else {
                // Les joueurs non suivis ne peuvent pas interagir
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en sous-mode 1"));
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_1) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de la file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                return;
            }

            net.minecraft.world.level.block.Block bloc = event.getState().getBlock();

            // Journaliser TOUTES les tentatives de casser des blocs
            MonSubMod.JOURNALISEUR.info("Tentative de casser un bloc par {}: {} à {} - estAdmin: {}, estVivant: {}, estSpectateur: {}, estEnPhaseDeSelection: {}",
                joueur.getName().getString(),
                bloc.getClass().getSimpleName(),
                event.getPos(),
                GestionnaireSousModes.getInstance().estAdmin(joueur),
                GestionnaireSousMode1.getInstance().estJoueurVivant(joueur.getUUID()),
                GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID()),
                GestionnaireSousMode1.getInstance().estEnPhaseSelection(joueur.getUUID()));

            // Les admins sur la plateforme spectateur ont les mêmes restrictions que les spectateurs
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                    event.setCanceled(true);
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs sur la plateforme spectateur"));
                }
                return;
            }

            // En Sous-mode 1, TOUS les joueurs non-admin ne peuvent pas casser de blocs
            event.setCanceled(true);
            if (GestionnaireSousMode1.getInstance().estJoueurVivant(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en sous-mode 1"));
            } else if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en tant que spectateur"));
            } else if (GestionnaireSousMode1.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en sous-mode 1"));
            } else {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en sous-mode 1"));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_1) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de la file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                return;
            }

            // Les admins sur la plateforme spectateur ont les mêmes restrictions que les spectateurs
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                    event.setCanceled(true);
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs sur la plateforme spectateur"));
                }
                return;
            }

            // En Sous-mode 1, TOUS les joueurs non-admin ne peuvent pas placer de blocs
            event.setCanceled(true);
            if (GestionnaireSousMode1.getInstance().estJoueurVivant(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en sous-mode 1"));
            } else if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en tant que spectateur"));
            } else if (GestionnaireSousMode1.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en sous-mode 1"));
            } else {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en sous-mode 1"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerCrafting(PlayerEvent.ItemCraftedEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_1) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de la file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                return;
            }

            // En Sous-mode 1, TOUS les joueurs non-admin ne peuvent pas fabriquer d'objets
            if (!GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                event.setCanceled(true);
                if (GestionnaireSousMode1.getInstance().estJoueurVivant(joueur.getUUID())) {
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en sous-mode 1"));
                } else if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en tant que spectateur"));
                } else if (GestionnaireSousMode1.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en sous-mode 1"));
                } else {
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en sous-mode 1"));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityItemPickup(EntityItemPickupEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_1) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de la file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                return;
            }

            // Les admins peuvent ramasser n'importe quoi
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            ItemStack pile = event.getItem().getItem();

            // Autoriser uniquement le ramassage de bonbons pour les joueurs vivants
            if (GestionnaireSousMode1.getInstance().estJoueurVivant(joueur.getUUID())) {
                if (pile.is(ItemsMod.BONBON.get())) {
                    // Incrémenter le compteur de bonbons pour le joueur
                    GestionnaireSousMode1.getInstance().incrementerCompteurBonbons(joueur.getUUID(), pile.getCount());

                    // Journaliser le ramassage de bonbons
                    if (GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees() != null) {
                        BlockPos pos = new BlockPos((int)joueur.getX(), (int)joueur.getY(), (int)joueur.getZ());
                        GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees().enregistrerCollecteBonbon(joueur, pos);
                    }

                    // Notifier le gestionnaire de bonbons
                    ItemEntity entiteObjet = event.getItem();
                    if (entiteObjet != null) {
                        GestionnaireBonbonsSousMode1.obtenirInstance().surRamassageBonbon(entiteObjet);
                    }
                    // Autoriser le ramassage de bonbons - ne pas annuler l'événement
                } else {
                    // Annuler le ramassage d'objets non-bonbons
                    event.setCanceled(true);
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez ramasser que des bonbons en sous-mode 1"));
                }
            } else if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                // Les spectateurs ne peuvent ramasser aucun objet
                event.setCanceled(true);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas ramasser d'objets en tant que spectateur"));
            } else if (GestionnaireSousMode1.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                // Les joueurs en phase de sélection ne peuvent ramasser aucun objet
                event.setCanceled(true);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas ramasser d'objets en sous-mode 1"));
            } else {
                // Les joueurs non suivis ne peuvent ramasser aucun objet
                event.setCanceled(true);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas ramasser d'objets en sous-mode 1"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer joueur) {
            MonSubMod.JOURNALISEUR.info("DEBUG: GestionnaireEvenementsSousMode1.onPlayerJoin appelé pour {}", joueur.getName().getString());

            // Ignorer les comptes candidats temporaires de la file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                MonSubMod.JOURNALISEUR.info("DEBUG: Le joueur {} est restreint, ignorer le traitement Sous-mode 1", joueur.getName().getString());
                return;
            }

            MonSubMod.JOURNALISEUR.info("DEBUG: Le joueur {} n'est PAS restreint, continuer avec le traitement Sous-mode 1", joueur.getName().getString());

            if (GestionnaireSousModes.getInstance().obtenirModeActuel() == SousMode.SOUS_MODE_1) {
                GestionnaireSousMode1 gestionnaire = GestionnaireSousMode1.getInstance();

                // Vérifier si le joueur a été déconnecté pendant le jeu
                if (gestionnaire.etaitJoueurDeconnecte(joueur.getName().getString())) {
                    // Joueur se reconnectant - restaurer son état
                    gestionnaire.gererReconnexionJoueur(joueur);
                } else {
                    // Nouveaux joueurs rejoignant - vérifier si nous sommes dans la phase de sélection de fichier
                    if (gestionnaire.estPhaseSelectionFichier()) {
                        // Pendant la phase de sélection de fichier, traiter les nouveaux joueurs non-admin comme participants
                        if (!GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                            gestionnaire.ajouterJoueurEnPhaseSelection(joueur);
                            joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eVous rejoignez le jeu. En attente de la sélection de fichier par l'admin..."));
                        } else {
                            // Les admins vont en spectateur
                            gestionnaire.teleporterVersSpectateur(joueur);
                        }
                    } else {
                        // Après la sélection de fichier, les nouveaux joueurs vont en spectateur
                        gestionnaire.teleporterVersSpectateur(joueur);
                    }
                }
            } else {
                // Joueur rejoignant quand Sous-mode 1 n'est PAS actif - effacer leur HUD et leur chronomètre
                // Envoyer des compteurs de bonbons vides pour désactiver le HUD de bonbons
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMiseAJourCompteurBonbons(new java.util.HashMap<>())
                );

                // Envoyer un chronomètre à -1 pour désactiver le chronomètre de jeu
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMinuterieJeu(-1)
                );

                MonSubMod.JOURNALISEUR.info("HUD Sous-mode 1 effacé pour le joueur se reconnectant: {}", joueur.getName().getString());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer joueur) {
            MonSubMod.JOURNALISEUR.info("DEBUG: GestionnaireEvenementsSousMode1.onPlayerLogout appelé pour {}",
                joueur.getName().getString());

            // Ignorer les comptes candidats temporaires de la file d'attente (mais ne pas retourner - ils sont déjà filtrés dans handlePlayerDisconnection)
            if (GestionnaireSousModes.getInstance().obtenirModeActuel() == SousMode.SOUS_MODE_1) {
                GestionnaireSousMode1 gestionnaire = GestionnaireSousMode1.getInstance();

                boolean estVivant = gestionnaire.estJoueurVivant(joueur.getUUID());
                boolean enPhaseDeSelection = gestionnaire.estEnPhaseSelection(joueur.getUUID());
                MonSubMod.JOURNALISEUR.info("DEBUG: Le joueur {} estVivant: {}, enPhaseDeSelection: {}",
                    joueur.getName().getString(), estVivant, enPhaseDeSelection);

                // Suivre le temps de déconnexion si le joueur était vivant OU en phase de sélection
                if (estVivant || enPhaseDeSelection) {
                    gestionnaire.gererDeconnexionJoueur(joueur);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_1) {
            return;
        }

        // Désactiver le sprint pour tous les joueurs en Sous-mode 1 en mettant la vitesse de sprint égale à la vitesse de marche
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(event.getServer())) {
            // Définir le modificateur d'attribut de vitesse de déplacement pour empêcher le boost de vitesse de sprint
            net.minecraft.world.entity.ai.attributes.AttributeInstance vitesseDeplacement =
                joueur.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);

            if (vitesseDeplacement != null) {
                // Retirer tout modificateur de sprint existant
                java.util.UUID uuidModificateurSprint = java.util.UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
                vitesseDeplacement.removeModifier(uuidModificateurSprint);

                // Ajouter un modificateur qui annule la vitesse de sprint (le sprint ajoute normalement 30% de vitesse)
                // En ajoutant un modificateur de -0.03 lors du sprint, nous annulons le boost
                if (joueur.isSprinting()) {
                    net.minecraft.world.entity.ai.attributes.AttributeModifier modificateurPasDeSprint =
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            uuidModificateurSprint,
                            "No sprint boost",
                            -0.003, // Annuler le boost de sprint de 30% (vitesse de base est 0.1, donc 0.1 * 0.3 = 0.03)
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION
                        );

                    if (vitesseDeplacement.getModifier(uuidModificateurSprint) == null) {
                        vitesseDeplacement.addTransientModifier(modificateurPasDeSprint);
                    }
                }
            }
        }

        // Maintenir la lumière du jour permanente PENDANT TOUT LE SUBMODE (pas seulement pendant le jeu actif)
        net.minecraft.server.level.ServerLevel overworld = event.getServer().getLevel(net.minecraft.server.level.ServerLevel.OVERWORLD);
        if (overworld != null) {
            long tempsActuel = overworld.getDayTime() % 24000;
            if (tempsActuel > 12000) { // Si c'est la nuit (après 12000 ticks)
                overworld.setDayTime(6000); // Réinitialiser à midi
            }

            // Désactiver les orages et la pluie - garder le temps clair en permanence
            if (overworld.isRaining() || overworld.isThundering()) {
                overworld.setWeatherParameters(6000, 0, false, false);
            }
        }

        if (!GestionnaireSousMode1.getInstance().estPartieActive()) {
            return;
        }

        ticksJournalisationPosition++;
        if (ticksJournalisationPosition >= INTERVALLE_JOURNALISATION_POSITION) {
            ticksJournalisationPosition = 0;

            // Journaliser les positions de tous les joueurs vivants et vérifier les limites des spectateurs
            if (GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees() != null) {
                for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(event.getServer())) {
                    if (GestionnaireSousMode1.getInstance().estJoueurVivant(joueur.getUUID())) {
                        GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees().enregistrerPositionJoueur(joueur);
                    }
                }
            }

            // Vérifier les limites des spectateurs
            for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(event.getServer())) {
                if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                    verifierLimitesSpectateur(joueur);
                }
            }
        }

        // Mettre à jour le HUD du compteur de bonbons pour tous les joueurs
        ticksMiseAJourCompteurBonbons++;
        if (ticksMiseAJourCompteurBonbons >= INTERVALLE_MISE_A_JOUR_COMPTEUR_BONBONS) {
            ticksMiseAJourCompteurBonbons = 0;

            // Obtenir les compteurs de bonbons du gestionnaire
            java.util.Map<com.example.mysubmod.sousmodes.sousmode1.iles.TypeIle, Integer> compteursBonbons =
                GestionnaireBonbonsSousMode1.obtenirInstance().obtenirBonbonsDisponiblesParIle(event.getServer());

            // Envoyer aux joueurs authentifiés uniquement
            com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMiseAJourCompteurBonbons paquet =
                new com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMiseAJourCompteurBonbons(compteursBonbons);
            for (net.minecraft.server.level.ServerPlayer joueur : com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(event.getServer())) {
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur), paquet);
            }
        }

        // Note: Le nettoyage des pissenlits est fait une fois au début de la phase de sélection dans GestionnaireSousMode1
    }

    @SubscribeEvent
    public static void onPlayerInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_1) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de la file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }

            // Les admins peuvent interagir
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            // Les joueurs vivants peuvent utiliser des bonbons
            if (GestionnaireSousMode1.getInstance().estJoueurVivant(joueur.getUUID())) {
                ItemStack objetTenu = joueur.getItemInHand(event.getHand());
                if (objetTenu.is(ItemsMod.BONBON.get())) {
                    return; // Autoriser l'utilisation de bonbons pour les joueurs vivants
                }
                // Bloquer l'utilisation d'autres objets pour les joueurs vivants
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez utiliser que les bonbons en sous-mode 1"));
                return;
            }

            // Les spectateurs, les joueurs en phase de sélection et les joueurs non suivis ne peuvent pas utiliser d'objets
            if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser d'objets en tant que spectateur"));
            } else if (GestionnaireSousMode1.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser d'objets en sous-mode 1"));
            } else {
                // Les joueurs non suivis ne peuvent pas utiliser d'objets
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser d'objets en sous-mode 1"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDropItem(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_1) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de la file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                event.setCanceled(true);
                return;
            }

            // Les admins peuvent jeter des objets
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            ItemStack objetJete = event.getEntity().getItem();

            if (GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                event.setCanceled(true);
                // Retourner l'objet à l'inventaire
                if (!joueur.getInventory().add(objetJete.copy())) {
                    joueur.drop(objetJete.copy(), false);
                }
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en tant que spectateur"));
            } else if (GestionnaireSousMode1.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                // Les joueurs en phase de sélection ne peuvent pas jeter d'objets
                event.setCanceled(true);
                // Retourner l'objet à l'inventaire
                if (!joueur.getInventory().add(objetJete.copy())) {
                    joueur.drop(objetJete.copy(), false);
                }
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en sous-mode 1"));
            } else if (GestionnaireSousMode1.getInstance().estJoueurVivant(joueur.getUUID())) {
                // Vérifier si le joueur essaie de jeter un bonbon - les joueurs vivants peuvent jeter des objets non-bonbons
                if (objetJete.is(ItemsMod.BONBON.get())) {
                    // Annuler l'événement de jet pour les bonbons
                    event.setCanceled(true);

                    // Retourner le bonbon à l'inventaire du joueur
                    if (!joueur.getInventory().add(objetJete.copy())) {
                        // Si l'inventaire est plein, le jeter quand même (ne devrait pas arriver dans ce cas)
                        joueur.drop(objetJete.copy(), false);
                    }

                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter les bonbons en sous-mode 1"));
                }
                // Les objets non-bonbons peuvent être jetés par les joueurs vivants - ne pas annuler
            } else {
                // Les joueurs non suivis ne peuvent pas jeter d'objets
                event.setCanceled(true);
                // Retourner l'objet à l'inventaire
                if (!joueur.getInventory().add(objetJete.copy())) {
                    joueur.drop(objetJete.copy(), false);
                }
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en sous-mode 1"));
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_1) {
            return;
        }

        // Empêcher TOUS les objets (sauf les bonbons de notre système d'apparition) d'apparaître sur les îles et les chemins
        if (event.getEntity() instanceof ItemEntity entiteObjet) {
            BlockPos positionObjet = entiteObjet.blockPosition();

            // Bloquer uniquement les objets sur les îles et les chemins
            if (GestionnaireSousMode1.getInstance().estProcheIleOuChemin(positionObjet)) {
                // Autoriser uniquement les bonbons de notre système d'apparition
                boolean estBonbon = entiteObjet.getItem().is(ItemsMod.BONBON.get());

                if (!estBonbon) {
                    event.setCanceled(true);
                }
            }
        }

        // Bloquer les mobs hostiles près des îles
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster) {
            BlockPos positionApparition = event.getEntity().blockPosition();

            // Vérifier si l'apparition est près d'une île
            if (estPresIle(positionApparition)) {
                event.setCanceled(true);
            }
        }
    }


    private static boolean estPresIle(BlockPos pos) {
        GestionnaireSousMode1 gestionnaire = GestionnaireSousMode1.getInstance();

        // Vérifier si dans la petite île (60x60, moitié = 30, +5 tampon = 35)
        if (estDansCarré(pos, gestionnaire.obtenirCentreIlePetite(), 35)) {
            return true;
        }

        // Vérifier si dans l'île moyenne (90x90, moitié = 45, +5 tampon = 50)
        if (estDansCarré(pos, gestionnaire.obtenirCentreIleMoyenne(), 50)) {
            return true;
        }

        // Vérifier si dans la grande île (120x120, moitié = 60, +5 tampon = 65)
        if (estDansCarré(pos, gestionnaire.obtenirCentreIleGrande(), 65)) {
            return true;
        }

        // Vérifier si dans l'île extra-grande (150x150, moitié = 75, +5 tampon = 80)
        if (estDansCarré(pos, gestionnaire.obtenirCentreIleTresGrande(), 80)) {
            return true;
        }

        // Vérifier si dans le carré central (20x20, moitié = 10, +5 tampon = 15)
        if (estDansCarré(pos, gestionnaire.obtenirPlaceCentrale(), 15)) {
            return true;
        }

        // Vérifier si dans la plateforme spectateur (30x30, moitié = 15, +5 tampon = 20)
        BlockPos centreSpectateur = new BlockPos(0, 150, 0);
        if (estDansCarré(pos, centreSpectateur, 20)) {
            return true;
        }

        return false;
    }

    private static boolean estDansCarré(BlockPos pos, BlockPos centre, int demiTaille) {
        if (centre == null) return false;
        int dx = Math.abs(pos.getX() - centre.getX());
        int dz = Math.abs(pos.getZ() - centre.getZ());
        return dx <= demiTaille && dz <= demiTaille;
    }

    public static void verifierLimitesSpectateur(ServerPlayer joueur) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_1) {
            return;
        }

        if (!GestionnaireSousMode1.getInstance().estJoueurSpectateur(joueur.getUUID())) {
            return;
        }

        // Centre de la plateforme spectateur à (0, 150, 0) avec taille 21x21 (-10 à +10)
        BlockPos centreSpectateur = new BlockPos(0, 150, 0);
        int taillePlateforme = 21;
        Vec3 positionJoueur = joueur.position();

        double distanceX = Math.abs(positionJoueur.x - centreSpectateur.getX());
        double distanceZ = Math.abs(positionJoueur.z - centreSpectateur.getZ());

        // Vérifier si le joueur est en dehors des limites de la plateforme ou en dessous de la plateforme
        if ((distanceX > taillePlateforme/2.0 - 1 || distanceZ > taillePlateforme/2.0 - 1 || positionJoueur.y < centreSpectateur.getY() - 5)
                && joueur.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            Vec3 positionTeleportation = new Vec3(centreSpectateur.getX() + 0.5, centreSpectateur.getY() + 1, centreSpectateur.getZ() + 0.5);
            joueur.teleportTo(positionTeleportation.x, positionTeleportation.y, positionTeleportation.z);
            joueur.sendSystemMessage(Component.literal("§eVous ne pouvez pas quitter la plateforme spectateur"));
        }
    }
}

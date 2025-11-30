package com.example.mysubmod.sousmodes.sousmode2;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.objets.ItemsMod;
import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
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

@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GestionnaireEvenementsSousMode2 {
    private static int ticsJournalisationPosition = 0;
    private static final int INTERVALLE_JOURNALISATION_POSITION = 100; // Journaliser toutes les 5 secondes (100 tics)
    private static int ticsMiseAJourCompteBonbons = 0;
    private static final int INTERVALLE_MISE_A_JOUR_COMPTE_BONBONS = 40; // Mettre à jour toutes les 2 secondes (40 tics)

    // Suivre l'état de sprint pour éviter de modifier les attributs à chaque tic
    private static final java.util.Map<java.util.UUID, Boolean> etatSprintJoueur = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.UUID UUID_MODIFICATEUR_SPRINT = java.util.UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");

    @SubscribeEvent
    public static void onPlayerAttack(LivingAttackEvent evenement) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_2) {
            return;
        }

        if (evenement.getSource().getEntity() instanceof ServerPlayer attaquant) {
            // Ignorer les comptes candidats temporaires de file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(attaquant)) {
                evenement.setCanceled(true);
                return;
            }

            // Dans Sous-mode 2, TOUS les joueurs non-admin sont restreints d'attaquer
            if (!GestionnaireSousModes.getInstance().estAdmin(attaquant)) {
                evenement.setCanceled(true);
                if (GestionnaireSousMode2.getInstance().estJoueurVivant(attaquant.getUUID())) {
                    attaquant.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en sous-mode 2"));
                } else if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(attaquant.getUUID())) {
                    attaquant.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en tant que spectateur"));
                } else if (GestionnaireSousMode2.getInstance().estEnPhaseSelection(attaquant.getUUID())) {
                    attaquant.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en sous-mode 2"));
                } else {
                    attaquant.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en sous-mode 2"));
                }
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock evenement) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_2) {
            return;
        }

        if (evenement.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (evenement.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                evenement.setCanceled(true);
                evenement.setCancellationResult(InteractionResult.FAIL);
                return;
            }

            // Les admins sur la plateforme spectateur ont les mêmes restrictions que les spectateurs
            // Ils ne peuvent interagir qu'avec le panneau pour passer en mode spectateur
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                    // Admin sur plateforme spectateur - mêmes restrictions que les spectateurs
                    if (evenement.getLevel().getBlockEntity(evenement.getPos()) instanceof SignBlockEntity) {
                        joueur.setGameMode(GameType.SPECTATOR);
                    } else {
                        evenement.setCanceled(true);
                        evenement.setCancellationResult(InteractionResult.FAIL);
                        joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs sur la plateforme spectateur"));
                    }
                }
                return;
            }

            // Autoriser l'utilisation de bonbons même en ciblant des blocs (pour les joueurs vivants seulement)
            ItemStack objetTenu = joueur.getItemInHand(evenement.getHand());
            if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueur.getUUID())) {
                if (objetTenu.is(ItemsMod.BONBON_BLEU.get()) || objetTenu.is(ItemsMod.BONBON_ROUGE.get())) {
                    return; // Autoriser l'utilisation de bonbons pour les joueurs vivants
                }
            }

            if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueur.getUUID())) {
                evenement.setCanceled(true);
                evenement.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en sous-mode 2"));
            } else if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                if (evenement.getLevel().getBlockEntity(evenement.getPos()) instanceof SignBlockEntity) {
                    joueur.setGameMode(GameType.SPECTATOR);
                } else {
                    evenement.setCanceled(true);
                    evenement.setCancellationResult(InteractionResult.FAIL);
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en tant que spectateur"));
                }
            } else if (GestionnaireSousMode2.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                evenement.setCanceled(true);
                evenement.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en sous-mode 2"));
            } else {
                // Les joueurs non suivis ne peuvent pas interagir
                evenement.setCanceled(true);
                evenement.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en sous-mode 2"));
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent evenement) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_2) {
            return;
        }

        if (evenement.getPlayer() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                evenement.setCanceled(true);
                return;
            }

            net.minecraft.world.level.block.Block bloc = evenement.getState().getBlock();

            // Journaliser TOUTES les tentatives de casser des blocs
            MonSubMod.JOURNALISEUR.info("Tentative de casser un bloc par {}: {} à {} - estAdmin: {}, estVivant: {}, estSpectateur: {}, estEnPhaseSelection: {}",
                joueur.getName().getString(),
                bloc.getClass().getSimpleName(),
                evenement.getPos(),
                GestionnaireSousModes.getInstance().estAdmin(joueur),
                GestionnaireSousMode2.getInstance().estJoueurVivant(joueur.getUUID()),
                GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID()),
                GestionnaireSousMode2.getInstance().estEnPhaseSelection(joueur.getUUID()));

            // Les admins sur la plateforme spectateur ont les mêmes restrictions que les spectateurs
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                    evenement.setCanceled(true);
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs sur la plateforme spectateur"));
                }
                return;
            }

            // Dans le Sous-mode 2, TOUS les joueurs non-admin sont restreints de casser des blocs
            // Cela inclut: les joueurs vivants, les spectateurs et les joueurs en phase de sélection
            evenement.setCanceled(true);
            if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en sous-mode 2"));
            } else if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en tant que spectateur"));
            } else if (GestionnaireSousMode2.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en sous-mode 2"));
            } else {
                // Le joueur a rejoint pendant le Sous-mode 2 mais n'est pas suivi - le bloquer quand même
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs en sous-mode 2"));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent evenement) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_2) {
            return;
        }

        if (evenement.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                evenement.setCanceled(true);
                return;
            }

            // Les admins sur la plateforme spectateur ont les mêmes restrictions que les spectateurs
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                    evenement.setCanceled(true);
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs sur la plateforme spectateur"));
                }
                return;
            }

            // Dans le Sous-mode 2, TOUS les joueurs non-admin sont restreints de placer des blocs
            // Cela inclut: les joueurs vivants, les spectateurs et les joueurs en phase de sélection
            evenement.setCanceled(true);
            if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en sous-mode 2"));
            } else if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en tant que spectateur"));
            } else if (GestionnaireSousMode2.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en sous-mode 2"));
            } else {
                // Le joueur a rejoint pendant le Sous-mode 2 mais n'est pas suivi - le bloquer quand même
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs en sous-mode 2"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerCrafting(PlayerEvent.ItemCraftedEvent evenement) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_2) {
            return;
        }

        if (evenement.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                evenement.setCanceled(true);
                return;
            }

            // Dans Sous-mode 2, TOUS les joueurs non-admin sont restreints de fabriquer
            if (!GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                evenement.setCanceled(true);
                if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueur.getUUID())) {
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en sous-mode 2"));
                } else if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en tant que spectateur"));
                } else if (GestionnaireSousMode2.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en sous-mode 2"));
                } else {
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas fabriquer d'objets en sous-mode 2"));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityItemPickup(EntityItemPickupEvent evenement) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_2) {
            return;
        }

        if (evenement.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                evenement.setCanceled(true);
                return;
            }

            // Les admins peuvent ramasser n'importe quoi
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            ItemStack pile = evenement.getItem().getItem();

            // Autoriser seulement le ramassage de bonbons pour les joueurs vivants
            if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueur.getUUID())) {
                if (pile.is(ItemsMod.BONBON_BLEU.get()) || pile.is(ItemsMod.BONBON_ROUGE.get())) {
                    // Incrémenter le compte de bonbons pour le joueur
                    GestionnaireSousMode2.getInstance().incrementerCompteurBonbons(joueur.getUUID(), pile.getCount());

                    // Journaliser le ramassage de bonbon avec le type de ressource
                    if (GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees() != null) {
                        BlockPos pos = new BlockPos((int)joueur.getX(), (int)joueur.getY(), (int)joueur.getZ());
                        TypeRessource typeRessource = GestionnaireBonbonsSousMode2.obtenirTypeRessourceDepuisBonbon(pile);
                        if (typeRessource != null) {
                            GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees().enregistrerRamassageBonbon(joueur, pos, typeRessource);
                        }
                    }

                    // Notifier le gestionnaire de bonbons
                    ItemEntity entiteItem = evenement.getItem();
                    if (entiteItem != null) {
                        GestionnaireBonbonsSousMode2.getInstance().quandBonbonRamasse(entiteItem);
                    }
                    // Autoriser le ramassage de bonbons - ne pas annuler l'événement
                } else {
                    // Annuler le ramassage d'objets non-bonbons
                    evenement.setCanceled(true);
                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez ramasser que des bonbons en sous-mode 2"));
                }
            } else if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                // Les spectateurs ne peuvent ramasser aucun objet
                evenement.setCanceled(true);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas ramasser d'objets en tant que spectateur"));
            } else if (GestionnaireSousMode2.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                // Les joueurs en phase de sélection ne peuvent ramasser aucun objet
                evenement.setCanceled(true);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas ramasser d'objets en sous-mode 2"));
            } else {
                // Les joueurs non suivis ne peuvent ramasser aucun objet
                evenement.setCanceled(true);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas ramasser d'objets en sous-mode 2"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evenement) {
        if (evenement.getEntity() instanceof ServerPlayer joueur) {
            MonSubMod.JOURNALISEUR.info("DEBUG: GestionnaireEvenementsSousMode2.onPlayerJoin appelé pour {}", joueur.getName().getString());

            // Ignorer les comptes candidats temporaires de file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                MonSubMod.JOURNALISEUR.info("DEBUG: Joueur {} est restreint, ignorer le traitement Sous-mode 2", joueur.getName().getString());
                return;
            }

            MonSubMod.JOURNALISEUR.info("DEBUG: Joueur {} n'est PAS restreint, continuer avec le traitement Sous-mode 2", joueur.getName().getString());

            if (GestionnaireSousModes.getInstance().obtenirModeActuel() == SousMode.SOUS_MODE_2) {
                GestionnaireSousMode2 gestionnaire = GestionnaireSousMode2.getInstance();

                // Vérifier si le joueur était déconnecté pendant le jeu
                if (gestionnaire.etaitJoueurDeconnecte(joueur.getName().getString())) {
                    // Joueur se reconnectant - restaurer son état
                    gestionnaire.gererReconnexionJoueur(joueur);
                } else {
                    // Nouveaux joueurs rejoignant - vérifier si nous sommes en phase de sélection de fichier
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
                // Joueur rejoignant quand Sous-mode 2 n'est PAS actif - effacer leur HUD et minuterie
                // Envoyer des comptes de bonbons vides pour désactiver le HUD de bonbons
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMiseAJourCompteurBonbons(new java.util.HashMap<>())
                );

                // Envoyer une minuterie à -1 pour désactiver la minuterie de jeu
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMinuterieJeu(-1)
                );

                // Désactiver le HUD de minuterie de pénalité
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSynchronisationPenalite(false, joueur.getUUID())
                );

                MonSubMod.JOURNALISEUR.info("HUD Sous-mode 2 effacé pour le joueur se reconnectant: {}", joueur.getName().getString());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent evenement) {
        if (evenement.getEntity() instanceof ServerPlayer joueur) {
            MonSubMod.JOURNALISEUR.info("DEBUG: GestionnaireEvenementsSousMode2.onPlayerLogout appelé pour {}",
                joueur.getName().getString());

            // Nettoyer le suivi de l'état de sprint
            etatSprintJoueur.remove(joueur.getUUID());

            // Ignorer les comptes candidats temporaires de file d'attente (mais ne pas retourner - ils sont déjà filtrés dans handlePlayerDisconnection)
            if (GestionnaireSousModes.getInstance().obtenirModeActuel() == SousMode.SOUS_MODE_2) {
                GestionnaireSousMode2 gestionnaire = GestionnaireSousMode2.getInstance();

                boolean estVivant = gestionnaire.estJoueurVivant(joueur.getUUID());
                boolean enPhaseSelection = gestionnaire.estEnPhaseSelection(joueur.getUUID());
                MonSubMod.JOURNALISEUR.info("DEBUG: Joueur {} estVivant: {}, enPhaseSelection: {}",
                    joueur.getName().getString(), estVivant, enPhaseSelection);

                // Suivre le temps de déconnexion si le joueur était vivant OU en phase de sélection
                if (estVivant || enPhaseSelection) {
                    gestionnaire.gererDeconnexionJoueur(joueur);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evenement) {
        if (evenement.phase != TickEvent.Phase.END) {
            return;
        }

        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_2) {
            return;
        }

        // Désactiver le sprint pour tous les joueurs dans Sous-mode 2 en définissant la vitesse de sprint à la vitesse de marche
        // Modifier les attributs seulement quand l'état de sprint change pour éviter les problèmes de performance
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(evenement.getServer())) {
            boolean estEnTrainDeSprinter = joueur.isSprinting();
            Boolean etatSprintPrecedent = etatSprintJoueur.get(joueur.getUUID());

            // Mettre à jour seulement si l'état de sprint a changé ou première vérification
            if (etatSprintPrecedent == null || etatSprintPrecedent != estEnTrainDeSprinter) {
                etatSprintJoueur.put(joueur.getUUID(), estEnTrainDeSprinter);

                net.minecraft.world.entity.ai.attributes.AttributeInstance vitesseMouvement =
                    joueur.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);

                if (vitesseMouvement != null) {
                    // Retirer tout modificateur de sprint existant
                    vitesseMouvement.removeModifier(UUID_MODIFICATEUR_SPRINT);

                    // Ajouter un modificateur qui annule la vitesse de sprint si le joueur est en train de sprinter
                    if (estEnTrainDeSprinter) {
                        net.minecraft.world.entity.ai.attributes.AttributeModifier modificateurPasDeSprint =
                            new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                UUID_MODIFICATEUR_SPRINT,
                                "No sprint boost",
                                -0.003, // Annuler le boost de sprint de 30% (vitesse de base est 0.1, donc 0.1 * 0.3 = 0.03)
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION
                            );
                        vitesseMouvement.addTransientModifier(modificateurPasDeSprint);
                    }
                }
            }
        }

        // Garder la lumière du jour permanente PENDANT TOUT LE SUBMODE (pas seulement le jeu actif)
        net.minecraft.server.level.ServerLevel surMonde = evenement.getServer().getLevel(net.minecraft.server.level.ServerLevel.OVERWORLD);
        if (surMonde != null) {
            long tempsActuel = surMonde.getDayTime() % 24000;
            if (tempsActuel > 12000) { // Si c'est la nuit (après 12000 tics)
                surMonde.setDayTime(6000); // Réinitialiser à midi
            }

            // Désactiver les orages et la pluie - garder le temps clair en permanence
            if (surMonde.isRaining() || surMonde.isThundering()) {
                surMonde.setWeatherParameters(6000, 0, false, false);
            }
        }

        if (!GestionnaireSousMode2.getInstance().estPartieActive()) {
            return;
        }

        ticsJournalisationPosition++;
        if (ticsJournalisationPosition >= INTERVALLE_JOURNALISATION_POSITION) {
            ticsJournalisationPosition = 0;

            // Journaliser les positions de tous les joueurs vivants et vérifier les limites des spectateurs
            if (GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees() != null) {
                for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(evenement.getServer())) {
                    if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueur.getUUID())) {
                        GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees().enregistrerPositionJoueur(joueur);
                    }
                }
            }

            // Vérifier les limites des spectateurs
            for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(evenement.getServer())) {
                if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                    verifierLimitesSpectateur(joueur);
                }
            }
        }

        // Mettre à jour le HUD de compte de bonbons pour tous les joueurs
        ticsMiseAJourCompteBonbons++;
        if (ticsMiseAJourCompteBonbons >= INTERVALLE_MISE_A_JOUR_COMPTE_BONBONS) {
            ticsMiseAJourCompteBonbons = 0;

            // Obtenir les comptes de bonbons depuis le gestionnaire (par île ET type de ressource)
            java.util.Map<com.example.mysubmod.sousmodes.sousmode2.iles.TypeIle, java.util.Map<TypeRessource, Integer>> comptesBonbons =
                GestionnaireBonbonsSousMode2.getInstance().obtenirBonbonsDisponiblesParIle(evenement.getServer());

            // Envoyer aux joueurs authentifiés seulement
            com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMiseAJourCompteurBonbons paquet =
                new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMiseAJourCompteurBonbons(comptesBonbons);
            for (net.minecraft.server.level.ServerPlayer joueur : com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(evenement.getServer())) {
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur), paquet);
            }
        }

        // Note: Le nettoyage des pissenlits se fait une fois au début de la phase de sélection dans GestionnaireSousMode2
    }

    @SubscribeEvent
    public static void onPlayerInteractItem(PlayerInteractEvent.RightClickItem evenement) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_2) {
            return;
        }

        if (evenement.getEntity() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                evenement.setCanceled(true);
                evenement.setCancellationResult(InteractionResult.FAIL);
                return;
            }

            // Les admins peuvent interagir
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            // Les joueurs vivants peuvent utiliser des bonbons
            if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueur.getUUID())) {
                ItemStack objetTenu = joueur.getItemInHand(evenement.getHand());
                if (objetTenu.is(ItemsMod.BONBON_BLEU.get()) || objetTenu.is(ItemsMod.BONBON_ROUGE.get())) {
                    return; // Autoriser l'utilisation de bonbons pour les joueurs vivants
                }
                // Bloquer l'utilisation d'autres objets pour les joueurs vivants
                evenement.setCanceled(true);
                evenement.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez utiliser que les bonbons en sous-mode 2"));
                return;
            }

            // Les spectateurs, les joueurs en phase de sélection et les joueurs non suivis ne peuvent pas utiliser d'objets
            if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                evenement.setCanceled(true);
                evenement.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser d'objets en tant que spectateur"));
            } else if (GestionnaireSousMode2.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                evenement.setCanceled(true);
                evenement.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser d'objets en sous-mode 2"));
            } else {
                // Les joueurs non suivis ne peuvent pas utiliser d'objets
                evenement.setCanceled(true);
                evenement.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser d'objets en sous-mode 2"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDropItem(net.minecraftforge.event.entity.item.ItemTossEvent evenement) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_2) {
            return;
        }

        if (evenement.getPlayer() instanceof ServerPlayer joueur) {
            // Ignorer les comptes candidats temporaires de file d'attente
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                evenement.setCanceled(true);
                return;
            }

            // Les admins peuvent jeter des objets
            if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            ItemStack objetJete = evenement.getEntity().getItem();

            if (GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
                evenement.setCanceled(true);
                // Retourner l'objet à l'inventaire
                if (!joueur.getInventory().add(objetJete.copy())) {
                    joueur.drop(objetJete.copy(), false);
                }
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en tant que spectateur"));
            } else if (GestionnaireSousMode2.getInstance().estEnPhaseSelection(joueur.getUUID())) {
                // Les joueurs en phase de sélection ne peuvent pas jeter d'objets
                evenement.setCanceled(true);
                // Retourner l'objet à l'inventaire
                if (!joueur.getInventory().add(objetJete.copy())) {
                    joueur.drop(objetJete.copy(), false);
                }
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en sous-mode 2"));
            } else if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueur.getUUID())) {
                // Vérifier si on essaie de jeter un bonbon - les joueurs vivants peuvent jeter des objets non-bonbons
                if (objetJete.is(ItemsMod.BONBON_BLEU.get()) || objetJete.is(ItemsMod.BONBON_ROUGE.get())) {
                    // Annuler l'événement de jet pour les bonbons
                    evenement.setCanceled(true);

                    // Retourner le bonbon à l'inventaire du joueur
                    if (!joueur.getInventory().add(objetJete.copy())) {
                        // Si l'inventaire est plein, le jeter quand même (ne devrait pas arriver dans ce cas)
                        joueur.drop(objetJete.copy(), false);
                    }

                    joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter les bonbons en sous-mode 2"));
                }
                // Les objets non-bonbons peuvent être jetés par les joueurs vivants - ne pas annuler
            } else {
                // Les joueurs non suivis ne peuvent pas jeter d'objets
                evenement.setCanceled(true);
                // Retourner l'objet à l'inventaire
                if (!joueur.getInventory().add(objetJete.copy())) {
                    joueur.drop(objetJete.copy(), false);
                }
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en sous-mode 2"));
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent evenement) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_2) {
            return;
        }

        // Empêcher TOUS les objets (sauf les bonbons de notre système d'apparition) d'apparaître sur les îles et chemins
        if (evenement.getEntity() instanceof ItemEntity entiteItem) {
            BlockPos posItem = entiteItem.blockPosition();

            // Bloquer les objets seulement sur les îles et chemins
            if (GestionnaireSousMode2.getInstance().estProcheIleOuChemin(posItem)) {
                // Autoriser seulement les bonbons de notre système d'apparition (bleus et rouges)
                boolean estBonbon = entiteItem.getItem().is(ItemsMod.BONBON_BLEU.get()) ||
                                  entiteItem.getItem().is(ItemsMod.BONBON_ROUGE.get());

                if (!estBonbon) {
                    evenement.setCanceled(true);
                }
            }
        }

        // Bloquer les mobs hostiles près des îles
        if (evenement.getEntity() instanceof net.minecraft.world.entity.monster.Monster) {
            BlockPos posApparition = evenement.getEntity().blockPosition();

            // Vérifier si l'apparition est près d'une île
            if (estPresIle(posApparition)) {
                evenement.setCanceled(true);
            }
        }
    }


    private static boolean estPresIle(BlockPos pos) {
        GestionnaireSousMode2 gestionnaire = GestionnaireSousMode2.getInstance();

        // Vérifier si dans la petite île (60x60, moitié = 30, +5 tampon = 35)
        if (estDansCarre(pos, gestionnaire.obtenirCentreIlePetite(), 35)) {
            return true;
        }

        // Vérifier si dans l'île moyenne (90x90, moitié = 45, +5 tampon = 50)
        if (estDansCarre(pos, gestionnaire.obtenirCentreIleMoyenne(), 50)) {
            return true;
        }

        // Vérifier si dans la grande île (120x120, moitié = 60, +5 tampon = 65)
        if (estDansCarre(pos, gestionnaire.obtenirCentreIleGrande(), 65)) {
            return true;
        }

        // Vérifier si dans l'île très grande (150x150, moitié = 75, +5 tampon = 80)
        if (estDansCarre(pos, gestionnaire.obtenirCentreIleTresGrande(), 80)) {
            return true;
        }

        // Vérifier si dans le carré central (20x20, moitié = 10, +5 tampon = 15)
        if (estDansCarre(pos, gestionnaire.obtenirPlaceCentrale(), 15)) {
            return true;
        }

        // Vérifier si dans la plateforme spectateur (30x30, moitié = 15, +5 tampon = 20)
        BlockPos centreSpectateur = new BlockPos(0, 150, 0);
        if (estDansCarre(pos, centreSpectateur, 20)) {
            return true;
        }

        return false;
    }

    private static boolean estDansCarre(BlockPos pos, BlockPos centre, int demiTaille) {
        if (centre == null) return false;
        int dx = Math.abs(pos.getX() - centre.getX());
        int dz = Math.abs(pos.getZ() - centre.getZ());
        return dx <= demiTaille && dz <= demiTaille;
    }

    public static void verifierLimitesSpectateur(ServerPlayer joueur) {
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SOUS_MODE_2) {
            return;
        }

        if (!GestionnaireSousMode2.getInstance().estJoueurSpectateur(joueur.getUUID())) {
            return;
        }

        // Centre de plateforme spectateur à (0, 150, 0) avec taille 21x21 (-10 à +10)
        BlockPos centreSpectateur = new BlockPos(0, 150, 0);
        int taillePlateforme = 21;
        Vec3 posJoueur = joueur.position();

        double distanceX = Math.abs(posJoueur.x - centreSpectateur.getX());
        double distanceZ = Math.abs(posJoueur.z - centreSpectateur.getZ());

        // Vérifier si le joueur est en dehors des limites de la plateforme ou en dessous de la plateforme
        if ((distanceX > taillePlateforme/2.0 - 1 || distanceZ > taillePlateforme/2.0 - 1 || posJoueur.y < centreSpectateur.getY() - 5)
            && joueur.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            Vec3 posTeleportation = new Vec3(centreSpectateur.getX() + 0.5, centreSpectateur.getY() + 1, centreSpectateur.getZ() + 0.5);
            joueur.teleportTo(posTeleportation.x, posTeleportation.y, posTeleportation.z);
            joueur.sendSystemMessage(Component.literal("§eVous ne pouvez pas quitter la plateforme spectateur"));
        }
    }
}

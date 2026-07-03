package com.example.mysubmod.sousmodes.sousmode3;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.objets.ItemsMod;
import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GestionnaireEvenementsSousMode3 {
    private static int ticksJournalisationPosition = 0;
    private static final int INTERVALLE_JOURNALISATION_POSITION = 100; // 5 secondes
    private static int ticksSynchronisationZones = 0;
    private static final int INTERVALLE_SYNCHRONISATION_ZONES = 100; // 5 secondes (les changements sont envoyés en temps réel)

    private static boolean estModeActif() {
        return GestionnaireSousModes.getInstance().obtenirModeActuel() == SousMode.SOUS_MODE_3;
    }

    // ==================== Attaques ====================

    @SubscribeEvent
    public static void onPlayerAttack(LivingAttackEvent event) {
        if (!estModeActif()) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attaquant) {
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(attaquant)) {
                event.setCanceled(true);
                return;
            }
            if (!GestionnaireSousModes.getInstance().estAdmin(attaquant)) {
                event.setCanceled(true);
                attaquant.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en sous-mode 3"));
            }
        }
    }

    // ==================== Minage ====================

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!estModeActif()) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer joueur)) {
            return;
        }

        if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
            event.setCanceled(true);
            return;
        }

        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();

        // Admins : mêmes restrictions que les spectateurs sur la plateforme
        if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
            if (gestionnaire.estJoueurSpectateur(joueur.getUUID())) {
                event.setCanceled(true);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs sur la plateforme spectateur"));
            }
            return;
        }

        // Seuls les joueurs vivants pendant la partie active peuvent miner
        if (!gestionnaire.estPartieActive() || !gestionnaire.estJoueurVivant(joueur.getUUID())) {
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser de blocs pour le moment"));
            return;
        }

        BlockPos pos = event.getPos();

        // Les joueurs peuvent miner tous les blocs de la carte, à l'intérieur de la cage uniquement
        if (!gestionnaire.estDansCage(pos)) {
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez miner que les blocs de la carte"));
            return;
        }

        BlockState etat = event.getState();
        ServerLevel niveau = joueur.serverLevel();

        // Bloc bonbon non-visible : détruit définitivement, laisse tomber les objets bonbons
        if (GestionnaireBonbonsSousMode3.estBlocBonbonCache(etat)
            || GestionnaireBonbonsSousMode3.obtenirInstance().estBlocBonbonCache(pos)) {
            event.setCanceled(true); // Gestion manuelle du minage et des drops
            int quantite = GestionnaireBonbonsSousMode3.obtenirInstance().gererMinageBonbonCache(niveau, pos);
            if (quantite <= 0) {
                // Bloc bonbon non suivi (cas limite) : le retirer sans rien déposer
                niveau.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            } else if (gestionnaire.obtenirEnregistreurDonnees() != null) {
                gestionnaire.obtenirEnregistreurDonnees().enregistrerMinageBonbonCache(joueur, pos, quantite);
            }
            return;
        }

        // Bloc normal de la carte : ajouté tel quel à l'inventaire du joueur (minable et replaçable indéfiniment)
        event.setCanceled(true);
        Block bloc = etat.getBlock();

        // Si l'inventaire est plein, le bloc reste en place (aucun item au sol)
        ItemStack pile = new ItemStack(bloc.asItem());
        if (!pile.isEmpty() && !joueur.getInventory().add(pile)) {
            joueur.sendSystemMessage(Component.literal("§cInventaire plein !"));
            return;
        }

        niveau.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);

        if (gestionnaire.obtenirEnregistreurDonnees() != null) {
            gestionnaire.obtenirEnregistreurDonnees().enregistrerMinageBloc(joueur, pos,
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(bloc).toString());
        }
    }

    // ==================== Placement ====================

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!estModeActif()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer joueur)) {
            return;
        }

        if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
            event.setCanceled(true);
            return;
        }

        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();

        if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
            if (gestionnaire.estJoueurSpectateur(joueur.getUUID())) {
                event.setCanceled(true);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs sur la plateforme spectateur"));
            }
            return;
        }

        if (!gestionnaire.estPartieActive() || !gestionnaire.estJoueurVivant(joueur.getUUID())) {
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer de blocs pour le moment"));
            return;
        }

        BlockPos pos = event.getPos();

        // Un bloc peut être replacé n'importe où à l'intérieur de la cage, y compris
        // dans l'eau et par-dessus un bloc bonbon non-visible
        if (!gestionnaire.estDansCage(pos)) {
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez placer des blocs qu'à l'intérieur de la cage"));
            return;
        }

        // Suivre le bloc placé pour le nettoyage à la désactivation
        gestionnaire.suivreBlocPlace(pos);
        if (gestionnaire.obtenirEnregistreurDonnees() != null) {
            gestionnaire.obtenirEnregistreurDonnees().enregistrerPlacementBloc(joueur, pos,
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().getBlock()).toString());
        }
    }

    // ==================== Interactions ====================

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!estModeActif()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer joueur)) {
            return;
        }

        if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();

        if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
            if (gestionnaire.estJoueurSpectateur(joueur.getUUID())) {
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

        ItemStack objetTenu = joueur.getItemInHand(event.getHand());

        if (gestionnaire.estJoueurVivant(joueur.getUUID()) && gestionnaire.estPartieActive()) {
            // Les joueurs vivants peuvent utiliser les bonbons et placer les blocs minés
            if (objetTenu.is(ItemsMod.BONBON.get()) || objetTenu.getItem() instanceof BlockItem) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez utiliser que les bonbons et les blocs minés"));
        } else if (gestionnaire.estJoueurSpectateur(joueur.getUUID())) {
            if (event.getLevel().getBlockEntity(event.getPos()) instanceof SignBlockEntity) {
                joueur.setGameMode(GameType.SPECTATOR);
            } else {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en tant que spectateur"));
            }
        } else {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas interagir avec les blocs en sous-mode 3"));
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (!estModeActif()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer joueur)) {
            return;
        }

        if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
            return;
        }

        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();
        ItemStack objetTenu = joueur.getItemInHand(event.getHand());

        if (gestionnaire.estJoueurVivant(joueur.getUUID())) {
            if (objetTenu.is(ItemsMod.BONBON.get()) || objetTenu.getItem() instanceof BlockItem) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez utiliser que les bonbons en sous-mode 3"));
        } else {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Le crafting est désactivé pour TOUS les joueurs du Sous-mode 3 (admins inclus).
     * ItemCraftedEvent n'est pas annulable : on vide le résultat fabriqué et on
     * rembourse les ingrédients (consommés par vanilla juste après l'événement).
     */
    @SubscribeEvent
    public static void onPlayerCrafting(PlayerEvent.ItemCraftedEvent event) {
        if (!estModeActif()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer joueur)) {
            return;
        }

        // Copier les ingrédients encore présents dans la grille (1 par case non vide)
        java.util.List<ItemStack> ingredients = new java.util.ArrayList<>();
        for (int i = 0; i < event.getInventory().getContainerSize(); i++) {
            ItemStack pile = event.getInventory().getItem(i);
            if (!pile.isEmpty()) {
                ingredients.add(pile.copyWithCount(1));
            }
        }

        // Annuler le résultat de la fabrication
        event.getCrafting().setCount(0);

        // Rembourser les ingrédients après leur consommation par vanilla
        joueur.server.execute(() -> {
            for (ItemStack pile : ingredients) {
                if (!joueur.getInventory().add(pile)) {
                    joueur.drop(pile, false);
                }
            }
        });

        joueur.sendSystemMessage(Component.literal("§cLe crafting est désactivé en sous-mode 3"));
    }

    // ==================== Ramassage / jet d'objets ====================

    @SubscribeEvent
    public static void onEntityItemPickup(EntityItemPickupEvent event) {
        if (!estModeActif()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer joueur)) {
            return;
        }

        if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
            event.setCanceled(true);
            return;
        }

        if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
            return;
        }

        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();
        ItemStack pile = event.getItem().getItem();

        if (gestionnaire.estJoueurVivant(joueur.getUUID())) {
            if (pile.is(ItemsMod.BONBON.get())) {
                gestionnaire.incrementerCompteurBonbons(joueur.getUUID(), pile.getCount());

                // Compteur de zone + réapparition (si bonbon visible de la carte)
                BlockPos posOrigine = GestionnaireBonbonsSousMode3.obtenirInstance()
                    .gererRamassageBonbon(event.getItem());

                if (gestionnaire.obtenirEnregistreurDonnees() != null) {
                    BlockPos pos = posOrigine != null ? posOrigine : joueur.blockPosition();
                    gestionnaire.obtenirEnregistreurDonnees().enregistrerCollecteBonbon(joueur, pos);
                }
            }
            // Les objets blocs (issus du minage) peuvent aussi être ramassés
        } else {
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas ramasser d'objets pour le moment"));
        }
    }

    @SubscribeEvent
    public static void onPlayerDropItem(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (!estModeActif()) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer joueur)) {
            return;
        }

        if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
            event.setCanceled(true);
            return;
        }

        if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
            return;
        }

        // Les joueurs ne peuvent pas jeter d'objets (bonbons ou blocs minés)
        event.setCanceled(true);
        ItemStack objetJete = event.getEntity().getItem();
        if (!joueur.getInventory().add(objetJete.copy())) {
            joueur.drop(objetJete.copy(), false);
        }
        joueur.sendSystemMessage(Component.literal("§cVous ne pouvez pas jeter d'objets en sous-mode 3"));
    }

    // ==================== Connexions / déconnexions ====================

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) {
            return;
        }

        if (GestionnaireSousModes.getInstance().obtenirModeActuel() == SousMode.SOUS_MODE_3) {
            if (UtilitaireFiltreJoueurs.estJoueurRestreint(joueur)) {
                return;
            }

            GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();

            // Envoyer les zones actuelles au joueur (HUD) — flèche réinitialisée à la reconnexion
            if (gestionnaire.estPartieActive() || gestionnaire.estPhaseAttente()) {
                GestionnaireBonbonsSousMode3.obtenirInstance().envoyerZonesCompletesAJoueur(joueur, true);
            }

            if (gestionnaire.etaitJoueurDeconnecte(joueur.getName().getString())) {
                gestionnaire.gererReconnexionJoueur(joueur);
            } else {
                if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                    gestionnaire.teleporterVersSpectateur(joueur);
                    if (gestionnaire.estPhaseAttente()) {
                        joueur.sendSystemMessage(Component.literal("§6Appuyez sur N pour ouvrir le menu de lancement de partie"));
                    }
                } else if (gestionnaire.estPhaseAttente()) {
                    // Pendant la phase d'attente, les nouveaux joueurs participent
                    gestionnaire.ajouterJoueurEnAttente(joueur);
                    joueur.sendSystemMessage(Component.literal("§eVous rejoignez le jeu. En attente du lancement de la partie..."));
                } else {
                    // Après le lancement, les nouveaux joueurs vont en spectateur
                    gestionnaire.teleporterVersSpectateur(joueur);
                }
            }
        } else {
            // Effacer le HUD Sous-mode 3 pour les joueurs qui se connectent dans un autre mode
            com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                new com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetMinuterieJeuSousMode3(-1));
            // Le HUD des zones est partagé avec les parties sur carte des sous-modes 1 et 2 :
            // ne pas l'effacer quand l'un d'eux utilise une carte (leur handler envoie les zones)
            boolean carteAutreSousMode =
                com.example.mysubmod.sousmodes.sousmode1.GestionnaireSousMode1.getInstance().modeCarteActif()
                || com.example.mysubmod.sousmodes.sousmode2.GestionnaireSousMode2.getInstance().modeCarteActif();
            if (!carteAutreSousMode) {
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                    com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetZonesSousMode3.vide());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) {
            return;
        }
        if (GestionnaireSousModes.getInstance().obtenirModeActuel() == SousMode.SOUS_MODE_3) {
            GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();
            boolean estVivant = gestionnaire.estJoueurVivant(joueur.getUUID());
            boolean enAttente = gestionnaire.estEnAttente(joueur.getUUID());
            if (estVivant || enAttente) {
                gestionnaire.gererDeconnexionJoueur(joueur);
            }
        }
    }

    // ==================== Tick serveur ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!estModeActif()) {
            return;
        }

        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();

        // Désactiver le boost de sprint (comme au Sous-mode 1)
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(event.getServer())) {
            net.minecraft.world.entity.ai.attributes.AttributeInstance vitesseDeplacement =
                joueur.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (vitesseDeplacement != null) {
                java.util.UUID uuidModificateurSprint = java.util.UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
                vitesseDeplacement.removeModifier(uuidModificateurSprint);
                if (joueur.isSprinting()) {
                    net.minecraft.world.entity.ai.attributes.AttributeModifier modificateurPasDeSprint =
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            uuidModificateurSprint, "No sprint boost", -0.003,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
                    if (vitesseDeplacement.getModifier(uuidModificateurSprint) == null) {
                        vitesseDeplacement.addTransientModifier(modificateurPasDeSprint);
                    }
                }
            }
        }

        // Jour permanent et temps clair
        ServerLevel overworld = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            long tempsActuel = overworld.getDayTime() % 24000;
            if (tempsActuel > 12000) {
                overworld.setDayTime(6000);
            }
            if (overworld.isRaining() || overworld.isThundering()) {
                overworld.setWeatherParameters(6000, 0, false, false);
            }
        }

        // La position de chaque joueur est vérifiée à chaque tick (20 fois/seconde)
        gestionnaire.verifierCageJoueurs(event.getServer());

        if (!gestionnaire.estPartieActive() && !gestionnaire.estPhaseAttente()) {
            return;
        }

        ticksJournalisationPosition++;
        if (ticksJournalisationPosition >= INTERVALLE_JOURNALISATION_POSITION) {
            ticksJournalisationPosition = 0;

            if (gestionnaire.estPartieActive() && gestionnaire.obtenirEnregistreurDonnees() != null) {
                for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(event.getServer())) {
                    if (gestionnaire.estJoueurVivant(joueur.getUUID())) {
                        gestionnaire.obtenirEnregistreurDonnees().enregistrerPositionJoueur(joueur);
                    }
                }
            }

            // Limites de la plateforme pour les spectateurs et les joueurs en attente
            for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(event.getServer())) {
                boolean surPlateforme = gestionnaire.estJoueurSpectateur(joueur.getUUID())
                    || (gestionnaire.estPhaseAttente() && gestionnaire.estEnAttente(joueur.getUUID()));
                if (surPlateforme) {
                    verifierLimitesPlateforme(joueur, gestionnaire);
                }
            }
        }

        // Resynchronisation périodique des compteurs de zones (filet de sécurité)
        ticksSynchronisationZones++;
        if (ticksSynchronisationZones >= INTERVALLE_SYNCHRONISATION_ZONES) {
            ticksSynchronisationZones = 0;
            if (gestionnaire.estPartieActive()) {
                GestionnaireBonbonsSousMode3.obtenirInstance().purgerEntitesFusionnees();
                GestionnaireBonbonsSousMode3.obtenirInstance().envoyerCompteursZones();
            }
        }
    }

    private static void verifierLimitesPlateforme(ServerPlayer joueur, GestionnaireSousMode3 gestionnaire) {
        BlockPos centre = gestionnaire.obtenirPlateformeSpectateur();
        int taillePlateforme = 21;
        Vec3 positionJoueur = joueur.position();

        double distanceX = Math.abs(positionJoueur.x - centre.getX());
        double distanceZ = Math.abs(positionJoueur.z - centre.getZ());

        if ((distanceX > taillePlateforme / 2.0 - 1 || distanceZ > taillePlateforme / 2.0 - 1
            || positionJoueur.y < centre.getY() - 5)
            && joueur.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            joueur.teleportTo(centre.getX() + 0.5, centre.getY() + 1, centre.getZ() + 0.5);
            joueur.sendSystemMessage(Component.literal("§eVous ne pouvez pas quitter la plateforme spectateur"));
        }
    }

    // ==================== Apparitions d'entités ====================

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!estModeActif()) {
            return;
        }

        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();

        // Bloquer tous les items étrangers dans l'aire de la carte (seuls les bonbons
        // sont autorisés) : empêche les résidus de la destruction du terrain principal
        // (fleurs, pousses, graines...) d'apparaître sur la carte
        if (event.getEntity() instanceof ItemEntity entiteObjet) {
            if (gestionnaire.estDansAireCarte(entiteObjet.blockPosition())
                && !entiteObjet.getItem().is(ItemsMod.BONBON.get())) {
                event.setCanceled(true);
            }
            return;
        }

        // Bloquer les monstres dans l'aire de la carte et près de la plateforme
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster) {
            BlockPos pos = event.getEntity().blockPosition();
            BlockPos plateforme = gestionnaire.obtenirPlateformeSpectateur();
            boolean presPlateforme = Math.abs(pos.getX() - plateforme.getX()) <= 20
                && Math.abs(pos.getZ() - plateforme.getZ()) <= 20;
            if (gestionnaire.estDansAireCarte(pos) || presPlateforme) {
                event.setCanceled(true);
            }
        }
    }
}

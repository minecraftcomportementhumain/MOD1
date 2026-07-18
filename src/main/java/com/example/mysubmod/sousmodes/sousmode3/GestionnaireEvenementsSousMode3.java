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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ServerChatEvent;
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

    // Vrai pendant qu'on redépose de force un objet refusé (inventaire plein). joueur.drop()
    // re-déclenche ItemTossEvent de façon synchrone dans ce même handler : sans cette garde, le
    // dépôt de secours se ré-annulait puis se re-déposait à l'infini -> StackOverflowError.
    private static boolean depotForceEnCours = false;

    /** UUID constant du modificateur d'attribut « pas de bonus de sprint » : évite un
     *  UUID.fromString par joueur et par tick dans onServerTick. */
    private static final java.util.UUID UUID_MODIFICATEUR_SPRINT =
        java.util.UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");

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
            if (GestionnaireSousModes.getInstance().estAdmin(attaquant)) {
                return;
            }
            GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();
            if (gestionnaire.estPartieActive() && gestionnaire.estJoueurVivant(attaquant.getUUID())) {
                if (event.getEntity() instanceof ServerPlayer cible) {
                    // Cible joueur : autorisée seulement si le PvP est activé et la cible vivante.
                    if (gestionnaire.obtenirConfig().pvp && gestionnaire.estJoueurVivant(cible.getUUID())) {
                        return;
                    }
                } else {
                    // Cible non-joueur (monstre hostile...) : autorisée, pour pouvoir se défendre.
                    return;
                }
            }
            event.setCanceled(true);
            attaquant.sendSystemMessage(Component.literal("§cVous ne pouvez pas attaquer en sous-mode 3"));
        }
    }

    // ==================== Chat ====================

    /**
     * Applique l'option « Chat entre joueurs » du menu N : quand elle est décochée, les messages
     * de chat des non-admins ne sont pas diffusés pendant la partie active (participants,
     * spectateurs et joueurs en attente — les spectateurs pourraient sinon renseigner les
     * vivants). L'admin garde toujours le chat pour s'adresser aux participants ; hors partie
     * (phase d'attente, fin de partie), le chat reste libre.
     */
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (!estModeActif()) {
            return;
        }
        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();
        ServerPlayer joueur = event.getPlayer();
        boolean bloque = gestionnaire.estPartieActive() && !gestionnaire.obtenirConfig().chatJoueurs
            && !GestionnaireSousModes.getInstance().estAdmin(joueur);

        // Corpus de recherche : journaliser TOUS les messages de la session, y compris ceux
        // que l'option « Chat entre joueurs » bloque (marqués diffuse=false).
        if (gestionnaire.obtenirEnregistreurDonnees() != null) {
            gestionnaire.obtenirEnregistreurDonnees()
                .enregistrerChat(joueur, event.getMessage().getString(), !bloque);
        }

        if (bloque) {
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cLe chat est désactivé dans cette partie"));
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

        // Pendant la génération de la carte, personne (admins inclus) ne peut modifier de
        // bloc : protège la plateforme spectateur tant que la carte n'est pas chargée.
        if (gestionnaire.estGenerationEnCours()) {
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cLa carte est en cours de génération, veuillez patienter..."));
            return;
        }

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

        // Destruction de blocs désactivée par la config : les blocs normaux sont incassables
        // (les blocs bonbons non-visibles, gérés plus haut, restent toujours minables).
        if (!gestionnaire.obtenirConfig().destructionBloc) {
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cLa destruction de blocs est désactivée dans cette partie"));
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

        // Pendant la génération de la carte, aucun placement de bloc (admins inclus).
        if (gestionnaire.estGenerationEnCours()) {
            event.setCanceled(true);
            return;
        }

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

        // Placement de blocs désactivé par la config
        if (!gestionnaire.obtenirConfig().placementBloc) {
            event.setCanceled(true);
            joueur.sendSystemMessage(Component.literal("§cLe placement de blocs est désactivé dans cette partie"));
            return;
        }

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
        if (!(event.getEntity() instanceof ServerPlayer joueur)) {
            return;
        }
        // Ne pas ignorer la main secondaire : l'ancien filtre MAIN_HAND laissait un objet en
        // main gauche contourner la restriction (ex. un spectateur mangeant un bonbon en visant
        // un bloc). On la traite dès qu'elle tient un objet ; une main gauche vide est ignorée
        // pour ne pas déclencher de faux refus à chaque clic normal.
        if (event.getHand() == InteractionHand.OFF_HAND
            && joueur.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
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
            if (GestionnaireBonbonsSousMode3.estObjetBonbon(objetTenu) || objetTenu.getItem() instanceof BlockItem) {
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
            if (GestionnaireBonbonsSousMode3.estObjetBonbon(objetTenu) || objetTenu.getItem() instanceof BlockItem) {
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

        // Crafting autorisé par la config : laisser la fabrication vanilla suivre son cours,
        // en journalisant l'objet produit (corpus de recherche).
        GestionnaireSousMode3 gestionnaireCraft = GestionnaireSousMode3.getInstance();
        if (gestionnaireCraft.obtenirConfig().crafting) {
            if (gestionnaireCraft.obtenirEnregistreurDonnees() != null && !event.getCrafting().isEmpty()) {
                gestionnaireCraft.obtenirEnregistreurDonnees().enregistrerCraft(joueur,
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                        event.getCrafting().getItem()).toString(),
                    event.getCrafting().getCount());
            }
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
                    depotForceEnCours = true;
                    try {
                        joueur.drop(pile, false);
                    } finally {
                        depotForceEnCours = false;
                    }
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
            // Un admin ne doit pas ramasser de bonbon : sinon l'entité disparaît sans
            // décrément de compteur ni réapparition (la purge la traite comme une fusion),
            // faussant les compteurs et le barycentre de navigation pour toute la partie.
            if (GestionnaireBonbonsSousMode3.estObjetBonbon(event.getItem().getItem())) {
                event.setCanceled(true);
            }
            return;
        }

        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();
        ItemStack pile = event.getItem().getItem();

        if (gestionnaire.estJoueurVivant(joueur.getUUID())) {
            if (GestionnaireBonbonsSousMode3.estObjetBonbon(pile)) {
                // Inventaire plein : ne PAS traiter le ramassage. Sinon l'événement se
                // re-déclenche à chaque tick (le bonbon reste au sol) → compteur gonflé et
                // réapparition planifiée alors que l'original est toujours là (duplication).
                if (!inventairePeutAccepter(joueur, pile)) {
                    event.setCanceled(true);
                    return;
                }
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

    /** Vrai si l'inventaire du joueur peut accueillir (tout ou partie de) la pile :
     *  un emplacement vide, ou une pile identique non pleine. */
    private static boolean inventairePeutAccepter(ServerPlayer joueur, ItemStack pile) {
        Inventory inv = joueur.getInventory();
        if (inv.getFreeSlot() != -1) {
            return true;
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getCount() < s.getMaxStackSize()
                && ItemStack.isSameItemSameTags(s, pile)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlayerDropItem(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (depotForceEnCours) {
            // Notre propre dépôt de secours (inventaire plein) : laisser vanilla le poser au sol
            // sans le ré-annuler, sous peine de récursion infinie sur le thread serveur.
            return;
        }
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
            // Estampiller l'objet sans le suivre ni l'autoriser : un bonbon /give jeté par
            // un admin, sauvé dans un chunk déchargé, échapperait sinon au tueur de résidus
            // s'il ne recharge que pendant une session ultérieure (objet non estampillé
            // toléré quand une session est active) et fausserait les scores.
            GestionnaireSousMode3.getInstance().estampillerObjetSession(event.getEntity());
            return;
        }

        // Jet d'objets autorisé par la config : suivre l'entité pour que le filtre anti-résidus
        // de l'aire de la carte la laisse apparaître (sinon l'objet serait détruit), puis la
        // laisser tomber normalement. Ces objets sont purgés à la désactivation du sous-mode.
        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();
        if (gestionnaire.obtenirConfig().dropObjet) {
            gestionnaire.suivreObjetAuSol(event.getEntity());
            event.getEntity().setUnlimitedLifetime();
            if (gestionnaire.obtenirEnregistreurDonnees() != null) {
                ItemStack pileJetee = event.getEntity().getItem();
                gestionnaire.obtenirEnregistreurDonnees().enregistrerObjetJete(joueur,
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(pileJetee.getItem()).toString(),
                    pileJetee.getCount());
            }
            return;
        }

        // Les joueurs ne peuvent pas jeter d'objets (bonbons ou blocs minés)
        event.setCanceled(true);
        ItemStack objetJete = event.getEntity().getItem();
        if (!joueur.getInventory().add(objetJete.copy())) {
            depotForceEnCours = true;
            try {
                joueur.drop(objetJete.copy(), false);
            } finally {
                depotForceEnCours = false;
            }
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

            // Purger une vie max personnalisée persistée dans le NBT (partie précédente) ;
            // la reconnexion en partie réapplique ensuite la valeur configurée.
            gestionnaire.reinitialiserSanteMaxJoueur(joueur);

            // Envoyer les zones actuelles au joueur (HUD) — flèche réinitialisée à la reconnexion.
            // Seulement une fois la partie lancée : avant, les compteurs ne reflètent rien et
            // les joueurs déjà connectés n'ont pas ce HUD (le lancement l'envoie à tous).
            if (gestionnaire.estPartieActive()) {
                GestionnaireBonbonsSousMode3.obtenirInstance().envoyerZonesCompletesAJoueur(joueur, true);
                // Temps de minage + options d'interface de la partie en cours (la valeur client
                // survit à une déconnexion mais pas à un redémarrage : toujours resynchroniser).
                gestionnaire.envoyerInterfacePartie(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur));
            } else {
                // Avant le lancement : effacer les HUD résiduels d'une partie précédente
                // (l'état du HUD côté client survit à une déconnexion/reconnexion)
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                    com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetZonesSousMode3.vide());
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetMinuterieJeuSousMode3(-1));
                com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3.envoyerInterfaceDefauts(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur));
            }

            if (gestionnaire.etaitJoueurDeconnecte(joueur.getName().getString())) {
                gestionnaire.gererReconnexionJoueur(joueur);
            } else {
                if (GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                    gestionnaire.teleporterVersSpectateur(joueur);
                    if (gestionnaire.estPhaseAttente()) {
                        gestionnaire.envoyerFaitsCarteAJoueur(joueur);
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
            // Purger une vie max personnalisée persistée dans le NBT d'une partie SM3 précédente
            // (le joueur s'était déconnecté avant la restauration de fin de partie)
            GestionnaireSousMode3.getInstance().reinitialiserSanteMaxJoueur(joueur);

            // Restaurer l'inventaire d'avant-partie d'un joueur déconnecté pendant la partie
            // et revenu après la fin : sinon il garderait les items de la carte SM3 et son
            // inventaire d'origine (conservé à part) serait perdu.
            GestionnaireSousMode3.getInstance().restaurerInventaireStockeSiPresent(joueur);

            // Effacer le HUD Sous-mode 3 pour les joueurs qui se connectent dans un autre mode
            com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                new com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetMinuterieJeuSousMode3(-1));
            com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetZonesSousMode3.vide());
            com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3.envoyerInterfaceDefauts(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur));
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
        ConfigPartieSousMode3 config = gestionnaire.obtenirConfig();

        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(event.getServer())) {
            // Boost de sprint : neutralisé par défaut (comme au Sous-mode 1), laissé actif si
            // l'option « bonus de vitesse au sprint » est cochée.
            net.minecraft.world.entity.ai.attributes.AttributeInstance vitesseDeplacement =
                joueur.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (vitesseDeplacement != null) {
                vitesseDeplacement.removeModifier(UUID_MODIFICATEUR_SPRINT);
                if (!config.bonusSprint && joueur.isSprinting()) {
                    net.minecraft.world.entity.ai.attributes.AttributeModifier modificateurPasDeSprint =
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            UUID_MODIFICATEUR_SPRINT, "No sprint boost", -0.003,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
                    if (vitesseDeplacement.getModifier(UUID_MODIFICATEUR_SPRINT) == null) {
                        vitesseDeplacement.addTransientModifier(modificateurPasDeSprint);
                    }
                }
            }

            // Faim désactivée : garder la nourriture au maximum pour les joueurs vivants.
            if (!config.faim && gestionnaire.estJoueurVivant(joueur.getUUID())) {
                if (joueur.getFoodData().getFoodLevel() < 20) {
                    joueur.getFoodData().setFoodLevel(20);
                }
            }
        }

        // Jour permanent et temps clair (chacun conditionné par la config)
        ServerLevel overworld = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            if (config.jourPermanent) {
                long tempsActuel = overworld.getDayTime() % 24000;
                if (tempsActuel > 12000) {
                    overworld.setDayTime(6000);
                }
            }
            if (!config.pluie && (overworld.isRaining() || overworld.isThundering())) {
                overworld.setWeatherParameters(6000, 0, false, false);
            } else if (config.pluie && !overworld.isRaining()) {
                // Ré-imposer la pluie : la forcée au lancement expire après 30 min, or une
                // partie peut durer jusqu'à 240 min — sinon le ciel redevient clair ensuite.
                overworld.setWeatherParameters(0, 6000, true, false);
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
        // L'événement se déclenche sur les DEUX côtés logiques : en solo/LAN intégré, les
        // entités client passeraient ici et pollueraient l'état serveur (suivi des bonbons)
        if (event.getLevel().isClientSide()) {
            return;
        }

        // Tueur de résidus — AVANT la garde de mode, pour agir aussi en salle d'attente :
        // un objet créé par une session de carte antérieure (bonbon, objet jeté/déposé)
        // dont le chunk était déchargé au moment des balayages de la désactivation revient
        // du disque au prochain chargement ; il est supprimé ici, quel que soit le moment.
        // Un bonbon sans estampille hors session (posé avant ce correctif, ou /give perdu)
        // est traité de la même façon.
        if (event.getEntity() instanceof ItemEntity objetCharge) {
            long sessionObjet = objetCharge.getPersistentData().getLong(GestionnaireSousMode3.TAG_SESSION_CARTE);
            long sessionCourante = GestionnaireSousMode3.getInstance().obtenirIdSessionCarte();
            boolean estBonbon = GestionnaireBonbonsSousMode3.estObjetBonbon(objetCharge.getItem());
            if ((sessionObjet != 0 && sessionObjet != sessionCourante)
                || (estBonbon && sessionObjet == 0 && sessionCourante == 0)) {
                event.setCanceled(true);
                return;
            }
        }

        if (!estModeActif()) {
            return;
        }

        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();

        // Bloquer tous les items étrangers dans l'aire de la carte (seuls les bonbons
        // sont autorisés) : empêche les résidus de la destruction du terrain principal
        // (fleurs, pousses, graines...) d'apparaître sur la carte
        if (event.getEntity() instanceof ItemEntity entiteObjet) {
            if (gestionnaire.estDansAireCarte(entiteObjet.blockPosition())
                && !GestionnaireBonbonsSousMode3.estObjetBonbon(entiteObjet.getItem())
                && !gestionnaire.estObjetAuSolAutorise(entiteObjet)) {
                event.setCanceled(true);
            } else if (GestionnaireBonbonsSousMode3.estObjetBonbon(entiteObjet.getItem())) {
                // Un bonbon visible qui (re)charge dans un chunk : le réassocier à sa cellule
                // si le déchargement avait cassé le suivi (keyé par instance d'entité), sinon
                // son ramassage ne décrémenterait plus le compteur ni ne réapparaîtrait.
                GestionnaireBonbonsSousMode3.obtenirInstance().reenregistrerBonbonVisibleSiConnu(entiteObjet);
            }
            return;
        }

        // Bloquer les créatures (monstres, animaux...) dans l'aire de la carte et près de la plateforme.
        // Exception : si « monstres hostiles » est coché, les monstres hostiles sont autorisés dans
        // l'aire de jeu (jamais près de la plateforme spectateur).
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob) {
            BlockPos pos = event.getEntity().blockPosition();
            BlockPos plateforme = gestionnaire.obtenirPlateformeSpectateur();
            boolean presPlateforme = Math.abs(pos.getX() - plateforme.getX()) <= 20
                && Math.abs(pos.getZ() - plateforme.getZ()) <= 20;
            if (presPlateforme) {
                event.setCanceled(true);
            } else if (gestionnaire.estDansAireCarte(pos)) {
                boolean monstreHostile = event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy;
                boolean autoriser = gestionnaire.obtenirConfig().monstresHostiles && monstreHostile;
                if (!autoriser) {
                    event.setCanceled(true);
                }
            }
        }
    }

    // ==================== Dégâts appliqués (journalisation de recherche) ====================

    /**
     * Journalise chaque dégât vanilla RÉELLEMENT appliqué à un participant vivant (PvP, chute,
     * faim, monstres, flèches...), avec sa cause et — pour le PvP — l'attaquant. Les dégâts
     * annulés en amont (noyade gérée par le sous-mode, chute désactivée, étouffement) ne
     * déclenchent pas cet événement ; la dégradation programmée de la santé, qui passe par
     * setHealth, non plus (elle est journalisée en CHANGEMENT_SANTE).
     */
    @SubscribeEvent
    public static void onLivingDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!estModeActif()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer joueur)) {
            return;
        }
        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();
        if (gestionnaire.obtenirEnregistreurDonnees() == null
            || !gestionnaire.estJoueurVivant(joueur.getUUID())) {
            return;
        }
        String attaquant = event.getSource().getEntity() instanceof ServerPlayer auteur
            ? auteur.getName().getString() : null;
        gestionnaire.obtenirEnregistreurDonnees().enregistrerDegat(
            joueur, event.getSource().getMsgId(), event.getAmount(), attaquant);
    }

    // ==================== Mort d'un participant (chute, PvP, faim, feu...) ====================

    /**
     * Intercepte la mort vanilla d'un participant (dégâts de chute, PvP, faim, feu, etc.) et la
     * route vers la logique du sous-mode (réapparition ou spectateur définitif + fin éventuelle),
     * pour éviter l'écran de mort et la réapparition au lit. La dégradation de santé, qui gère
     * elle-même sa mort, n'appelle jamais {@code die()} et n'est donc pas concernée.
     */
    // ==================== Vitesse de minage imposée ====================

    /**
     * Impose le temps de minage configuré (menu N › Bonbons & minage) à tout bloc miné pendant une
     * partie active, quel que soit le bloc ou l'outil. L'Eau n'est pas minable et la
     * Limite (barrière) est incassable — l'option ne les concerne pas. Le même calcul
     * tourne côté client (prédiction de casse) via GestionnaireEvenementsClient, avec la
     * valeur synchronisée par PaquetVitesseMinageSousMode3.
     */
    @SubscribeEvent
    public static void onVitesseMinage(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (event.getEntity().level().isClientSide()) {
            return; // Côté client : handler dédié avec la valeur synchronisée
        }
        if (!estModeActif() || !GestionnaireSousMode3.getInstance().estPartieActive()) {
            return;
        }
        appliquerTempsMinage(event, GestionnaireSousMode3.getInstance().obtenirConfig().tempsMinageSecondes);
    }

    /**
     * Force la vitesse de casse pour que le bloc visé prenne exactement
     * {@code tempsSecondes} : la progression vanilla par tick vaut
     * vitesse / dureté / 30 (ou / 100 sans l'outil requis pour la récolte) —
     * on inverse la formule. 0 = vitesses vanilla (aucun effet).
     */
    public static void appliquerTempsMinage(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event,
                                            float tempsSecondes) {
        if (tempsSecondes <= 0) {
            return;
        }
        BlockPos pos = event.getPosition().orElse(null);
        if (pos == null) {
            return;
        }
        float durete = event.getState().getDestroySpeed(event.getEntity().level(), pos);
        if (durete <= 0) {
            return; // Incassable (Limite/barrière) : hors du champ de l'option
        }
        float facteur = event.getEntity().hasCorrectToolForDrops(event.getState()) ? 30f : 100f;
        event.setNewSpeed(durete * facteur / (20f * tempsSecondes));
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onEntityDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!estModeActif()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer joueur)) {
            return;
        }
        GestionnaireSousMode3 gestionnaire = GestionnaireSousMode3.getInstance();
        if (gestionnaire.gererMortParticipant(joueur, event.getSource().getMsgId())) {
            event.setCanceled(true);
            return;
        }
        // Fenêtre entre la fin de partie et la désactivation (partie inactive mais joueurs
        // encore suivis comme vivants) : bloquer la mort vanilla et resoigner sur place,
        // sinon le joueur resterait figé à 0 PV (la mort annulée n'est pas prise en charge).
        if (gestionnaire.estJoueurVivant(joueur.getUUID())) {
            event.setCanceled(true);
            joueur.setHealth(joueur.getMaxHealth());
            return;
        }
        // Joueur en attente ou spectateur (jamais dans joueursVivants) : la réapparition
        // vanilla l'enverrait au spawn du monde, HORS de la plateforme, sans aucun rappel
        // (le confinement ne suit que les ensembles attente/spectateur). Annuler la mort
        // (famine pendant une longue génération, /kill...), resoigner et ramener sur place.
        if (gestionnaire.estEnAttente(joueur.getUUID())
            || gestionnaire.estJoueurSpectateur(joueur.getUUID())) {
            event.setCanceled(true);
            joueur.setHealth(joueur.getMaxHealth());
            joueur.getFoodData().setFoodLevel(20);
            joueur.getFoodData().setSaturation(5.0f);
            if (gestionnaire.estJoueurSpectateur(joueur.getUUID())) {
                gestionnaire.teleporterVersSpectateur(joueur);
            } else {
                gestionnaire.teleporterVersPlateforme(joueur);
            }
        }
    }
}

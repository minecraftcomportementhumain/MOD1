package com.example.mysubmod.sousmodes.salleattente;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GestionnaireSalleAttente {
    private static GestionnaireSalleAttente instance;
    private final Map<UUID, List<ItemStack>> inventairesStockes = new ConcurrentHashMap<>();
    private final Set<UUID> joueursSalleAttente = ConcurrentHashMap.newKeySet();
    private Timer minuterieRappel;

    private static final BlockPos CENTRE_PLATEFORME = new BlockPos(0, 100, 0);
    private static final int TAILLE_PLATEFORME = 20;
    private static final int HAUTEUR_PLATEFORME = 3;

    private GestionnaireSalleAttente() {}

    public static GestionnaireSalleAttente getInstance() {
        if (instance == null) {
            instance = new GestionnaireSalleAttente();
        }
        return instance;
    }

    public void activate(MinecraftServer serveur) {
        MonSubMod.JOURNALISEUR.info("Activation du mode salle d'attente");

        ServerLevel overworld = serveur.getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            genererPlateforme(overworld);
            teleporterTousLesJoueursSalleAttente(serveur);
            demarrerMessagesRappel(serveur);
        }
    }

    public void deactivate(MinecraftServer serveur) {
        MonSubMod.JOURNALISEUR.info("Désactivation du mode salle d'attente");

        // Fermer tous les écrans ouverts pour tous les joueurs authentifiés
        try {
            for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                joueur.closeContainer();
            }
            MonSubMod.JOURNALISEUR.info("Fermeture de tous les écrans ouverts pour les joueurs");
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors de la fermeture des écrans des joueurs", e);
        }

        arreterMessagesRappel();
        restaurerTousLesInventaires(serveur);
        nettoyerPlateforme(serveur.getLevel(ServerLevel.OVERWORLD));
        joueursSalleAttente.clear();
    }

    private void genererPlateforme(ServerLevel niveau) {
        BlockPos centre = CENTRE_PLATEFORME;

        // Créer la base de la plateforme (pierre)
        for (int x = -TAILLE_PLATEFORME/2; x <= TAILLE_PLATEFORME/2; x++) {
            for (int z = -TAILLE_PLATEFORME/2; z <= TAILLE_PLATEFORME/2; z++) {
                BlockPos pos = centre.offset(x, -1, z);
                niveau.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            }
        }

        // Créer la surface de la plateforme (briques de pierre)
        for (int x = -TAILLE_PLATEFORME/2; x <= TAILLE_PLATEFORME/2; x++) {
            for (int z = -TAILLE_PLATEFORME/2; z <= TAILLE_PLATEFORME/2; z++) {
                BlockPos pos = centre.offset(x, 0, z);
                niveau.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }

        // Créer les barrières autour de la plateforme
        for (int x = -TAILLE_PLATEFORME/2; x <= TAILLE_PLATEFORME/2; x++) {
            for (int y = 1; y <= HAUTEUR_PLATEFORME; y++) {
                // Murs nord et sud
                niveau.setBlock(centre.offset(x, y, -TAILLE_PLATEFORME/2), Blocks.BARRIER.defaultBlockState(), 3);
                niveau.setBlock(centre.offset(x, y, TAILLE_PLATEFORME/2), Blocks.BARRIER.defaultBlockState(), 3);
            }
        }

        for (int z = -TAILLE_PLATEFORME/2; z <= TAILLE_PLATEFORME/2; z++) {
            for (int y = 1; y <= HAUTEUR_PLATEFORME; y++) {
                // Murs est et ouest
                niveau.setBlock(centre.offset(-TAILLE_PLATEFORME/2, y, z), Blocks.BARRIER.defaultBlockState(), 3);
                niveau.setBlock(centre.offset(TAILLE_PLATEFORME/2, y, z), Blocks.BARRIER.defaultBlockState(), 3);
            }
        }

        MonSubMod.JOURNALISEUR.info("Plateforme de la salle d'attente générée à {}", centre);
    }

    private void nettoyerPlateforme(ServerLevel niveau) {
        if (niveau == null) return;

        BlockPos centre = CENTRE_PLATEFORME;

        // Nettoyer toute la zone de la plateforme
        for (int x = -TAILLE_PLATEFORME/2; x <= TAILLE_PLATEFORME/2; x++) {
            for (int z = -TAILLE_PLATEFORME/2; z <= TAILLE_PLATEFORME/2; z++) {
                for (int y = -2; y <= HAUTEUR_PLATEFORME + 1; y++) {
                    BlockPos pos = centre.offset(x, y, z);
                    niveau.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        MonSubMod.JOURNALISEUR.info("Plateforme de la salle d'attente nettoyée");
    }

    private void teleporterTousLesJoueursSalleAttente(MinecraftServer serveur) {
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            teleporterJoueurVersSalleAttente(joueur);
        }
    }

    public void teleporterJoueurVersSalleAttente(ServerPlayer joueur) {
        stockerInventaireJoueur(joueur);
        viderInventaireJoueur(joueur);

        ServerLevel overworld = joueur.server.getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            Vec3 positionApparition = new Vec3(CENTRE_PLATEFORME.getX() + 0.5, CENTRE_PLATEFORME.getY() + 1, CENTRE_PLATEFORME.getZ() + 0.5);
            joueur.teleportTo(overworld, positionApparition.x, positionApparition.y, positionApparition.z, 0.0f, 0.0f);
            joueursSalleAttente.add(joueur.getUUID());
            joueur.setGameMode(GameType.SURVIVAL);

            joueur.sendSystemMessage(Component.literal("§eVous avez été téléporté vers la salle d'attente"));
        }
    }

    private void stockerInventaireJoueur(ServerPlayer joueur) {
        List<ItemStack> inventaire = new ArrayList<>();

        // Stocker l'inventaire principal
        for (int i = 0; i < joueur.getInventory().getContainerSize(); i++) {
            inventaire.add(joueur.getInventory().getItem(i).copy());
        }

        inventairesStockes.put(joueur.getUUID(), inventaire);
    }

    private void viderInventaireJoueur(ServerPlayer joueur) {
        joueur.getInventory().clearContent();
    }

    private void restaurerTousLesInventaires(MinecraftServer serveur) {
        for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
            restaurerInventaireJoueur(joueur);
        }
    }

    public void restaurerInventaireJoueur(ServerPlayer joueur) {
        List<ItemStack> inventaire = inventairesStockes.remove(joueur.getUUID());
        if (inventaire != null) {
            for (int i = 0; i < Math.min(inventaire.size(), joueur.getInventory().getContainerSize()); i++) {
                joueur.getInventory().setItem(i, inventaire.get(i));
            }
            joueur.sendSystemMessage(Component.literal("§aVotre inventaire a été restauré"));
        }
        joueursSalleAttente.remove(joueur.getUUID());
    }

    private void demarrerMessagesRappel(MinecraftServer serveur) {
        minuterieRappel = new Timer();
        minuterieRappel.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Component message = Component.literal("§e[Salle d'attente] Veuillez attendre qu'un administrateur lance un jeu");
                for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                    joueur.sendSystemMessage(message);
                }
            }
        }, 60000, 60000); // Délai initial d'1 minute, puis toutes les minutes
    }

    private void arreterMessagesRappel() {
        if (minuterieRappel != null) {
            minuterieRappel.cancel();
            minuterieRappel = null;
        }
    }

    public boolean estJoueurDansSalleAttente(ServerPlayer joueur) {
        return joueursSalleAttente.contains(joueur.getUUID());
    }

    public BlockPos obtenirCentrePlateforme() {
        return CENTRE_PLATEFORME;
    }

    public int obtenirTaillePlateforme() {
        return TAILLE_PLATEFORME;
    }
}

package com.example.mysubmod.sousmodes.sousmode2.iles;

import com.example.mysubmod.MonSubMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

public class GenerateurIles {

    public void genererIle(ServerLevel niveau, BlockPos centre, TypeIle type) {
        MonSubMod.JOURNALISEUR.info("Génération île carrée {} à {}", type.obtenirNomAffichage(), centre);

        int taille = type.obtenirRayon(); // Représente la demi-taille du carré
        Random aleatoire = new Random();

        // Générer la base de l'île carrée (terre/herbe)
        for (int x = -taille; x <= taille; x++) {
            for (int z = -taille; z <= taille; z++) {
                // Ajouter un peu d'aléatoire aux bords pour un aspect naturel
                boolean estBord = (Math.abs(x) == taille || Math.abs(z) == taille);
                if (estBord && aleatoire.nextFloat() > 0.8f) {
                    continue; // Sauter quelques blocs de bord aléatoirement
                }

                BlockPos pos = centre.offset(x, -1, z);
                niveau.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);

                // Ajouter de l'herbe au-dessus
                pos = centre.offset(x, 0, z);
                niveau.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);

                // Ajouter une variation de hauteur (moins près des bords)
                int distanceDepuisBord = Math.min(Math.min(taille - Math.abs(x), taille - Math.abs(z)), taille);
                if (aleatoire.nextFloat() > 0.8f && distanceDepuisBord >= 3) {
                    pos = centre.offset(x, 1, z);
                    niveau.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);

                    if (aleatoire.nextFloat() > 0.9f && distanceDepuisBord >= 5) {
                        pos = centre.offset(x, 2, z);
                        niveau.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    }
                }
            }
        }

        // Ajouter des éléments décoratifs
        ajouterDecorations(niveau, centre, taille, aleatoire);

        // Effacer la zone au-dessus de l'île pour éviter les obstructions
        effacerDessusIle(niveau, centre, taille);

        MonSubMod.JOURNALISEUR.info("Génération île carrée terminée pour {}", type.obtenirNomAffichage());
    }

    private void ajouterDecorations(ServerLevel niveau, BlockPos centre, int taille, Random aleatoire) {
        // Ajouter quelques arbres (éviter les bords)
        int nombreArbres = taille / 5;
        for (int i = 0; i < nombreArbres; i++) {
            int x = aleatoire.nextInt((taille - 3) * 2) - (taille - 3); // Rester loin des bords
            int z = aleatoire.nextInt((taille - 3) * 2) - (taille - 3);

            BlockPos posArbre = centre.offset(x, 1, z);
            genererArbreSimple(niveau, posArbre, aleatoire);
        }

        // Ajouter des fleurs et de l'herbe haute (dans les limites du carré)
        for (int x = -taille + 2; x <= taille - 2; x++) { // Rester à l'intérieur des bords
            for (int z = -taille + 2; z <= taille - 2; z++) {
                if (aleatoire.nextFloat() > 0.95f) {
                    BlockPos posDecor = centre.offset(x, 1, z);
                    if (niveau.getBlockState(posDecor).isAir()) {
                        if (aleatoire.nextBoolean()) {
                            niveau.setBlock(posDecor, Blocks.DANDELION.defaultBlockState(), 3);
                        } else {
                            niveau.setBlock(posDecor, Blocks.TALL_GRASS.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    private void genererArbreSimple(ServerLevel niveau, BlockPos base, Random aleatoire) {
        int hauteur = 3 + aleatoire.nextInt(3);

        // Générer le tronc
        for (int y = 0; y < hauteur; y++) {
            niveau.setBlock(base.offset(0, y, 0), Blocks.OAK_LOG.defaultBlockState(), 3);
        }

        // Générer les feuilles
        BlockPos baseFeuilles = base.offset(0, hauteur - 1, 0);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 2; y++) {
                    if (Math.abs(x) + Math.abs(z) + y <= 3 && aleatoire.nextFloat() > 0.2f) {
                        BlockPos posFeuille = baseFeuilles.offset(x, y, z);
                        if (niveau.getBlockState(posFeuille).isAir()) {
                            niveau.setBlock(posFeuille, Blocks.OAK_LEAVES.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    private void effacerDessusIle(ServerLevel niveau, BlockPos centre, int taille) {
        for (int x = -taille - 5; x <= taille + 5; x++) {
            for (int z = -taille - 5; z <= taille + 5; z++) {
                for (int y = 1; y <= 20; y++) {
                    BlockPos pos = centre.offset(x, y, z);
                    BlockState etat = niveau.getBlockState(pos);
                    if (!etat.isAir() && !etat.is(Blocks.OAK_LOG) && !etat.is(Blocks.OAK_LEAVES) &&
                        !etat.is(Blocks.DANDELION) && !etat.is(Blocks.TALL_GRASS)) {
                        niveau.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    public static void effacerIle(ServerLevel niveau, BlockPos centre, TypeIle type) {
        if (niveau == null) return;

        int taille = type.obtenirRayon() + 10; // Nettoyer un peu plus pour être sûr

        for (int x = -taille; x <= taille; x++) {
            for (int z = -taille; z <= taille; z++) {
                for (int y = -5; y <= 25; y++) {
                    BlockPos pos = centre.offset(x, y, z);
                    niveau.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        MonSubMod.JOURNALISEUR.info("Zone de l'île carrée nettoyée pour {}", type.obtenirNomAffichage());
    }
}
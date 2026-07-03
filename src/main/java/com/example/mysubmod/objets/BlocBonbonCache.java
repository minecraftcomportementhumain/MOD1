package com.example.mysubmod.objets;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Bloc bonbon non-visible (minable) du Sous-mode 3.
 * Placé 1 bloc sous la surface d'un bloc Île ou Pierre : ses faces latérales et
 * inférieure sont identiques aux blocs environnants ; seule sa face supérieure
 * affiche un dessin de bonbon (normalement recouverte par le bloc du dessus).
 * Les objets bonbons sont déposés par le gestionnaire du Sous-mode 3 au minage
 * (pas de table de butin), selon la quantité configurée sur le bloc de la carte.
 */
public class BlocBonbonCache extends Block {

    public BlocBonbonCache(boolean variantePierre) {
        super(BlockBehaviour.Properties.of()
            .mapColor(variantePierre ? MapColor.STONE : MapColor.DIRT)
            .strength(variantePierre ? 1.5f : 0.5f)
            .sound(variantePierre ? SoundType.STONE : SoundType.GRAVEL)
            .noLootTable());
    }
}

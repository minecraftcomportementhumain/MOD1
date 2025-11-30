package com.example.mysubmod.objets;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode1.GestionnaireSousMode1;
import com.example.mysubmod.sousmodes.sousmode2.GestionnaireSousMode2;
import com.example.mysubmod.sousmodes.sousmode2.GestionnaireBonbonsSousMode2;
import com.example.mysubmod.sousmodes.sousmode2.GestionnaireSanteSousMode2;
import com.example.mysubmod.sousmodes.sousmode2.TypeRessource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class ItemBonbon extends Item {
    private static final float MONTANT_SOIN = 2.0f; // 1 coeur

    public ItemBonbon() {
        super(new Properties()
            .stacksTo(64) // Taille maximale de pile
            .food(new net.minecraft.world.food.FoodProperties.Builder()
                .nutrition(1)
                .saturationMod(0.1f)
                .alwaysEat()
                .build()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pileObjets = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer joueurServeur) {
            SousMode modeActuel = GestionnaireSousModes.getInstance().obtenirModeActuel();

            // Logique Sous-mode 1
            if (modeActuel == SousMode.SOUS_MODE_1 && GestionnaireSousMode1.getInstance().estPartieActive()) {
                if (GestionnaireSousMode1.getInstance().estJoueurVivant(joueurServeur.getUUID())) {
                    float vieActuelle = joueurServeur.getHealth();
                    float vieMaximale = joueurServeur.getMaxHealth();

                    if (vieActuelle + MONTANT_SOIN > vieMaximale) {
                        joueurServeur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser ce bonbon car il vous donnerait plus de vie que votre maximum"));
                        return InteractionResultHolder.fail(pileObjets);
                    }

                    soignerJoueur(joueurServeur);
                    pileObjets.shrink(1);

                    if (GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees() != null) {
                        GestionnaireSousMode1.getInstance().obtenirEnregistreurDonnees().enregistrerConsommationBonbon(joueurServeur);
                    }

                    return InteractionResultHolder.success(pileObjets);
                } else {
                    joueurServeur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser de bonbons en tant que spectateur"));
                    return InteractionResultHolder.fail(pileObjets);
                }
            }
            // Logique Sous-mode 2 avec système de spécialisation
            else if (modeActuel == SousMode.SOUS_MODE_2 && GestionnaireSousMode2.getInstance().estPartieActive()) {
                if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueurServeur.getUUID())) {
                    // Extraire le type de ressource du NBT
                    TypeRessource typeRessource = GestionnaireBonbonsSousMode2.obtenirTypeRessourceDepuisBonbon(pileObjets);

                    if (typeRessource == null) {
                        joueurServeur.sendSystemMessage(Component.literal("§cCe bonbon n'a pas de type valide"));
                        return InteractionResultHolder.fail(pileObjets);
                    }

                    float vieActuelle = joueurServeur.getHealth();
                    float vieMaximale = joueurServeur.getMaxHealth();

                    if (vieActuelle + MONTANT_SOIN > vieMaximale) {
                        joueurServeur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser ce bonbon car il vous donnerait plus de vie que votre maximum"));
                        return InteractionResultHolder.fail(pileObjets);
                    }

                    // Utiliser GestionnaireSanteSousMode2 qui gère la spécialisation et les pénalités
                    GestionnaireSanteSousMode2.getInstance().gererConsommationBonbon(joueurServeur, typeRessource);
                    pileObjets.shrink(1);

                    // Jouer le son de consommation
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, 1.0f);

                    // Enregistrer la consommation de bonbon avec le type de ressource
                    if (GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees() != null) {
                        GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees().enregistrerConsommationBonbon(joueurServeur, typeRessource);
                    }

                    return InteractionResultHolder.success(pileObjets);
                } else {
                    joueurServeur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser de bonbons en tant que spectateur"));
                    return InteractionResultHolder.fail(pileObjets);
                }
            }
            else {
                joueurServeur.sendSystemMessage(Component.literal("§cLes bonbons ne peuvent être utilisés qu'en sous-mode 1 ou 2"));
                return InteractionResultHolder.fail(pileObjets);
            }
        }

        return InteractionResultHolder.consume(pileObjets);
    }

    private void soignerJoueur(ServerPlayer joueur) {
        float vieActuelle = joueur.getHealth();
        float vieMaximale = joueur.getMaxHealth();
        float nouvelleVie = Math.min(vieMaximale, vieActuelle + MONTANT_SOIN);

        joueur.setHealth(nouvelleVie);
        joueur.sendSystemMessage(Component.literal("§a✓ Vous avez récupéré " + (MONTANT_SOIN / 2) + " cœurs !"));

        // Jouer le son de consommation
        joueur.level().playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(),
            SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, 1.0f);

        MonSubMod.JOURNALISEUR.debug("Joueur {} soigné de {} à {} points de vie", joueur.getName().getString(), vieActuelle, nouvelleVie);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        // Vérifier si c'est un bonbon Sous-mode 2 (a un type de ressource)
        TypeRessource typeRessource = GestionnaireBonbonsSousMode2.obtenirTypeRessourceDepuisBonbon(stack);

        if (typeRessource != null) {
            // Bonbon Sous-mode 2
            tooltip.add(Component.literal("§7Type: §f" + typeRessource.obtenirNomAffichage()));
            tooltip.add(Component.literal("§7Restaure §c1 cœur §7(0.75 cœur si pénalité)"));
            tooltip.add(Component.literal("§eUtilisable en sous-mode 2"));
        } else {
            // Bonbon Sous-mode 1
            tooltip.add(Component.literal("§7Restaure §c1 cœur §7de santé"));
            tooltip.add(Component.literal("§eUtilisable en sous-mode 1"));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Le rendre brillant pour indiquer qu'il est spécial
    }
}
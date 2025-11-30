package com.example.mysubmod.objets;

import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode2.GestionnaireSousMode2;
import com.example.mysubmod.sousmodes.sousmode2.GestionnaireSanteSousMode2;
import com.example.mysubmod.sousmodes.sousmode2.GestionnaireSpecialisation;
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

/**
 * Bonbon rouge pour le Sous-mode 2 (Type B)
 */
public class ItemBonbonRouge extends Item {

    public ItemBonbonRouge() {
        super(new Properties()
            .stacksTo(64)
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

            if (modeActuel == SousMode.SOUS_MODE_2 && GestionnaireSousMode2.getInstance().estPartieActive()) {
                if (GestionnaireSousMode2.getInstance().estJoueurVivant(joueurServeur.getUUID())) {
                    float vieActuelle = joueurServeur.getHealth();
                    float vieMaximale = joueurServeur.getMaxHealth();

                    // Calculer le soin réel (avec ou sans pénalité)
                    float multiplicateur = GestionnaireSpecialisation.getInstance().obtenirMultiplicateurSanteActuel(joueurServeur.getUUID());
                    float soinBase = 2.0f; // 1 cœur = 2 points de vie
                    float soinReel = soinBase * multiplicateur;

                    // Ne pas permettre de manger si le soin dépasserait le maximum de vie
                    if (vieActuelle + soinReel > vieMaximale) {
                        joueurServeur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser ce bonbon car le soin dépasserait votre maximum de vie"));
                        return InteractionResultHolder.fail(pileObjets);
                    }

                    // Utiliser GestionnaireSanteSousMode2 qui gère la spécialisation et les pénalités
                    GestionnaireSanteSousMode2.getInstance().gererConsommationBonbon(joueurServeur, TypeRessource.BONBON_ROUGE);
                    pileObjets.shrink(1);

                    // Jouer le son
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, 1.0f);

                    // Enregistrer la consommation
                    if (GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees() != null) {
                        GestionnaireSousMode2.getInstance().obtenirEnregistreurDonnees().enregistrerConsommationBonbon(joueurServeur, TypeRessource.BONBON_ROUGE);
                    }

                    return InteractionResultHolder.success(pileObjets);
                } else {
                    joueurServeur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser de bonbons en tant que spectateur"));
                    return InteractionResultHolder.fail(pileObjets);
                }
            }
        }

        return InteractionResultHolder.consume(pileObjets);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§cBonbon Rouge (Type B)"));
        tooltip.add(Component.literal("§7Restaure §c1 cœur §7(50% si pénalité)"));
        tooltip.add(Component.literal("§eUtilisable en sous-mode 2"));
    }
}

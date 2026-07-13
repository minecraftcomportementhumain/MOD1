package com.example.mysubmod.objets;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
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

            // Logique Sous-mode 3
            if (modeActuel == SousMode.SOUS_MODE_3
                && com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3.getInstance().estPartieActive()) {
                com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3 gestionnaireSM3 =
                    com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3.getInstance();
                if (gestionnaireSM3.estJoueurVivant(joueurServeur.getUUID())) {
                    float vieActuelle = joueurServeur.getHealth();
                    float vieMaximale = joueurServeur.getMaxHealth();

                    // Avec la spécialisation active, le bonbon standard (issu des blocs cachés)
                    // reste neutre : il ne change pas la spécialisation mais subit la pénalité.
                    float multiplicateur = gestionnaireSM3.obtenirConfig().specialisation
                        ? com.example.mysubmod.sousmodes.sousmode3.GestionnaireSpecialisationSousMode3
                            .getInstance().obtenirMultiplicateurSanteActuel(joueurServeur.getUUID())
                        : 1.0f;
                    // Santé rendue configurable par partie (menu N › Bonbons & minage)
                    float soinReel = gestionnaireSM3.obtenirConfig().soinBonbonStandard * multiplicateur;

                    // Par défaut, on refuse de gaspiller un bonbon qui dépasserait le maximum.
                    // Si l'admin a coché « manger même à vie pleine », on autorise la consommation
                    // (le soin reste plafonné au maximum).
                    boolean autoriserDepassement =
                        gestionnaireSM3.obtenirConfig().mangerDepasseMax;
                    if (!autoriserDepassement && vieActuelle + soinReel > vieMaximale) {
                        joueurServeur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser ce bonbon car il vous donnerait plus de vie que votre maximum"));
                        return InteractionResultHolder.fail(pileObjets);
                    }

                    if (multiplicateur < 1.0f) {
                        float nouvelleVie = Math.min(vieMaximale, vieActuelle + soinReel);
                        joueurServeur.setHealth(nouvelleVie);
                        joueurServeur.sendSystemMessage(Component.literal(
                            String.format("§e✓ Vous avez récupéré %.2f cœur(s) §c(pénalité)", soinReel / 2.0f)));
                        joueurServeur.level().playSound(null, joueurServeur.getX(), joueurServeur.getY(),
                            joueurServeur.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, 1.0f);
                    } else {
                        soignerJoueur(joueurServeur, soinReel);
                    }
                    pileObjets.shrink(1);

                    if (gestionnaireSM3.obtenirEnregistreurDonnees() != null) {
                        gestionnaireSM3.obtenirEnregistreurDonnees().enregistrerConsommationBonbon(
                            joueurServeur, "STANDARD", multiplicateur < 1.0f, soinReel);
                    }

                    return InteractionResultHolder.success(pileObjets);
                } else {
                    joueurServeur.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser de bonbons en tant que spectateur"));
                    return InteractionResultHolder.fail(pileObjets);
                }
            }
            else {
                joueurServeur.sendSystemMessage(Component.literal("§cLes bonbons ne peuvent être utilisés qu'en sous-mode 3"));
                return InteractionResultHolder.fail(pileObjets);
            }
        }

        return InteractionResultHolder.consume(pileObjets);
    }

    private void soignerJoueur(ServerPlayer joueur, float soin) {
        float vieActuelle = joueur.getHealth();
        float vieMaximale = joueur.getMaxHealth();
        float nouvelleVie = Math.min(vieMaximale, vieActuelle + soin);

        joueur.setHealth(nouvelleVie);
        // Locale fixe : le format ne doit pas dépendre de la locale de l'OS hôte
        joueur.sendSystemMessage(Component.literal(
            String.format(java.util.Locale.ROOT, "§a✓ Vous avez récupéré %.1f cœur(s) !", soin / 2.0f)));

        // Jouer le son de consommation
        joueur.level().playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(),
            SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, 1.0f);

        MonSubMod.JOURNALISEUR.debug("Joueur {} soigné de {} à {} points de vie", joueur.getName().getString(), vieActuelle, nouvelleVie);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        // Valeur définie par la config de partie (menu N), inconnue côté client : rester générique
        tooltip.add(Component.literal("§7Restaure de la santé (selon la partie)"));
        tooltip.add(Component.literal("§eUtilisable en sous-mode 3"));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Le rendre brillant pour indiquer qu'il est spécial
    }
}
package com.example.mysubmod.objets;

import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode3.TypeRessource;
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

            // Sous-mode 3 : bonbons typés (option « Spécialisation » de la config de partie)
            if (modeActuel == SousMode.SOUS_MODE_3
                && com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3.getInstance().estPartieActive()) {
                return UtilisationBonbonTypeSousMode3.utiliser(level, joueurServeur, pileObjets,
                    TypeRessource.BONBON_ROUGE);
            }
        }

        return InteractionResultHolder.consume(pileObjets);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§cBonbon Rouge (Type B)"));
        tooltip.add(Component.literal("§7Restaure §c1 cœur §7(réduit si pénalité)"));
        tooltip.add(Component.literal("§eUtilisable en sous-mode 3"));
    }
}

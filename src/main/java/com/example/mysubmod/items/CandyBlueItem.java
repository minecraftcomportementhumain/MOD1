package com.example.mysubmod.items;

import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode2.SubMode2Manager;
import com.example.mysubmod.submodes.submode2.SubMode2HealthManager;
import com.example.mysubmod.submodes.submode2.ResourceType;
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
 * Blue candy for SubMode2 (Type A)
 */
public class CandyBlueItem extends Item {

    public CandyBlueItem() {
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
        ItemStack itemStack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SubMode currentMode = SubModeManager.getInstance().getCurrentMode();

            if (currentMode == SubMode.SUB_MODE_2 && SubMode2Manager.getInstance().isGameActive()) {
                if (SubMode2Manager.getInstance().isPlayerAlive(serverPlayer.getUUID())) {
                    float currentHealth = serverPlayer.getHealth();
                    float maxHealth = serverPlayer.getMaxHealth();

                    if (currentHealth >= maxHealth) {
                        serverPlayer.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser ce bonbon car vous êtes déjà à votre maximum de vie"));
                        return InteractionResultHolder.fail(itemStack);
                    }

                    // Use SubMode2HealthManager which handles specialization and penalties
                    SubMode2HealthManager.getInstance().handleCandyConsumption(serverPlayer, ResourceType.TYPE_A);
                    itemStack.shrink(1);

                    // Play sound
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, 1.0f);

                    // Log consumption
                    if (SubMode2Manager.getInstance().getDataLogger() != null) {
                        SubMode2Manager.getInstance().getDataLogger().logCandyConsumption(serverPlayer, ResourceType.TYPE_A);
                    }

                    return InteractionResultHolder.success(itemStack);
                } else {
                    serverPlayer.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser de bonbons en tant que spectateur"));
                    return InteractionResultHolder.fail(itemStack);
                }
            }
        }

        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§9Bonbon Bleu (Type A)"));
        tooltip.add(Component.literal("§7Restaure §c1 cœur §7(50% si pénalité)"));
        tooltip.add(Component.literal("§eUtilisable en sous-mode 2"));
    }
}

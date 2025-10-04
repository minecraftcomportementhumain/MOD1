package com.example.mysubmod.items;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import com.example.mysubmod.submodes.submode1.SubMode1Manager;
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

public class CandyItem extends Item {
    private static final float HEALING_AMOUNT = 2.0f; // 1 heart

    public CandyItem() {
        super(new Properties()
            .stacksTo(64) // Maximum stack size
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
            // Only allow use in SubMode1
            if (SubModeManager.getInstance().getCurrentMode() == SubMode.SUB_MODE_1 &&
                SubMode1Manager.getInstance().isGameActive()) {

                // Only allow alive players to use candy
                if (SubMode1Manager.getInstance().isPlayerAlive(serverPlayer.getUUID())) {
                    healPlayer(serverPlayer);
                    itemStack.shrink(1);

                    // Log candy consumption
                    if (SubMode1Manager.getInstance().getDataLogger() != null) {
                        SubMode1Manager.getInstance().getDataLogger().logCandyConsumption(serverPlayer);
                    }

                    return InteractionResultHolder.success(itemStack);
                } else {
                    serverPlayer.sendSystemMessage(Component.literal("§cVous ne pouvez pas utiliser de bonbons en tant que spectateur"));
                    return InteractionResultHolder.fail(itemStack);
                }
            } else {
                serverPlayer.sendSystemMessage(Component.literal("§cLes bonbons ne peuvent être utilisés qu'en sous-mode 1"));
                return InteractionResultHolder.fail(itemStack);
            }
        }

        return InteractionResultHolder.consume(itemStack);
    }

    private void healPlayer(ServerPlayer player) {
        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float newHealth = Math.min(maxHealth, currentHealth + HEALING_AMOUNT);

        player.setHealth(newHealth);
        player.sendSystemMessage(Component.literal("§a✓ Vous avez récupéré " + (HEALING_AMOUNT / 2) + " cœurs !"));

        // Play eating sound
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, 1.0f);

        MySubMod.LOGGER.debug("Player {} healed from {} to {} health", player.getName().getString(), currentHealth, newHealth);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7Restaure §c1 cœur §7de santé"));
        tooltip.add(Component.literal("§eUtilisable uniquement en sous-mode 1"));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Make it shiny to indicate it's special
    }
}
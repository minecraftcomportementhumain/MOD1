package com.example.mysubmod.submodes.submode2;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submodeParent.HealthManager;
import com.example.mysubmod.util.PlayerFilterUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * SubMode2 health manager with specialization penalty support
 * Applies health multipliers (50% during penalty, 100% otherwise) when players consume resources
 */
public class SubMode2HealthManager extends HealthManager {

    public static SubMode2HealthManager getInstance() {
        if (instance == null) {
            instance = new SubMode2HealthManager();
        }
        return (SubMode2HealthManager) instance;
    }
    /**

     * SubMode2 health manager with specialization penalty support

     * Applies health multipliers (50% during penalty, 100% otherwise) when players consume resources

     */

    private void degradePlayerHealth(ServerPlayer player) {
        float currentHealth = player.getHealth();
        float newHealth = Math.max(0.0f, currentHealth - HEALTH_LOSS_PER_TICK);

        player.setHealth(newHealth);

        // Warn player when health is low
        if (newHealth <= 2.0f && newHealth > 0.0f) {
            player.sendSystemMessage(Component.literal("§c⚠ Santé critique ! Trouvez un bonbon !"));
        }

        // Handle player death
        if (newHealth <= 0.0f) {
            handlePlayerDeath(player);
        }

        // Log health change (basic logging without resource context)
        if (SubMode2Manager.getInstance().getDataLogger() != null) {
            // Use basic health change logging for degradation
            // Full logging with resource type happens in handleCandyConsumption
        }
    }

    /**
     * Handle candy consumption with specialization system
     * Returns the amount of health actually restored
     */
    public float handleCandyConsumption(ServerPlayer player, ResourceType resourceType) {
        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();

        // Get health multiplier from SpecializationManager (1.0 or 0.5)
        float healthMultiplier = SpecializationManager.getInstance().handleResourceCollection(player, resourceType);

        // Base health restoration: 1 heart (2.0 health points)
        float baseRestoration = 2.0f;
        float actualRestoration = baseRestoration * healthMultiplier;

        // Calculate new health (capped at max)
        float newHealth = Math.min(maxHealth, currentHealth + actualRestoration);
        player.setHealth(newHealth);

        // Check if player has penalty for display feedback
        boolean hasPenalty = SpecializationManager.getInstance().hasPenalty(player.getUUID());

        // Provide feedback to player
        if (hasPenalty) {
            long remainingMs = SpecializationManager.getInstance().getRemainingPenaltyTime(player.getUUID());
            String timeStr = SpecializationManager.formatTime(remainingMs);
            player.sendSystemMessage(Component.literal(
                String.format("§e+%.1f ❤ §c(Pénalité: 50%% - %s restant)", actualRestoration / 2.0f, timeStr)
            ));
        } else {
            player.sendSystemMessage(Component.literal(
                String.format("§a+%.1f ❤ §7(%s)", actualRestoration / 2.0f, resourceType.getDisplayName())
            ));
        }

        // Log health change with resource type context
        if (SubMode2Manager.getInstance().getDataLogger() != null) {
            SubMode2Manager.getInstance().getDataLogger().logHealthChange(
                player, currentHealth, newHealth, resourceType, hasPenalty
            );
        }

        MySubMod.LOGGER.info("Player {} consumed {} - restored {:.1f} health (multiplier: {:.1f}x)",
            player.getName().getString(), resourceType.getDisplayName(), actualRestoration, healthMultiplier);

        return actualRestoration;
    }

}

package com.example.mysubmod.submodes.submode2;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.util.PlayerFilterUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * SubMode2 health manager with specialization penalty support
 * Applies health multipliers (75% during penalty, 100% otherwise) when players consume resources
 */
public class SubMode2HealthManager {
    private static SubMode2HealthManager instance;
    private Timer healthTimer;
    private static final float HEALTH_LOSS_PER_TICK = 1.0f; // 1 heart every 10 seconds
    private static final int TICK_INTERVAL = 10000; // 10 seconds

    private SubMode2HealthManager() {}

    public static SubMode2HealthManager getInstance() {
        if (instance == null) {
            instance = new SubMode2HealthManager();
        }
        return instance;
    }

    public void startHealthDegradation(MinecraftServer server) {
        MySubMod.LOGGER.info("Starting health degradation for SubMode2");

        healthTimer = new Timer();
        healthTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                server.execute(() -> {
                    // Only degrade health if the game is active (not during island selection)
                    if (!SubMode2Manager.getInstance().isGameActive()) {
                        return;
                    }

                    // Degrade health for connected players
                    for (ServerPlayer player : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
                        if (SubMode2Manager.getInstance().isPlayerAlive(player.getUUID())) {
                            degradePlayerHealth(player);
                        }
                    }

                    // Check disconnected players and handle deaths server-side
                    checkDisconnectedPlayersHealth(server);
                });
            }
        }, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stopHealthDegradation() {
        if (healthTimer != null) {
            healthTimer.cancel();
            healthTimer = null;
            MySubMod.LOGGER.info("Stopped health degradation for SubMode2");
        }
    }

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

        // Get health multiplier from SpecializationManager (1.0 or 0.75)
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
                String.format("§e+%.2f ❤ §c(Pénalité: 75%% - %s restant)", actualRestoration / 2.0f, timeStr)
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

    /**
     * Check disconnected players' health server-side and handle deaths
     * This ensures the game ends even if disconnected players die while offline
     */
    private void checkDisconnectedPlayersHealth(MinecraftServer server) {
        SubMode2Manager manager = SubMode2Manager.getInstance();

        // Get snapshot of disconnected players
        java.util.Map<String, ?> disconnectedPlayers = manager.getDisconnectedPlayersInfo();
        long currentTime = System.currentTimeMillis();
        long gameStartTime = manager.getGameStartTime();

        if (gameStartTime <= 0) return; // Game hasn't started yet

        for (java.util.Map.Entry<String, ?> entry : disconnectedPlayers.entrySet()) {
            String playerName = entry.getKey();
            Object infoObj = entry.getValue();

            // Use reflection to access DisconnectInfo fields since it's private
            try {
                java.lang.reflect.Field uuidField = infoObj.getClass().getDeclaredField("oldUUID");
                java.lang.reflect.Field disconnectTimeField = infoObj.getClass().getDeclaredField("disconnectTime");
                java.lang.reflect.Field healthField = infoObj.getClass().getDeclaredField("healthAtDisconnect");
                java.lang.reflect.Field isDeadField = infoObj.getClass().getDeclaredField("isDead");

                uuidField.setAccessible(true);
                disconnectTimeField.setAccessible(true);
                healthField.setAccessible(true);
                isDeadField.setAccessible(true);

                UUID playerId = (UUID) uuidField.get(infoObj);
                long disconnectTime = disconnectTimeField.getLong(infoObj);
                float healthAtDisconnect = healthField.getFloat(infoObj);
                boolean isDead = isDeadField.getBoolean(infoObj);

                // Skip players already marked as dead
                if (isDead) {
                    continue;
                }

                // Only check players who are still marked as alive
                if (!manager.isPlayerAlive(playerId)) {
                    continue;
                }

                // Calculate health based on time disconnected
                long effectiveDisconnectTime = Math.max(disconnectTime, gameStartTime);
                long msFromGameStartAtDisconnect = effectiveDisconnectTime - gameStartTime;
                long msFromGameStartNow = currentTime - gameStartTime;

                long tickNumberAtDisconnect = msFromGameStartAtDisconnect / 10000;
                long tickNumberNow = msFromGameStartNow / 10000;

                int missedHealthTicks = (int) (tickNumberNow - tickNumberAtDisconnect);
                float currentHealth = healthAtDisconnect - (missedHealthTicks * HEALTH_LOSS_PER_TICK);

                // Player has died while disconnected
                if (currentHealth <= 0.0f) {
                    MySubMod.LOGGER.info("SubMode2: Disconnected player {} died server-side (health: {} -> {}, missed {} ticks)",
                        playerName, healthAtDisconnect, currentHealth, missedHealthTicks);

                    // Handle death server-side
                    manager.handleDisconnectedPlayerDeath(playerName, playerId);

                    // Broadcast death message
                    String deathMessage = "§e" + playerName + " §cest mort pendant sa déconnexion !";
                    for (ServerPlayer p : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
                        p.sendSystemMessage(Component.literal(deathMessage));
                    }

                    // Check if all players are dead
                    if (manager.getAlivePlayers().isEmpty()) {
                        MySubMod.LOGGER.info("SubMode2: All players are dead (including disconnected) - ending game");
                        server.execute(() -> {
                            for (ServerPlayer p : PlayerFilterUtil.getAuthenticatedPlayers(server)) {
                                p.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                            }
                            manager.endGame(server);
                        });
                        return; // Stop checking, game is ending
                    }
                }
            } catch (Exception e) {
                MySubMod.LOGGER.error("Error checking disconnected player health in SubMode2: {}", e.getMessage());
            }
        }
    }

    private void handlePlayerDeath(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§cVous êtes mort ! Téléportation vers la zone spectateur..."));

        // Record death time for leaderboard
        SubMode2Manager.getInstance().recordPlayerDeath(player.getUUID());

        // Teleport to spectator area
        SubMode2Manager.getInstance().teleportToSpectator(player);

        // Restore health for spectator mode
        player.setHealth(player.getMaxHealth());

        // Broadcast death message
        String deathMessage = "§e" + player.getName().getString() + " §cest mort !";
        for (ServerPlayer p : PlayerFilterUtil.getAuthenticatedPlayers(player.server)) {
            p.sendSystemMessage(Component.literal(deathMessage));
        }

        // Log death
        if (SubMode2Manager.getInstance().getDataLogger() != null) {
            SubMode2Manager.getInstance().getDataLogger().logPlayerDeath(player);
        }

        // Check if all players are dead (no alive players left)
        if (SubMode2Manager.getInstance().getAlivePlayers().isEmpty()) {
            MySubMod.LOGGER.info("SubMode2: All players are dead - ending game");
            player.server.execute(() -> {
                for (ServerPlayer p : PlayerFilterUtil.getAuthenticatedPlayers(player.server)) {
                    p.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                }
                SubMode2Manager.getInstance().endGame(player.server);
            });
        }
    }
}

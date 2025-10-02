package com.example.mysubmod.submodes.submode1;

import com.example.mysubmod.MySubMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Timer;
import java.util.TimerTask;

public class SubMode1HealthManager {
    private static SubMode1HealthManager instance;
    private Timer healthTimer;
    private static final float HEALTH_LOSS_PER_TICK = 1.0f; // 1 heart every 10 seconds
    private static final int TICK_INTERVAL = 10000; // 10 seconds

    private SubMode1HealthManager() {}

    public static SubMode1HealthManager getInstance() {
        if (instance == null) {
            instance = new SubMode1HealthManager();
        }
        return instance;
    }

    public void startHealthDegradation(MinecraftServer server) {
        MySubMod.LOGGER.info("Starting health degradation for SubMode1");

        healthTimer = new Timer();
        healthTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                server.execute(() -> {
                    // Only degrade health if the game is active (not during island selection)
                    if (!SubMode1Manager.getInstance().isGameActive()) {
                        return;
                    }

                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        if (SubMode1Manager.getInstance().isPlayerAlive(player.getUUID())) {
                            degradePlayerHealth(player);
                        }
                    }
                });
            }
        }, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stopHealthDegradation() {
        if (healthTimer != null) {
            healthTimer.cancel();
            healthTimer = null;
            MySubMod.LOGGER.info("Stopped health degradation for SubMode1");
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

        // Log health change
        if (SubMode1Manager.getInstance().getDataLogger() != null) {
            SubMode1Manager.getInstance().getDataLogger().logHealthChange(player, currentHealth, newHealth);
        }
    }

    private void handlePlayerDeath(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§cVous êtes mort ! Téléportation vers la zone spectateur..."));

        // Teleport to spectator area
        SubMode1Manager.getInstance().teleportToSpectator(player);

        // Restore health for spectator mode
        player.setHealth(player.getMaxHealth());

        // Broadcast death message
        String deathMessage = "§e" + player.getName().getString() + " §cest mort !";
        for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(deathMessage));
        }

        // Log death
        if (SubMode1Manager.getInstance().getDataLogger() != null) {
            SubMode1Manager.getInstance().getDataLogger().logPlayerDeath(player);
        }

        // Check if all players are dead (no alive players left)
        if (SubMode1Manager.getInstance().getAlivePlayers().isEmpty()) {
            MySubMod.LOGGER.info("All players are dead - ending game");
            player.server.execute(() -> {
                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    p.sendSystemMessage(Component.literal("§c§lTous les joueurs sont morts !"));
                }
                SubMode1Manager.getInstance().endGame(player.server);
            });
        }
    }
}
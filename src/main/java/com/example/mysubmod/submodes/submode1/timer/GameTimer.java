package com.example.mysubmod.submodes.submode1.timer;

import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.submode1.SubMode1Manager;
import com.example.mysubmod.submodes.submode1.network.GameTimerPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Timer;
import java.util.TimerTask;

public class GameTimer {
    private final int totalMinutes;
    private final MinecraftServer server;
    private Timer timer;
    private int secondsLeft;

    public GameTimer(int minutes, MinecraftServer server) {
        this.totalMinutes = minutes;
        this.server = server;
        this.secondsLeft = minutes * 60;
    }

    public void start() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Check if server is still valid before executing
                if (server == null || server.isStopped()) {
                    stop(); // Stop the timer if server is invalid
                    return;
                }

                server.execute(() -> {
                    secondsLeft--;

                    // Send timer update to all players - with safety check
                    try {
                        if (server != null && !server.isStopped() && server.getPlayerList() != null) {
                            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                                new GameTimerPacket(secondsLeft));
                        }
                    } catch (Exception e) {
                        // Ignore network errors during server shutdown
                    }

                    // Show important time milestones
                    if (secondsLeft == 300) { // 5 minutes left
                        broadcastTimeWarning("§e5 minutes restantes !");
                    } else if (secondsLeft == 120) { // 2 minutes left
                        broadcastTimeWarning("§c2 minutes restantes !");
                    } else if (secondsLeft == 60) { // 1 minute left
                        broadcastTimeWarning("§c§l1 MINUTE RESTANTE !");
                    } else if (secondsLeft == 30) { // 30 seconds left
                        broadcastTimeWarning("§c§l30 SECONDES !");
                    } else if (secondsLeft <= 10 && secondsLeft > 0) { // Final countdown
                        broadcastTimeWarning("§c§l" + secondsLeft + " !");
                    }

                    if (secondsLeft <= 0) {
                        teleportAllToSpectator();
                        endGame();
                    }
                });
            }
        }, 1000, 1000); // Start after 1 second, then every second
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void broadcastTimeWarning(String message) {
        try {
            if (server != null && !server.isStopped() && server.getPlayerList() != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.sendSystemMessage(Component.literal(message));
                }
            }
        } catch (Exception e) {
            // Ignore errors during server shutdown
        }
    }

    private void teleportAllToSpectator() {
        try {
            if (server != null && !server.isStopped() && server.getPlayerList() != null) {
                // Teleport all alive players to spectator platform
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (SubMode1Manager.getInstance().isPlayerAlive(player.getUUID())) {
                        SubMode1Manager.getInstance().teleportToSpectator(player);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors during server shutdown
        }
    }

    private void endGame() {
        stop();
        SubMode1Manager.getInstance().endGame(server);
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    public String getFormattedTime() {
        int minutes = secondsLeft / 60;
        int seconds = secondsLeft % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
package com.example.mysubmod.submodes.submode2.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientGameTimer {
    private static int secondsLeft = 0;
    private static boolean active = false;
    private static boolean gameEnded = false; // Track if game has ended

    public static void updateTimer(int seconds) {
        if (seconds < 0) {
            // Special case: negative value means deactivate
            deactivate();
        } else {
            secondsLeft = seconds;
            active = seconds > 0;

            // If timer reaches 0 during active game, mark game as ended
            if (active && seconds == 0) {
                gameEnded = true;
            }
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static String getFormattedTime() {
        if (!active) return "";

        int minutes = secondsLeft / 60;
        int seconds = secondsLeft % 60;
        return String.format("§e%02d:%02d", minutes, seconds);
    }

    public static int getSecondsLeft() {
        return secondsLeft;
    }

    public static void deactivate() {
        active = false;
        secondsLeft = 0;
        gameEnded = false; // Reset when deactivating (changing mode)
    }

    public static boolean hasGameEnded() {
        return gameEnded;
    }

    public static void markGameAsEnded() {
        gameEnded = true;
    }
}

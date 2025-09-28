package com.example.mysubmod.submodes.submode1.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientGameTimer {
    private static int secondsLeft = 0;
    private static boolean active = false;

    public static void updateTimer(int seconds) {
        secondsLeft = seconds;
        active = seconds > 0;
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
    }
}
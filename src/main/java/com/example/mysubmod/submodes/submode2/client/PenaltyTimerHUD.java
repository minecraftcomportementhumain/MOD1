package com.example.mysubmod.submodes.submode2.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.UUID;

/**
 * HUD display for specialization penalty timer
 * Shows in top-left corner when player has active penalty
 */
@OnlyIn(Dist.CLIENT)
public class PenaltyTimerHUD {
    private static boolean active = false;
    private static long penaltyEndTime = 0; // Temps de fin de la pénalité en ms (epoch)
    private static UUID localPlayerId = null;

    public static void activate(UUID playerId, long endTime) {
        active = true;
        localPlayerId = playerId;
        penaltyEndTime = endTime;
    }

    public static void deactivate() {
        active = false;
        penaltyEndTime = 0;
        localPlayerId = null;
    }

    public static boolean isActive() {
        return active && localPlayerId != null;
    }

    public static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (!isActive()) return;

        // Calculate remaining time
        long now = System.currentTimeMillis();
        long penaltyRemainingMs = penaltyEndTime - now;

        // If penalty expired, deactivate
        if (penaltyRemainingMs <= 0) {
            deactivate();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Position: top-left corner, non-invasive
        int x = 10;
        int y = 10;

        // Format time as "M:SS"
        String timeStr = formatTime(penaltyRemainingMs);

        // Background for better readability
        String displayText = "§cPénalité: " + timeStr;
        int textWidth = font.width(displayText);
        int bgX = x - 3;
        int bgY = y - 3;
        int bgWidth = textWidth + 6;
        int bgHeight = font.lineHeight + 6;
        guiGraphics.fill(bgX, bgY, bgX + bgWidth, bgY + bgHeight, 0x80000000); // Semi-transparent black

        // Render penalty text
        guiGraphics.drawString(font, displayText, x, y, 0xFF5555); // Red color
    }

    /**
     * Format time in milliseconds as "M:SS"
     */
    private static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }

    /**
     * Update the local player ID (called when game starts)
     */
    public static void setLocalPlayer(UUID playerId) {
        localPlayerId = playerId;
    }

    public static UUID getLocalPlayerId() {
        return localPlayerId;
    }
}

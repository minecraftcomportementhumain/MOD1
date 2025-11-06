package com.example.mysubmod.submodes.submode2.client;

import com.example.mysubmod.submodes.submode2.SpecializationManager;
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
    private static long penaltyRemainingMs = 0;
    private static UUID localPlayerId = null;

    public static void activate(UUID playerId) {
        active = true;
        localPlayerId = playerId;
    }

    public static void deactivate() {
        active = false;
        penaltyRemainingMs = 0;
        localPlayerId = null;
    }

    public static boolean isActive() {
        return active && localPlayerId != null;
    }

    public static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (!isActive()) return;

        // Update penalty time from SpecializationManager
        penaltyRemainingMs = SpecializationManager.getInstance().getRemainingPenaltyTime(localPlayerId);
        System.out.println(penaltyRemainingMs);

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
        String timeStr = SpecializationManager.formatTime(penaltyRemainingMs);

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
     * Update the local player ID (called when game starts)
     */
    public static void setLocalPlayer(UUID playerId) {
        localPlayerId = playerId;
    }

    public static UUID getLocalPlayerId() {
        return localPlayerId;
    }
}

package com.example.mysubmod.submodes.submode1.client;

import com.example.mysubmod.submodes.submode1.islands.IslandType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class CandyCountHUD {
    private static final Map<IslandType, Integer> candyCounts = new HashMap<>();
    private static boolean active = false;

    public static void updateCandyCounts(Map<IslandType, Integer> counts) {
        candyCounts.clear();
        if (counts.isEmpty()) {
            active = false; // Deactivate if empty (game ended)
        } else {
            candyCounts.putAll(counts);
            active = true;
        }
    }

    public static void deactivate() {
        active = false;
        candyCounts.clear();
    }

    public static boolean isActive() {
        return active;
    }

    public static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Position: top-right corner, below the timer, non-invasive
        int x = screenWidth - 150;
        int y = 35; // Moved down to avoid overlapping with timer (was 10)
        int lineHeight = 12;

        // Background for better readability
        int bgX = x - 5;
        int bgY = y - 3;
        int bgWidth = 145;
        int bgHeight = 60;
        guiGraphics.fill(bgX, bgY, bgX + bgWidth, bgY + bgHeight, 0x80000000); // Semi-transparent black

        // Title
        guiGraphics.drawString(font, "§6Bonbons disponibles:", x, y, 0xFFFFFF);
        y += lineHeight;

        // Display counts per island
        renderIslandCount(guiGraphics, font, x, y, IslandType.SMALL, candyCounts.getOrDefault(IslandType.SMALL, 0));
        y += lineHeight;

        renderIslandCount(guiGraphics, font, x, y, IslandType.MEDIUM, candyCounts.getOrDefault(IslandType.MEDIUM, 0));
        y += lineHeight;

        renderIslandCount(guiGraphics, font, x, y, IslandType.LARGE, candyCounts.getOrDefault(IslandType.LARGE, 0));
        y += lineHeight;

        renderIslandCount(guiGraphics, font, x, y, IslandType.EXTRA_LARGE, candyCounts.getOrDefault(IslandType.EXTRA_LARGE, 0));
    }

    private static void renderIslandCount(GuiGraphics guiGraphics, Font font, int x, int y, IslandType island, int count) {
        String text = getIslandShortName(island) + ": " + count;
        int color = getIslandColor(island);
        guiGraphics.drawString(font, text, x + 5, y, color);
    }

    private static String getIslandShortName(IslandType island) {
        return switch (island) {
            case SMALL -> "§fPetite";
            case MEDIUM -> "§fMoyenne";
            case LARGE -> "§fGrande";
            case EXTRA_LARGE -> "§fTrès Grande";
        };
    }

    private static int getIslandColor(IslandType island) {
        return switch (island) {
            case SMALL -> 0xFFFFFF;      // White
            case MEDIUM -> 0x55FF55;     // Green
            case LARGE -> 0x5555FF;      // Blue
            case EXTRA_LARGE -> 0xFFAA00; // Orange
        };
    }
}

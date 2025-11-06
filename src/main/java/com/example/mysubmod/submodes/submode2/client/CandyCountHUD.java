package com.example.mysubmod.submodes.submode2.client;

import com.example.mysubmod.submodes.submode2.ResourceType;
import com.example.mysubmod.submodes.submode2.islands.IslandType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * HUD for SubMode2 showing candy counts by BOTH island AND resource type
 */
@OnlyIn(Dist.CLIENT)
public class CandyCountHUD {
    private static final Map<IslandType, Map<ResourceType, Integer>> candyCounts = new HashMap<>();
    private static boolean active = false;

    public static void updateCandyCounts(Map<IslandType, Map<ResourceType, Integer>> counts) {
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

        // Position: right corner, compact layout
        int x = screenWidth - 95; // Moved closer to the right edge
        int y = 35; // Below the timer
        int lineHeight = 10; // Very compact

        // Title
        guiGraphics.drawString(font, "§6Bonbons:", x, y, 0xFFFFFF);
        y += lineHeight;

        // Display counts per island with type breakdown
        y = renderIslandWithTypes(guiGraphics, font, x, y, IslandType.SMALL, lineHeight);
        y = renderIslandWithTypes(guiGraphics, font, x, y, IslandType.MEDIUM, lineHeight);
        y = renderIslandWithTypes(guiGraphics, font, x, y, IslandType.LARGE, lineHeight);
        renderIslandWithTypes(guiGraphics, font, x, y, IslandType.EXTRA_LARGE, lineHeight);
    }

    private static int renderIslandWithTypes(GuiGraphics guiGraphics, Font font, int x, int y, IslandType island, int lineHeight) {
        Map<ResourceType, Integer> typeCounts = candyCounts.getOrDefault(island, new HashMap<>());

        int typeA = typeCounts.getOrDefault(ResourceType.TYPE_A, 0);
        int typeB = typeCounts.getOrDefault(ResourceType.TYPE_B, 0);
        int total = typeA + typeB;

        // Island name with total
        String islandText = getIslandShortName(island) + ": " + total;
        int islandColor = getIslandColor(island);
        guiGraphics.drawString(font, islandText, x, y, islandColor);
        y += lineHeight;

        // Type breakdown (minimal indent)
        String typeAText = "§9Bleu: " + typeA;
        guiGraphics.drawString(font, typeAText, x + 3, y, ResourceType.TYPE_A.getColor());
        y += lineHeight;

        String typeBText = "§cRouge: " + typeB;
        guiGraphics.drawString(font, typeBText, x + 3, y, ResourceType.TYPE_B.getColor());
        y += lineHeight;

        return y;
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

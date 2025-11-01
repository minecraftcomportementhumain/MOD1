package com.example.mysubmod.submodes.submodeParent.islands;

public enum IslandType {
    SMALL("Petite Île (60x60)", 30),
    MEDIUM("Île Moyenne (90x90)", 45),
    LARGE("Grande Île (120x120)", 60),
    EXTRA_LARGE("Très Grande Île (150x150)", 75);

    private final String displayName;
    private final int radius;

    IslandType(String displayName, int radius) {
        this.displayName = displayName;
        this.radius = radius;
    }

    public String getIslandShortName() {
        return switch (this) {
            case SMALL -> "§fPetite";
            case MEDIUM -> "§fMoyenne";
            case LARGE -> "§fGrande";
            case EXTRA_LARGE -> "§fTrès Grande";
        };
    }

    public int getIslandColor() {
        return switch (this) {
            case SMALL -> 0xFFFFFF;      // White
            case MEDIUM -> 0x55FF55;     // Green
            case LARGE -> 0x5555FF;      // Blue
            case EXTRA_LARGE -> 0xFFAA00; // Orange
        };
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRadius() {
        return radius;
    }
}
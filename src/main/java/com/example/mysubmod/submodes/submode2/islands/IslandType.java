package com.example.mysubmod.submodes.submode2.islands;

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

    public String getDisplayName() {
        return displayName;
    }

    public int getRadius() {
        return radius;
    }
}
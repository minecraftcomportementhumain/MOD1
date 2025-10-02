package com.example.mysubmod.submodes.submode1.islands;

public enum IslandType {
    SMALL("Petite Île (60x60)", 30, 1),
    MEDIUM("Île Moyenne (90x90)", 45, 2),
    LARGE("Grande Île (120x120)", 60, 3),
    EXTRA_LARGE("Très Grande Île (150x150)", 75, 4);

    private final String displayName;
    private final int radius;
    private final int spawnPointCount;

    IslandType(String displayName, int radius, int spawnPointCount) {
        this.displayName = displayName;
        this.radius = radius;
        this.spawnPointCount = spawnPointCount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRadius() {
        return radius;
    }

    public int getSpawnPointCount() {
        return spawnPointCount;
    }
}
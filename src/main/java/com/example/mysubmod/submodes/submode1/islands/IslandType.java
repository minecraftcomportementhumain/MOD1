package com.example.mysubmod.submodes.submode1.islands;

public enum IslandType {
    SMALL("Petite Île", 15),
    MEDIUM("Île Moyenne", 25),
    LARGE("Grande Île", 35);

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
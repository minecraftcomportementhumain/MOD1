package com.example.mysubmod.submodes.submode2;

/**
 * Types de ressources disponibles dans le Sous-mode 2
 * Chaque type a un nom d'affichage et une couleur pour l'interface
 */
public enum ResourceType {
    TYPE_A("Bonbon Bleu", 0x5555FF),  // Bleu
    TYPE_B("Bonbon Rouge", 0xFF5555);  // Rouge

    private final String displayName;
    private final int color;

    ResourceType(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        return color;
    }

    /**
     * Parse resource type from string (case insensitive)
     */
    public static ResourceType fromString(String str) {
        if (str == null) return null;
        String upper = str.toUpperCase();
        if (upper.equals("A") || upper.equals("TYPE_A")) {
            return TYPE_A;
        } else if (upper.equals("B") || upper.equals("TYPE_B")) {
            return TYPE_B;
        }
        return null;
    }
}

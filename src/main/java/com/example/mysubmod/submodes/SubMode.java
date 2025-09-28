package com.example.mysubmod.submodes;

public enum SubMode {
    WAITING_ROOM("Salle d'attente"),
    SUB_MODE_1("Sous-mode 1"),
    SUB_MODE_2("Sous-mode 2");

    private final String displayName;

    SubMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
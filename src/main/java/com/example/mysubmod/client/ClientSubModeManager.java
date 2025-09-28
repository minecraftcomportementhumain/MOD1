package com.example.mysubmod.client;

import com.example.mysubmod.submodes.SubMode;

public class ClientSubModeManager {
    private static SubMode currentMode = SubMode.WAITING_ROOM;
    private static boolean isAdmin = false;

    public static void setCurrentMode(SubMode mode) {
        currentMode = mode;
    }

    public static SubMode getCurrentMode() {
        return currentMode;
    }

    public static void setIsAdmin(boolean admin) {
        isAdmin = admin;
    }

    public static boolean isAdmin() {
        return isAdmin;
    }
}
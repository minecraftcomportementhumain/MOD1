package com.example.mysubmod.client;

import com.example.mysubmod.submodes.SubMode;

public class ClientSubModeManager {
    private static SubMode currentMode = SubMode.WAITING_ROOM;
    private static boolean isAdmin = false;

    public static void setCurrentMode(SubMode mode) {
        SubMode oldMode = currentMode;
        currentMode = mode;

        // Clean up HUD elements when leaving SubMode2
        if (oldMode == SubMode.SUB_MODE_2 && mode != SubMode.SUB_MODE_2) {
            // Deactivate SubMode2 timer
            com.example.mysubmod.submodes.submode2.client.ClientGameTimer.deactivate();

            // Deactivate SubMode2 candy count HUD
            com.example.mysubmod.submodes.submode2.client.CandyCountHUD.deactivate();

            // Deactivate SubMode2 penalty timer HUD
            com.example.mysubmod.submodes.submode2.client.PenaltyTimerHUD.deactivate();
        }

        // Clean up HUD elements when leaving SubMode1
        if (oldMode == SubMode.SUB_MODE_1 && mode != SubMode.SUB_MODE_1) {
            // Deactivate SubMode1 timer
            com.example.mysubmod.submodes.submode1.client.ClientGameTimer.deactivate();

            // Deactivate SubMode1 candy count HUD
            com.example.mysubmod.submodes.submode1.client.CandyCountHUD.deactivate();
        }
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
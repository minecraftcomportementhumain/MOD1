package com.example.mysubmod.submodes.submode1;

import com.example.mysubmod.submodes.submode1.data.SubMode1DataLogger;
import com.example.mysubmod.submodes.submode1.data.SubMode1SpawnFileManager;
import com.example.mysubmod.submodes.submode2.SubMode2CandyManager;
import com.example.mysubmod.submodes.submode2.data.SubMode2DataLogger;
import com.example.mysubmod.submodes.submodeParent.CandyManager;
import com.example.mysubmod.submodes.submodeParent.HealthManager;
import com.example.mysubmod.submodes.submodeParent.SubModeParentManager;
import com.example.mysubmod.submodes.submodeParent.data.SpawnFileManager;


public class SubMode1Manager extends SubModeParentManager {
    //Specifics changes to SubMode1
    public static void initialize(){
        setInstance(new SubMode1Manager());
        instance.setSpawnFileManager(new SpawnFileManager());
        instance.setHealthManager(new HealthManager());
        instance.setCandyManager(new CandyManager());
        instance.setDataLogger(new SubMode1DataLogger());
    }
}
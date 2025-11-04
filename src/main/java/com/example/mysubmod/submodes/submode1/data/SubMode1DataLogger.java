package com.example.mysubmod.submodes.submode1.data;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submode2.ResourceType;
import com.example.mysubmod.submodes.submodeParent.data.DataLogger;
import com.example.mysubmod.submodes.submodeParent.islands.IslandType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CSV-based data logger for SubMode1
 * Format: timestamp,player,event_type,x,y,z,health,additional_data
 */
public class SubMode1DataLogger extends DataLogger {
    //If anything that exclusive to this submode, add in here
}

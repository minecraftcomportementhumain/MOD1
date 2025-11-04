package com.example.mysubmod.submodes.submode1;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.submodes.submodeParent.EventHandler;

import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MySubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SubMode1EventHandler extends EventHandler {

    //If anything that exclusive to this submode, add in here
}
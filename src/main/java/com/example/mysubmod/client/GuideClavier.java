package com.example.mysubmod.client;

import com.example.mysubmod.MonSubMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Touche configurable qui ouvre le guide du jeu ({@link com.example.mysubmod.client.gui.EcranGuide}).
 * Enregistrée dans Options › Commandes (catégorie du mod) ; touche H par défaut, rebindable.
 */
@Mod.EventBusSubscriber(modid = MonSubMod.ID_MOD, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class GuideClavier {

    public static final KeyMapping TOUCHE_GUIDE = new KeyMapping(
        "key.mysubmod.guide",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_H,
        "key.categories.mysubmod");

    private GuideClavier() {
    }

    @SubscribeEvent
    public static void enregistrer(RegisterKeyMappingsEvent event) {
        event.register(TOUCHE_GUIDE);
    }
}

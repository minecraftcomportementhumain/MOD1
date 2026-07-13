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
 * Touche configurable qui affiche/masque le panneau du HUD des parcelles
 * ({@link com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3}).
 * Enregistrée dans Options › Commandes (catégorie du mod) ; touche J par défaut, rebindable.
 * Le panneau est affiché par défaut ; la flèche de navigation (ciblée volontairement
 * via [N]) reste visible même panneau masqué.
 */
@Mod.EventBusSubscriber(modid = MonSubMod.ID_MOD, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class HUDClavier {

    public static final KeyMapping TOUCHE_BASCULE_HUD_PARCELLES = new KeyMapping(
        "key.mysubmod.bascule_hud_parcelles",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_J,
        "key.categories.mysubmod");

    private HUDClavier() {
    }

    @SubscribeEvent
    public static void enregistrer(RegisterKeyMappingsEvent event) {
        event.register(TOUCHE_BASCULE_HUD_PARCELLES);
    }
}

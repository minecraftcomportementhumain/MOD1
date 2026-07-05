package com.example.mysubmod.miseajour;

import com.example.mysubmod.MonSubMod;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Câble le système de mise à jour au cycle de vie du serveur.
 */
@Mod.EventBusSubscriber(modid = MonSubMod.ID_MOD, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GestionnaireEvenementsMiseAJour {

    @SubscribeEvent
    public static void onServeurDemarre(ServerStartedEvent event) {
        MonSubMod.JOURNALISEUR.info("[MonSubMod] === Build de test #1 charge (verification mise a jour auto) ===");
        GestionnaireMiseAJour.getInstance().auDemarrage(event.getServer());
    }

    @SubscribeEvent
    public static void onServeurArrete(ServerStoppingEvent event) {
        GestionnaireMiseAJour.getInstance().arreter();
    }
}

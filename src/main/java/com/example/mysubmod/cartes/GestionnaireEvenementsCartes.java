package com.example.mysubmod.cartes;

import com.example.mysubmod.MonSubMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Événements serveur du système de cartes : libère le verrou de l'outil de
 * création si l'admin qui l'utilisait se déconnecte.
 */
@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GestionnaireEvenementsCartes {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer joueur) {
            GestionnaireCartes.getInstance().libererEditeurSiDeconnecte(joueur.getUUID());
        }
    }
}

package com.example.mysubmod.cartes;

import com.example.mysubmod.MonSubMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Événements serveur du système de cartes : libère le verrou de l'outil de
 * création si l'admin qui l'utilisait se déconnecte, et garde l'éditeur actif
 * face au minuteur d'inactivité vanilla.
 */
@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GestionnaireEvenementsCartes {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer joueur) {
            GestionnaireCartes.getInstance().libererEditeurSiDeconnecte(joueur.getUUID());
        }
    }

    /**
     * Empêche le kick pour inactivité pendant l'édition d'une carte. Le minuteur
     * vanilla (player-idle-timeout) ne compte que le MOUVEMENT du personnage ; or
     * l'admin qui dessine ne bouge pas. Tant que l'éditeur est ouvert, on réarme son
     * horloge d'activité à chaque tick — il n'est donc kické que s'il ferme l'éditeur
     * ET reste immobile.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        UUID editeur = GestionnaireCartes.getInstance().obtenirEditeurVerrouilleePar();
        if (editeur == null) {
            return;
        }
        ServerPlayer joueur = event.getServer().getPlayerList().getPlayer(editeur);
        if (joueur != null) {
            joueur.resetLastActionTime();
        }
    }
}

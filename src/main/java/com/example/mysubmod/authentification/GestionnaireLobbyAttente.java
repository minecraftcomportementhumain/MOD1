package com.example.mysubmod.authentification;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Bloque toutes les actions de joueur dans le parking lobby
 */
@Mod.EventBusSubscriber
public class GestionnaireLobbyAttente {

    /**
     * Bloque le mouvement
     */
    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer joueur) {
            if (GestionnaireSalleAttente.getInstance().estDansLobbyStationnement(joueur.getUUID())) {
                // Gèle la position
                joueur.setDeltaMovement(0, joueur.getDeltaMovement().y, 0);

                // Réinitialise la rotation de la tête pour empêcher de regarder autour
                joueur.setXRot(0);
                joueur.setYRot(0);
            }
        }
    }

    /**
     * Bloque le chat
     */
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer joueur = event.getPlayer();
        if (GestionnaireSalleAttente.getInstance().estDansLobbyStationnement(joueur.getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * Bloque les interactions (clic droit)
     */
    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent event) {
        Player joueur = event.getEntity();
        if (joueur instanceof ServerPlayer serverPlayer) {
            if (GestionnaireSalleAttente.getInstance().estDansLobbyStationnement(serverPlayer.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Bloque les attaques
     */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer joueur) {
            if (GestionnaireSalleAttente.getInstance().estDansLobbyStationnement(joueur.getUUID())) {
                event.setCanceled(true);
            }
        }
    }
}

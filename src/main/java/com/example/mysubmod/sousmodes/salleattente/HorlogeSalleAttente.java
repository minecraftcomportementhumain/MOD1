package com.example.mysubmod.sousmodes.salleattente;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HorlogeSalleAttente {
    private static int compteurTicks = 0;
    private static final int INTERVALLE_VERIFICATION = 20; // Vérifier chaque seconde (20 ticks)

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (GestionnaireSousModes.getInstance().obtenirModeActuel() != SousMode.SALLE_ATTENTE) {
            return;
        }

        // Maintenir la lumière du jour permanente en salle d'attente
        net.minecraft.server.level.ServerLevel overworld = event.getServer().getLevel(net.minecraft.server.level.ServerLevel.OVERWORLD);
        if (overworld != null) {
            long tempsActuel = overworld.getDayTime() % 24000;
            if (tempsActuel > 12000) { // Si c'est la nuit (après 12000 ticks)
                overworld.setDayTime(6000); // Réinitialiser à midi
            }

            // Désactiver les orages et la pluie - garder le temps clair en permanence
            if (overworld.isRaining() || overworld.isThundering()) {
                overworld.setWeatherParameters(6000, 0, false, false);
            }
        }

        compteurTicks++;
        if (compteurTicks >= INTERVALLE_VERIFICATION) {
            compteurTicks = 0;

            // Vérifier tous les joueurs pour les violations de limites
            for (ServerPlayer joueur : event.getServer().getPlayerList().getPlayers()) {
                GestionnaireEvenementsSalleAttente.verifierLimitesJoueur(joueur);
            }
        }
    }
}

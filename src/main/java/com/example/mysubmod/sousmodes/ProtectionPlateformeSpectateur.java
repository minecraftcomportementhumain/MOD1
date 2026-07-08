package com.example.mysubmod.sousmodes;

import com.example.mysubmod.MonSubMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pare-feu inconditionnel protégeant la plateforme spectateur (centrée sur 0, 150, 0),
 * commune à tous les sous-modes. Quel que soit l'état (génération de carte, phase
 * d'attente, partie en cours, transition entre sous-modes) et quel que soit le joueur
 * (admin inclus), aucun bloc de la plateforme ne peut être cassé ni placé par un joueur.
 *
 * <p>Indépendant des drapeaux d'état des gestionnaires : ferme toute fenêtre où la
 * protection par état pourrait ne pas encore/plus s'appliquer (notamment pendant le
 * chargement de la carte, avant que la partie ne démarre). Les modifications
 * programmatiques (génération/effacement via setBlock) ne passent pas par ces
 * événements et ne sont donc pas affectées.</p>
 */
@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ProtectionPlateformeSpectateur {
    // Plateforme : sol de verre à Y150, murs barrier Y151-153, sur x/z ∈ [-11, 11].
    // Boîte de protection légèrement élargie pour couvrir le tout sans jamais gêner le jeu
    // (les joueurs sont confinés dans la cage Y84-116 pendant la partie, jamais à Y≥148).
    private static final int RAYON = 13;
    private static final int Y_MIN = 148;
    private static final int Y_MAX = 156;

    private static boolean sousModeActif() {
        return GestionnaireSousModes.getInstance().obtenirModeActuel() == SousMode.SOUS_MODE_3;
    }

    private static boolean surPlateforme(BlockPos pos) {
        return pos.getY() >= Y_MIN && pos.getY() <= Y_MAX
            && Math.abs(pos.getX()) <= RAYON && Math.abs(pos.getZ()) <= RAYON;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCasse(BlockEvent.BreakEvent event) {
        if (!sousModeActif() || !surPlateforme(event.getPos())) {
            return;
        }
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer joueur) {
            MonSubMod.JOURNALISEUR.info("Protection plateforme spectateur : casse annulée pour {} à {} (mode {})",
                joueur.getName().getString(), event.getPos(),
                GestionnaireSousModes.getInstance().obtenirModeActuel());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPose(BlockEvent.EntityPlaceEvent event) {
        if (!sousModeActif() || !surPlateforme(event.getPos())) {
            return;
        }
        event.setCanceled(true);
    }
}

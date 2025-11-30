package com.example.mysubmod.client;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.client.gui.EcranControleSousMode;
import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.sousmode1.reseau.GestionnairePaquetsClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class GestionnaireEvenementsClient {

    private static boolean suggestionsAutomatiquesDésactivées = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // Désactiver les suggestions de commandes et le tutoriel une fois lorsque le jeu est chargé
        if (!suggestionsAutomatiquesDésactivées && mc.options != null) {
            mc.options.autoSuggestions().set(false);

            // Forcer le tutoriel à NONE pour désactiver tous les conseils
            if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
                mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                mc.options.save();
                MonSubMod.JOURNALISEUR.info("Tutoriel forcé à NONE (désactivé)");
            }

            suggestionsAutomatiquesDésactivées = true;
            MonSubMod.JOURNALISEUR.info("Suggestions de commandes et tutoriel désactivés automatiquement");
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();

        // Touche M - Ouvrir l'écran de contrôle des sous-modes
        if (event.getKey() == GLFW.GLFW_KEY_M && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.screen == null && mc.player != null) {
                // Demander l'écran au serveur avec le nombre de joueurs
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.sendToServer(
                    new com.example.mysubmod.reseau.PaquetDemandeEcranControleSousMode()
                );
            }
        }

        // Touche N - Ouvrir l'écran de sélection de fichier de bonbons (Sous-mode 1 et Sous-mode 2)
        if (event.getKey() == GLFW.GLFW_KEY_N && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.screen == null && mc.player != null) {
                SousMode modeActuel = GestionnaireSubModeClient.obtenirModeActuel();

                if (modeActuel == SousMode.SOUS_MODE_1) {
                    // Vérifier si le jeu est terminé
                    if (com.example.mysubmod.sousmodes.sousmode1.client.MinuterieJeuClient.partieEstTerminee()) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cLe menu de sélection de fichier est désactivé après la fin de la partie"));
                    } else {
                        GestionnairePaquetsClient.ouvrirEcranSelectionFichierBonbons();
                    }
                } else if (modeActuel == SousMode.SOUS_MODE_2) {
                    // Vérifier si le jeu est terminé
                    if (com.example.mysubmod.sousmodes.sousmode2.client.MinuterieJeuClient.partieEstTerminee()) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cLe menu de sélection de fichier est désactivé après la fin de la partie"));
                    } else {
                        com.example.mysubmod.sousmodes.sousmode2.reseau.GestionnairePaquetsClient.ouvrirEcranSelectionFichierBonbons();
                    }
                }
            }
        }
    }
}

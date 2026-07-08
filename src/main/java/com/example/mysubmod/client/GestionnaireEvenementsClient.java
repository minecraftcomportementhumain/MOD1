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
                // Carte en cours de génération (SM1/2/3) : N bloqué (ni lancement, ni sélection
                // de fichier). Le HUD des zones n'est pas encore actif, sinon SM1/SM2 ouvriraient
                // par erreur l'écran de sélection de fichier d'apparition.
                if (com.example.mysubmod.cartes.client.ChargementCarteClient.estActif()) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§cLa carte est en cours de génération, veuillez patienter..."));
                    return;
                }

                SousMode modeActuel = GestionnaireSubModeClient.obtenirModeActuel();

                boolean partieSurCarte = com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3.estActif();

                if (modeActuel == SousMode.SOUS_MODE_1) {
                    if (partieSurCarte) {
                        // Partie sur carte : pas de sélection de fichier de spawn.
                        // Admins : bouton de lancement ; joueurs : ciblage de zone (flèche).
                        gererToucheNPartieSurCarte(mc, SousMode.SOUS_MODE_1,
                            com.example.mysubmod.sousmodes.sousmode1.client.MinuterieJeuClient.estActif(),
                            com.example.mysubmod.sousmodes.sousmode1.client.MinuterieJeuClient.partieEstTerminee());
                    } else if (com.example.mysubmod.sousmodes.sousmode1.client.MinuterieJeuClient.partieEstTerminee()) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cLe menu de sélection de fichier est désactivé après la fin de la partie"));
                    } else {
                        GestionnairePaquetsClient.ouvrirEcranSelectionFichierBonbons();
                    }
                } else if (modeActuel == SousMode.SOUS_MODE_2) {
                    if (partieSurCarte) {
                        gererToucheNPartieSurCarte(mc, SousMode.SOUS_MODE_2,
                            com.example.mysubmod.sousmodes.sousmode2.client.MinuterieJeuClient.estActif(),
                            com.example.mysubmod.sousmodes.sousmode2.client.MinuterieJeuClient.partieEstTerminee());
                    } else if (com.example.mysubmod.sousmodes.sousmode2.client.MinuterieJeuClient.partieEstTerminee()) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cLe menu de sélection de fichier est désactivé après la fin de la partie"));
                    } else {
                        com.example.mysubmod.sousmodes.sousmode2.reseau.GestionnairePaquetsClient.ouvrirEcranSelectionFichierBonbons();
                    }
                } else if (modeActuel == SousMode.SOUS_MODE_3) {
                    // Sous-mode 3 : pas de sélection de fichier de spawn dans le menu N.
                    // Admins : uniquement le bouton de lancement de partie.
                    // Joueurs : sélection d'une zone pour la flèche de navigation.
                    gererToucheNPartieSurCarte(mc, SousMode.SOUS_MODE_3,
                        com.example.mysubmod.sousmodes.sousmode3.client.MinuterieJeuClientSousMode3.estActif(),
                        com.example.mysubmod.sousmodes.sousmode3.client.MinuterieJeuClientSousMode3.partieEstTerminee());
                }
            }
        }
    }

    /**
     * Touche N pendant une partie sur carte (Sous-modes 1, 2 et 3) :
     * admins avant le lancement -> bouton de lancement de partie ;
     * joueurs pendant la partie -> ciblage d'une zone du HUD (flèche de navigation).
     */
    private static void gererToucheNPartieSurCarte(Minecraft mc, SousMode mode, boolean minuterieActive, boolean partieTerminee) {
        if (GestionnaireSubModeClient.estAdmin()) {
            if (minuterieActive || partieTerminee) {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cLa partie est déjà lancée"));
            } else if (mode == SousMode.SOUS_MODE_3) {
                // Sous-mode 3 : menu N enrichi avec les conditions de partie (cases à cocher).
                mc.setScreen(new com.example.mysubmod.sousmodes.sousmode3.client.EcranConfigurationPartieSousMode3());
            } else {
                // Sous-modes 1 et 2 sur carte : menu N historique (simple bouton de lancement).
                mc.setScreen(new com.example.mysubmod.sousmodes.sousmode3.client.EcranLancementPartieSousMode3());
            }
        } else if (com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3.estActif()) {
            mc.setScreen(new com.example.mysubmod.sousmodes.sousmode3.client.EcranZonesSousMode3());
        }
    }
}

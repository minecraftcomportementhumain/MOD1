package com.example.mysubmod.client;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.client.gui.EcranControleSousMode;
import com.example.mysubmod.sousmodes.SousMode;
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
    private static boolean astuceGuideAffichee = false;

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

        // Ouvrir le guide sur pression de la touche dédiée (rebindable)
        while (GuideClavier.TOUCHE_GUIDE.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new com.example.mysubmod.client.gui.EcranGuide());
            }
        }


        // Astuce de découvrabilité, une fois par connexion à un monde
        if (mc.level == null) {
            astuceGuideAffichee = false;
        } else if (!astuceGuideAffichee && mc.player != null && mc.screen == null) {
            astuceGuideAffichee = true;
            String touche = GuideClavier.TOUCHE_GUIDE.getTranslatedKeyMessage().getString();
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7Astuce : appuyez sur §e[" + touche + "]§7 (ou le bouton §eGuide§7 du menu M) pour ouvrir le guide du jeu."));
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();

        // Afficher/masquer le panneau du HUD des parcelles (F par défaut, rebindable).
        // Gérée en entrée BRUTE et non par consumeClick : F est partagée avec « échanger
        // de main » vanilla, et le routage Forge d'une touche partagée ne livre le clic
        // qu'à UN seul des deux mappings selon l'état du jeu — la bascule ne répondait
        // pas pour les joueurs en partie (seulement pour l'admin spectateur).
        if (event.getAction() == GLFW.GLFW_PRESS
            && HUDClavier.TOUCHE_BASCULE_HUD_PARCELLES.matches(event.getKey(), event.getScanCode())
            && mc.screen == null && mc.player != null) {
            // Panneau interdit par la config de la partie en cours (menu N › Interface)
            if (GestionnaireSubModeClient.obtenirModeActuel() == SousMode.SOUS_MODE_3
                && !com.example.mysubmod.sousmodes.sousmode3.client.OptionsHudClientSousMode3.panneauAutorise()) {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cLe HUD des parcelles est désactivé dans cette partie"));
                return;
            }
            String touche = HUDClavier.TOUCHE_BASCULE_HUD_PARCELLES.getTranslatedKeyMessage().getString();
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3.basculerPanneauOuPage(
                    net.minecraft.client.gui.screens.Screen.hasShiftDown(), touche,
                    mc.getWindow().getGuiScaledHeight())));
        }

        // Touche M - Ouvrir l'écran de contrôle des sous-modes
        if (event.getKey() == GLFW.GLFW_KEY_M && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.screen == null && mc.player != null) {
                // Demander l'écran au serveur avec le nombre de joueurs
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.sendToServer(
                    new com.example.mysubmod.reseau.PaquetDemandeEcranControleSousMode()
                );
            }
        }

        // Touche N - Menu de lancement de partie (admin) / ciblage de zone (joueurs) au Sous-mode 3
        if (event.getKey() == GLFW.GLFW_KEY_N && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.screen == null && mc.player != null) {
                // Carte en cours de génération : N bloqué
                if (com.example.mysubmod.cartes.client.ChargementCarteClient.estActif()) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§cLa carte est en cours de génération, veuillez patienter..."));
                    return;
                }

                if (GestionnaireSubModeClient.obtenirModeActuel() == SousMode.SOUS_MODE_3) {
                    gererToucheNSousMode3(mc,
                        com.example.mysubmod.sousmodes.sousmode3.client.MinuterieJeuClientSousMode3.estActif(),
                        com.example.mysubmod.sousmodes.sousmode3.client.MinuterieJeuClientSousMode3.partieEstTerminee());
                }
            }
        }
    }

    /**
     * Côté client : impose le temps de minage synchronisé par le serveur (menu N › Bonbons & minage).
     * La progression de casse est PRÉDITE par le client — sans ce handler, l'animation
     * suivrait les vitesses vanilla et le serveur rejetterait la casse (ou l'accepterait
     * trop tôt). Valeur 0 hors partie = vitesses vanilla, aucun effet.
     */
    @SubscribeEvent
    public static void onVitesseMinage(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (!event.getEntity().level().isClientSide()) {
            return; // Côté serveur : géré par GestionnaireEvenementsSousMode3 (config)
        }
        com.example.mysubmod.sousmodes.sousmode3.GestionnaireEvenementsSousMode3.appliquerTempsMinage(event,
            com.example.mysubmod.sousmodes.sousmode3.client.VitesseMinageClientSousMode3.obtenirTempsSecondes());
    }

    /**
     * Touche N au Sous-mode 3 : admins avant le lancement -> écran de configuration des
     * conditions de partie ; joueurs pendant la partie -> ciblage d'une zone du HUD (flèche).
     */
    private static void gererToucheNSousMode3(Minecraft mc, boolean minuterieActive, boolean partieTerminee) {
        if (GestionnaireSubModeClient.estAdmin()) {
            if (minuterieActive || partieTerminee) {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cLa partie est déjà lancée"));
            } else {
                mc.setScreen(new com.example.mysubmod.sousmodes.sousmode3.client.EcranConfigurationPartieSousMode3());
            }
        } else if (com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3.estActif()) {
            // Ciblage de parcelle interdit par la config de la partie (menu N › Interface)
            if (!com.example.mysubmod.sousmodes.sousmode3.client.OptionsHudClientSousMode3.flecheAutorisee()) {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cLa navigation par flèche est désactivée dans cette partie"));
                return;
            }
            mc.setScreen(new com.example.mysubmod.sousmodes.sousmode3.client.EcranZonesSousMode3());
        }
    }
}

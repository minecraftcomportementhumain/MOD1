package com.example.mysubmod.sousmodes.sousmode3.client;

import com.example.mysubmod.sousmodes.sousmode3.ConfigPartieSousMode3;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * État client des presets de config du menu N : liste des noms disponibles (reçue du
 * serveur) et application d'un preset chargé à l'écran de configuration ouvert.
 */
@OnlyIn(Dist.CLIENT)
public final class PresetsClientSousMode3 {

    private static final List<String> NOMS = new ArrayList<>();

    private PresetsClientSousMode3() {
    }

    /** Réponse serveur : met à jour la liste, applique une config chargée et affiche le message. */
    public static void surReponse(List<String> noms, ConfigPartieSousMode3 configChargee, String message) {
        NOMS.clear();
        NOMS.addAll(noms);

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranConfigurationPartieSousMode3 ecran) {
            if (configChargee != null) {
                ecran.appliquerConfigChargee(configChargee);
            } else {
                ecran.rafraichirPresets();
            }
        }
        if (message != null && !message.isBlank() && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }

    public static List<String> obtenirNoms() {
        return new ArrayList<>(NOMS);
    }
}

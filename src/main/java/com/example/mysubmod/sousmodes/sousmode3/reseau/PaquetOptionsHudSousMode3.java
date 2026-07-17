package com.example.mysubmod.sousmodes.sousmode3.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet serveur -> client : options d'interface de la partie en cours (menu N › Interface).
 * Synchronise l'autorisation de la flèche de navigation (touche N des joueurs) et du panneau
 * des parcelles (touche F). Envoyé au lancement, à la reconnexion, et avec les valeurs par
 * défaut (tout autorisé) hors partie. Consommé par
 * {@link com.example.mysubmod.sousmodes.sousmode3.client.OptionsHudClientSousMode3}.
 */
public class PaquetOptionsHudSousMode3 {

    private final boolean flecheNavigation;
    private final boolean hudParcelles;

    public PaquetOptionsHudSousMode3(boolean flecheNavigation, boolean hudParcelles) {
        this.flecheNavigation = flecheNavigation;
        this.hudParcelles = hudParcelles;
    }

    /** Valeurs par défaut (tout autorisé) : hors partie, ou fin de partie. */
    public static PaquetOptionsHudSousMode3 defauts() {
        return new PaquetOptionsHudSousMode3(true, true);
    }

    public static void encode(PaquetOptionsHudSousMode3 paquet, FriendlyByteBuf tampon) {
        tampon.writeBoolean(paquet.flecheNavigation);
        tampon.writeBoolean(paquet.hudParcelles);
    }

    public static PaquetOptionsHudSousMode3 decode(FriendlyByteBuf tampon) {
        return new PaquetOptionsHudSousMode3(tampon.readBoolean(), tampon.readBoolean());
    }

    public static void traiter(PaquetOptionsHudSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.example.mysubmod.sousmodes.sousmode3.client.OptionsHudClientSousMode3.definir(
                paquet.flecheNavigation, paquet.hudParcelles);
        });
        ctx.get().setPacketHandled(true);
    }
}

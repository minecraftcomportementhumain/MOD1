package com.example.mysubmod.cartes.reseau;

import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet client -> serveur : choix de la zone de départ par un joueur
 * (parties des Sous-modes 1 et 2 sur carte).
 */
public class PaquetChoixZoneDepart {
    private final String nomZone;

    public PaquetChoixZoneDepart(String nomZone) {
        this.nomZone = nomZone;
    }

    public static void encode(PaquetChoixZoneDepart paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.nomZone);
    }

    public static PaquetChoixZoneDepart decode(FriendlyByteBuf tampon) {
        return new PaquetChoixZoneDepart(tampon.readUtf());
    }

    public static void traiter(PaquetChoixZoneDepart paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null) {
                return;
            }
            SousMode mode = GestionnaireSousModes.getInstance().obtenirModeActuel();
            if (mode == SousMode.SOUS_MODE_3) {
                // Option « choix de la zone de départ » de la config de partie
                com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3.getInstance()
                    .selectionnerZoneDepart(joueur, paquet.nomZone);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

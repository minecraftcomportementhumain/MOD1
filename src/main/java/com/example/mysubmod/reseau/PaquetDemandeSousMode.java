package com.example.mysubmod.reseau;

import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet pour demander un changement de sous-mode
 */
public class PaquetDemandeSousMode {
    private final SousMode modeDemande;

    public PaquetDemandeSousMode(SousMode modeDemande) {
        this.modeDemande = modeDemande;
    }

    public static void encode(PaquetDemandeSousMode paquet, FriendlyByteBuf tampon) {
        tampon.writeEnum(paquet.modeDemande);
    }

    public static PaquetDemandeSousMode decode(FriendlyByteBuf tampon) {
        return new PaquetDemandeSousMode(tampon.readEnum(SousMode.class));
    }

    public static void traiter(PaquetDemandeSousMode paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur != null) {
                GestionnaireSousModes.getInstance().changerSousMode(paquet.modeDemande, joueur);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
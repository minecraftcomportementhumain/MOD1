package com.example.mysubmod.reseau;

import com.example.mysubmod.client.GestionnaireSubModeClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet pour synchroniser le statut administrateur du client
 */
public class PaquetStatutAdmin {
    private final boolean estAdmin;

    public PaquetStatutAdmin(boolean estAdmin) {
        this.estAdmin = estAdmin;
    }

    public static void encode(PaquetStatutAdmin paquet, FriendlyByteBuf tampon) {
        tampon.writeBoolean(paquet.estAdmin);
    }

    public static PaquetStatutAdmin decode(FriendlyByteBuf tampon) {
        return new PaquetStatutAdmin(tampon.readBoolean());
    }

    public static void traiter(PaquetStatutAdmin paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            GestionnaireSubModeClient.definirEstAdmin(paquet.estAdmin);
        });
        ctx.get().setPacketHandled(true);
    }
}
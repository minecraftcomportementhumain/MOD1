package com.example.mysubmod.reseau;

import com.example.mysubmod.client.GestionnaireSubModeClient;
import com.example.mysubmod.sousmodes.SousMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet pour notifier le client d'un changement de sous-mode
 */
public class PaquetChangementSousMode {
    private final SousMode nouveauMode;

    public PaquetChangementSousMode(SousMode nouveauMode) {
        this.nouveauMode = nouveauMode;
    }

    public static void encode(PaquetChangementSousMode paquet, FriendlyByteBuf tampon) {
        tampon.writeEnum(paquet.nouveauMode);
    }

    public static PaquetChangementSousMode decode(FriendlyByteBuf tampon) {
        return new PaquetChangementSousMode(tampon.readEnum(SousMode.class));
    }

    public static void traiter(PaquetChangementSousMode paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            GestionnaireSubModeClient.definirModeActuel(paquet.nouveauMode);
        });
        ctx.get().setPacketHandled(true);
    }
}
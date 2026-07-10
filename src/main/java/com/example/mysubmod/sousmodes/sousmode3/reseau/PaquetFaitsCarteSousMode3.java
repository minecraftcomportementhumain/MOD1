package com.example.mysubmod.sousmodes.sousmode3.reseau;

import com.example.mysubmod.sousmodes.sousmode3.client.FaitsCarteClientSousMode3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet serveur -> client : faits sur la carte active du Sous-mode 3 (envoyé aux admins),
 * utilisés par le menu N pour griser les options non applicables à la carte.
 */
public class PaquetFaitsCarteSousMode3 {
    private final boolean aBonbonsNonVisibles;
    private final boolean aBonbonsTypes;
    private final boolean aParcelles;

    public PaquetFaitsCarteSousMode3(boolean aBonbonsNonVisibles, boolean aBonbonsTypes, boolean aParcelles) {
        this.aBonbonsNonVisibles = aBonbonsNonVisibles;
        this.aBonbonsTypes = aBonbonsTypes;
        this.aParcelles = aParcelles;
    }

    public static void encode(PaquetFaitsCarteSousMode3 paquet, FriendlyByteBuf tampon) {
        tampon.writeBoolean(paquet.aBonbonsNonVisibles);
        tampon.writeBoolean(paquet.aBonbonsTypes);
        tampon.writeBoolean(paquet.aParcelles);
    }

    public static PaquetFaitsCarteSousMode3 decode(FriendlyByteBuf tampon) {
        return new PaquetFaitsCarteSousMode3(tampon.readBoolean(), tampon.readBoolean(), tampon.readBoolean());
    }

    public static void traiter(PaquetFaitsCarteSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            FaitsCarteClientSousMode3.definir(paquet.aBonbonsNonVisibles, paquet.aBonbonsTypes, paquet.aParcelles));
        ctx.get().setPacketHandled(true);
    }
}

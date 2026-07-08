package com.example.mysubmod.sousmodes.sousmode3.reseau;

import com.example.mysubmod.sousmodes.sousmode3.TypeRessource;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet serveur -> client : spécialisation Bleu/Rouge du joueur (option du menu N).
 * (Relocalisé depuis le Sous-mode 2 lors de sa suppression.)
 */
public class PaquetSyncSpecialisation {
    private final TypeRessource specialisation; // null = pas encore de spécialisation

    public PaquetSyncSpecialisation(TypeRessource specialisation) {
        this.specialisation = specialisation;
    }

    public static void encode(PaquetSyncSpecialisation paquet, FriendlyByteBuf tampon) {
        tampon.writeBoolean(paquet.specialisation != null);
        if (paquet.specialisation != null) {
            tampon.writeUtf(paquet.specialisation.name());
        }
    }

    public static PaquetSyncSpecialisation decode(FriendlyByteBuf tampon) {
        TypeRessource specialisation = tampon.readBoolean() ? TypeRessource.valueOf(tampon.readUtf()) : null;
        return new PaquetSyncSpecialisation(specialisation);
    }

    public static void traiter(PaquetSyncSpecialisation paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.example.mysubmod.sousmodes.sousmode3.client.SpecialisationClientSousMode3
                    .definirSpecialisation(paquet.specialisation));
        });
        ctx.get().setPacketHandled(true);
    }
}

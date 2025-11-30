package com.example.mysubmod.sousmodes.sousmode2.reseau;

import com.example.mysubmod.sousmodes.sousmode2.TypeRessource;
import com.example.mysubmod.sousmodes.sousmode2.client.HUDCompteurBonbons;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet pour synchroniser la spécialisation du joueur du serveur vers le client
 */
public class PaquetSyncSpecialisation {
    private final TypeRessource specialisation; // null si pas de spécialisation encore

    public PaquetSyncSpecialisation(TypeRessource specialisation) {
        this.specialisation = specialisation;
    }

    public static void encode(PaquetSyncSpecialisation paquet, FriendlyByteBuf tampon) {
        // Écrire si la spécialisation existe
        tampon.writeBoolean(paquet.specialisation != null);
        if (paquet.specialisation != null) {
            tampon.writeUtf(paquet.specialisation.name());
        }
    }

    public static PaquetSyncSpecialisation decode(FriendlyByteBuf tampon) {
        boolean aSpecialisation = tampon.readBoolean();
        TypeRessource specialisation = null;
        if (aSpecialisation) {
            specialisation = TypeRessource.valueOf(tampon.readUtf());
        }
        return new PaquetSyncSpecialisation(specialisation);
    }

    public static void traiter(PaquetSyncSpecialisation paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                HUDCompteurBonbons.definirSpecialisationJoueur(paquet.specialisation);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}

package com.example.mysubmod.sousmodes.sousmode1.reseau;

import com.example.mysubmod.sousmodes.sousmode1.GestionnaireSousMode1;
import com.example.mysubmod.sousmodes.sousmode1.iles.TypeIle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PaquetChoixIle {
    private final TypeIle ileSelectionnee;

    public PaquetChoixIle(TypeIle ileSelectionnee) {
        this.ileSelectionnee = ileSelectionnee;
    }

    public static void encode(PaquetChoixIle paquet, FriendlyByteBuf tampon) {
        tampon.writeEnum(paquet.ileSelectionnee);
    }

    public static PaquetChoixIle decode(FriendlyByteBuf tampon) {
        return new PaquetChoixIle(tampon.readEnum(TypeIle.class));
    }

    public static void traiter(PaquetChoixIle paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur != null) {
                GestionnaireSousMode1.getInstance().selectionnerIle(joueur, paquet.ileSelectionnee);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
package com.example.mysubmod.sousmodes.sousmode2.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PaquetSelectionIle {
    private final int secondesRestantes;

    public PaquetSelectionIle(int secondesRestantes) {
        this.secondesRestantes = secondesRestantes;
    }

    public static void encoder(PaquetSelectionIle paquet, FriendlyByteBuf tampon) {
        tampon.writeInt(paquet.secondesRestantes);
    }

    public static PaquetSelectionIle decoder(FriendlyByteBuf tampon) {
        return new PaquetSelectionIle(tampon.readInt());
    }

    public static void traiter(PaquetSelectionIle paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GestionnairePaquetsClient.ouvrirEcranSelectionIle(paquet.secondesRestantes));
        });
        ctx.get().setPacketHandled(true);
    }
}

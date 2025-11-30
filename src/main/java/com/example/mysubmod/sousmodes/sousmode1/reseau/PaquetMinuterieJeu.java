package com.example.mysubmod.sousmodes.sousmode1.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PaquetMinuterieJeu {
    private final int secondesRestantes;

    public PaquetMinuterieJeu(int secondesRestantes) {
        this.secondesRestantes = secondesRestantes;
    }

    public static void encode(PaquetMinuterieJeu paquet, FriendlyByteBuf tampon) {
        tampon.writeInt(paquet.secondesRestantes);
    }

    public static PaquetMinuterieJeu decode(FriendlyByteBuf tampon) {
        return new PaquetMinuterieJeu(tampon.readInt());
    }

    public static void traiter(PaquetMinuterieJeu paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GestionnairePaquetsClient.mettreAJourMinuterieJeu(paquet.secondesRestantes));
        });
        ctx.get().setPacketHandled(true);
    }
}
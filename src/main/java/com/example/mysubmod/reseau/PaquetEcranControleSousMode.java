package com.example.mysubmod.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet envoyé du serveur au client pour ouvrir l'écran de contrôle des sous-modes avec le nombre de joueurs
 */
public class PaquetEcranControleSousMode {
    private final int nombreJoueursNonAdmin;

    public PaquetEcranControleSousMode(int nombreJoueursNonAdmin) {
        this.nombreJoueursNonAdmin = nombreJoueursNonAdmin;
    }

    public static void encode(PaquetEcranControleSousMode paquet, FriendlyByteBuf tampon) {
        tampon.writeInt(paquet.nombreJoueursNonAdmin);
    }

    public static PaquetEcranControleSousMode decode(FriendlyByteBuf tampon) {
        return new PaquetEcranControleSousMode(tampon.readInt());
    }

    public static void traiter(PaquetEcranControleSousMode paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Exécuter uniquement côté client
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GestionnaireClient.gererEcranControle(paquet));
        });
        ctx.get().setPacketHandled(true);
    }

    // Classe de gestion côté client uniquement
    public static class GestionnaireClient {
        public static void gererEcranControle(PaquetEcranControleSousMode paquet) {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            minecraft.execute(() -> {
                minecraft.setScreen(new com.example.mysubmod.client.gui.EcranControleSousMode(paquet.nombreJoueursNonAdmin));
            });
        }
    }
}

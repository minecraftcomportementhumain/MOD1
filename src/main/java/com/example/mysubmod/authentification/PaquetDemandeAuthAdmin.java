package com.example.mysubmod.authentification;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet envoyé du serveur au client pour demander l'authentification (ouvre l'écran de mot de passe)
 */
public class PaquetDemandeAuthAdmin {
    private final String typeCompte; // "ADMINISTRATEUR" ou "JOUEUR_PROTEGE"
    private final int tentativesRestantes;
    private final int delaiSecondes;

    public PaquetDemandeAuthAdmin(String typeCompte, int tentativesRestantes, int delaiSecondes) {
        this.typeCompte = typeCompte;
        this.tentativesRestantes = tentativesRestantes;
        this.delaiSecondes = delaiSecondes;
    }

    public static void encode(PaquetDemandeAuthAdmin paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.typeCompte);
        tampon.writeInt(paquet.tentativesRestantes);
        tampon.writeInt(paquet.delaiSecondes);
    }

    public static PaquetDemandeAuthAdmin decode(FriendlyByteBuf tampon) {
        return new PaquetDemandeAuthAdmin(tampon.readUtf(), tampon.readInt(), tampon.readInt());
    }

    public static void traiter(PaquetDemandeAuthAdmin paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // S'exécute uniquement côté client
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.gererDemandeAuth(paquet));
        });
        ctx.get().setPacketHandled(true);
    }

    // Classe de gestionnaire uniquement client
    public static class ClientPacketHandler {
        public static void gererDemandeAuth(PaquetDemandeAuthAdmin packet) {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            minecraft.execute(() -> {
                minecraft.setScreen(new EcranMotDePasseAuth(packet.typeCompte, packet.tentativesRestantes, packet.delaiSecondes));
            });
        }
    }
}

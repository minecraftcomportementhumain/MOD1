package com.example.mysubmod.authentification;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet envoyé du serveur au client avec le résultat d'authentification
 */
public class PaquetReponseAuthAdmin {
    private final boolean succes;
    private final int tentativesRestantes;
    private final String messageReponse;

    public PaquetReponseAuthAdmin(boolean succes, int tentativesRestantes, String messageReponse) {
        this.succes = succes;
        this.tentativesRestantes = tentativesRestantes;
        this.messageReponse = messageReponse;
    }

    public static void encode(PaquetReponseAuthAdmin paquet, FriendlyByteBuf tampon) {
        tampon.writeBoolean(paquet.succes);
        tampon.writeInt(paquet.tentativesRestantes);
        tampon.writeUtf(paquet.messageReponse, 200);
    }

    public static PaquetReponseAuthAdmin decode(FriendlyByteBuf tampon) {
        return new PaquetReponseAuthAdmin(
            tampon.readBoolean(),
            tampon.readInt(),
            tampon.readUtf(200)
        );
    }

    public static void traiter(PaquetReponseAuthAdmin paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // S'exécute uniquement côté client
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.gererReponseAuth(paquet));
        });
        ctx.get().setPacketHandled(true);
    }

    // Classe de gestionnaire uniquement client
    public static class ClientPacketHandler {
        public static void gererReponseAuth(PaquetReponseAuthAdmin paquet) {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();

            if (paquet.succes) {
                // Succès - ferme l'écran de mot de passe
                minecraft.execute(() -> minecraft.setScreen(null));
            } else {
                // Échec - met à jour l'écran avec tentatives restantes ou affiche l'erreur
                minecraft.execute(() -> {
                    if (minecraft.screen instanceof EcranMotDePasseAuth ecranAuth) {
                        // Met à jour l'écran d'auth unifié
                        ecranAuth.definirTentativesRestantes(paquet.tentativesRestantes);
                        ecranAuth.afficherErreur(paquet.messageReponse);
                    } else if (minecraft.screen instanceof EcranMotDePasseAdmin ecranMotDePasse) {
                        // Support legacy pour ancien écran admin
                        ecranMotDePasse.definirTentativesRestantes(paquet.tentativesRestantes);
                        ecranMotDePasse.afficherErreur(paquet.messageReponse);
                    }
                });
            }
        }
    }
}

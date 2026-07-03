package com.example.mysubmod.cartes.reseau;

import com.example.mysubmod.cartes.GestionnaireCartes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet client -> serveur : l'admin a fermé l'outil de création de carte (libère le verrou).
 */
public class PaquetFermetureEditeurCarte {

    public PaquetFermetureEditeurCarte() {
    }

    public static void encode(PaquetFermetureEditeurCarte paquet, FriendlyByteBuf tampon) {
    }

    public static PaquetFermetureEditeurCarte decode(FriendlyByteBuf tampon) {
        return new PaquetFermetureEditeurCarte();
    }

    public static void traiter(PaquetFermetureEditeurCarte paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur != null) {
                GestionnaireCartes.getInstance().libererEditeur(joueur.getUUID());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

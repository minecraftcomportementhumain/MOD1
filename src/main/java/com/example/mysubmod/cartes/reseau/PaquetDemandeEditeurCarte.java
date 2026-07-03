package com.example.mysubmod.cartes.reseau;

import com.example.mysubmod.cartes.GestionnaireCartes;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Paquet client -> serveur : demander l'accès à l'outil de création de carte.
 * L'outil est accessible à un seul admin à la fois.
 */
public class PaquetDemandeEditeurCarte {

    public PaquetDemandeEditeurCarte() {
    }

    public static void encode(PaquetDemandeEditeurCarte paquet, FriendlyByteBuf tampon) {
    }

    public static PaquetDemandeEditeurCarte decode(FriendlyByteBuf tampon) {
        return new PaquetDemandeEditeurCarte();
    }

    public static void traiter(PaquetDemandeEditeurCarte paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null || !GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }

            String occupePar = GestionnaireCartes.getInstance().verrouillerEditeur(
                joueur.getUUID(), joueur.getName().getString());
            GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                new PaquetReponseEditeurCarte(occupePar == null, occupePar != null ? occupePar : ""));
        });
        ctx.get().setPacketHandled(true);
    }
}

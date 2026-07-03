package com.example.mysubmod.sousmodes.sousmode3.reseau;

import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet client -> serveur : un admin demande le lancement de la partie (menu N).
 * Sous-mode 3 : décompte de 10 secondes puis téléportation au point d'apparition.
 * Sous-modes 1 et 2 sur carte : lance la phase de sélection de la zone de départ.
 */
public class PaquetLancerPartieSousMode3 {

    public PaquetLancerPartieSousMode3() {
    }

    public static void encode(PaquetLancerPartieSousMode3 paquet, FriendlyByteBuf tampon) {
    }

    public static PaquetLancerPartieSousMode3 decode(FriendlyByteBuf tampon) {
        return new PaquetLancerPartieSousMode3();
    }

    public static void traiter(PaquetLancerPartieSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null || !GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                return;
            }
            SousMode mode = GestionnaireSousModes.getInstance().obtenirModeActuel();
            switch (mode) {
                case SOUS_MODE_3 -> GestionnaireSousMode3.getInstance().lancerPartie(joueur.server, joueur);
                case SOUS_MODE_1 -> com.example.mysubmod.sousmodes.sousmode1.GestionnaireSousMode1.getInstance()
                    .lancerPartieCarte(joueur.server, joueur);
                case SOUS_MODE_2 -> com.example.mysubmod.sousmodes.sousmode2.GestionnaireSousMode2.getInstance()
                    .lancerPartieCarte(joueur.server, joueur);
                default -> {
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

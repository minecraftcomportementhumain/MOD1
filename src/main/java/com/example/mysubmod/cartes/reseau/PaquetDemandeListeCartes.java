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
 * Paquet client -> serveur pour demander la liste des cartes sauvegardées.
 * Le champ « but » indique ce que le client fera de la liste (voir PaquetListeCartes).
 */
public class PaquetDemandeListeCartes {
    private final int but;

    public PaquetDemandeListeCartes(int but) {
        this.but = but;
    }

    public static void encode(PaquetDemandeListeCartes paquet, FriendlyByteBuf tampon) {
        tampon.writeInt(paquet.but);
    }

    public static PaquetDemandeListeCartes decode(FriendlyByteBuf tampon) {
        return new PaquetDemandeListeCartes(tampon.readInt());
    }

    public static void traiter(PaquetDemandeListeCartes paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur != null && GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                GestionnaireCartes gestionnaire = GestionnaireCartes.getInstance();
                String carteActive = gestionnaire.obtenirCarteSelectionnee();
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new PaquetListeCartes(gestionnaire.obtenirCartesDisponibles(),
                        carteActive != null ? carteActive : "", paquet.but));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

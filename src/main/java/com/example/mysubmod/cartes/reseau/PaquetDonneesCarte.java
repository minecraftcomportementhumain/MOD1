package com.example.mysubmod.cartes.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquet serveur -> client : données JSON d'une carte, envoyées en morceaux
 * (à charger dans l'éditeur).
 */
public class PaquetDonneesCarte {
    private final UUID idTransfert;
    private final int nombreTotalMorceaux;
    private final int indexMorceau;
    private final byte[] donneesMorceau;

    public PaquetDonneesCarte(UUID idTransfert, int nombreTotalMorceaux, int indexMorceau, byte[] donneesMorceau) {
        this.idTransfert = idTransfert;
        this.nombreTotalMorceaux = nombreTotalMorceaux;
        this.indexMorceau = indexMorceau;
        this.donneesMorceau = donneesMorceau;
    }

    public static void encode(PaquetDonneesCarte paquet, FriendlyByteBuf tampon) {
        tampon.writeUUID(paquet.idTransfert);
        tampon.writeInt(paquet.nombreTotalMorceaux);
        tampon.writeInt(paquet.indexMorceau);
        tampon.writeInt(paquet.donneesMorceau.length);
        tampon.writeBytes(paquet.donneesMorceau);
    }

    public static PaquetDonneesCarte decode(FriendlyByteBuf tampon) {
        UUID idTransfert = tampon.readUUID();
        int nombreTotalMorceaux = tampon.readInt();
        int indexMorceau = tampon.readInt();
        int longueur = tampon.readInt();
        if (longueur < 0 || longueur > tampon.readableBytes()) {
            throw new io.netty.handler.codec.DecoderException("Longueur de morceau invalide: " + longueur);
        }
        byte[] donnees = new byte[longueur];
        tampon.readBytes(donnees);
        return new PaquetDonneesCarte(idTransfert, nombreTotalMorceaux, indexMorceau, donnees);
    }

    public static void traiter(PaquetDonneesCarte paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                com.example.mysubmod.cartes.client.GestionnairePaquetsCartes.gererMorceauDonneesCarte(
                    paquet.idTransfert, paquet.nombreTotalMorceaux, paquet.indexMorceau, paquet.donneesMorceau));
        });
        ctx.get().setPacketHandled(true);
    }
}

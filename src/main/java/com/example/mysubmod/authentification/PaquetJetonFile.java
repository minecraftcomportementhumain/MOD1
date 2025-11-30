package com.example.mysubmod.authentification;

import com.example.mysubmod.MonSubMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet envoyé du serveur au client avec jeton de file
 * Le client stocke ce jeton et le renvoie lors de la reconnexion
 */
public class PaquetJetonFile {
    private final String nomCompte;
    private final String jeton;
    private final long monopoleDebutMs;
    private final long monopoleFinMs;

    public PaquetJetonFile(String nomCompte, String jeton, long monopoleDebutMs, long monopoleFinMs) {
        this.nomCompte = nomCompte;
        this.jeton = jeton;
        this.monopoleDebutMs = monopoleDebutMs;
        this.monopoleFinMs = monopoleFinMs;
    }

    public static void encode(PaquetJetonFile paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.nomCompte, 50);
        tampon.writeUtf(paquet.jeton, 10);
        tampon.writeLong(paquet.monopoleDebutMs);
        tampon.writeLong(paquet.monopoleFinMs);
    }

    public static PaquetJetonFile decode(FriendlyByteBuf tampon) {
        return new PaquetJetonFile(
            tampon.readUtf(50),
            tampon.readUtf(10),
            tampon.readLong(),
            tampon.readLong()
        );
    }

    public static void traiter(PaquetJetonFile paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Vérifie si c'est une demande d'effacement de jeton (jeton vide)
            if (paquet.jeton.isEmpty()) {
                StockageJetonsFile.retirerJeton(paquet.nomCompte);
                MonSubMod.JOURNALISEUR.info("Jeton de file effacé pour {}", paquet.nomCompte);
            } else {
                // Stocke le jeton côté client
                StockageJetonsFile.stockerJeton(paquet.nomCompte, paquet.jeton, paquet.monopoleDebutMs, paquet.monopoleFinMs);
                MonSubMod.JOURNALISEUR.info("Jeton de file reçu et stocké pour {}: {}", paquet.nomCompte, paquet.jeton);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

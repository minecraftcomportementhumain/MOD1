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
    // Durées RELATIVES (ms à partir de la réception), et NON des horodatages absolus du serveur :
    // le client les ancre sur SA propre horloge, pour que la fenêtre de monopole de 45 s ne
    // dépende pas de la synchro d'horloge serveur/client (même principe que PaquetSynchronisationPenalite).
    private final long debutDansMs;
    private final long finDansMs;

    public PaquetJetonFile(String nomCompte, String jeton, long debutDansMs, long finDansMs) {
        this.nomCompte = nomCompte;
        this.jeton = jeton;
        this.debutDansMs = debutDansMs;
        this.finDansMs = finDansMs;
    }

    public static void encode(PaquetJetonFile paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.nomCompte, 50);
        tampon.writeUtf(paquet.jeton, 64);
        tampon.writeLong(paquet.debutDansMs);
        tampon.writeLong(paquet.finDansMs);
    }

    public static PaquetJetonFile decode(FriendlyByteBuf tampon) {
        return new PaquetJetonFile(
            tampon.readUtf(50),
            tampon.readUtf(64),
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
                // Ancre les durées relatives reçues sur l'horloge du CLIENT : évite tout décalage
                // d'horloge serveur/client qui fausserait la fenêtre de monopole.
                long maintenant = System.currentTimeMillis();
                StockageJetonsFile.stockerJeton(paquet.nomCompte, paquet.jeton,
                    maintenant + paquet.debutDansMs, maintenant + paquet.finDansMs);
                MonSubMod.JOURNALISEUR.info("Jeton de file reçu et stocké pour {}", paquet.nomCompte);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

package com.example.mysubmod.sousmodes.sousmode2.reseau;

import com.example.mysubmod.sousmodes.sousmode2.client.HUDMinuteriePenalite;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquet pour synchroniser l'état de pénalité avec le client
 * Envoyé quand une pénalité est appliquée ou lors de la reconnexion
 *
 * Note: On envoie le temps RESTANT en ms au lieu du timestamp absolu pour éviter
 * les problèmes de désynchronisation d'horloge entre le serveur et le client.
 */
public class PaquetSynchronisationPenalite {
    private final boolean aPenalite;
    private final UUID idJoueur;
    private final long tempsRestantMs; // Temps restant de la pénalité en ms

    public PaquetSynchronisationPenalite(boolean aPenalite, UUID idJoueur) {
        this.aPenalite = aPenalite;
        this.idJoueur = idJoueur;
        this.tempsRestantMs = 0;
    }

    /**
     * Constructeur avec temps de fin absolu du serveur - converti automatiquement en temps restant
     */
    public PaquetSynchronisationPenalite(boolean aPenalite, UUID idJoueur, long tempsFinServeur) {
        this.aPenalite = aPenalite;
        this.idJoueur = idJoueur;
        // Convertir le temps de fin absolu en temps restant
        if (aPenalite && tempsFinServeur > 0) {
            long maintenant = System.currentTimeMillis();
            this.tempsRestantMs = Math.max(0, tempsFinServeur - maintenant);
        } else {
            this.tempsRestantMs = 0;
        }
    }

    public static void encoder(PaquetSynchronisationPenalite paquet, FriendlyByteBuf tampon) {
        tampon.writeBoolean(paquet.aPenalite);
        tampon.writeUUID(paquet.idJoueur);
        tampon.writeLong(paquet.tempsRestantMs);
    }

    public static PaquetSynchronisationPenalite decoder(FriendlyByteBuf tampon) {
        boolean aPenalite = tampon.readBoolean();
        UUID idJoueur = tampon.readUUID();
        long tempsRestantMs = tampon.readLong();
        // Reconstruire avec le temps restant déjà calculé
        PaquetSynchronisationPenalite paquet = new PaquetSynchronisationPenalite(aPenalite, idJoueur);
        // Utiliser la réflexion pour définir le temps restant directement (ou créer un constructeur privé)
        return new PaquetSynchronisationPenalite(aPenalite, idJoueur, tempsRestantMs, true);
    }

    /**
     * Constructeur privé pour le décodage - le temps restant est déjà calculé
     */
    private PaquetSynchronisationPenalite(boolean aPenalite, UUID idJoueur, long tempsRestantMs, boolean dejaDecode) {
        this.aPenalite = aPenalite;
        this.idJoueur = idJoueur;
        this.tempsRestantMs = tempsRestantMs;
    }

    public static void traiter(PaquetSynchronisationPenalite paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Vérifier que c'est bien le joueur local
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.getUUID().equals(paquet.idJoueur)) {
                    if (paquet.aPenalite && paquet.tempsRestantMs > 0) {
                        // Calculer le temps de fin basé sur l'horloge locale du client
                        long tempsFinLocal = System.currentTimeMillis() + paquet.tempsRestantMs;
                        HUDMinuteriePenalite.activer(paquet.idJoueur, tempsFinLocal);
                    } else {
                        HUDMinuteriePenalite.desactiver();
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}

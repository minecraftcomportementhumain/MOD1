package com.example.mysubmod.sousmodes.sousmode3.reseau;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquet serveur -> client : état de la pénalité de spécialisation (option du menu N).
 * On transmet le temps RESTANT en ms (pas un horodatage absolu) pour éviter les
 * désynchronisations d'horloge entre serveur et client.
 * (Relocalisé depuis le Sous-mode 2 lors de sa suppression.)
 */
public class PaquetSynchronisationPenalite {
    private final boolean aPenalite;
    private final UUID idJoueur;
    private final long tempsRestantMs;

    /** Désactivation de la pénalité */
    public PaquetSynchronisationPenalite(boolean aPenalite, UUID idJoueur) {
        this.aPenalite = aPenalite;
        this.idJoueur = idJoueur;
        this.tempsRestantMs = 0;
    }

    /** Activation : {@code tempsFinServeur} = horodatage absolu serveur, converti en temps restant */
    public PaquetSynchronisationPenalite(boolean aPenalite, UUID idJoueur, long tempsFinServeur) {
        this.aPenalite = aPenalite;
        this.idJoueur = idJoueur;
        this.tempsRestantMs = (aPenalite && tempsFinServeur > 0)
            ? Math.max(0, tempsFinServeur - System.currentTimeMillis()) : 0;
    }

    private PaquetSynchronisationPenalite(boolean aPenalite, UUID idJoueur, long tempsRestantMs, boolean brut) {
        this.aPenalite = aPenalite;
        this.idJoueur = idJoueur;
        this.tempsRestantMs = tempsRestantMs;
    }

    public static void encode(PaquetSynchronisationPenalite paquet, FriendlyByteBuf tampon) {
        tampon.writeBoolean(paquet.aPenalite);
        tampon.writeUUID(paquet.idJoueur);
        tampon.writeLong(paquet.tempsRestantMs);
    }

    public static PaquetSynchronisationPenalite decode(FriendlyByteBuf tampon) {
        return new PaquetSynchronisationPenalite(
            tampon.readBoolean(), tampon.readUUID(), tampon.readLong(), true);
    }

    public static void traiter(PaquetSynchronisationPenalite paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.getUUID().equals(paquet.idJoueur)) {
                    if (paquet.aPenalite && paquet.tempsRestantMs > 0) {
                        long tempsFinLocal = System.currentTimeMillis() + paquet.tempsRestantMs;
                        com.example.mysubmod.sousmodes.sousmode3.client.HUDPenaliteSousMode3
                            .activer(paquet.idJoueur, tempsFinLocal);
                    } else {
                        com.example.mysubmod.sousmodes.sousmode3.client.HUDPenaliteSousMode3.desactiver();
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}

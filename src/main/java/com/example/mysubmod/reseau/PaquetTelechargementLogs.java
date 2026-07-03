package com.example.mysubmod.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet pour demander le téléchargement de fichiers journaux
 */
public class PaquetTelechargementLogs {
    private final String nomDossier;
    private final boolean telechargerTout;
    private final int numeroSousMode;

    public PaquetTelechargementLogs(String nomDossier, boolean telechargerTout, int numeroSousMode) {
        this.nomDossier = nomDossier;
        this.telechargerTout = telechargerTout;
        this.numeroSousMode = numeroSousMode;
    }

    public PaquetTelechargementLogs(FriendlyByteBuf tampon) {
        this.telechargerTout = tampon.readBoolean();
        this.nomDossier = tampon.readBoolean() ? tampon.readUtf() : null;
        this.numeroSousMode = tampon.readInt();
    }

    public void toBytes(FriendlyByteBuf tampon) {
        tampon.writeBoolean(telechargerTout);
        tampon.writeBoolean(nomDossier != null);
        if (nomDossier != null) {
            tampon.writeUtf(nomDossier);
        }
        tampon.writeInt(numeroSousMode);
    }

    public String obtenirNomDossier() {
        return nomDossier;
    }

    public boolean estTelechargerTout() {
        return telechargerTout;
    }

    public boolean traiter(Supplier<NetworkEvent.Context> fournisseur) {
        NetworkEvent.Context contexte = fournisseur.get();
        contexte.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer joueur = contexte.getSender();
            if (joueur != null && com.example.mysubmod.sousmodes.GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                com.example.mysubmod.serveur.GestionnaireLogs.telechargerLogs(joueur, nomDossier, telechargerTout, numeroSousMode);
            }
        });
        return true;
    }
}

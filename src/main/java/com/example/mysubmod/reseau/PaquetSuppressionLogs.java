package com.example.mysubmod.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet pour demander la suppression de fichiers journaux
 */
public class PaquetSuppressionLogs {
    private final String nomDossier;
    private final boolean supprimerTout;

    public PaquetSuppressionLogs(String nomDossier, boolean supprimerTout) {
        this.nomDossier = nomDossier;
        this.supprimerTout = supprimerTout;
    }

    public PaquetSuppressionLogs(FriendlyByteBuf tampon) {
        this.supprimerTout = tampon.readBoolean();
        this.nomDossier = tampon.readBoolean() ? tampon.readUtf() : null;
    }

    public void toBytes(FriendlyByteBuf tampon) {
        tampon.writeBoolean(supprimerTout);
        tampon.writeBoolean(nomDossier != null);
        if (nomDossier != null) {
            tampon.writeUtf(nomDossier);
        }
    }

    public String obtenirNomDossier() {
        return nomDossier;
    }

    public boolean estSupprimerTout() {
        return supprimerTout;
    }

    public boolean traiter(Supplier<NetworkEvent.Context> fournisseur) {
        NetworkEvent.Context contexte = fournisseur.get();
        contexte.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer joueur = contexte.getSender();
            if (joueur != null && com.example.mysubmod.sousmodes.GestionnaireSousModes.getInstance().estAdmin(joueur)) {
                com.example.mysubmod.serveur.GestionnaireLogs.supprimerLogs(joueur, nomDossier, supprimerTout);
            }
        });
        return true;
    }
}

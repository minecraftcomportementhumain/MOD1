package com.example.mysubmod.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet pour envoyer les données d'un fichier journal du serveur au client
 */
public class PaquetDonneesLogs {
    private final String nomFichier;
    private final byte[] donnees;

    public PaquetDonneesLogs(String nomFichier, byte[] donnees) {
        this.nomFichier = nomFichier;
        this.donnees = donnees;
    }

    public PaquetDonneesLogs(FriendlyByteBuf tampon) {
        this.nomFichier = tampon.readUtf();
        int longueurDonnees = tampon.readInt();
        this.donnees = new byte[longueurDonnees];
        tampon.readBytes(this.donnees);
    }

    public void toBytes(FriendlyByteBuf tampon) {
        tampon.writeUtf(nomFichier);
        tampon.writeInt(donnees.length);
        tampon.writeBytes(donnees);
    }

    public String obtenirNomFichier() {
        return nomFichier;
    }

    public byte[] obtenirDonnees() {
        return donnees;
    }

    public boolean traiter(Supplier<NetworkEvent.Context> fournisseur) {
        NetworkEvent.Context contexte = fournisseur.get();
        contexte.enqueueWork(() -> {
            // Traitement côté client
            com.example.mysubmod.client.GestionnairePaquetsLogs.gererDonneesLog(nomFichier, donnees);
        });
        return true;
    }
}

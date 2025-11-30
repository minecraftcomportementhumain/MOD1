package com.example.mysubmod.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paquet contenant la liste des dossiers de journaux disponibles
 */
public class PaquetListeLogs {
    private final List<String> dossiersJournaux;

    public PaquetListeLogs(List<String> dossiersJournaux) {
        this.dossiersJournaux = dossiersJournaux;
    }

    public PaquetListeLogs(FriendlyByteBuf tampon) {
        int taille = tampon.readInt();
        this.dossiersJournaux = new ArrayList<>();
        for (int i = 0; i < taille; i++) {
            this.dossiersJournaux.add(tampon.readUtf());
        }
    }

    public void toBytes(FriendlyByteBuf tampon) {
        tampon.writeInt(dossiersJournaux.size());
        for (String dossier : dossiersJournaux) {
            tampon.writeUtf(dossier);
        }
    }

    public List<String> obtenirDossiersJournaux() {
        return dossiersJournaux;
    }

    public boolean traiter(Supplier<NetworkEvent.Context> fournisseur) {
        NetworkEvent.Context contexte = fournisseur.get();
        contexte.enqueueWork(() -> {
            com.example.mysubmod.client.GestionnairePaquetsLogs.gererPaquetListeLogs(this);
        });
        return true;
    }
}

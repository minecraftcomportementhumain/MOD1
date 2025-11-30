package com.example.mysubmod.sousmodes.sousmode1.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PaquetListeFichiersBonbons {
    private final List<String> fichiersDisponibles;
    private final boolean ouvrirEcran; // Indique si l'écran doit être ouvert automatiquement

    public PaquetListeFichiersBonbons(List<String> fichiersDisponibles) {
        this(fichiersDisponibles, true); // Par défaut ouvrir l'écran
    }

    public PaquetListeFichiersBonbons(List<String> fichiersDisponibles, boolean ouvrirEcran) {
        this.fichiersDisponibles = new ArrayList<>(fichiersDisponibles);
        this.ouvrirEcran = ouvrirEcran;
    }

    public static void encoder(PaquetListeFichiersBonbons paquet, FriendlyByteBuf tampon) {
        tampon.writeInt(paquet.fichiersDisponibles.size());
        for (String nomFichier : paquet.fichiersDisponibles) {
            tampon.writeUtf(nomFichier);
        }
        tampon.writeBoolean(paquet.ouvrirEcran);
    }

    public static PaquetListeFichiersBonbons decoder(FriendlyByteBuf tampon) {
        int taille = tampon.readInt();
        List<String> fichiers = new ArrayList<>();
        for (int i = 0; i < taille; i++) {
            fichiers.add(tampon.readUtf());
        }
        boolean ouvrirEcran = tampon.readBoolean();
        return new PaquetListeFichiersBonbons(fichiers, ouvrirEcran);
    }

    public static void traiter(PaquetListeFichiersBonbons paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            GestionnairePaquetsClient.traiterListeFichiersBonbons(paquet.fichiersDisponibles, paquet.ouvrirEcran);
        });
        ctx.get().setPacketHandled(true);
    }

    public List<String> obtenirFichiersDisponibles() {
        return fichiersDisponibles;
    }

    public boolean doitOuvrirEcran() {
        return ouvrirEcran;
    }
}
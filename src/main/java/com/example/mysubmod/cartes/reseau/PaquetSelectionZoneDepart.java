package com.example.mysubmod.cartes.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paquet serveur -> client : ouvre l'écran de sélection de la zone de départ
 * (parties des Sous-modes 1 et 2 sur carte — remplace la sélection d'île).
 */
public class PaquetSelectionZoneDepart {
    private final List<String> zones;
    private final int secondesRestantes;

    public PaquetSelectionZoneDepart(List<String> zones, int secondesRestantes) {
        this.zones = zones;
        this.secondesRestantes = secondesRestantes;
    }

    public static void encode(PaquetSelectionZoneDepart paquet, FriendlyByteBuf tampon) {
        tampon.writeInt(paquet.zones.size());
        for (String zone : paquet.zones) {
            tampon.writeUtf(zone);
        }
        tampon.writeInt(paquet.secondesRestantes);
    }

    public static PaquetSelectionZoneDepart decode(FriendlyByteBuf tampon) {
        int taille = tampon.readInt();
        List<String> zones = new ArrayList<>();
        for (int i = 0; i < taille; i++) {
            zones.add(tampon.readUtf());
        }
        return new PaquetSelectionZoneDepart(zones, tampon.readInt());
    }

    public static void traiter(PaquetSelectionZoneDepart paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Appel statique vers la classe client (chargement paresseux : jamais exécuté côté serveur)
            com.example.mysubmod.cartes.client.GestionnairePaquetsCartes.ouvrirSelectionZoneDepart(
                paquet.zones, paquet.secondesRestantes);
        });
        ctx.get().setPacketHandled(true);
    }
}

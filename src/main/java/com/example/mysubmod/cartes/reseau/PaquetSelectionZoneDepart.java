package com.example.mysubmod.cartes.reseau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paquet serveur -> client : ouvre l'écran de sélection de la zone de départ
 * (parties des Sous-modes 1 et 2 sur carte — remplace la sélection d'île ; aussi
 * utilisé par le Sous-mode 3 avec l'option « choix de zone »).
 *
 * <p>Chaque zone peut être accompagnée de sa taille en blocs (nombre de cellules) ;
 * une taille absente ({@code -1}) laisse l'écran afficher le nom seul — c'est le cas
 * des Sous-modes 1 et 2, dont l'affichage historique est conservé.</p>
 */
public class PaquetSelectionZoneDepart {
    private final List<String> zones;
    private final List<Integer> tailles; // en blocs ; -1 = non communiquée
    private final int secondesRestantes;

    public PaquetSelectionZoneDepart(List<String> zones, int secondesRestantes) {
        this(zones, taillesAbsentes(zones.size()), secondesRestantes);
    }

    public PaquetSelectionZoneDepart(List<String> zones, List<Integer> tailles, int secondesRestantes) {
        this.zones = zones;
        this.tailles = tailles;
        this.secondesRestantes = secondesRestantes;
    }

    private static List<Integer> taillesAbsentes(int nombre) {
        List<Integer> tailles = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            tailles.add(-1);
        }
        return tailles;
    }

    public static void encode(PaquetSelectionZoneDepart paquet, FriendlyByteBuf tampon) {
        tampon.writeInt(paquet.zones.size());
        for (int i = 0; i < paquet.zones.size(); i++) {
            tampon.writeUtf(paquet.zones.get(i));
            tampon.writeInt(i < paquet.tailles.size() ? paquet.tailles.get(i) : -1);
        }
        tampon.writeInt(paquet.secondesRestantes);
    }

    public static PaquetSelectionZoneDepart decode(FriendlyByteBuf tampon) {
        int taille = tampon.readInt();
        List<String> zones = new ArrayList<>();
        List<Integer> tailles = new ArrayList<>();
        for (int i = 0; i < taille; i++) {
            zones.add(tampon.readUtf());
            tailles.add(tampon.readInt());
        }
        return new PaquetSelectionZoneDepart(zones, tailles, tampon.readInt());
    }

    public static void traiter(PaquetSelectionZoneDepart paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Appel statique vers la classe client (chargement paresseux : jamais exécuté côté serveur)
            com.example.mysubmod.cartes.client.GestionnairePaquetsCartes.ouvrirSelectionZoneDepart(
                paquet.zones, paquet.tailles, paquet.secondesRestantes);
        });
        ctx.get().setPacketHandled(true);
    }
}

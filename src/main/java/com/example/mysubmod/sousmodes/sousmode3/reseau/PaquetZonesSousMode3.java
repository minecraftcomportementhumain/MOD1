package com.example.mysubmod.sousmodes.sousmode3.reseau;

import com.example.mysubmod.sousmodes.sousmode3.GestionnaireBonbonsSousMode3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paquet serveur -> client : zones de la carte pour le HUD du Sous-mode 3
 * (nom, centre géométrique, cellules, compteurs de bonbons visibles / non-visibles).
 * Version « complète » (avec cellules) à l'initialisation / reconnexion ;
 * version « compteurs » (sans cellules) pour les mises à jour en temps réel.
 */
public class PaquetZonesSousMode3 {

    public static class ZoneReseau {
        public final String nom;
        public final double centreX;
        public final double centreZ;
        public final int bonbonsVisibles;
        public final int bonbonsNonVisibles;
        public final int bonbonsBleus;   // Détail par type (Sous-mode 2 sur carte)
        public final int bonbonsRouges;
        public final List<int[]> cellules;

        public ZoneReseau(String nom, double centreX, double centreZ, int bonbonsVisibles,
                          int bonbonsNonVisibles, int bonbonsBleus, int bonbonsRouges, List<int[]> cellules) {
            this.nom = nom;
            this.centreX = centreX;
            this.centreZ = centreZ;
            this.bonbonsVisibles = bonbonsVisibles;
            this.bonbonsNonVisibles = bonbonsNonVisibles;
            this.bonbonsBleus = bonbonsBleus;
            this.bonbonsRouges = bonbonsRouges;
            this.cellules = cellules;
        }
    }

    private final List<ZoneReseau> zones;
    private final boolean complet;       // true = inclut les cellules (remplace tout l'état client)
    private final boolean reinitialiser; // true = réinitialise aussi la flèche de navigation

    public PaquetZonesSousMode3(List<ZoneReseau> zones, boolean complet, boolean reinitialiser) {
        this.zones = zones;
        this.complet = complet;
        this.reinitialiser = reinitialiser;
    }

    /** Liste complète des zones (avec cellules) */
    public static PaquetZonesSousMode3 complet(List<GestionnaireBonbonsSousMode3.DonneesZone> zones, boolean reinitialiser) {
        List<ZoneReseau> liste = new ArrayList<>();
        for (GestionnaireBonbonsSousMode3.DonneesZone zone : zones) {
            liste.add(new ZoneReseau(zone.nom, zone.centreX, zone.centreZ,
                zone.bonbonsVisibles, zone.bonbonsNonVisibles,
                zone.bonbonsBleus, zone.bonbonsRouges, zone.cellulesMonde));
        }
        return new PaquetZonesSousMode3(liste, true, reinitialiser);
    }

    /** Mise à jour des compteurs uniquement */
    public static PaquetZonesSousMode3 compteurs(List<GestionnaireBonbonsSousMode3.DonneesZone> zones) {
        List<ZoneReseau> liste = new ArrayList<>();
        for (GestionnaireBonbonsSousMode3.DonneesZone zone : zones) {
            liste.add(new ZoneReseau(zone.nom, zone.centreX, zone.centreZ,
                zone.bonbonsVisibles, zone.bonbonsNonVisibles,
                zone.bonbonsBleus, zone.bonbonsRouges, new ArrayList<>()));
        }
        return new PaquetZonesSousMode3(liste, false, false);
    }

    /** Paquet vide qui efface le HUD des zones côté client */
    public static PaquetZonesSousMode3 vide() {
        return new PaquetZonesSousMode3(new ArrayList<>(), true, true);
    }

    public static void encode(PaquetZonesSousMode3 paquet, FriendlyByteBuf tampon) {
        tampon.writeBoolean(paquet.complet);
        tampon.writeBoolean(paquet.reinitialiser);
        tampon.writeInt(paquet.zones.size());
        for (ZoneReseau zone : paquet.zones) {
            tampon.writeUtf(zone.nom);
            tampon.writeDouble(zone.centreX);
            tampon.writeDouble(zone.centreZ);
            tampon.writeInt(zone.bonbonsVisibles);
            tampon.writeInt(zone.bonbonsNonVisibles);
            tampon.writeInt(zone.bonbonsBleus);
            tampon.writeInt(zone.bonbonsRouges);
            tampon.writeInt(zone.cellules.size());
            for (int[] cellule : zone.cellules) {
                tampon.writeInt(cellule[0]);
                tampon.writeInt(cellule[1]);
            }
        }
    }

    public static PaquetZonesSousMode3 decode(FriendlyByteBuf tampon) {
        boolean complet = tampon.readBoolean();
        boolean reinitialiser = tampon.readBoolean();
        int nombreZones = tampon.readInt();
        List<ZoneReseau> zones = new ArrayList<>();
        for (int i = 0; i < nombreZones; i++) {
            String nom = tampon.readUtf();
            double centreX = tampon.readDouble();
            double centreZ = tampon.readDouble();
            int visibles = tampon.readInt();
            int nonVisibles = tampon.readInt();
            int bleus = tampon.readInt();
            int rouges = tampon.readInt();
            int nombreCellules = tampon.readInt();
            List<int[]> cellules = new ArrayList<>();
            for (int j = 0; j < nombreCellules; j++) {
                cellules.add(new int[]{tampon.readInt(), tampon.readInt()});
            }
            zones.add(new ZoneReseau(nom, centreX, centreZ, visibles, nonVisibles, bleus, rouges, cellules));
        }
        return new PaquetZonesSousMode3(zones, complet, reinitialiser);
    }

    public static void traiter(PaquetZonesSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3.mettreAJourZones(
                paquet.zones, paquet.complet, paquet.reinitialiser);
        });
        ctx.get().setPacketHandled(true);
    }
}

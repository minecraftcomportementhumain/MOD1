package com.example.mysubmod.sousmodes.sousmode3.reseau;

import com.example.mysubmod.sousmodes.sousmode3.GestionnaireBonbonsSousMode3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paquet serveur -> client : zones de la carte pour le HUD du Sous-mode 3
 * (nom, centre géométrique, cellules, compteurs de bonbons visibles / non-visibles).
 * Version « complète » (avec cellules) à l'initialisation / reconnexion ;
 * version « compteurs » (sans cellules) pour les mises à jour en temps réel.
 *
 * Les cellules voyagent en plages « z, x0, longueur » et la liste complète est
 * découpée en plusieurs parties si nécessaire : sur les grandes cartes (jusqu'à
 * 2500×2500), une zone peut compter des millions de cellules, ce qui dépasserait
 * la taille maximale d'une trame réseau (~2 Mio) si chaque cellule était écrite
 * individuellement dans un paquet unique.
 */
public class PaquetZonesSousMode3 {

    /** Budget approximatif d'une partie (marge large sous la limite de trame de ~2 Mio) */
    private static final int BUDGET_OCTETS_PARTIE = 900_000;
    private static final int COUT_OCTETS_PLAGE = 12;
    private static final int COUT_OCTETS_ENTETE_ZONE = 128;

    public static class ZoneReseau {
        public final String nom;
        public final double centreX;
        public final double centreZ;
        public final int bonbonsVisibles;
        public final int bonbonsNonVisibles;
        public final int bonbonsBleus;   // Détail par type (Sous-mode 2 sur carte)
        public final int bonbonsRouges;
        public final List<int[]> cellules;
        /** Représentation réseau {z, x0, longueur} — présente côté émetteur seulement */
        private final List<int[]> plages;

        public ZoneReseau(String nom, double centreX, double centreZ, int bonbonsVisibles,
                          int bonbonsNonVisibles, int bonbonsBleus, int bonbonsRouges, List<int[]> cellules) {
            this(nom, centreX, centreZ, bonbonsVisibles, bonbonsNonVisibles, bonbonsBleus, bonbonsRouges,
                cellules, calculerPlages(cellules));
        }

        private ZoneReseau(String nom, double centreX, double centreZ, int bonbonsVisibles,
                           int bonbonsNonVisibles, int bonbonsBleus, int bonbonsRouges,
                           List<int[]> cellules, List<int[]> plages) {
            this.nom = nom;
            this.centreX = centreX;
            this.centreZ = centreZ;
            this.bonbonsVisibles = bonbonsVisibles;
            this.bonbonsNonVisibles = bonbonsNonVisibles;
            this.bonbonsBleus = bonbonsBleus;
            this.bonbonsRouges = bonbonsRouges;
            this.cellules = cellules;
            this.plages = plages;
        }

        /** Copie de la zone limitée à une tranche de ses plages (scission des zones géantes) */
        private ZoneReseau segment(int debutPlage, int finPlage) {
            return new ZoneReseau(nom, centreX, centreZ, bonbonsVisibles, bonbonsNonVisibles,
                bonbonsBleus, bonbonsRouges, List.of(), new ArrayList<>(plages.subList(debutPlage, finPlage)));
        }

        private int coutOctets() {
            return COUT_OCTETS_ENTETE_ZONE + nom.length() * 3 + plages.size() * COUT_OCTETS_PLAGE;
        }
    }

    /** Cellules (coordonnées monde) -> plages « z, x0, longueur » (tri par rangée puis fusion) */
    private static List<int[]> calculerPlages(List<int[]> cellules) {
        long[] indices = new long[cellules.size()];
        int n = 0;
        for (int[] cellule : cellules) {
            // Clé triable : z signé en poids fort, x décalé en non-signé en poids faible
            indices[n++] = (((long) cellule[1]) << 32) | ((cellule[0] - (long) Integer.MIN_VALUE) & 0xFFFFFFFFL);
        }
        Arrays.sort(indices);

        List<int[]> plages = new ArrayList<>();
        int i = 0;
        while (i < indices.length) {
            int debut = i;
            while (i + 1 < indices.length && indices[i + 1] == indices[i] + 1
                && (indices[i + 1] & 0xFFFFFFFFL) != 0) { // ne pas franchir un changement de rangée
                i++;
            }
            int z = (int) (indices[debut] >> 32);
            int x0 = (int) ((indices[debut] & 0xFFFFFFFFL) + Integer.MIN_VALUE);
            plages.add(new int[]{z, x0, i - debut + 1});
            i++;
        }
        return plages;
    }

    private final List<ZoneReseau> zones;
    private final boolean complet;       // true = inclut les cellules (remplace tout l'état client)
    private final boolean reinitialiser; // true = réinitialise aussi la flèche de navigation
    private final int indexPartie;
    private final int totalParties;

    public PaquetZonesSousMode3(List<ZoneReseau> zones, boolean complet, boolean reinitialiser) {
        this(zones, complet, reinitialiser, 0, 1);
    }

    private PaquetZonesSousMode3(List<ZoneReseau> zones, boolean complet, boolean reinitialiser,
                                 int indexPartie, int totalParties) {
        this.zones = zones;
        this.complet = complet;
        this.reinitialiser = reinitialiser;
        this.indexPartie = indexPartie;
        this.totalParties = totalParties;
    }

    /**
     * Liste complète des zones (avec cellules), découpée en autant de paquets que
     * nécessaire pour rester sous la limite de trame. Les zones géantes sont scindées
     * en segments de même nom, refusionnés à la réception.
     */
    public static List<PaquetZonesSousMode3> completEnParties(
            List<GestionnaireBonbonsSousMode3.DonneesZone> zones, boolean reinitialiser) {
        int maxPlagesParSegment = (BUDGET_OCTETS_PARTIE - COUT_OCTETS_ENTETE_ZONE) / COUT_OCTETS_PLAGE;
        List<ZoneReseau> segments = new ArrayList<>();
        for (GestionnaireBonbonsSousMode3.DonneesZone zone : zones) {
            ZoneReseau zoneReseau = new ZoneReseau(zone.nom, zone.centreX, zone.centreZ,
                zone.bonbonsVisibles, zone.bonbonsNonVisibles,
                zone.bonbonsBleus, zone.bonbonsRouges, zone.cellulesMonde);
            if (zoneReseau.plages.size() <= maxPlagesParSegment) {
                segments.add(zoneReseau);
            } else {
                for (int debut = 0; debut < zoneReseau.plages.size(); debut += maxPlagesParSegment) {
                    segments.add(zoneReseau.segment(debut,
                        Math.min(zoneReseau.plages.size(), debut + maxPlagesParSegment)));
                }
            }
        }

        // Regroupement des segments en parties sous le budget
        List<List<ZoneReseau>> parties = new ArrayList<>();
        List<ZoneReseau> partieCourante = new ArrayList<>();
        int coutCourant = 0;
        for (ZoneReseau segment : segments) {
            int cout = segment.coutOctets();
            if (!partieCourante.isEmpty() && coutCourant + cout > BUDGET_OCTETS_PARTIE) {
                parties.add(partieCourante);
                partieCourante = new ArrayList<>();
                coutCourant = 0;
            }
            partieCourante.add(segment);
            coutCourant += cout;
        }
        parties.add(partieCourante); // Toujours au moins une partie (même vide : efface le HUD)

        List<PaquetZonesSousMode3> paquets = new ArrayList<>();
        for (int i = 0; i < parties.size(); i++) {
            paquets.add(new PaquetZonesSousMode3(parties.get(i), true, reinitialiser, i, parties.size()));
        }
        return paquets;
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
        tampon.writeInt(paquet.indexPartie);
        tampon.writeInt(paquet.totalParties);
        tampon.writeInt(paquet.zones.size());
        for (ZoneReseau zone : paquet.zones) {
            tampon.writeUtf(zone.nom);
            tampon.writeDouble(zone.centreX);
            tampon.writeDouble(zone.centreZ);
            tampon.writeInt(zone.bonbonsVisibles);
            tampon.writeInt(zone.bonbonsNonVisibles);
            tampon.writeInt(zone.bonbonsBleus);
            tampon.writeInt(zone.bonbonsRouges);
            tampon.writeInt(zone.plages.size());
            for (int[] plage : zone.plages) {
                tampon.writeInt(plage[0]);
                tampon.writeInt(plage[1]);
                tampon.writeInt(plage[2]);
            }
        }
    }

    public static PaquetZonesSousMode3 decode(FriendlyByteBuf tampon) {
        boolean complet = tampon.readBoolean();
        boolean reinitialiser = tampon.readBoolean();
        int indexPartie = tampon.readInt();
        int totalParties = tampon.readInt();
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
            int nombrePlages = tampon.readInt();
            List<int[]> cellules = new ArrayList<>();
            for (int j = 0; j < nombrePlages; j++) {
                int z = tampon.readInt();
                int x0 = tampon.readInt();
                int longueur = tampon.readInt();
                for (int k = 0; k < longueur; k++) {
                    cellules.add(new int[]{x0 + k, z});
                }
            }
            zones.add(new ZoneReseau(nom, centreX, centreZ, visibles, nonVisibles, bleus, rouges,
                cellules, List.of()));
        }
        return new PaquetZonesSousMode3(zones, complet, reinitialiser, indexPartie, totalParties);
    }

    // Accumulation côté client des envois en plusieurs parties (le réseau garantit l'ordre)
    private static final List<ZoneReseau> partiesEnCours = new ArrayList<>();

    public static void traiter(PaquetZonesSousMode3 paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (!paquet.complet || paquet.totalParties <= 1) {
                com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3.mettreAJourZones(
                    paquet.zones, paquet.complet, paquet.reinitialiser);
                return;
            }

            if (paquet.indexPartie == 0) {
                partiesEnCours.clear();
            }
            for (ZoneReseau zone : paquet.zones) {
                // Refusionner les segments d'une zone scindée (même nom, envoyés consécutivement)
                if (!partiesEnCours.isEmpty()
                    && partiesEnCours.get(partiesEnCours.size() - 1).nom.equals(zone.nom)) {
                    ZoneReseau precedent = partiesEnCours.remove(partiesEnCours.size() - 1);
                    List<int[]> cellulesFusionnees = new ArrayList<>(precedent.cellules);
                    cellulesFusionnees.addAll(zone.cellules);
                    partiesEnCours.add(new ZoneReseau(precedent.nom, precedent.centreX, precedent.centreZ,
                        precedent.bonbonsVisibles, precedent.bonbonsNonVisibles,
                        precedent.bonbonsBleus, precedent.bonbonsRouges, cellulesFusionnees, List.of()));
                } else {
                    partiesEnCours.add(zone);
                }
            }
            if (paquet.indexPartie == paquet.totalParties - 1) {
                com.example.mysubmod.sousmodes.sousmode3.client.HUDZonesSousMode3.mettreAJourZones(
                    new ArrayList<>(partiesEnCours), true, paquet.reinitialiser);
                partiesEnCours.clear();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

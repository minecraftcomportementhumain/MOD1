package com.example.mysubmod.sousmodes.sousmode3.reseau;

import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.sousmodes.sousmode3.GestionnaireBonbonsSousMode3;
import io.netty.handler.codec.DecoderException;
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
 * <p>Les cellules voyagent et restent stockées en plages triées « z, x0, longueur »
 * (jamais développées cellule par cellule : une zone d'une carte 2500×2500 peut en
 * compter des millions), et l'appartenance se teste par recherche binaire. La liste
 * complète est découpée en plusieurs parties sous la limite de trame (~2 Mio) ; une
 * zone géante est scindée en segments marqués « suite », refusionnés à la réception.</p>
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
        /** Plages de cellules triées par (z, x0) : {z, x0, longueur} */
        public final List<int[]> plages;
        /** Segment de continuation d'une zone scindée (fusionné à la réception) */
        public final boolean suite;

        /** Construit une zone à partir de plages déjà triées par (z, x0) — jamais de cellules développées */
        public ZoneReseau(String nom, double centreX, double centreZ, int bonbonsVisibles,
                          int bonbonsNonVisibles, int bonbonsBleus, int bonbonsRouges, List<int[]> plages) {
            this(nom, centreX, centreZ, bonbonsVisibles, bonbonsNonVisibles, bonbonsBleus, bonbonsRouges,
                plages, false);
        }

        private ZoneReseau(String nom, double centreX, double centreZ, int bonbonsVisibles,
                           int bonbonsNonVisibles, int bonbonsBleus, int bonbonsRouges,
                           List<int[]> plages, boolean suite) {
            this.nom = nom;
            this.centreX = centreX;
            this.centreZ = centreZ;
            this.bonbonsVisibles = bonbonsVisibles;
            this.bonbonsNonVisibles = bonbonsNonVisibles;
            this.bonbonsBleus = bonbonsBleus;
            this.bonbonsRouges = bonbonsRouges;
            this.plages = plages;
            this.suite = suite;
        }

        /** Copie de la zone limitée à une tranche de ses plages (scission des zones géantes) */
        private ZoneReseau segment(int debutPlage, int finPlage, boolean estSuite) {
            return new ZoneReseau(nom, centreX, centreZ, bonbonsVisibles, bonbonsNonVisibles,
                bonbonsBleus, bonbonsRouges, new ArrayList<>(plages.subList(debutPlage, finPlage)), estSuite);
        }

        /** Fusion d'un segment de continuation (les plages restent triées : tranches contiguës) */
        private ZoneReseau fusionner(ZoneReseau suiteZone) {
            List<int[]> fusionnees = new ArrayList<>(plages.size() + suiteZone.plages.size());
            fusionnees.addAll(plages);
            fusionnees.addAll(suiteZone.plages);
            return new ZoneReseau(nom, centreX, centreZ, bonbonsVisibles, bonbonsNonVisibles,
                bonbonsBleus, bonbonsRouges, fusionnees, suite);
        }

        private int coutOctets() {
            return COUT_OCTETS_ENTETE_ZONE + nom.length() * 3 + plages.size() * COUT_OCTETS_PLAGE;
        }
    }

    /** La cellule (cx, cz) appartient-elle à ces plages triées ? Recherche binaire, O(log n). */
    public static boolean plagesContiennent(List<int[]> plages, int cx, int cz) {
        return com.example.mysubmod.cartes.ZoneCarte.plagesContiennent(plages, cx, cz);
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
     * en segments marqués « suite », refusionnés à la réception.
     */
    public static List<PaquetZonesSousMode3> completEnParties(
            List<GestionnaireBonbonsSousMode3.DonneesZone> zones, boolean reinitialiser) {
        int maxPlagesParSegment = (BUDGET_OCTETS_PARTIE - COUT_OCTETS_ENTETE_ZONE) / COUT_OCTETS_PLAGE;
        List<ZoneReseau> segments = new ArrayList<>();
        for (GestionnaireBonbonsSousMode3.DonneesZone zone : zones) {
            ZoneReseau zoneReseau = new ZoneReseau(zone.nom, zone.centreX, zone.centreZ,
                zone.bonbonsVisibles, zone.bonbonsNonVisibles,
                zone.bonbonsBleus, zone.bonbonsRouges, zone.plagesMonde);
            if (zoneReseau.plages.size() <= maxPlagesParSegment) {
                segments.add(zoneReseau);
            } else {
                for (int debut = 0; debut < zoneReseau.plages.size(); debut += maxPlagesParSegment) {
                    segments.add(zoneReseau.segment(debut,
                        Math.min(zoneReseau.plages.size(), debut + maxPlagesParSegment), debut > 0));
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
            tampon.writeBoolean(zone.suite);
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
        long totalCellules = 0;
        for (int i = 0; i < nombreZones; i++) {
            String nom = tampon.readUtf();
            boolean suite = tampon.readBoolean();
            double centreX = tampon.readDouble();
            double centreZ = tampon.readDouble();
            int visibles = tampon.readInt();
            int nonVisibles = tampon.readInt();
            int bleus = tampon.readInt();
            int rouges = tampon.readInt();
            int nombrePlages = tampon.readInt();
            // Bornes : chaque plage occupe 12 octets dans la trame, et le total de
            // cellules décrites ne peut pas dépasser l'aire maximale d'une carte
            if (nombrePlages < 0 || (long) nombrePlages * COUT_OCTETS_PLAGE > tampon.readableBytes()) {
                throw new DecoderException("Nombre de plages de zone invalide: " + nombrePlages);
            }
            List<int[]> plages = new ArrayList<>(nombrePlages);
            for (int j = 0; j < nombrePlages; j++) {
                int z = tampon.readInt();
                int x0 = tampon.readInt();
                int longueur = tampon.readInt();
                if (longueur < 1 || longueur > CarteDonnees.BLOCS_MAX) {
                    throw new DecoderException("Longueur de plage de zone invalide: " + longueur);
                }
                totalCellules += longueur;
                if (totalCellules > CarteDonnees.BLOCS_MAX) {
                    throw new DecoderException("Trop de cellules de zones dans le paquet");
                }
                plages.add(new int[]{z, x0, longueur});
            }
            zones.add(new ZoneReseau(nom, centreX, centreZ, visibles, nonVisibles, bleus, rouges,
                plages, suite));
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
                // Refusionner uniquement les segments explicitement marqués « suite »
                if (zone.suite && !partiesEnCours.isEmpty()) {
                    ZoneReseau precedent = partiesEnCours.remove(partiesEnCours.size() - 1);
                    partiesEnCours.add(precedent.fusionner(zone));
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

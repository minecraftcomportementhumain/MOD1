package com.example.mysubmod.sousmodes.sousmode3.client;

import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetZonesSousMode3;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HUD des zones du Sous-mode 3 : liste toutes les zones de la carte contenant
 * au moins un bonbon, avec les compteurs de bonbons visibles / non-visibles
 * restants, et affiche la flèche de navigation vers la zone sélectionnée.
 */
@OnlyIn(Dist.CLIENT)
public class HUDZonesSousMode3 {

    public static class ZoneClient {
        public final String nom;
        public final double centreX;
        public final double centreZ;
        public int bonbonsVisibles;
        public int bonbonsNonVisibles;
        public int bonbonsBleus;   // Détail par type (Sous-mode 2 sur carte)
        public int bonbonsRouges;
        public List<int[]> cellules = new ArrayList<>();

        ZoneClient(String nom, double centreX, double centreZ) {
            this.nom = nom;
            this.centreX = centreX;
            this.centreZ = centreZ;
        }

        public boolean contientPosition(double x, double z) {
            int cx = (int) Math.floor(x);
            int cz = (int) Math.floor(z);
            for (int[] cellule : cellules) {
                if (cellule[0] == cx && cellule[1] == cz) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final List<ZoneClient> ZONES = new ArrayList<>();
    private static boolean actif = false;
    private static String zoneCiblee = null; // Cible de la flèche (propre à chaque joueur, côté client)

    public static synchronized void mettreAJourZones(List<PaquetZonesSousMode3.ZoneReseau> zonesRecues,
                                                     boolean complet, boolean reinitialiser) {
        if (reinitialiser) {
            zoneCiblee = null; // Flèche réinitialisée (ex. reconnexion en cours de partie)
        }

        if (complet) {
            ZONES.clear();
            for (PaquetZonesSousMode3.ZoneReseau zoneReseau : zonesRecues) {
                ZoneClient zone = new ZoneClient(zoneReseau.nom, zoneReseau.centreX, zoneReseau.centreZ);
                zone.bonbonsVisibles = zoneReseau.bonbonsVisibles;
                zone.bonbonsNonVisibles = zoneReseau.bonbonsNonVisibles;
                zone.bonbonsBleus = zoneReseau.bonbonsBleus;
                zone.bonbonsRouges = zoneReseau.bonbonsRouges;
                zone.cellules = zoneReseau.cellules;
                ZONES.add(zone);
            }
            actif = !ZONES.isEmpty();
        } else {
            // Mise à jour des compteurs uniquement (les zones restent affichées même à 0)
            Map<String, PaquetZonesSousMode3.ZoneReseau> parNom = new HashMap<>();
            for (PaquetZonesSousMode3.ZoneReseau zoneReseau : zonesRecues) {
                parNom.put(zoneReseau.nom, zoneReseau);
            }
            for (ZoneClient zone : ZONES) {
                PaquetZonesSousMode3.ZoneReseau maj = parNom.get(zone.nom);
                if (maj != null) {
                    zone.bonbonsVisibles = maj.bonbonsVisibles;
                    zone.bonbonsNonVisibles = maj.bonbonsNonVisibles;
                    zone.bonbonsBleus = maj.bonbonsBleus;
                    zone.bonbonsRouges = maj.bonbonsRouges;
                }
            }
        }

        if (zoneCiblee != null && obtenirZone(zoneCiblee) == null) {
            zoneCiblee = null;
        }
    }

    public static synchronized List<ZoneClient> obtenirZones() {
        return new ArrayList<>(ZONES);
    }

    private static ZoneClient obtenirZone(String nom) {
        for (ZoneClient zone : ZONES) {
            if (zone.nom.equals(nom)) {
                return zone;
            }
        }
        return null;
    }

    public static boolean estActif() {
        return actif;
    }

    public static void desactiver() {
        actif = false;
        ZONES.clear();
        zoneCiblee = null;
    }

    /** Cibler une zone : active la flèche de navigation (une seule flèche à la fois) */
    public static void ciblerZone(String nom) {
        zoneCiblee = nom;
    }

    public static String obtenirZoneCiblee() {
        return zoneCiblee;
    }

    // ==================== Rendu ====================

    public static void afficher(GuiGraphics guiGraphics, int largeurEcran, int hauteurEcran) {
        if (!actif) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font police = mc.font;

        List<ZoneClient> zones = obtenirZones();
        com.example.mysubmod.sousmodes.SousMode mode =
            com.example.mysubmod.client.GestionnaireSubModeClient.obtenirModeActuel();

        // Spécialisation du joueur (Sous-mode 2 sur carte, ou Sous-mode 3 avec l'option
        // « Spécialisation » — dans les deux cas, null tant qu'aucun bonbon typé n'est consommé)
        com.example.mysubmod.sousmodes.sousmode2.TypeRessource specialisation =
            (mode == com.example.mysubmod.sousmodes.SousMode.SOUS_MODE_2
                || mode == com.example.mysubmod.sousmodes.SousMode.SOUS_MODE_3)
                ? com.example.mysubmod.sousmodes.sousmode2.client.HUDCompteurBonbons.obtenirSpecialisationJoueur()
                : null;

        // Panneau des zones : coin supérieur droit, sous la minuterie
        int largeurPanneau = 170;
        int x = largeurEcran - largeurPanneau - 5;
        int y = 30;
        int hauteurLigne = 11;
        int hauteurPanneau = 16 + zones.size() * hauteurLigne + 12 + (specialisation != null ? 12 : 0);

        guiGraphics.fill(x - 4, y - 4, x + largeurPanneau, y + hauteurPanneau - 4, 0x80000000);

        if (specialisation != null) {
            String texteSpec = "Spécialisation: "
                + (specialisation == com.example.mysubmod.sousmodes.sousmode2.TypeRessource.BONBON_BLEU ? "Bleu" : "Rouge");
            guiGraphics.drawString(police, texteSpec, x, y, specialisation.obtenirCouleur());
            y += 12;
        }

        guiGraphics.drawString(police, "§6Zones :", x, y, 0xFFFFFF);
        y += 13;

        for (ZoneClient zone : zones) {
            boolean estCiblee = zone.nom.equals(zoneCiblee);
            String prefixe = estCiblee ? "§b➤ " : "§f";
            String texte = prefixe + zone.nom + " §7— " + formaterCompteurs(zone, mode);
            guiGraphics.drawString(police, texte, x, y, 0xFFFFFF);
            y += hauteurLigne;
        }

        guiGraphics.drawString(police, "§8[N] cibler une zone", x, y + 2, 0xFFFFFF);

        // Flèche de navigation
        afficherFleche(guiGraphics, largeurEcran, hauteurEcran);
    }

    /** Format des compteurs d'une zone selon le sous-mode courant */
    public static String formaterCompteurs(ZoneClient zone, com.example.mysubmod.sousmodes.SousMode mode) {
        if (mode == com.example.mysubmod.sousmodes.SousMode.SOUS_MODE_2) {
            // Sous-mode 2 : détail par couleur de bonbon
            return "§9" + zone.bonbonsBleus + " bleu(s)§7, §c" + zone.bonbonsRouges + " rouge(s)";
        }
        if (mode == com.example.mysubmod.sousmodes.SousMode.SOUS_MODE_1) {
            // Sous-mode 1 : pas de bonbons non-visibles (ignorés)
            return "§e" + zone.bonbonsVisibles + " bonbon(s)";
        }
        return "§e" + zone.bonbonsVisibles + " vis.§7, §d" + zone.bonbonsNonVisibles + " invis.";
    }

    /**
     * Flèche dynamique pointant vers le centre géométrique de la zone sélectionnée.
     * Visible en permanence, tourne selon la position et l'orientation du joueur.
     * Se désactive automatiquement quand le joueur entre dans les limites de la zone.
     */
    private static void afficherFleche(GuiGraphics guiGraphics, int largeurEcran, int hauteurEcran) {
        String cible = zoneCiblee;
        if (cible == null) {
            return;
        }
        ZoneClient zone = obtenirZone(cible);
        Minecraft mc = Minecraft.getInstance();
        if (zone == null || mc.player == null) {
            return;
        }

        // Désactivation automatique quand le joueur entre dans les limites de la zone
        if (zone.contientPosition(mc.player.getX(), mc.player.getZ())) {
            zoneCiblee = null;
            return;
        }

        double dx = zone.centreX - mc.player.getX();
        double dz = zone.centreZ - mc.player.getZ();
        float lacet = (float) Math.toRadians(mc.player.getYRot());

        // Composantes avant / droite par rapport au regard du joueur
        double avant = -dx * Math.sin(lacet) + dz * Math.cos(lacet);
        double droite = -dx * Math.cos(lacet) - dz * Math.sin(lacet);
        float angleDegres = (float) Math.toDegrees(Math.atan2(droite, avant));

        int centreX = largeurEcran / 2;
        int centreY = 46;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centreX, centreY, 0);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(angleDegres));

        // Dessiner une flèche pointant vers le haut (tournée par la pose)
        int couleur = 0xFF00E5FF;
        // Pointe
        guiGraphics.fill(-1, -14, 1, -10, couleur);
        guiGraphics.fill(-3, -10, 3, -8, couleur);
        guiGraphics.fill(-5, -8, 5, -6, couleur);
        // Corps
        guiGraphics.fill(-2, -8, 2, 12, couleur);

        guiGraphics.pose().popPose();

        // Nom de la zone ciblée + distance sous la flèche
        double distance = Math.sqrt(dx * dx + dz * dz);
        String texte = "§b" + zone.nom + " §7(" + (int) distance + " m)";
        guiGraphics.drawCenteredString(mc.font, texte, centreX, centreY + 16, 0xFFFFFF);
    }
}

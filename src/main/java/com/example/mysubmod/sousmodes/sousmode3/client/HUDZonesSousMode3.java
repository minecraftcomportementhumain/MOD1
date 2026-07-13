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
        /** Point visé par la flèche : barycentre des bonbons restants de la zone
         *  (recalculé par le serveur, mis à jour avec les compteurs) */
        public double centreX;
        public double centreZ;
        public int bonbonsVisibles;
        public int bonbonsNonVisibles;
        public int bonbonsBleus;   // Détail par type (Sous-mode 2 sur carte)
        public int bonbonsRouges;
        /** Plages de cellules triées « z, x0, longueur » (jamais développées : une zone
         *  d'une grande carte peut compter des millions de cellules) */
        public List<int[]> plages = new ArrayList<>();

        ZoneClient(String nom, double centreX, double centreZ) {
            this.nom = nom;
            this.centreX = centreX;
            this.centreZ = centreZ;
        }

        public boolean contientPosition(double x, double z) {
            return PaquetZonesSousMode3.plagesContiennent(plages, (int) Math.floor(x), (int) Math.floor(z));
        }
    }

    private static final List<ZoneClient> ZONES = new ArrayList<>();
    private static boolean actif = false;
    private static String zoneCiblee = null; // Cible de la flèche (propre à chaque joueur, côté client)
    /** Panneau affiché/masqué par le joueur (touche J, rebindable) — affiché par défaut,
     *  choix conservé pour la session client. La flèche de navigation n'est pas concernée. */
    private static boolean panneauAffiche = true;

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
                zone.plages = zoneReseau.plages;
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
                    // Le point de navigation suit les bonbons restants
                    zone.centreX = maj.centreX;
                    zone.centreZ = maj.centreZ;
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

    /** Bascule l'affichage du panneau des parcelles ; retourne le nouvel état (vrai = affiché). */
    public static boolean basculerPanneau() {
        panneauAffiche = !panneauAffiche;
        return panneauAffiche;
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
        // Panneau masqué par le joueur : ne garder que la flèche de navigation,
        // demandée volontairement via [N]
        if (!panneauAffiche) {
            afficherFleche(guiGraphics, largeurEcran, hauteurEcran);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font police = mc.font;

        List<ZoneClient> zones = obtenirZones();

        // Spécialisation du joueur (option « Spécialisation » du menu N — null tant
        // qu'aucun bonbon typé n'est consommé)
        com.example.mysubmod.sousmodes.sousmode3.TypeRessource specialisation =
            SpecialisationClientSousMode3.obtenirSpecialisation();
        String texteSpec = specialisation == null ? null : "Spécialisation: "
            + (specialisation == com.example.mysubmod.sousmodes.sousmode3.TypeRessource.BONBON_BLEU ? "Bleu" : "Rouge");

        // Largeur du panneau adaptée au contenu (le panneau est ancré au bord DROIT :
        // à largeur fixe, une ligne longue — nom + « N bleu(s), N rouge(s), N invis. »
        // avec la spécialisation — sortait de l'écran). Un nom de parcelle trop long est
        // abrégé (…) pour que les compteurs restent toujours entièrement lisibles.
        String pied = "§8[N] cibler une parcelle";
        int largeurLigneMax = largeurEcran / 2;
        List<String> lignes = new ArrayList<>(zones.size());
        int largeurContenu = Math.max(police.width(pied),
            texteSpec != null ? police.width(texteSpec) : 0);
        for (ZoneClient zone : zones) {
            String prefixe = zone.nom.equals(zoneCiblee) ? "§b➤ " : "§f";
            String suffixe = " §7— " + formaterCompteurs(zone);
            String nom = zone.nom;
            if (police.width(prefixe + nom + suffixe) > largeurLigneMax) {
                while (nom.length() > 1
                    && police.width(prefixe + nom + "…" + suffixe) > largeurLigneMax) {
                    nom = nom.substring(0, nom.length() - 1);
                }
                nom = nom + "…";
            }
            String texte = prefixe + nom + suffixe;
            lignes.add(texte);
            largeurContenu = Math.max(largeurContenu, police.width(texte));
        }

        // Panneau des zones : coin supérieur droit, sous la minuterie
        int largeurPanneau = Math.max(170, largeurContenu + 4);
        int x = largeurEcran - largeurPanneau - 5;
        int y = 30;
        int hauteurLigne = 11;
        int hauteurPanneau = 16 + zones.size() * hauteurLigne + 12 + (texteSpec != null ? 12 : 0);

        guiGraphics.fill(x - 4, y - 4, x + largeurPanneau, y + hauteurPanneau - 4, 0x80000000);

        if (texteSpec != null) {
            guiGraphics.drawString(police, texteSpec, x, y, specialisation.obtenirCouleur());
            y += 12;
        }

        guiGraphics.drawString(police, "§6Parcelles :", x, y, 0xFFFFFF);
        y += 13;

        for (String ligne : lignes) {
            guiGraphics.drawString(police, ligne, x, y, 0xFFFFFF);
            y += hauteurLigne;
        }

        guiGraphics.drawString(police, pied, x, y + 2, 0xFFFFFF);

        // Flèche de navigation
        afficherFleche(guiGraphics, largeurEcran, hauteurEcran);
    }

    /** Format des compteurs d'une zone */
    public static String formaterCompteurs(ZoneClient zone) {
        // Avec la spécialisation (option de la config) : détail Bleu/Rouge en plus des
        // bonbons non-visibles ; sans spécialisation, les compteurs typés restent à zéro
        if (zone.bonbonsBleus > 0 || zone.bonbonsRouges > 0) {
            return "§9" + zone.bonbonsBleus + " bleu(s)§7, §c" + zone.bonbonsRouges
                + " rouge(s)§7, §d" + zone.bonbonsNonVisibles + " invis.";
        }
        return "§e" + zone.bonbonsVisibles + " vis.§7, §d" + zone.bonbonsNonVisibles + " invis.";
    }

    /** Distance (blocs) au point visé sous laquelle la flèche s'éteint : assez près
     *  pour repérer les bonbons à l'œil sans révéler leur position exacte */
    private static final double RAYON_ARRIVEE = 15.0;

    /**
     * Flèche dynamique pointant vers le barycentre des bonbons restants de la zone
     * sélectionnée (le point suit les ramassages et réapparitions). Visible en
     * permanence, tourne selon la position et l'orientation du joueur. Se désactive
     * automatiquement à l'approche du point visé — et non plus à l'entrée de la zone,
     * qui laissait le joueur sans repère sur une grande île.
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

        // Parcelle vidée de ses bonbons : plus rien à viser, la flèche disparaît
        if (zone.bonbonsVisibles <= 0 && zone.bonbonsNonVisibles <= 0
            && zone.bonbonsBleus <= 0 && zone.bonbonsRouges <= 0) {
            zoneCiblee = null;
            return;
        }

        double dx = zone.centreX - mc.player.getX();
        double dz = zone.centreZ - mc.player.getZ();

        // Désactivation automatique à l'approche du point visé — seulement quand la
        // parcelle contient encore des bonbons (le point est alors leur barycentre, donc
        // pertinent). Vide, le point retombe sur le centre géométrique : ne pas s'éteindre
        // dessus, on l'a déjà écarté ci-dessus.
        if (Math.sqrt(dx * dx + dz * dz) <= RAYON_ARRIVEE) {
            zoneCiblee = null;
            return;
        }

        float lacet = (float) Math.toRadians(mc.player.getYRot());

        // Composantes avant / droite par rapport au regard du joueur
        double avant = -dx * Math.sin(lacet) + dz * Math.cos(lacet);
        double droite = -dx * Math.cos(lacet) - dz * Math.sin(lacet);
        float angleDegres = (float) Math.toDegrees(Math.atan2(droite, avant));

        int centreX = largeurEcran / 2;
        // Bas de l'écran, au-dessus des barres de vie/faim (la flèche s'étend de -14 à
        // +12 autour du centre, et le libellé « zone (N m) » occupe +16 à +25)
        int centreY = hauteurEcran - 75;

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

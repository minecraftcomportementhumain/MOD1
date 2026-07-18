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
 * HUD des zones du Sous-mode 3 : liste les parcelles de la carte avec les compteurs
 * de bonbons visibles / non-visibles restants (une parcelle vidée reste listée et
 * ciblable), et affiche la flèche de navigation vers la zone sélectionnée.
 *
 * <p>Le panneau est borné en hauteur et paginé : au-delà du plafond de lignes (adapté à
 * l'écran, {@value #LIGNES_MAX_PANNEAU} au maximum), les parcelles sont réparties en pages
 * que la touche F fait défiler — page suivante, puis masqué après la dernière (Maj+F :
 * sens inverse). Toutes les parcelles et leurs compteurs restent ainsi consultables sans
 * que le panneau sorte de l'écran ni n'en occupe une part démesurée ; l'écran de ciblage
 * [N] ne liste, lui, que les noms.</p>
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
    /** Panneau affiché/masqué par le joueur (touche F, rebindable) — affiché par défaut,
     *  choix conservé pour la session client. La flèche de navigation n'est pas concernée. */
    private static boolean panneauAffiche = true;

    /** Hauteur d'une ligne de parcelle du panneau (px). */
    private static final int HAUTEUR_LIGNE_PANNEAU = 11;
    /** Plafond dur de lignes de parcelles par page : même sur un grand écran, le panneau ne
     *  doit pas dominer l'affichage — au-delà, les parcelles se répartissent en pages (touche F). */
    private static final int LIGNES_MAX_PANNEAU = 12;
    /** Marge minimale conservée sous le panneau (px), pour ne jamais atteindre le bas de l'écran. */
    private static final int MARGE_BASSE_PANNEAU = 45;

    /** Page affichée du panneau (base 0) — la touche F fait défiler les pages. */
    private static int pageCourante = 0;

    public static synchronized void mettreAJourZones(List<PaquetZonesSousMode3.ZoneReseau> zonesRecues,
                                                     boolean complet, boolean reinitialiser) {
        if (reinitialiser) {
            zoneCiblee = null; // Flèche réinitialisée (ex. reconnexion en cours de partie)
            pageCourante = 0;  // Repartir de la première page du panneau
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
        pageCourante = 0;
    }

    /**
     * Touche F : fait défiler le panneau des parcelles — page suivante, puis masqué après la
     * dernière page, puis retour à la page 1 ({@code recule} = sens inverse, convention Maj du
     * mod). Avec une seule page, simple bascule affiché/masqué (comportement historique).
     * Le total de pages est recalculé ici depuis l'état courant ({@code hauteurEcran} du client),
     * jamais repris d'une valeur figée au rendu — sans quoi, panneau masqué, F se fiait à un
     * total périmé. Retourne le message d'état à montrer au joueur.
     */
    public static synchronized String basculerPanneauOuPage(boolean recule, String nomTouche, int hauteurEcran) {
        int total = totalPagesPour(hauteurEcran);
        if (!panneauAffiche) {
            panneauAffiche = true;
            pageCourante = recule ? total - 1 : 0;
        } else if (recule) {
            if (pageCourante <= 0) {
                panneauAffiche = false;
            } else {
                pageCourante--;
            }
        } else if (pageCourante >= total - 1) {
            panneauAffiche = false;
        } else {
            pageCourante++;
        }
        if (!panneauAffiche) {
            return "§7HUD des parcelles masqué — §e[" + nomTouche + "]§7 pour le réafficher";
        }
        if (total > 1) {
            return "§7Parcelles — page §e" + (pageCourante + 1) + "§7/§e" + total;
        }
        return "§7HUD des parcelles affiché";
    }

    /** Lignes de parcelles par page, adaptées à la hauteur d'écran (bornées par
     *  LIGNES_MAX_PANNEAU). Partagé par le rendu et la pagination pour qu'ils s'accordent. */
    private static int lignesParPage(int hauteurEcran) {
        boolean spec = SpecialisationClientSousMode3.obtenirSpecialisation() != null;
        int espaceVertical = hauteurEcran - MARGE_BASSE_PANNEAU - 30 - 16 - 12 - (spec ? 12 : 0);
        return Math.max(4, Math.min(LIGNES_MAX_PANNEAU, espaceVertical / HAUTEUR_LIGNE_PANNEAU));
    }

    /** Nombre total de pages pour l'état courant (nombre de parcelles + hauteur d'écran). */
    private static synchronized int totalPagesPour(int hauteurEcran) {
        int parPage = lignesParPage(hauteurEcran);
        return Math.max(1, (ZONES.size() + parPage - 1) / parPage);
    }

    /** Cibler une zone : active la flèche de navigation (une seule flèche à la fois).
     *  Le choix — ou la désactivation — est rapporté au serveur pour la journalisation de
     *  recherche : le ciblage est un état purement client, invisible du serveur sinon. */
    public static void ciblerZone(String nom) {
        zoneCiblee = nom;
        envoyerCiblage(nom != null ? nom : "", nom != null
            ? com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetCiblageParcelleSousMode3.RAISON_CHOISIE
            : com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetCiblageParcelleSousMode3.RAISON_DESACTIVEE);
    }

    /** Rapporte un événement de ciblage au serveur (ligne CIBLAGE_PARCELLE du journal CSV). */
    private static void envoyerCiblage(String zone, String raison) {
        if (Minecraft.getInstance().getConnection() != null) {
            com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.sendToServer(
                new com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetCiblageParcelleSousMode3(zone, raison));
        }
    }

    public static String obtenirZoneCiblee() {
        return zoneCiblee;
    }

    // ==================== Rendu ====================

    public static void afficher(GuiGraphics guiGraphics, int largeurEcran, int hauteurEcran) {
        if (!actif) {
            return;
        }
        // Panneau interdit par la config de partie (menu N › Interface), ou masqué par le
        // joueur (touche F) : ne garder que la flèche de navigation, demandée via [N]
        if (!OptionsHudClientSousMode3.panneauAutorise() || !panneauAffiche) {
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
        // Lignes par page : helper partagé avec la pagination (touche F), pour que F voie
        // toujours le MÊME total de pages que ce qui est affiché (la place du pied — 12 px —
        // est toujours réservée dans le calcul).
        int lignesPage = lignesParPage(hauteurEcran);

        // Pagination par la touche F (suivante ; Maj = précédente ; masqué en fin de cycle) :
        // toutes les parcelles restent consultables avec leurs compteurs, sans agrandir le
        // panneau — l'écran [N] ne liste plus que les noms, pour le ciblage.
        int totalPages = Math.max(1, (zones.size() + lignesPage - 1) / lignesPage);
        pageCourante = Math.max(0, Math.min(pageCourante, totalPages - 1));
        int debut = pageCourante * lignesPage;
        List<ZoneClient> zonesAffichees = zones.subList(debut,
            Math.min(zones.size(), debut + lignesPage));

        boolean flecheOk = OptionsHudClientSousMode3.flecheAutorisee();
        String pied;
        if (flecheOk && totalPages > 1) {
            pied = "§8[N] cibler · [F] page suiv.";
        } else if (flecheOk) {
            pied = "§8[N] cibler une parcelle";
        } else if (totalPages > 1) {
            pied = "§8[F] page suivante";
        } else {
            pied = null;
        }

        int largeurLigneMax = largeurEcran / 2;
        List<String> lignes = new ArrayList<>(zonesAffichees.size());
        int largeurContenu = Math.max(pied != null ? police.width(pied) : 0,
            texteSpec != null ? police.width(texteSpec) : 0);
        for (ZoneClient zone : zonesAffichees) {
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
        int hauteurLigne = HAUTEUR_LIGNE_PANNEAU;
        int hauteurPanneau = 16 + lignes.size() * hauteurLigne + (pied != null ? 12 : 0)
            + (texteSpec != null ? 12 : 0);

        guiGraphics.fill(x - 4, y - 4, x + largeurPanneau, y + hauteurPanneau - 4, 0x80000000);

        if (texteSpec != null) {
            guiGraphics.drawString(police, texteSpec, x, y, specialisation.obtenirCouleur());
            y += 12;
        }

        String titrePanneau = totalPages > 1
            ? "§6Parcelles §7(" + (pageCourante + 1) + "/" + totalPages + ") :"
            : "§6Parcelles :";
        guiGraphics.drawString(police, titrePanneau, x, y, 0xFFFFFF);
        y += 13;

        for (String ligne : lignes) {
            guiGraphics.drawString(police, ligne, x, y, 0xFFFFFF);
            y += hauteurLigne;
        }

        if (pied != null) {
            guiGraphics.drawString(police, pied, x, y + 2, 0xFFFFFF);
        }

        // Flèche de navigation
        afficherFleche(guiGraphics, largeurEcran, hauteurEcran);
    }

    /** Format des compteurs d'une zone (n'apparaît que dans le panneau : l'écran de
     *  ciblage [N] ne liste que les noms) */
    private static String formaterCompteurs(ZoneClient zone) {
        // Avec la spécialisation (option de la config) : détail Bleu/Rouge en plus des
        // bonbons non-visibles ; sans spécialisation, les compteurs typés restent à zéro.
        // La COULEUR du nombre porte le type (bleu/rouge) — pas de libellé, pour garder
        // les lignes courtes.
        if (zone.bonbonsBleus > 0 || zone.bonbonsRouges > 0) {
            return "§9" + zone.bonbonsBleus + "§7, §c" + zone.bonbonsRouges
                + "§7, §d" + zone.bonbonsNonVisibles + " invis.";
        }
        return "§e" + zone.bonbonsVisibles + " vis.§7, §d" + zone.bonbonsNonVisibles + " invis.";
    }

    /** Distance (blocs) au point visé sous laquelle la flèche s'éteint : assez près
     *  pour repérer les bonbons à l'œil sans révéler leur position exacte */
    private static final double RAYON_ARRIVEE = 15.0;

    /**
     * Flèche dynamique pointant vers le barycentre des bonbons restants de la zone
     * sélectionnée (le point suit les ramassages et réapparitions) — ou, si la parcelle
     * est vide, vers son centre géométrique (repli envoyé par le serveur : la cibler
     * reste utile, par exemple pour s'y rendre en attendant les réapparitions). Visible
     * en permanence, tourne selon la position et l'orientation du joueur. Se désactive
     * automatiquement à l'« arrivée » : à l'approche du point visé quand la parcelle est
     * garnie — et non à l'entrée de la zone, qui laissait le joueur sans repère sur une
     * grande île — ou à l'entrée dans la parcelle quand elle est vide.
     */
    private static void afficherFleche(GuiGraphics guiGraphics, int largeurEcran, int hauteurEcran) {
        // Navigation interdite par la config de partie (menu N › Interface) : jamais de flèche
        // (le ciblage est aussi bloqué en amont, ceci est une défense en profondeur)
        if (!OptionsHudClientSousMode3.flecheAutorisee()) {
            return;
        }
        String cible = zoneCiblee;
        if (cible == null) {
            return;
        }
        ZoneClient zone = obtenirZone(cible);
        Minecraft mc = Minecraft.getInstance();
        if (zone == null || mc.player == null) {
            return;
        }

        // Parcelle vide : la flèche reste active et vise le centre géométrique (repli
        // du serveur) — seul le critère d'arrivée change ci-dessous
        boolean parcelleVide = zone.bonbonsVisibles <= 0 && zone.bonbonsNonVisibles <= 0
            && zone.bonbonsBleus <= 0 && zone.bonbonsRouges <= 0;

        double dx = zone.centreX - mc.player.getX();
        double dz = zone.centreZ - mc.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Extinction automatique à l'« arrivée » : à l'approche du point visé (barycentre
        // des bonbons restants, ou centre de repli si la parcelle est vide), et aussi, pour
        // une parcelle vide, à l'entrée dans la parcelle — son centre peut être loin de la
        // frontière et vider soi-même la parcelle depuis l'intérieur doit éteindre aussitôt.
        // La clause de distance reste nécessaire même vide : le centre géométrique d'une
        // parcelle concave (en L) peut être hors de ses cellules, et la pseudo-parcelle
        // « Hors parcelle » n'a aucune cellule (plages vides) — sans elle, la flèche
        // resterait allumée indéfiniment une fois le point atteint.
        if (distance <= RAYON_ARRIVEE
            || (parcelleVide && zone.contientPosition(mc.player.getX(), mc.player.getZ()))) {
            zoneCiblee = null;
            // Arrivée au but : rapportée aussi (fin de l'épisode de navigation dans le journal)
            envoyerCiblage(cible,
                com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetCiblageParcelleSousMode3.RAISON_ARRIVEE);
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
        String texte = "§b" + zone.nom + " §7(" + (int) distance + " m)";
        guiGraphics.drawCenteredString(mc.font, texte, centreX, centreY + 16, 0xFFFFFF);
    }
}

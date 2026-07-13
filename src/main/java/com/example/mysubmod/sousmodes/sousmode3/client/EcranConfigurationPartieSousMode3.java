package com.example.mysubmod.sousmodes.sousmode3.client;

import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode3.ConfigPartieSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetLancerPartieConfigureeSousMode3;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Menu N du Sous-mode 3 : l'admin choisit les conditions de partie via des cases à cocher et
 * des sélecteurs (clic = valeur suivante, Maj+clic = précédente) avant de lancer. La config
 * ({@link ConfigPartieSousMode3}) est le modèle vivant, tenu à jour à chaque interaction, puis
 * envoyée telle quelle au serveur au lancement.
 *
 * <p>La mise en page s'adapte à la taille de la fenêtre (mode compact sous 310 px de haut,
 * largeur de colonne réduite si nécessaire) pour rester utilisable à la résolution GUI
 * minimale de Minecraft (427×240). Certaines options sont grisées selon la carte active —
 * voir {@link FaitsCarteClientSousMode3}.</p>
 */
@OnlyIn(Dist.CLIENT)
public class EcranConfigurationPartieSousMode3 extends Screen {

    private static final int ECART_COLONNE = 12;

    // Valeurs proposées par les sélecteurs
    private static final int[] DUREES_MIN = {5, 10, 15, 20, 30, 45, 60};
    private static final int[] DECOMPTES_S = {0, 3, 5, 10, 15, 30};
    private static final int[] PERTES_POINTS = {1, 2, 3, 4, 6, 8};       // 1 point = ½ cœur
    private static final int[] INTERVALLES_S = {3, 5, 10, 15, 20, 30};
    private static final int[] SANTES_MAX_POINTS = {10, 14, 20, 30, 40}; // en points (20 = 10 cœurs)
    /** Soin par bonbon, en points de vie (2 = 1 cœur, comportement historique ; 0 = aucun soin). */
    private static final float[] SOINS_POINTS = {0, 1, 2, 3, 4, 6, 8, 12, 16, 20};
    /** Temps de minage par bloc, en secondes (0 = vitesses vanilla). */
    private static final float[] TEMPS_MINAGE_S = {0, 0.5f, 1, 2, 3, 5, 8, 10, 15, 20, 30};

    private final ConfigPartieSousMode3 config = new ConfigPartieSousMode3();
    private final List<Entete> entetes = new ArrayList<>();

    // Métriques calculées dans init() selon la taille de la fenêtre
    private int largeurColonne;
    private int pasLigne;
    private int pasTitre;
    private int hauteurWidget;
    private boolean compact;

    public EcranConfigurationPartieSousMode3() {
        super(Component.literal("Sous-mode 3 — Conditions de partie"));
    }

    private record Entete(int x, int y, String texte) {
    }

    @Override
    protected void init() {
        super.init();
        entetes.clear();

        // La carte impose la destruction de blocs si elle cache des bonbons non-visibles.
        if (FaitsCarteClientSousMode3.aBonbonsNonVisibles()) {
            config.destructionBloc = true;
        }

        // Mode compact sous 340 px : toutes les options tiennent sur UN seul écran (pas de
        // sous-fenêtre), au prix de rangées resserrées. La fenêtre Minecraft par défaut
        // donne une GUI de 427×240.
        compact = this.height < 340;
        pasLigne = compact ? 15 : 20;
        pasTitre = compact ? 11 : 15;
        hauteurWidget = compact ? 14 : 20;
        largeurColonne = Math.max(126, Math.min(150, (this.width - 2 * ECART_COLONNE - 12) / 3));
        int yDepart = compact ? 17 : 32;

        int largeurTotale = 3 * largeurColonne + 2 * ECART_COLONNE;
        int xA = (this.width - largeurTotale) / 2;
        int xB = xA + largeurColonne + ECART_COLONNE;
        int xC = xB + largeurColonne + ECART_COLONNE;

        // ---- Colonne A : Durée & Santé ----
        int[] ya = {yDepart};
        titre(xA, ya, "§e§lDurée & rythme");
        selecteurInt(xA, ya, "Durée: ", DUREES_MIN, config.dureePartieMinutes,
            v -> config.dureePartieMinutes = v, v -> v + " min");
        selecteurInt(xA, ya, "Décompte: ", DECOMPTES_S, config.decompteSecondes,
            v -> config.decompteSecondes = v, v -> v + " s");
        caseAcocher(xA, ya, "Sans limite", config.sansLimiteTemps, v -> config.sansLimiteTemps = v);

        titre(xA, ya, "§e§lSanté & survie");
        caseAcocher(xA, ya, "Dégradation santé", config.degradationSante, v -> config.degradationSante = v);
        selecteurInt(xA, ya, "Perte: ", PERTES_POINTS, (int) config.perteSanteParTick,
            v -> config.perteSanteParTick = v, v -> coeurs(v) + " cœur");
        selecteurInt(xA, ya, "Intervalle: ", INTERVALLES_S, config.intervalleDegradationSecondes,
            v -> config.intervalleDegradationSecondes = v, v -> v + " s");
        selecteurInt(xA, ya, "Vie max: ", SANTES_MAX_POINTS, config.santeMaxPoints,
            v -> config.santeMaxPoints = v, v -> coeurs(v) + " cœurs");
        caseAcocher(xA, ya, "Régén. naturelle", config.regenerationNaturelle, v -> config.regenerationNaturelle = v);
        caseAcocher(xA, ya, "Réapparition", config.reapparitionAutorisee, v -> config.reapparitionAutorisee = v);

        // ---- Colonne B : Bonbons & minage, Environnement ----
        int[] yb = {yDepart};
        titre(xB, yb, "§e§lBonbons & minage");
        selecteurFloat(xB, yb, "Soin std: ", SOINS_POINTS, config.soinBonbonStandard,
            v -> config.soinBonbonStandard = v, EcranConfigurationPartieSousMode3::coeursFloat);
        selecteurFloat(xB, yb, "Soin bleu: ", SOINS_POINTS, config.soinBonbonBleu,
            v -> config.soinBonbonBleu = v, EcranConfigurationPartieSousMode3::coeursFloat);
        selecteurFloat(xB, yb, "Soin rouge: ", SOINS_POINTS, config.soinBonbonRouge,
            v -> config.soinBonbonRouge = v, EcranConfigurationPartieSousMode3::coeursFloat);
        selecteurFloat(xB, yb, "Minage: ", TEMPS_MINAGE_S, config.tempsMinageSecondes,
            v -> config.tempsMinageSecondes = v, EcranConfigurationPartieSousMode3::secondesMinage);

        titre(xB, yb, "§e§lEnvironnement");
        caseAcocher(xB, yb, "Jour permanent", config.jourPermanent, v -> config.jourPermanent = v);
        caseAcocher(xB, yb, "Dégâts de chute", config.degatsChute, v -> config.degatsChute = v);
        caseAcocher(xB, yb, "Noyade mortelle", config.noyadeMortelle, v -> config.noyadeMortelle = v);
        caseAcocher(xB, yb, "Faim", config.faim, v -> config.faim = v);
        caseAcocher(xB, yb, "PvP entre joueurs", config.pvp, v -> config.pvp = v);
        caseAcocher(xB, yb, "Pluie", config.pluie, v -> config.pluie = v);

        // ---- Colonne C : Mode, Interactions & fin de partie ----
        int[] yc = {yDepart};
        titre(xC, yc, "§e§lMode");
        if (!FaitsCarteClientSousMode3.aBonbonsTypes()) {
            config.specialisation = false; // tous les bonbons de la carte ne sont pas typés Bleu/Rouge
        }
        Checkbox caseSpecialisation = caseAcocher(xC, yc, "Spécialisation B/R", config.specialisation,
            v -> config.specialisation = v);
        caseSpecialisation.active = FaitsCarteClientSousMode3.aBonbonsTypes();
        if (!FaitsCarteClientSousMode3.aParcelles()) {
            config.selectionZoneDepart = false; // la carte n'a aucune parcelle
        }
        Checkbox caseZone = caseAcocher(xC, yc, "Parcelle au choix", config.selectionZoneDepart,
            v -> config.selectionZoneDepart = v);
        caseZone.active = FaitsCarteClientSousMode3.aParcelles();
        caseAcocher(xC, yc, "Bonus sprint", config.bonusSprint, v -> config.bonusSprint = v);

        titre(xC, yc, "§e§lInteractions");
        caseAcocher(xC, yc, "Crafting", config.crafting, v -> config.crafting = v);
        Checkbox caseDestruction = caseAcocher(xC, yc, "Destruction blocs", config.destructionBloc,
            v -> config.destructionBloc = v);
        if (FaitsCarteClientSousMode3.aBonbonsNonVisibles()) {
            caseDestruction.active = false; // imposée par la carte (bonbons non-visibles à miner)
        }
        caseAcocher(xC, yc, "Placement blocs", config.placementBloc, v -> config.placementBloc = v);
        caseAcocher(xC, yc, "Jeter des objets", config.dropObjet, v -> config.dropObjet = v);
        caseAcocher(xC, yc, "Drop à la mort", config.dropInventaireMort, v -> config.dropInventaireMort = v);
        caseAcocher(xC, yc, "Manger à vie max", config.mangerDepasseMax, v -> config.mangerDepasseMax = v);

        titre(xC, yc, "§e§lFin de partie");
        ajouterBasculeClassement(xC, yc);
        caseAcocher(xC, yc, "Dernier survivant", config.finAuDernierSurvivant, v -> config.finAuDernierSurvivant = v);

        // ---- Boutons de bas de page (« Par défaut » y a rejoint Lancer/Fermer : la barre
        // du haut est supprimée pour donner leur hauteur aux colonnes) ----
        int hBouton = compact ? 18 : 20;
        int yBas = this.height - hBouton - (compact ? 4 : 8);
        int xBas = (this.width - 380) / 2;
        addRenderableWidget(Button.builder(Component.literal("Par défaut"), b -> reinitialiserConfig())
            .bounds(xBas, yBas, 100, hBouton).build());
        addRenderableWidget(Button.builder(Component.literal("§aLancer la partie"), b -> {
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetLancerPartieConfigureeSousMode3(config));
            this.onClose();
        }).bounds(xBas + 104, yBas, 150, hBouton).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> this.onClose())
            .bounds(xBas + 258, yBas, 122, hBouton).build());
    }

    private void titre(int x, int[] y, String texte) {
        entetes.add(new Entete(x, y[0] + 2, texte));
        y[0] += pasTitre;
    }

    private Checkbox caseAcocher(int x, int[] y, String texte, boolean coche, Consumer<Boolean> surChangement) {
        CaseConfig c = new CaseConfig(x, y[0], largeurColonne, hauteurWidget,
            Component.literal(texte), coche, surChangement);
        addRenderableWidget(c);
        y[0] += pasLigne;
        return c;
    }

    private void selecteurInt(int x, int[] y, String prefixe, int[] valeurs, int valeurCourante,
                              IntConsumer setter, IntFunction<String> formateur) {
        int[] idx = {indexDe(valeurs, valeurCourante)};
        Button bouton = Button.builder(Component.literal(prefixe + formateur.apply(valeurs[idx[0]])), b -> {
            boolean arriere = Screen.hasShiftDown();
            idx[0] = (idx[0] + (arriere ? valeurs.length - 1 : 1)) % valeurs.length;
            setter.accept(valeurs[idx[0]]);
            b.setMessage(Component.literal(prefixe + formateur.apply(valeurs[idx[0]])));
        }).bounds(x, y[0], largeurColonne, hauteurWidget).build();
        addRenderableWidget(bouton);
        y[0] += pasLigne;
    }

    /** Variante float de {@link #selecteurInt} (soins des bonbons, temps de minage). */
    private void selecteurFloat(int x, int[] y, String prefixe, float[] valeurs, float valeurCourante,
                                Consumer<Float> setter, java.util.function.Function<Float, String> formateur) {
        int[] idx = {indexDe(valeurs, valeurCourante)};
        Button bouton = Button.builder(Component.literal(prefixe + formateur.apply(valeurs[idx[0]])), b -> {
            boolean arriere = Screen.hasShiftDown();
            idx[0] = (idx[0] + (arriere ? valeurs.length - 1 : 1)) % valeurs.length;
            setter.accept(valeurs[idx[0]]);
            b.setMessage(Component.literal(prefixe + formateur.apply(valeurs[idx[0]])));
        }).bounds(x, y[0], largeurColonne, hauteurWidget).build();
        addRenderableWidget(bouton);
        y[0] += pasLigne;
    }

    private static int indexDe(float[] valeurs, float valeur) {
        for (int i = 0; i < valeurs.length; i++) {
            if (valeurs[i] == valeur) {
                return i;
            }
        }
        return 0;
    }

    /** Formate un soin en cœurs (2 points = 1 cœur), « aucun » pour 0. */
    private static String coeursFloat(float points) {
        if (points == 0) {
            return "aucun";
        }
        double h = points / 2.0;
        return (h == Math.floor(h) ? String.valueOf((int) h) : String.valueOf(h)) + " cœur(s)";
    }

    /** Formate un temps de minage (0 = vitesses vanilla). */
    private static String secondesMinage(float secondes) {
        if (secondes == 0) {
            return "vanilla";
        }
        return (secondes == Math.floor(secondes)
            ? String.valueOf((int) secondes) : String.valueOf(secondes)) + " s / bloc";
    }

    private void ajouterBasculeClassement(int x, int[] y) {
        Button bouton = Button.builder(Component.literal(libelleClassement()), b -> {
            config.classementParSurvie = !config.classementParSurvie;
            b.setMessage(Component.literal(libelleClassement()));
        }).bounds(x, y[0], largeurColonne, hauteurWidget).build();
        addRenderableWidget(bouton);
        y[0] += pasLigne;
    }

    private String libelleClassement() {
        return "Éliminés: " + (config.classementParSurvie ? "Survie" : "Bonbons");
    }

    private static int indexDe(int[] valeurs, int valeur) {
        for (int i = 0; i < valeurs.length; i++) {
            if (valeurs[i] == valeur) {
                return i;
            }
        }
        return 0;
    }

    /** Formate un nombre de points de vie en cœurs (1 point = ½ cœur), sans « .0 » superflu. */
    private static String coeurs(int points) {
        double h = points / 2.0;
        return (h == Math.floor(h)) ? String.valueOf((int) h) : String.valueOf(h);
    }

    /** Remet toutes les conditions aux valeurs par défaut (comportement historique) */
    private void reinitialiserConfig() {
        copier(new ConfigPartieSousMode3(), config);
        rebuildWidgets();
    }

    /** Recopie tous les champs de {@code source} dans {@code cible} (le modèle vivant de l'écran). */
    private static void copier(ConfigPartieSousMode3 source, ConfigPartieSousMode3 cible) {
        cible.dureePartieMinutes = source.dureePartieMinutes;
        cible.sansLimiteTemps = source.sansLimiteTemps;
        cible.decompteSecondes = source.decompteSecondes;
        cible.degradationSante = source.degradationSante;
        cible.perteSanteParTick = source.perteSanteParTick;
        cible.intervalleDegradationSecondes = source.intervalleDegradationSecondes;
        cible.regenerationNaturelle = source.regenerationNaturelle;
        cible.reapparitionAutorisee = source.reapparitionAutorisee;
        cible.santeMaxPoints = source.santeMaxPoints;
        cible.soinBonbonStandard = source.soinBonbonStandard;
        cible.soinBonbonBleu = source.soinBonbonBleu;
        cible.soinBonbonRouge = source.soinBonbonRouge;
        cible.tempsMinageSecondes = source.tempsMinageSecondes;
        cible.specialisation = source.specialisation;
        cible.dureePenaliteSpecialisationSecondes = source.dureePenaliteSpecialisationSecondes;
        cible.multiplicateurSantePenalite = source.multiplicateurSantePenalite;
        cible.bonusSprint = source.bonusSprint;
        cible.selectionZoneDepart = source.selectionZoneDepart;
        cible.jourPermanent = source.jourPermanent;
        cible.degatsChute = source.degatsChute;
        cible.noyadeMortelle = source.noyadeMortelle;
        cible.faim = source.faim;
        cible.pvp = source.pvp;
        cible.monstresHostiles = source.monstresHostiles;
        cible.pluie = source.pluie;
        cible.classementParSurvie = source.classementParSurvie;
        cible.finAuDernierSurvivant = source.finAuDernierSurvivant;
        cible.crafting = source.crafting;
        cible.destructionBloc = source.destructionBloc;
        cible.placementBloc = source.placementBloc;
        cible.dropObjet = source.dropObjet;
        cible.dropInventaireMort = source.dropInventaireMort;
        cible.mangerDepasseMax = source.mangerDepasseMax;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, compact ? 4 : 10, 0xFFFFFF);
        if (!compact) {
            guiGraphics.drawCenteredString(this.font,
                "§7Les conditions imposées par la carte ne sont pas modifiables ici",
                this.width / 2, 20, 0xAAAAAA);
        }
        for (Entete entete : entetes) {
            guiGraphics.drawString(this.font, entete.texte(), entete.x(), entete.y(), 0xFFFFFF);
        }
        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Case à cocher qui répercute son état dans la config à chaque clic. */
    private static class CaseConfig extends Checkbox {
        private final Consumer<Boolean> surChangement;

        CaseConfig(int x, int y, int largeur, int hauteur, Component texte, boolean coche,
                   Consumer<Boolean> surChangement) {
            super(x, y, largeur, hauteur, texte, coche);
            this.surChangement = surChangement;
        }

        @Override
        public void onPress() {
            super.onPress();
            surChangement.accept(this.selected());
        }
    }
}

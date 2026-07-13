package com.example.mysubmod.sousmodes.sousmode3.client;

import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode3.ConfigPartieSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.GestionnairePresetsSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetActionPresetSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetLancerPartieConfigureeSousMode3;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
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

    // Presets : nom saisi (conservé à travers les reconstructions de widgets) et preset
    // sélectionné dans la liste reçue du serveur
    private String texteNomPreset = "";
    private int indexPreset = 0;
    private EditBox champNomPreset;
    /** La liste des presets n'est demandée qu'une fois : rebuildWidgets() relance init(),
     *  et une réponse serveur reconstruit l'écran — redemander à chaque fois bouclerait. */
    private boolean presetsDemandes = false;

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

        // Mode compact sous 352 px : toutes les options tiennent sur UN seul écran (pas de
        // sous-fenêtre), au prix de rangées resserrées. La fenêtre Minecraft par défaut
        // donne une GUI de 427×240. Seuil : en non-compact la colonne la plus chargée
        // descend à y=297 et la rangée des presets commence à h−51 → il faut h ≥ 352
        // pour garder un écart (sous 348 elles se chevaucheraient).
        compact = this.height < 352;
        pasLigne = compact ? 14 : 20;
        pasTitre = compact ? 10 : 15;
        hauteurWidget = compact ? 13 : 20;
        largeurColonne = Math.max(126, Math.min(150, (this.width - 2 * ECART_COLONNE - 12) / 3));
        int yDepart = compact ? 15 : 32;

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

        // ---- Rangée des presets + rangée des boutons d'action, en bas de page ----
        int hBouton = compact ? 16 : 20;
        int yBas = this.height - hBouton - (compact ? 4 : 8);
        int yPresets = yBas - hBouton - 3;
        int xBas = (this.width - 380) / 2;

        construireBarrePresets(xBas, yPresets, hBouton);

        addRenderableWidget(Button.builder(Component.literal("Par défaut"), b -> reinitialiserConfig())
            .bounds(xBas, yBas, 100, hBouton).build());
        addRenderableWidget(Button.builder(Component.literal("§aLancer la partie"), b -> {
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetLancerPartieConfigureeSousMode3(config));
            this.onClose();
        }).bounds(xBas + 104, yBas, 150, hBouton).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> this.onClose())
            .bounds(xBas + 258, yBas, 122, hBouton).build());

        // Demander la liste des presets au serveur, UNE seule fois (la réponse reconstruit
        // l'écran ; redemander à chaque reconstruction bouclerait)
        if (!presetsDemandes) {
            presetsDemandes = true;
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetActionPresetSousMode3(
                PaquetActionPresetSousMode3.Action.LISTER, "", null));
        }
    }

    /** Barre des presets (bas de page) : sélecteur du preset enregistré, Charger, Supprimer,
     *  champ de nom et Sauver. Réservée à l'admin (le menu N ne s'ouvre que pour lui). */
    private void construireBarrePresets(int x, int y, int h) {
        List<String> noms = com.example.mysubmod.sousmodes.sousmode3.client.PresetsClientSousMode3.obtenirNoms();
        if (!noms.isEmpty()) {
            indexPreset = Math.floorMod(indexPreset, noms.size());
        } else {
            indexPreset = 0;
        }
        boolean aPresets = !noms.isEmpty();
        String nomSel = aPresets ? noms.get(indexPreset) : "(aucun)";

        Button selecteur = Button.builder(Component.literal("Preset: " + nomSel), b -> {
            if (noms.isEmpty()) {
                return;
            }
            indexPreset = Math.floorMod(indexPreset + (Screen.hasShiftDown() ? -1 : 1), noms.size());
            // Pré-remplir le champ de nom avec le preset choisi (sauvegarde « écraser » facile)
            texteNomPreset = noms.get(indexPreset);
            rebuildWidgets();
        }).bounds(x, y, 128, h)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Preset enregistré (clic = suivant, Maj+clic = précédent)")))
            .build();
        selecteur.active = aPresets;
        addRenderableWidget(selecteur);

        Button charger = Button.builder(Component.literal("Charger"), b -> {
            if (aPresets) {
                GestionnaireReseau.INSTANCE.sendToServer(new PaquetActionPresetSousMode3(
                    PaquetActionPresetSousMode3.Action.CHARGER, noms.get(indexPreset), null));
            }
        }).bounds(x + 130, y, 64, h).build();
        charger.active = aPresets;
        addRenderableWidget(charger);

        Button supprimer = Button.builder(Component.literal("Suppr"), b -> {
            if (aPresets) {
                GestionnaireReseau.INSTANCE.sendToServer(new PaquetActionPresetSousMode3(
                    PaquetActionPresetSousMode3.Action.SUPPRIMER, noms.get(indexPreset), null));
            }
        }).bounds(x + 196, y, 52, h)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Supprimer le preset sélectionné")))
            .build();
        supprimer.active = aPresets;
        addRenderableWidget(supprimer);

        champNomPreset = new EditBox(this.font, x + 250, y, 74, h, Component.literal("Nom du preset"));
        champNomPreset.setMaxLength(GestionnairePresetsSousMode3.LONGUEUR_MAX_NOM);
        champNomPreset.setValue(texteNomPreset);
        champNomPreset.setHint(Component.literal("nom…"));
        champNomPreset.setResponder(t -> texteNomPreset = t);
        addRenderableWidget(champNomPreset);

        addRenderableWidget(Button.builder(Component.literal("Sauver"), b -> {
            String nom = texteNomPreset == null ? "" : texteNomPreset.trim();
            if (!GestionnairePresetsSousMode3.nomValide(nom)) {
                if (this.minecraft.player != null) {
                    this.minecraft.player.sendSystemMessage(Component.literal(
                        "§cNom de preset invalide (lettres, chiffres, - et _)"));
                }
                return;
            }
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetActionPresetSousMode3(
                PaquetActionPresetSousMode3.Action.SAUVEGARDER, nom, config));
        }).bounds(x + 326, y, 54, h)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Sauvegarder les réglages actuels sous ce nom")))
            .build());
    }

    /** Applique un preset chargé (reçu du serveur) au formulaire et reconstruit l'écran. */
    public void appliquerConfigChargee(ConfigPartieSousMode3 chargee) {
        copier(chargee, config);
        rebuildWidgets();
    }

    /** Rafraîchit la barre des presets après un changement de liste (serveur). */
    public void rafraichirPresets() {
        rebuildWidgets();
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

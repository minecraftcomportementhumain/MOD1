package com.example.mysubmod.cartes.client;

import com.example.mysubmod.cartes.BlocCarte;
import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.cartes.TypeElementCarte;
import com.example.mysubmod.cartes.reseau.PaquetDemandeListeCartes;
import com.example.mysubmod.cartes.reseau.PaquetFermetureEditeurCarte;
import com.example.mysubmod.cartes.reseau.PaquetListeCartes;
import com.example.mysubmod.cartes.reseau.PaquetSauvegardeCarte;
import com.example.mysubmod.reseau.GestionnaireReseau;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Outil de création de carte : plan cartésien bloc par bloc (vue du dessus).
 * Barre du haut groupée (fichier / aire / limite), palette à pastilles de
 * couleur à gauche, grille zoomable au centre, inspecteur permanent à droite
 * (bloc survolé + état de la carte, ou formulaire délais/types en mode
 * sélection) et barre d'état en bas (coordonnées, outil, contrôles, zoom).
 */
@OnlyIn(Dist.CLIENT)
public class EcranEditeurCarte extends Screen {

    /** Élément actif de la palette */
    public enum OutilPalette {
        EAU("Eau"),
        ILE("Île"),
        PIERRE("Pierre"),
        LIMITE("Limite"),
        BONBON_VISIBLE("Bonbon visible"),
        BONBON_NON_VISIBLE("Bonbon non-visible"),
        APPARITION("Apparition des joueurs");

        final String nomAffichage;

        OutilPalette(String nomAffichage) {
            this.nomAffichage = nomAffichage;
        }
    }

    private static final int MAX_ANNULATIONS = 20;
    private static final int HAUTEUR_BARRE_ETAT = 16;
    /** Largeur d'écran à partir de laquelle la barre du haut tient sur une seule rangée */
    private static final int SEUIL_BARRE_UNIQUE = 880;
    /** En dessous de cette largeur, palette et inspecteur se compactent pour préserver la grille */
    private static final int SEUIL_LATERAUX_COMPACTS = 640;
    private static final int LARGEUR_CHAMP_SELECTION = 62;

    // Couleurs de l'habillage
    private static final int COULEUR_ACCENT = 0xFF4CB566;          // Outil actif
    private static final int COULEUR_SELECTION = 0xFF00E5E5;      // Mode sélection
    private static final int COULEUR_BONBON_VISIBLE = 0xFFFFC81E;
    private static final int COULEUR_BONBON_NON_VISIBLE = 0xFFC896FF;
    private static final int COULEUR_ETIQUETTE = 0xFF9AA396;      // Libellés secondaires
    private static final int COULEUR_TEXTE_ESTOMPE = 0xFF6F7871;  // Astuces / textes discrets
    private static final int COULEUR_FOND_CARTE_INSP = 0xFF1F2328;
    private static final int COULEUR_CADRE_CARTE_INSP = 0xFF31373E;

    // Données de la carte en cours d'édition
    private CarteDonnees carte = new CarteDonnees();

    // Outils
    private OutilPalette outilActif = null;
    private boolean modeSelection = false;

    // Vue (zoom / défilement)
    private double tailleCellule = 16;
    private double decalageX = 10;
    private double decalageY = 10;
    private boolean panEnCours = false;

    // Sélection rectangle
    private boolean tracageSelection = false;
    private double selectionDebutX;
    private double selectionDebutY;
    private double selectionCouranteX;
    private double selectionCouranteY;
    private final Set<Long> cellulesSelectionnees = new HashSet<>();
    // Résumé de la sélection, recalculé quand elle change — jamais obsolète : annuler/rétablir
    // vident la sélection, et Appliquer/l'élévation groupée ne changent pas la présence des
    // bonbons. Évite de balayer chaque image une sélection qui peut compter des millions de cellules.
    private boolean selectionABonbonVisible = false;
    private boolean selectionABonbonNonVisible = false;
    private int selectionNbTerrain = 0;
    private Integer selectionElevationCommune = null;

    // Pinceau (peinture continue par glissement pour Eau / Île / Pierre / Limite)
    private boolean peintureEnCours = false;
    private int dernierePeintureCx;
    private int dernierePeintureCz;
    private ActionEditeur actionPeinture = null;
    private Set<Long> interieurPeinture = null;      // Cache de l'intérieur du périmètre pendant un trait
    private boolean limiteValidePeinture = false;

    // Annuler / rétablir
    private final Deque<ActionEditeur> pileAnnulation = new ArrayDeque<>();
    private final Deque<ActionEditeur> pileRetablissement = new ArrayDeque<>();

    // Champs de saisie (textes conservés entre les init())
    private String texteNom = "";
    private String texteLargeur = "20";
    private String texteHauteur = "20";
    private String texteDelaiVisible = "";
    private String texteDelaiNonVisible = "";
    private String texteApparitionInitiale = "";
    private String texteApparitionInitialeNonVisible = "";
    private com.example.mysubmod.cartes.TypeBonbonCarte typeBonbonChoisi = null; // null = inchangé
    private com.example.mysubmod.cartes.TypeBonbonCarte typeBonbonNonVisibleChoisi = null; // null = inchangé

    private EditBox champNom;
    private EditBox champLargeur;
    private EditBox champHauteur;
    private EditBox champDelaiVisible;
    private EditBox champDelaiNonVisible;
    private EditBox champApparitionInitiale;
    private EditBox champApparitionInitialeNonVisible;
    private Button boutonTypeBonbon;
    private Button boutonTypeBonbonNonVisible;
    private Button boutonAppliquerDelais;
    private Button boutonSauvegarder;
    private Button boutonSelection;
    private Button boutonAnnuler;
    private Button boutonRetablir;

    // Habillage recalculé à chaque init() (dépend de la taille de l'écran)
    private int hautGrille = 52;
    private int largeurPalette = 148;
    private int largeurPanneauDroit = 160;
    private int pitchSelection = 18;       // Pas vertical d'une rangée des cartes du panneau sélection
    private int hauteurCarteSelection = 72; // Hauteur d'une carte Bonbon visible / non-visible
    private final List<TextePeint> textesPeints = new ArrayList<>();
    private final List<int[]> separateursPeints = new ArrayList<>(); // {x, y0, y1}

    // État « sauvegardé » : sommet de la pile d'annulation et nom au dernier point de
    // sauvegarde réussie (ou chargement). L'éditeur est modifié dès que l'un des deux
    // diffère — annuler jusqu'au point de sauvegarde redevient donc « non modifié ».
    private ActionEditeur reperePileSauvegarde = null;
    private String nomSauvegarde = "";
    // Instantané au moment de l'envoi du JSON : l'accusé du serveur peut arriver après
    // de nouvelles modifications, qui doivent rester marquées non sauvegardées
    private ActionEditeur reperePileEnvoi = null;
    private String nomEnvoi = "";
    private boolean fermetureConfirmee = false;

    // Notification non bloquante (refus de placement / succès)
    private String toastTexte = null;
    private boolean toastErreur = false;
    private long toastExpiration = 0;

    // Statistiques de la carte pour l'inspecteur (recalculées après une action,
    // au plus toutes les 500 ms — voir rafraichirStatsCarte)
    private boolean statsObsoletes = true;
    private long statsDernierCalcul = 0;
    private boolean statLimiteFermee = false;
    private int statBonbonsVisibles = 0;
    private int statBonbonsNonVisibles = 0;
    // Cellules Limite (peu nombreuses) : redessinées explicitement en fort dézoom,
    // où l'échantillonnage du rendu pourrait sauter un mur d'une cellule d'épaisseur
    private long[] cellulesLimiteCache = new long[0];

    // Fenêtre modale interne (messages / confirmations / choix)
    private boolean modalActif = false;
    private String modalTitre = "";
    private List<String> modalLignes = new ArrayList<>();
    private List<String> modalBoutons = new ArrayList<>();
    private List<Runnable> modalActions = new ArrayList<>();

    // Sauvegarde en attente de confirmation d'écrasement
    private String jsonSauvegardeEnAttente = null;

    public EcranEditeurCarte() {
        super(Component.literal("Outil de création de carte"));
        carte.largeur = 20;
        carte.hauteur = 20;
    }

    // ==================== Initialisation des widgets ====================

    @Override
    protected void init() {
        super.init();
        textesPeints.clear();
        separateursPeints.clear();

        // ----- Barre du haut : groupes Fichier / Aire / Limite, sur une rangée si
        // l'écran est assez large, sinon sur deux (Fichier en haut, Aire+Limite dessous).
        // Panneaux latéraux compactés sous SEUIL_LATERAUX_COMPACTS pour garder de la grille -----
        boolean barreUnique = this.width >= SEUIL_BARRE_UNIQUE;
        boolean laterauxCompacts = this.width < SEUIL_LATERAUX_COMPACTS;
        hautGrille = barreUnique ? 30 : 52;
        largeurPalette = laterauxCompacts ? 116 : 148;
        largeurPanneauDroit = laterauxCompacts ? 136 : 160;

        int x = 6;
        champNom = new EditBox(this.font, x, 6, 130, 18, Component.literal("Nom de la carte"));
        champNom.setMaxLength(CarteDonnees.LONGUEUR_MAX_NOM);
        champNom.setHint(Component.literal("Nom de la carte"));
        champNom.setValue(texteNom);
        champNom.setResponder(texte -> {
            texteNom = texte;
            mettreAJourIndicateurSauvegarde(); // Le nom fait partie de l'état sauvegardé
        });
        addRenderableWidget(champNom);
        x += 136;

        // Le ● « modifications non sauvegardées » vit sur le bouton Sauvegarder
        boutonSauvegarder = Button.builder(Component.literal("Sauvegarder"), b -> sauvegarder())
            .bounds(x, 5, 80, 20).build();
        addRenderableWidget(boutonSauvegarder);
        x += 84;

        addRenderableWidget(Button.builder(Component.literal("Charger"), b ->
            GestionnaireReseau.INSTANCE.sendToServer(new PaquetDemandeListeCartes(PaquetListeCartes.BUT_CHARGEMENT_EDITEUR)))
            .bounds(x, 5, 60, 20).build());
        x += 64;

        addRenderableWidget(Button.builder(Component.literal("Importer CSV"), b ->
            this.minecraft.setScreen(new EcranImportCsvCarte(this)))
            .bounds(x, 5, 84, 20).build());
        x += 88;

        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> this.onClose())
            .bounds(this.width - 66, 5, 60, 20).build());

        int yBoutons2 = barreUnique ? 5 : 28;
        int yChamps2 = barreUnique ? 6 : 29;
        int yTexte2 = barreUnique ? 11 : 34;
        if (barreUnique) {
            separateursPeints.add(new int[]{x, 5, 25});
            x += 8;
        } else {
            x = 6;
        }

        // Étiquettes de groupes omises sous 480 px : la rangée Aire+Limite doit tenir
        // aux largeurs GUI minimales (elles n'apportent que du contexte visuel)
        boolean etiquettesGroupes = this.width >= 480;
        if (etiquettesGroupes) {
            textesPeints.add(new TextePeint(x, yTexte2, "Aire", COULEUR_ETIQUETTE));
            x += this.font.width("Aire") + 6;
        }
        champLargeur = new EditBox(this.font, x, yChamps2, 36, 18, Component.literal("Largeur"));
        champLargeur.setMaxLength(4);
        champLargeur.setValue(texteLargeur);
        champLargeur.setResponder(texte -> texteLargeur = texte);
        addRenderableWidget(champLargeur);
        x += 40;

        if (etiquettesGroupes) {
            textesPeints.add(new TextePeint(x, yTexte2, "×", COULEUR_ETIQUETTE));
            x += this.font.width("×") + 4;
        }
        champHauteur = new EditBox(this.font, x, yChamps2, 36, 18, Component.literal("Hauteur"));
        champHauteur.setMaxLength(4);
        champHauteur.setValue(texteHauteur);
        champHauteur.setResponder(texte -> texteHauteur = texte);
        addRenderableWidget(champHauteur);
        x += 40;

        addRenderableWidget(Button.builder(Component.literal("Redimensionner"), b -> redimensionnerDepuisChamps())
            .bounds(x, yBoutons2, 96, 20).build());
        x += 100;

        if (etiquettesGroupes) {
            separateursPeints.add(new int[]{x, yBoutons2, yBoutons2 + 20});
            x += 8;
            textesPeints.add(new TextePeint(x, yTexte2, "Limite", COULEUR_ETIQUETTE));
            x += this.font.width("Limite") + 6;
        }

        addRenderableWidget(Button.builder(Component.literal("Par défaut"), b -> appliquerLimiteParDefaut())
            .bounds(x, yBoutons2, 70, 20)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Place la Limite sur le contour de l'aire totale (écrase l'existante sans confirmation)")))
            .build());
        x += 74;

        addRenderableWidget(Button.builder(Component.literal("Supprimer"), b -> supprimerLimite())
            .bounds(x, yBoutons2, 70, 20)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Retire tous les blocs Limite de la carte")))
            .build());

        // ----- Palette (panneau latéral gauche) : trois sections titrées.
        // Compactage sur les écrans courts : 3 titres + 9 rangées (7 outils + Sélection +
        // Annuler/Rétablir) doivent tenir entre le haut de la palette et la barre d'état -----
        int yPalette = hautGrille + 4;
        int hauteurTitre = 10;
        int pasPalette = Math.max(14, Math.min(22,
            (this.height - HAUTEUR_BARRE_ETAT - yPalette - 3 * hauteurTitre - 4) / 9));
        int hauteurBoutonPalette = pasPalette - 2;

        yPalette = ajouterTitrePalette("Terrain", yPalette, hauteurTitre);
        for (OutilPalette outil : new OutilPalette[]{
                OutilPalette.EAU, OutilPalette.ILE, OutilPalette.PIERRE, OutilPalette.LIMITE}) {
            yPalette = ajouterBoutonOutil(outil, yPalette, pasPalette);
        }

        yPalette = ajouterTitrePalette(laterauxCompacts ? "Bonbons" : "Bonbons & apparition", yPalette, hauteurTitre);
        for (OutilPalette outil : new OutilPalette[]{
                OutilPalette.BONBON_VISIBLE, OutilPalette.BONBON_NON_VISIBLE, OutilPalette.APPARITION}) {
            yPalette = ajouterBoutonOutil(outil, yPalette, pasPalette);
        }

        yPalette = ajouterTitrePalette("Édition", yPalette, hauteurTitre);
        boutonSelection = new BoutonPalette(6, yPalette, largeurPalette - 12, hauteurBoutonPalette,
            Component.literal("Sélection"), b -> basculerModeSelection(),
            BoutonPalette.Pastille.SELECTION, 0, () -> modeSelection, COULEUR_SELECTION);
        boutonSelection.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("Mode sélection : tracer un rectangle pour sélectionner le terrain (Île, Pierre) "
                + "et les bonbons. Ctrl+molette ajuste l'élévation de toute la sélection")));
        addRenderableWidget(boutonSelection);
        yPalette += pasPalette;

        int largeurMoitie = (largeurPalette - 16) / 2;
        boutonAnnuler = Button.builder(Component.literal("↶ Annuler"), b -> annuler())
            .bounds(6, yPalette, largeurMoitie, hauteurBoutonPalette)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Annuler (Ctrl+Z)")))
            .build();
        addRenderableWidget(boutonAnnuler);

        boutonRetablir = Button.builder(Component.literal("↷ Rétablir"), b -> retablir())
            .bounds(6 + largeurMoitie + 4, yPalette, largeurMoitie, hauteurBoutonPalette)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Rétablir (Ctrl+Y)")))
            .build();
        addRenderableWidget(boutonRetablir);

        // ----- Inspecteur (panneau latéral droit), état sélection : deux cartes
        // (Bonbon visible / non-visible) de trois rangées libellées à gauche, puis le
        // bouton Appliquer. Le pas des rangées se compacte pour tenir sous la hauteur
        // GUI minimale (240 px) ; les positions verticales sont posées par
        // mettreAJourPanneauDelais() selon les familles présentes dans la sélection -----
        int basPanneau = this.height - HAUTEUR_BARRE_ETAT;
        int dispoSelection = basPanneau - (hautGrille + 18) - 24;
        pitchSelection = Math.max(14, Math.min(20, (dispoSelection / 2 - 18) / 3));
        hauteurCarteSelection = 18 + 3 * pitchSelection;
        int hauteurChamp = Math.min(14, pitchSelection - 2);
        int largeurChamp = LARGEUR_CHAMP_SELECTION;
        int xChamp = xChampSelection();
        int xType = xTypeSelection();
        int largeurType = largeurPanneauDroit - 54;

        champDelaiVisible = new EditBox(this.font, xChamp, 0, largeurChamp, hauteurChamp,
            Component.literal("Délai bonbon visible (s)"));
        champDelaiVisible.setMaxLength(8);
        champDelaiVisible.setValue(texteDelaiVisible);
        champDelaiVisible.setResponder(texte -> texteDelaiVisible = texte);
        champDelaiVisible.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
            "Secondes avant la réapparition du bonbon visible après ramassage (vide = valeur conservée)")));
        addRenderableWidget(champDelaiVisible);

        champDelaiNonVisible = new EditBox(this.font, xChamp, 0, largeurChamp, hauteurChamp,
            Component.literal("Délai bonbon non-visible (s)"));
        champDelaiNonVisible.setMaxLength(8);
        champDelaiNonVisible.setValue(texteDelaiNonVisible);
        champDelaiNonVisible.setResponder(texte -> texteDelaiNonVisible = texte);
        champDelaiNonVisible.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
            "Secondes avant la réapparition du bonbon non-visible après minage (vide = valeur conservée)")));
        addRenderableWidget(champDelaiNonVisible);

        champApparitionInitiale = new EditBox(this.font, xChamp, 0, largeurChamp, hauteurChamp,
            Component.literal("Apparition initiale visible (s)"));
        champApparitionInitiale.setMaxLength(8);
        champApparitionInitiale.setValue(texteApparitionInitiale);
        champApparitionInitiale.setResponder(texte -> texteApparitionInitiale = texte);
        champApparitionInitiale.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
            "Secondes après le début de partie avant l'apparition du bonbon visible (0 = dès le début)")));
        addRenderableWidget(champApparitionInitiale);

        champApparitionInitialeNonVisible = new EditBox(this.font, xChamp, 0, largeurChamp, hauteurChamp,
            Component.literal("Apparition initiale non-visible (s)"));
        champApparitionInitialeNonVisible.setMaxLength(8);
        champApparitionInitialeNonVisible.setValue(texteApparitionInitialeNonVisible);
        champApparitionInitialeNonVisible.setResponder(texte -> texteApparitionInitialeNonVisible = texte);
        champApparitionInitialeNonVisible.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
            "Secondes après le début de partie avant l'apparition du bloc bonbon non-visible (0 = dès le début)")));
        addRenderableWidget(champApparitionInitialeNonVisible);

        boutonTypeBonbon = Button.builder(Component.literal("(inchangé)"), b -> cyclerTypeBonbon())
            .bounds(xType, 0, largeurType, hauteurChamp)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Type des bonbons visibles sélectionnés : Standard, ou Bleu / Rouge (spécialisation)")))
            .build();
        addRenderableWidget(boutonTypeBonbon);

        boutonTypeBonbonNonVisible = Button.builder(Component.literal("(inchangé)"),
                b -> cyclerTypeBonbonNonVisible())
            .bounds(xType, 0, largeurType, hauteurChamp)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Type des bonbons non-visibles sélectionnés : Standard, ou Bleu / Rouge (spécialisation)")))
            .build();
        addRenderableWidget(boutonTypeBonbonNonVisible);

        boutonAppliquerDelais = Button.builder(
                Component.literal(laterauxCompacts ? "Appliquer" : "Appliquer à la sélection"), b -> appliquerDelais())
            .bounds(this.width - largeurPanneauDroit + 8, 0, largeurPanneauDroit - 16, 16).build();
        addRenderableWidget(boutonAppliquerDelais);

        // ----- Barre d'état : contrôles de zoom à droite -----
        int yZoom = this.height - HAUTEUR_BARRE_ETAT + 1;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> zoomerDepuisBoutons(1 / 1.2))
            .bounds(this.width - 136, yZoom, 14, 14)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Dézoomer (molette)")))
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomerDepuisBoutons(1.2))
            .bounds(this.width - 74, yZoom, 14, 14)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Zoomer (molette)")))
            .build());
        addRenderableWidget(Button.builder(Component.literal("Ajuster"), b -> ajusterVue())
            .bounds(this.width - 56, yZoom, 50, 14)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Cadrer toute la carte dans la vue")))
            .build());

        mettreAJourBoutonsAnnulation();
        mettreAJourIndicateurSauvegarde();
        mettreAJourPanneauDelais();
    }

    /** Colonne des champs du panneau sélection (alignés au bord droit) */
    private int xChampSelection() {
        return this.width - 8 - LARGEUR_CHAMP_SELECTION;
    }

    /** Colonne des boutons de type du panneau sélection */
    private int xTypeSelection() {
        return this.width - largeurPanneauDroit + 46;
    }

    /** Ajoute un titre de section peint dans la palette et retourne le y suivant */
    private int ajouterTitrePalette(String titre, int y, int hauteurTitre) {
        textesPeints.add(new TextePeint(8, y + 1, titre, COULEUR_TEXTE_ESTOMPE));
        return y + hauteurTitre;
    }

    /** Crée le bouton de palette d'un outil (pastille + étiquette) et retourne le y suivant */
    private int ajouterBoutonOutil(OutilPalette outil, int y, int pasPalette) {
        boolean compact = largeurPalette < 148;
        String etiquette = switch (outil) {
            case BONBON_VISIBLE -> compact ? "Bonbon vis." : outil.nomAffichage;
            case BONBON_NON_VISIBLE -> compact ? "Bonbon non-vis." : outil.nomAffichage;
            case APPARITION -> "Apparition";
            default -> outil.nomAffichage;
        };
        BoutonPalette.Pastille pastille = switch (outil) {
            case BONBON_NON_VISIBLE -> BoutonPalette.Pastille.ENTERREE;
            case APPARITION -> BoutonPalette.Pastille.DRAPEAU;
            default -> BoutonPalette.Pastille.PLEINE;
        };
        int couleurPastille = switch (outil) {
            case EAU -> 0xFF2E64C8;
            case ILE -> 0xFF3FA34D;
            case PIERRE -> 0xFF8C8C8C;
            case LIMITE -> 0xFFC83232;
            case BONBON_VISIBLE, BONBON_NON_VISIBLE -> COULEUR_BONBON_VISIBLE;
            case APPARITION -> 0;
        };
        BoutonPalette bouton = new BoutonPalette(6, y, largeurPalette - 12, pasPalette - 2,
            Component.literal(etiquette), b -> selectionnerOutil(outil),
            pastille, couleurPastille, () -> outilActif == outil, COULEUR_ACCENT);
        bouton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(outil.nomAffichage)));
        addRenderableWidget(bouton);
        return y + pasPalette;
    }

    // ==================== Gestion des outils ====================

    private void selectionnerOutil(OutilPalette outil) {
        if (outilActif == outil) {
            outilActif = null; // Re-cliquer désélectionne
        } else {
            outilActif = outil;
        }
        // Sortie du mode sélection : même nettoyage que basculerModeSelection, sinon les
        // champs/boutons du panneau de droite et la surbrillance resteraient affichés
        modeSelection = false;
        tracageSelection = false;
        cellulesSelectionnees.clear();
        mettreAJourPanneauDelais();
    }

    private void basculerModeSelection() {
        modeSelection = !modeSelection;
        if (modeSelection) {
            outilActif = null;
        } else {
            cellulesSelectionnees.clear();
            tracageSelection = false;
        }
        mettreAJourPanneauDelais();
    }

    // ==================== Coordonnées grille <-> écran ====================

    private int grilleGaucheEcran() {
        return largeurPalette;
    }

    private int grilleDroiteEcran() {
        // L'inspecteur est permanent : la grille garde une largeur constante
        return this.width - largeurPanneauDroit;
    }

    private int basGrille() {
        return this.height - HAUTEUR_BARRE_ETAT;
    }

    private double ecranVersCelluleX(double sourisX) {
        return (sourisX - grilleGaucheEcran() - decalageX) / tailleCellule;
    }

    private double ecranVersCelluleZ(double sourisY) {
        return (sourisY - hautGrille - decalageY) / tailleCellule;
    }

    private int[] celluleSousSouris(double sourisX, double sourisY) {
        if (sourisX < grilleGaucheEcran() || sourisX >= grilleDroiteEcran()
            || sourisY < hautGrille || sourisY >= basGrille()) {
            return null;
        }
        int cx = (int) Math.floor(ecranVersCelluleX(sourisX));
        int cz = (int) Math.floor(ecranVersCelluleZ(sourisY));
        if (!carte.estDansAire(cx, cz)) {
            return null;
        }
        return new int[]{cx, cz};
    }

    // ==================== Actions d'édition ====================

    private void enregistrerAction(ActionEditeur action) {
        pileAnnulation.push(action);
        while (pileAnnulation.size() > MAX_ANNULATIONS) {
            pileAnnulation.removeLast();
        }
        pileRetablissement.clear();
        surCarteModifiee();
    }

    private void annuler() {
        if (pileAnnulation.isEmpty()) {
            return;
        }
        ActionEditeur action = pileAnnulation.pop();
        action.annuler(carte);
        pileRetablissement.push(action);
        cellulesSelectionnees.clear();
        surCarteModifiee();
        synchroniserChampsTaille();
        mettreAJourPanneauDelais();
    }

    private void retablir() {
        if (pileRetablissement.isEmpty()) {
            return;
        }
        ActionEditeur action = pileRetablissement.pop();
        action.retablir(carte);
        pileAnnulation.push(action);
        cellulesSelectionnees.clear();
        surCarteModifiee();
        synchroniserChampsTaille();
        mettreAJourPanneauDelais();
    }

    /** À appeler après toute action sur la carte : statistiques, indicateur ●, boutons ↶/↷ */
    private void surCarteModifiee() {
        statsObsoletes = true;
        mettreAJourIndicateurSauvegarde();
        mettreAJourBoutonsAnnulation();
    }

    /**
     * Des modifications existent-elles depuis la dernière sauvegarde réussie (ou le
     * dernier chargement) ? Comparaison par repère de pile : annuler jusqu'au point
     * de sauvegarde exact redonne « non modifié ». Le nom fait partie de l'état.
     */
    private boolean estModifie() {
        return pileAnnulation.peek() != reperePileSauvegarde || !texteNom.equals(nomSauvegarde);
    }

    /** Le bouton Sauvegarder porte un ● lorsque des modifications ne sont pas sauvegardées */
    private void mettreAJourIndicateurSauvegarde() {
        if (boutonSauvegarder != null) {
            boutonSauvegarder.setMessage(Component.literal(estModifie() ? "Sauvegarder ●" : "Sauvegarder"));
        }
    }

    /** Annuler / Rétablir sont grisés lorsque leur pile est vide */
    private void mettreAJourBoutonsAnnulation() {
        if (boutonAnnuler != null) {
            boutonAnnuler.active = !pileAnnulation.isEmpty();
        }
        if (boutonRetablir != null) {
            boutonRetablir.active = !pileRetablissement.isEmpty();
        }
    }

    private void synchroniserChampsTaille() {
        texteLargeur = String.valueOf(carte.largeur);
        texteHauteur = String.valueOf(carte.hauteur);
        if (champLargeur != null) {
            champLargeur.setValue(texteLargeur);
        }
        if (champHauteur != null) {
            champHauteur.setValue(texteHauteur);
        }
    }

    private void afficherMessage(String titre, List<String> lignes) {
        afficherModal(titre, lignes, List.of("OK"), java.util.Collections.singletonList(null));
    }

    private void afficherMessage(String titre, String ligne) {
        afficherMessage(titre, List.of(ligne));
    }

    private void afficherConfirmation(String titre, List<String> lignes, Runnable actionOui, Runnable actionNon) {
        afficherModal(titre, lignes, List.of("Oui", "Non"), java.util.Arrays.asList(actionOui, actionNon));
    }

    private void afficherChoix(String titre, List<String> lignes, List<String> boutons, List<Runnable> actions) {
        afficherModal(titre, lignes, boutons, actions);
    }

    private void afficherModal(String titre, List<String> lignes, List<String> boutons, List<Runnable> actions) {
        modalActif = true;
        modalTitre = titre;
        modalLignes = decouperLignesModal(lignes);
        modalBoutons = new ArrayList<>(boutons);
        modalActions = new ArrayList<>(actions);
    }

    /**
     * Notification non bloquante affichée quelques secondes en bas de la grille.
     * Utilisée pour les refus de placement et les confirmations de succès : le même
     * message qu'avant, sans interrompre le geste d'édition. Les fenêtres modales
     * restent réservées aux décisions (écrasement, remplissage, erreurs de sauvegarde).
     */
    private void afficherToast(String message, boolean erreur) {
        // Repli : si la bande de grille est trop étroite pour un toast lisible,
        // reprendre la fenêtre modale d'origine — le message ne doit jamais se perdre
        if (grilleDroiteEcran() - grilleGaucheEcran() - 24 < 60) {
            afficherMessage(erreur ? "Placement refusé" : "Information", message);
            return;
        }
        toastTexte = message;
        toastErreur = erreur;
        toastExpiration = net.minecraft.Util.getMillis() + 2600;
    }

    /** Découpe les lignes trop longues pour tenir dans la fenêtre modale */
    private List<String> decouperLignesModal(List<String> lignes) {
        int largeurMax = largeurModal() - 24;
        List<String> resultat = new ArrayList<>();
        for (String ligne : lignes) {
            if (this.font.width(ligne) <= largeurMax) {
                resultat.add(ligne);
                continue;
            }
            StringBuilder courante = new StringBuilder();
            for (String mot : ligne.split(" ")) {
                String essai = courante.isEmpty() ? mot : courante + " " + mot;
                if (this.font.width(essai) > largeurMax && !courante.isEmpty()) {
                    resultat.add(courante.toString());
                    courante = new StringBuilder(mot);
                } else {
                    courante = new StringBuilder(essai);
                }
            }
            if (!courante.isEmpty()) {
                resultat.add(courante.toString());
            }
        }
        return resultat;
    }

    /**
     * Clic gauche sur un bloc : place l'élément sélectionné (bloc par bloc).
     * En mode pinceau (silencieux = vrai), les placements invalides sont ignorés
     * sans message et les changements s'accumulent dans l'action du trait en cours.
     */
    private void placerElement(int cx, int cz, boolean silencieux) {
        if (outilActif == null) {
            return; // Aucun élément sélectionné : rien ne se passe
        }

        BlocCarte existant = carte.obtenirBloc(cx, cz);

        // Aucun élément ne peut être superposé sur un bloc Limite (sauf un autre Limite)
        if (existant.type == TypeElementCarte.LIMITE && outilActif != OutilPalette.LIMITE) {
            if (!silencieux) {
                afficherToast("Aucun élément ne peut être superposé sur un bloc Limite", true);
            }
            return;
        }

        BlocCarte avant = existant.copier();
        BlocCarte apres = existant.copier();

        switch (outilActif) {
            case EAU -> {
                if (existant.qteBonbonNonVisible > 0) {
                    if (!silencieux) {
                        afficherToast("Impossible de placer de l'Eau sur un bloc contenant un bonbon non-visible", true);
                    }
                    return;
                }
                // L'eau doit être placée à l'intérieur du périmètre Limite (si valide)
                boolean limiteValide;
                Set<Long> interieur;
                if (peintureEnCours) {
                    limiteValide = limiteValidePeinture;
                    interieur = interieurPeinture;
                } else {
                    limiteValide = carte.limiteEstBoucleFermeeValide();
                    interieur = limiteValide ? carte.calculerInterieurLimite() : null;
                }
                if (limiteValide && (interieur == null || !interieur.contains(CarteDonnees.cle(cx, cz)))) {
                    if (!silencieux) {
                        afficherToast("L'Eau doit être placée à l'intérieur du périmètre Limite", true);
                    }
                    return;
                }
                apres.type = TypeElementCarte.EAU;
                apres.elevation = Math.min(0, apres.elevation);
            }
            case ILE -> {
                apres.type = TypeElementCarte.ILE;
            }
            case PIERRE -> {
                apres.type = TypeElementCarte.PIERRE;
            }
            case LIMITE -> {
                // Doit être adjacent à au moins un bloc non-Limite
                boolean adjacentNonLimite = false;
                int[][] voisins = {{cx + 1, cz}, {cx - 1, cz}, {cx, cz + 1}, {cx, cz - 1}};
                for (int[] voisin : voisins) {
                    if (carte.estDansAire(voisin[0], voisin[1])
                        && carte.obtenirBloc(voisin[0], voisin[1]).type != TypeElementCarte.LIMITE) {
                        adjacentNonLimite = true;
                        break;
                    }
                }
                if (!adjacentNonLimite) {
                    if (!silencieux) {
                        afficherToast("Un élément Limite doit être adjacent à au moins un bloc non-Limite", true);
                    }
                    return;
                }
                // Écrase tout le contenu sans confirmation
                apres = new BlocCarte(TypeElementCarte.LIMITE, 0);
            }
            case BONBON_VISIBLE -> {
                if (!existant.type.estElementDeBase()) {
                    if (!silencieux) {
                        afficherToast("Un bonbon visible doit être placé sur un bloc Île, Pierre ou Eau", true);
                    }
                    return;
                }
                if ((existant.type == TypeElementCarte.ILE || existant.type == TypeElementCarte.PIERRE)
                    && existant.elevation >= CarteDonnees.ELEVATION_MAX) {
                    if (!silencieux) {
                        afficherToast("Un bonbon visible ne peut pas être placé sur un bloc à élévation +15 : il serait inatteignable sous le plafond de la cage", true);
                    }
                    return;
                }
                apres.qteBonbonVisible = existant.qteBonbonVisible + 1;
            }
            case BONBON_NON_VISIBLE -> {
                if (existant.type != TypeElementCarte.ILE && existant.type != TypeElementCarte.PIERRE) {
                    if (!silencieux) {
                        afficherToast("Un bonbon non-visible ne peut être placé que sur un bloc Île ou Pierre", true);
                    }
                    return;
                }
                if (existant.elevation - 1 < CarteDonnees.ELEVATION_MIN) {
                    if (!silencieux) {
                        afficherToast("Élévation trop basse : le bonbon serait sous le plancher de la cage", true);
                    }
                    return;
                }
                apres.qteBonbonNonVisible = existant.qteBonbonNonVisible + 1;
            }
            case APPARITION -> {
                if (!carte.limiteEstBoucleFermeeValide()) {
                    if (!silencieux) {
                        afficherToast("Définissez d'abord un périmètre Limite fermé valide", true);
                    }
                    return;
                }
                if (!carte.calculerInterieurLimite().contains(CarteDonnees.cle(cx, cz))) {
                    if (!silencieux) {
                        afficherToast("Le point d'apparition doit être à l'intérieur du périmètre Limite", true);
                    }
                    return;
                }
                ActionEditeur actionApparition = new ActionEditeur(carte);
                actionApparition.apparitionApresX = cx;
                actionApparition.apparitionApresZ = cz;
                carte.apparitionX = cx;
                carte.apparitionZ = cz;
                enregistrerAction(actionApparition);
                return;
            }
        }

        if (apres.memeContenu(avant)) {
            return; // Aucun changement
        }

        if (peintureEnCours && actionPeinture != null) {
            // Tout le trait du pinceau forme une seule action annulable
            actionPeinture.ajouterChangement(cx, cz, avant, apres);
            carte.definirBloc(cx, cz, apres);
        } else {
            ActionEditeur action = new ActionEditeur(carte);
            action.ajouterChangement(cx, cz, avant, apres);
            carte.definirBloc(cx, cz, apres);
            enregistrerAction(action);
        }
    }

    /** Les éléments Eau, Île, Pierre et Limite se peignent par glissement (pinceau) */
    private boolean estOutilPinceau() {
        return outilActif == OutilPalette.EAU || outilActif == OutilPalette.ILE
            || outilActif == OutilPalette.PIERRE || outilActif == OutilPalette.LIMITE;
    }

    private void commencerPeinture(int cx, int cz) {
        peintureEnCours = true;
        actionPeinture = new ActionEditeur(carte);
        dernierePeintureCx = cx;
        dernierePeintureCz = cz;
        // Mettre en cache l'intérieur du périmètre : le trait d'Eau ne modifie pas la Limite
        limiteValidePeinture = outilActif == OutilPalette.EAU && carte.limiteEstBoucleFermeeValide();
        interieurPeinture = limiteValidePeinture ? carte.calculerInterieurLimite() : null;
        placerElement(cx, cz, false); // Premier bloc : messages d'erreur autorisés
    }

    /** Peint tous les blocs entre deux cellules (mouvements rapides de la souris) */
    private void peindreSegment(int deCx, int deCz, int versCx, int versCz) {
        int etapes = Math.max(Math.abs(versCx - deCx), Math.abs(versCz - deCz));
        for (int i = 1; i <= etapes; i++) {
            int cx = deCx + Math.round((float) (versCx - deCx) * i / etapes);
            int cz = deCz + Math.round((float) (versCz - deCz) * i / etapes);
            if (carte.estDansAire(cx, cz)) {
                placerElement(cx, cz, true); // Pendant le trait : silencieux
            }
        }
    }

    private void terminerPeinture() {
        peintureEnCours = false;
        if (actionPeinture != null && !actionPeinture.estVide()) {
            enregistrerAction(actionPeinture);
        }
        actionPeinture = null;
        interieurPeinture = null;
        limiteValidePeinture = false;
    }

    /** Clic droit sur un bloc : retire l'élément du dessus de la pile / décrémente un bonbon */
    private void retirerElement(int cx, int cz) {
        BlocCarte existant = carte.obtenirBlocOuNull(cx, cz);

        // En mode palette Bonbon : décrémenter la quantité du type sélectionné
        if (outilActif == OutilPalette.BONBON_VISIBLE || outilActif == OutilPalette.BONBON_NON_VISIBLE) {
            if (existant == null) {
                return;
            }
            BlocCarte avant = existant.copier();
            BlocCarte apres = existant.copier();
            if (outilActif == OutilPalette.BONBON_VISIBLE && existant.qteBonbonVisible > 0) {
                apres.qteBonbonVisible--;
                if (apres.qteBonbonVisible == 0) {
                    apres.delaiBonbonVisible = 0;
                    apres.delaiApparitionInitiale = 0;
                    apres.typeBonbonVisible = com.example.mysubmod.cartes.TypeBonbonCarte.STANDARD;
                }
            } else if (outilActif == OutilPalette.BONBON_NON_VISIBLE && existant.qteBonbonNonVisible > 0) {
                apres.qteBonbonNonVisible--;
                if (apres.qteBonbonNonVisible == 0) {
                    apres.delaiBonbonNonVisible = 0;
                    apres.delaiApparitionInitialeNonVisible = 0;
                    apres.typeBonbonNonVisible = com.example.mysubmod.cartes.TypeBonbonCarte.STANDARD;
                }
            } else {
                return;
            }
            ActionEditeur action = new ActionEditeur(carte);
            action.ajouterChangement(cx, cz, avant, apres);
            carte.definirBloc(cx, cz, apres);
            enregistrerAction(action);
            return;
        }

        // Outil Apparition : clic droit sur le point d'apparition le retire
        if (outilActif == OutilPalette.APPARITION) {
            if (carte.apparitionX == cx && carte.apparitionZ == cz) {
                ActionEditeur action = new ActionEditeur(carte);
                action.apparitionApresX = -1;
                action.apparitionApresZ = -1;
                carte.apparitionX = -1;
                carte.apparitionZ = -1;
                enregistrerAction(action);
            }
            return;
        }

        // Mode palette autre : retirer l'élément du dessus de la pile
        if (existant == null) {
            return;
        }
        BlocCarte avant = existant.copier();
        BlocCarte apres = existant.copier();

        if (existant.type == TypeElementCarte.LIMITE) {
            // Bloc Limite : clic droit retire le Limite et laisse le bloc vide
            apres = new BlocCarte();
        } else if (existant.qteBonbonVisible > 0) {
            apres.qteBonbonVisible = 0;
            apres.delaiBonbonVisible = 0;
            apres.delaiApparitionInitiale = 0;
            apres.typeBonbonVisible = com.example.mysubmod.cartes.TypeBonbonCarte.STANDARD;
        } else if (existant.qteBonbonNonVisible > 0) {
            apres.qteBonbonNonVisible = 0;
            apres.delaiBonbonNonVisible = 0;
            apres.delaiApparitionInitialeNonVisible = 0;
            apres.typeBonbonNonVisible = com.example.mysubmod.cartes.TypeBonbonCarte.STANDARD;
        } else if (existant.type != TypeElementCarte.VIDE) {
            apres.type = TypeElementCarte.VIDE;
            apres.elevation = 0;
        } else {
            return;
        }

        ActionEditeur action = new ActionEditeur(carte);
        action.ajouterChangement(cx, cz, avant, apres);
        carte.definirBloc(cx, cz, apres);
        enregistrerAction(action);
    }

    /** Bornes d'élévation [min, max] du bloc, ou null si son type n'en a pas (Vide / Limite) */
    private static int[] bornesElevation(BlocCarte existant) {
        int min = CarteDonnees.ELEVATION_MIN;
        int max = CarteDonnees.ELEVATION_MAX;
        if (existant.type == TypeElementCarte.EAU) {
            max = 0; // L'eau ne peut pas être placée au-dessus du niveau de la mer
        } else if (existant.type == TypeElementCarte.ILE || existant.type == TypeElementCarte.PIERRE) {
            if (existant.qteBonbonNonVisible > 0) {
                min = CarteDonnees.ELEVATION_MIN + 1; // Le bonbon non-visible est 1 bloc sous la surface
            }
            if (existant.qteBonbonVisible > 0) {
                max = CarteDonnees.ELEVATION_MAX - 1; // Un bonbon visible à +15 serait inatteignable
            }
        } else {
            return null;
        }
        return new int[]{min, max};
    }

    /** Ctrl+molette sur un bloc : élévation d'un cran à la fois */
    private void changerElevation(int cx, int cz, int delta) {
        BlocCarte existant = carte.obtenirBlocOuNull(cx, cz);
        if (existant == null) {
            return;
        }

        int[] bornes = bornesElevation(existant);
        if (bornes == null) {
            return; // Pas d'élévation pour les blocs vides / Limite
        }

        int nouvelleElevation = Math.max(bornes[0], Math.min(bornes[1], existant.elevation + delta));
        if (nouvelleElevation == existant.elevation) {
            return;
        }

        BlocCarte avant = existant.copier();
        BlocCarte apres = existant.copier();
        apres.elevation = nouvelleElevation;

        ActionEditeur action = new ActionEditeur(carte);
        action.ajouterChangement(cx, cz, avant, apres);
        carte.definirBloc(cx, cz, apres);
        enregistrerAction(action);
    }

    /**
     * Ctrl+molette avec une sélection : élève/abaisse d'un cran tous les blocs sélectionnés
     * qui le peuvent (chacun clampé à ses propres bornes). Une seule entrée dans la pile
     * d'annulation ; au-delà de {@link #MAX_BLOCS_ACTION_ANNULABLE} blocs modifiés, le même
     * traitement que les remplissages géants s'applique (historique vidé, non annulable).
     */
    private void changerElevationSelection(int delta) {
        int aChanger = 0;
        for (long cle : cellulesSelectionnees) {
            BlocCarte existant = carte.blocs.get(cle);
            if (existant == null) {
                continue;
            }
            int[] bornes = bornesElevation(existant);
            if (bornes == null) {
                continue;
            }
            if (Math.max(bornes[0], Math.min(bornes[1], existant.elevation + delta)) != existant.elevation) {
                aChanger++;
            }
        }
        if (aChanger == 0) {
            return; // Toute la sélection est déjà à sa borne : rien à faire
        }
        boolean annulable = aChanger <= MAX_BLOCS_ACTION_ANNULABLE;

        ActionEditeur action = annulable ? new ActionEditeur(carte) : null;
        for (long cle : cellulesSelectionnees) {
            BlocCarte existant = carte.blocs.get(cle);
            if (existant == null) {
                continue;
            }
            int[] bornes = bornesElevation(existant);
            if (bornes == null) {
                continue;
            }
            int nouvelleElevation = Math.max(bornes[0], Math.min(bornes[1], existant.elevation + delta));
            if (nouvelleElevation == existant.elevation) {
                continue;
            }
            int cx = CarteDonnees.cleX(cle);
            int cz = CarteDonnees.cleZ(cle);
            BlocCarte apres = existant.copier();
            apres.elevation = nouvelleElevation;
            if (action != null) {
                action.ajouterChangement(cx, cz, existant.copier(), apres);
            }
            carte.definirBloc(cx, cz, apres);
        }

        if (action != null) {
            enregistrerAction(action);
        } else {
            // Même traitement que les remplissages géants : l'historique ne reflète plus
            // l'état, on le vide, et un repère jamais empilé garde ● jusqu'à la sauvegarde
            pileAnnulation.clear();
            pileRetablissement.clear();
            reperePileSauvegarde = new ActionEditeur(carte);
            surCarteModifiee();
            afficherToast("Élévation de " + aChanger + " blocs appliquée (trop volumineux pour l'annulation)", false);
        }
        recalculerResumeSelection();
    }

    /** Bouton « Limite › Supprimer » : retire tous les éléments Limite */
    private void supprimerLimite() {
        ActionEditeur action = new ActionEditeur(carte);
        List<long[]> aRetirer = new ArrayList<>();
        for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
            if (entree.getValue().type == TypeElementCarte.LIMITE) {
                aRetirer.add(new long[]{entree.getKey()});
            }
        }
        if (aRetirer.isEmpty()) {
            return;
        }
        for (long[] cle : aRetirer) {
            int cx = CarteDonnees.cleX(cle[0]);
            int cz = CarteDonnees.cleZ(cle[0]);
            BlocCarte avant = carte.obtenirBloc(cx, cz).copier();
            action.ajouterChangement(cx, cz, avant, new BlocCarte());
            carte.definirBloc(cx, cz, null);
        }
        enregistrerAction(action);
    }

    /** Bouton « Limite › Par défaut » : Limite sur le contour de l'aire totale (écrase sans confirmation) */
    private void appliquerLimiteParDefaut() {
        ActionEditeur action = new ActionEditeur(carte);

        // Supprimer les éléments Limite existants
        for (Map.Entry<Long, BlocCarte> entree : new HashMap<>(carte.blocs).entrySet()) {
            if (entree.getValue().type == TypeElementCarte.LIMITE) {
                int cx = CarteDonnees.cleX(entree.getKey());
                int cz = CarteDonnees.cleZ(entree.getKey());
                action.ajouterChangement(cx, cz, entree.getValue().copier(), new BlocCarte());
                carte.definirBloc(cx, cz, null);
            }
        }

        // Placer la Limite sur le contour de l'aire totale
        for (int cx = 0; cx < carte.largeur; cx++) {
            for (int cz = 0; cz < carte.hauteur; cz++) {
                boolean estContour = cx == 0 || cz == 0 || cx == carte.largeur - 1 || cz == carte.hauteur - 1;
                if (!estContour) {
                    continue;
                }
                BlocCarte avant = carte.obtenirBloc(cx, cz).copier();
                BlocCarte apres = new BlocCarte(TypeElementCarte.LIMITE, 0);
                if (!avant.memeContenu(apres)) {
                    action.ajouterChangement(cx, cz, avant, apres);
                    carte.definirBloc(cx, cz, apres);
                }
            }
        }

        // Le point d'apparition sur le contour est écrasé
        if (carte.aPointApparition()) {
            boolean surContour = carte.apparitionX == 0 || carte.apparitionZ == 0
                || carte.apparitionX == carte.largeur - 1 || carte.apparitionZ == carte.hauteur - 1;
            if (surContour) {
                action.apparitionApresX = -1;
                action.apparitionApresZ = -1;
                carte.apparitionX = -1;
                carte.apparitionZ = -1;
            }
        }

        if (!action.estVide()) {
            enregistrerAction(action);
        }
    }

    private void redimensionnerDepuisChamps() {
        int nouvelleLargeur;
        int nouvelleHauteur;
        try {
            nouvelleLargeur = Integer.parseInt(texteLargeur.trim());
            nouvelleHauteur = Integer.parseInt(texteHauteur.trim());
        } catch (NumberFormatException e) {
            afficherMessage("Redimensionnement refusé", "Largeur et hauteur doivent être des nombres entiers");
            return;
        }
        if (nouvelleLargeur < 1 || nouvelleHauteur < 1) {
            afficherMessage("Redimensionnement refusé", "Taille minimale : 1×1 bloc");
            return;
        }
        if (nouvelleLargeur > CarteDonnees.DIMENSION_MAX || nouvelleHauteur > CarteDonnees.DIMENSION_MAX) {
            afficherMessage("Redimensionnement refusé",
                "Taille maximale : " + CarteDonnees.DIMENSION_MAX + "×" + CarteDonnees.DIMENSION_MAX + " blocs");
            return;
        }
        if (nouvelleLargeur == carte.largeur && nouvelleHauteur == carte.hauteur) {
            return;
        }

        ActionEditeur action = new ActionEditeur(carte);
        action.largeurApres = nouvelleLargeur;
        action.hauteurApres = nouvelleHauteur;

        // Rétrécir : les éléments hors de la nouvelle aire sont supprimés
        for (Map.Entry<Long, BlocCarte> entree : new HashMap<>(carte.blocs).entrySet()) {
            int cx = CarteDonnees.cleX(entree.getKey());
            int cz = CarteDonnees.cleZ(entree.getKey());
            if (cx >= nouvelleLargeur || cz >= nouvelleHauteur) {
                action.ajouterChangement(cx, cz, entree.getValue().copier(), new BlocCarte());
            }
        }
        if (carte.aPointApparition() && (carte.apparitionX >= nouvelleLargeur || carte.apparitionZ >= nouvelleHauteur)) {
            action.apparitionApresX = -1;
            action.apparitionApresZ = -1;
        }

        action.retablir(carte); // Applique le redimensionnement + suppressions
        enregistrerAction(action);
        synchroniserChampsTaille();
    }

    /** Import CSV : réinitialise complètement la carte */
    public void appliquerImportCsv(int nouvelleLargeur, int nouvelleHauteur, List<int[]> bonbons) {
        ActionEditeur action = new ActionEditeur(carte);
        action.largeurApres = nouvelleLargeur;
        action.hauteurApres = nouvelleHauteur;
        action.apparitionApresX = -1;
        action.apparitionApresZ = -1;

        // Tous les éléments présents sont effacés
        for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
            action.ajouterChangement(CarteDonnees.cleX(entree.getKey()), CarteDonnees.cleZ(entree.getKey()),
                entree.getValue().copier(), new BlocCarte());
        }

        // Nouveaux blocs : Île (élévation 0) sous chaque bonbon ; les autres blocs
        // restent Vides (le choix de remplissage Eau/Pierre se fait à la sauvegarde)
        Map<Long, BlocCarte> nouveauxBlocs = new HashMap<>();
        for (int[] bonbon : bonbons) {
            int cx = bonbon[0];
            int cz = bonbon[1];
            boolean visible = bonbon[2] == 1;
            int quantite = bonbon[3];
            long cle = CarteDonnees.cle(cx, cz);
            BlocCarte bloc = nouveauxBlocs.computeIfAbsent(cle, k -> new BlocCarte(TypeElementCarte.ILE, 0));
            if (visible) {
                bloc.qteBonbonVisible += quantite;
            } else {
                bloc.qteBonbonNonVisible += quantite;
            }
        }
        for (Map.Entry<Long, BlocCarte> entree : nouveauxBlocs.entrySet()) {
            int cx = CarteDonnees.cleX(entree.getKey());
            int cz = CarteDonnees.cleZ(entree.getKey());
            BlocCarte avant = carte.obtenirBloc(cx, cz).copier();
            action.ajouterChangement(cx, cz, avant, entree.getValue());
        }

        action.retablir(carte);
        enregistrerAction(action);
        synchroniserChampsTaille();
        cellulesSelectionnees.clear();
        mettreAJourPanneauDelais();
    }

    /**
     * Charge une carte existante dans l'éditeur (le nom est pré-rempli).
     * Comme la fermeture, le chargement écrase le travail en cours : il demande
     * confirmation si des modifications ne sont pas sauvegardées.
     */
    public void chargerCarte(CarteDonnees carteChargee) {
        if (estModifie()) {
            afficherConfirmation("Modifications non sauvegardées",
                List.of("Charger « " + carteChargee.nom + " » remplacera les modifications non sauvegardées.",
                    "Voulez-vous continuer ?"),
                () -> appliquerCarteChargee(carteChargee),
                null);
            return;
        }
        appliquerCarteChargee(carteChargee);
    }

    private void appliquerCarteChargee(CarteDonnees carteChargee) {
        this.carte = carteChargee;
        this.texteNom = carteChargee.nom;
        if (champNom != null) {
            champNom.setValue(texteNom);
        }
        pileAnnulation.clear();
        pileRetablissement.clear();
        cellulesSelectionnees.clear();
        // La carte chargée devient le nouvel état « sauvegardé »
        reperePileSauvegarde = null;
        nomSauvegarde = texteNom;
        surCarteModifiee();
        synchroniserChampsTaille();
        mettreAJourPanneauDelais();
    }

    // ==================== Délais de réapparition ====================

    private boolean panneauDelaisVisible() {
        if (!modeSelection || cellulesSelectionnees.isEmpty()) {
            return false;
        }
        return selectionContientBonbonVisible() || selectionContientBonbonNonVisible();
    }

    private boolean selectionContientBonbonVisible() {
        return selectionABonbonVisible;
    }

    private boolean selectionContientBonbonNonVisible() {
        return selectionABonbonNonVisible;
    }

    /** Une passe sur la sélection : présence des deux familles de bonbons, nombre de blocs
     *  terrain (Île/Pierre) et leur élévation commune (null = valeurs mixtes ou aucun terrain) */
    private void recalculerResumeSelection() {
        selectionABonbonVisible = false;
        selectionABonbonNonVisible = false;
        selectionNbTerrain = 0;
        selectionElevationCommune = null;
        boolean elevationMixte = false;
        for (long cle : cellulesSelectionnees) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc == null) {
                continue;
            }
            if (bloc.qteBonbonVisible > 0) {
                selectionABonbonVisible = true;
            }
            if (bloc.qteBonbonNonVisible > 0) {
                selectionABonbonNonVisible = true;
            }
            if (bloc.type == TypeElementCarte.ILE || bloc.type == TypeElementCarte.PIERRE) {
                selectionNbTerrain++;
                if (!elevationMixte) {
                    if (selectionElevationCommune == null) {
                        selectionElevationCommune = bloc.elevation;
                    } else if (selectionElevationCommune != bloc.elevation) {
                        selectionElevationCommune = null;
                        elevationMixte = true;
                    }
                }
            }
        }
    }

    /** Met à jour la visibilité et le contenu des champs de délai selon la sélection */
    private void mettreAJourPanneauDelais() {
        if (champDelaiVisible == null || champDelaiNonVisible == null || boutonAppliquerDelais == null
            || champApparitionInitiale == null || champApparitionInitialeNonVisible == null
            || boutonTypeBonbon == null || boutonTypeBonbonNonVisible == null) {
            return;
        }

        boolean panneauVisible = panneauDelaisVisible();
        boolean aVisible = panneauVisible && selectionContientBonbonVisible();
        boolean aNonVisible = panneauVisible && selectionContientBonbonNonVisible();

        champDelaiVisible.visible = aVisible;
        champDelaiVisible.active = aVisible;
        champDelaiNonVisible.visible = aNonVisible;
        champDelaiNonVisible.active = aNonVisible;
        champApparitionInitiale.visible = aVisible;
        champApparitionInitiale.active = aVisible;
        champApparitionInitialeNonVisible.visible = aNonVisible;
        champApparitionInitialeNonVisible.active = aNonVisible;
        boutonTypeBonbon.visible = aVisible;
        boutonTypeBonbon.active = aVisible;
        boutonTypeBonbonNonVisible.visible = aNonVisible;
        boutonTypeBonbonNonVisible.active = aNonVisible;
        boutonAppliquerDelais.visible = panneauVisible;
        boutonAppliquerDelais.active = panneauVisible;

        if (aVisible) {
            Integer valeurCommune = valeurDelaiCommune(true);
            if (valeurCommune == null) {
                texteDelaiVisible = "";
                champDelaiVisible.setValue("");
                champDelaiVisible.setHint(Component.literal("—"));
            } else {
                texteDelaiVisible = valeurCommune > 0 ? String.valueOf(valeurCommune) : "";
                champDelaiVisible.setValue(texteDelaiVisible);
                champDelaiVisible.setHint(Component.literal(valeurCommune == 0 ? "0 (aucune)" : ""));
            }

            // Apparition initiale : valeur commune ou « — » si valeurs mixtes
            Integer apparitionCommune = valeurApparitionInitialeCommune(true);
            if (apparitionCommune == null) {
                texteApparitionInitiale = "";
                champApparitionInitiale.setValue("");
                champApparitionInitiale.setHint(Component.literal("—"));
            } else {
                texteApparitionInitiale = String.valueOf(apparitionCommune);
                champApparitionInitiale.setValue(texteApparitionInitiale);
                champApparitionInitiale.setHint(Component.literal(""));
            }

            // Type de bonbon : réinitialisé à « inchangé » à chaque nouvelle sélection
            // (la carte « Bonbon visible » de l'inspecteur donne le contexte du bouton)
            typeBonbonChoisi = null;
            com.example.mysubmod.cartes.TypeBonbonCarte typeCommun = typeBonbonCommun();
            boutonTypeBonbon.setMessage(Component.literal(
                typeCommun != null ? typeCommun.obtenirNomAffichage() + " (inchangé)" : "— (inchangé)"));
        }
        if (aNonVisible) {
            // Type de bonbon non-visible : réinitialisé à « inchangé » à chaque nouvelle sélection
            typeBonbonNonVisibleChoisi = null;
            com.example.mysubmod.cartes.TypeBonbonCarte typeCommunNonVisible = typeBonbonNonVisibleCommun();
            boutonTypeBonbonNonVisible.setMessage(Component.literal(
                typeCommunNonVisible != null
                    ? typeCommunNonVisible.obtenirNomAffichage() + " (inchangé)" : "— (inchangé)"));
            Integer valeurCommune = valeurDelaiCommune(false);
            if (valeurCommune == null) {
                texteDelaiNonVisible = "";
                champDelaiNonVisible.setValue("");
                champDelaiNonVisible.setHint(Component.literal("—"));
            } else {
                texteDelaiNonVisible = valeurCommune > 0 ? String.valueOf(valeurCommune) : "";
                champDelaiNonVisible.setValue(texteDelaiNonVisible);
                champDelaiNonVisible.setHint(Component.literal(valeurCommune == 0 ? "0 (aucune)" : ""));
            }

            // Apparition initiale non-visible : valeur commune ou « — » si valeurs mixtes
            Integer apparitionCommune = valeurApparitionInitialeCommune(false);
            if (apparitionCommune == null) {
                texteApparitionInitialeNonVisible = "";
                champApparitionInitialeNonVisible.setValue("");
                champApparitionInitialeNonVisible.setHint(Component.literal("—"));
            } else {
                texteApparitionInitialeNonVisible = String.valueOf(apparitionCommune);
                champApparitionInitialeNonVisible.setValue(texteApparitionInitialeNonVisible);
                champApparitionInitialeNonVisible.setHint(Component.literal(""));
            }
        }

        // Repositionnement : les cartes de l'inspecteur s'empilent selon les familles
        // présentes dans la sélection (les cadres sont dessinés par dessinerInspecteur
        // aux mêmes positions, dérivées des flags de visibilité des champs)
        int xChampSel = xChampSelection();
        int xTypeSel = xTypeSelection();
        int ySel = hautGrille + 18;
        if (aVisible) {
            champDelaiVisible.setPosition(xChampSel, ySel + 16);
            champApparitionInitiale.setPosition(xChampSel, ySel + 16 + pitchSelection);
            boutonTypeBonbon.setPosition(xTypeSel, ySel + 16 + 2 * pitchSelection);
            ySel += hauteurCarteSelection + 4;
        }
        if (aNonVisible) {
            champDelaiNonVisible.setPosition(xChampSel, ySel + 16);
            champApparitionInitialeNonVisible.setPosition(xChampSel, ySel + 16 + pitchSelection);
            boutonTypeBonbonNonVisible.setPosition(xTypeSel, ySel + 16 + 2 * pitchSelection);
            ySel += hauteurCarteSelection + 4;
        }
        boutonAppliquerDelais.setPosition(this.width - largeurPanneauDroit + 8, ySel);
    }

    /** Valeur d'apparition initiale commune aux blocs sélectionnés contenant ce type de bonbon (null = mixte) */
    private Integer valeurApparitionInitialeCommune(boolean visible) {
        Integer valeur = null;
        for (long cle : cellulesSelectionnees) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc == null || (visible ? bloc.qteBonbonVisible : bloc.qteBonbonNonVisible) <= 0) {
                continue;
            }
            int delai = visible ? bloc.delaiApparitionInitiale : bloc.delaiApparitionInitialeNonVisible;
            if (valeur == null) {
                valeur = delai;
            } else if (valeur != delai) {
                return null;
            }
        }
        return valeur;
    }

    /** Type de bonbon commun aux blocs sélectionnés à bonbons visibles (null = mixte) */
    private com.example.mysubmod.cartes.TypeBonbonCarte typeBonbonCommun() {
        com.example.mysubmod.cartes.TypeBonbonCarte type = null;
        for (long cle : cellulesSelectionnees) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc == null || bloc.qteBonbonVisible <= 0) {
                continue;
            }
            if (type == null) {
                type = bloc.typeBonbonVisible;
            } else if (type != bloc.typeBonbonVisible) {
                return null;
            }
        }
        return type;
    }

    /** Fait tourner le choix de type : (inchangé) → Standard → Bleu → Rouge → (inchangé) */
    private void cyclerTypeBonbon() {
        if (typeBonbonChoisi == null) {
            typeBonbonChoisi = com.example.mysubmod.cartes.TypeBonbonCarte.STANDARD;
        } else if (typeBonbonChoisi == com.example.mysubmod.cartes.TypeBonbonCarte.ROUGE) {
            typeBonbonChoisi = null;
        } else {
            typeBonbonChoisi = typeBonbonChoisi.suivant();
        }
        boutonTypeBonbon.setMessage(Component.literal(
            typeBonbonChoisi == null ? "(inchangé)" : typeBonbonChoisi.obtenirNomAffichage()));
    }

    /** Type de bonbon commun aux blocs sélectionnés à bonbons non-visibles (null = mixte) */
    private com.example.mysubmod.cartes.TypeBonbonCarte typeBonbonNonVisibleCommun() {
        com.example.mysubmod.cartes.TypeBonbonCarte type = null;
        for (long cle : cellulesSelectionnees) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc == null || bloc.qteBonbonNonVisible <= 0) {
                continue;
            }
            if (type == null) {
                type = bloc.typeBonbonNonVisible;
            } else if (type != bloc.typeBonbonNonVisible) {
                return null;
            }
        }
        return type;
    }

    /** Fait tourner le choix de type non-visible : (inchangé) → Standard → Bleu → Rouge → (inchangé) */
    private void cyclerTypeBonbonNonVisible() {
        if (typeBonbonNonVisibleChoisi == null) {
            typeBonbonNonVisibleChoisi = com.example.mysubmod.cartes.TypeBonbonCarte.STANDARD;
        } else if (typeBonbonNonVisibleChoisi == com.example.mysubmod.cartes.TypeBonbonCarte.ROUGE) {
            typeBonbonNonVisibleChoisi = null;
        } else {
            typeBonbonNonVisibleChoisi = typeBonbonNonVisibleChoisi.suivant();
        }
        boutonTypeBonbonNonVisible.setMessage(Component.literal(
            typeBonbonNonVisibleChoisi == null ? "(inchangé)"
                : typeBonbonNonVisibleChoisi.obtenirNomAffichage()));
    }

    /** Valeur de délai commune aux blocs sélectionnés contenant ce type de bonbon (null = valeurs mixtes) */
    private Integer valeurDelaiCommune(boolean visible) {
        Integer valeur = null;
        for (long cle : cellulesSelectionnees) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc == null) {
                continue;
            }
            int quantite = visible ? bloc.qteBonbonVisible : bloc.qteBonbonNonVisible;
            if (quantite <= 0) {
                continue;
            }
            int delai = visible ? bloc.delaiBonbonVisible : bloc.delaiBonbonNonVisible;
            if (valeur == null) {
                valeur = delai;
            } else if (valeur != delai) {
                return null; // Valeurs mixtes
            }
        }
        return valeur;
    }

    /** Bouton « Appliquer » : assigne délais, apparition initiale et type aux blocs sélectionnés */
    private void appliquerDelais() {
        Integer delaiVisible = null;
        Integer delaiNonVisible = null;
        Integer apparitionInitiale = null;

        if (champDelaiVisible.visible && !texteDelaiVisible.trim().isEmpty()) {
            try {
                delaiVisible = Integer.parseInt(texteDelaiVisible.trim());
            } catch (NumberFormatException e) {
                afficherMessage("Valeur refusée", "Délai bonbon visible : valeur non numérique");
                return;
            }
            if (delaiVisible < 1) {
                afficherMessage("Valeur refusée",
                    "Le délai minimum est de 1 seconde (0 est réservé au comportement par défaut)");
                return;
            }
        }
        if (champDelaiNonVisible.visible && !texteDelaiNonVisible.trim().isEmpty()) {
            try {
                delaiNonVisible = Integer.parseInt(texteDelaiNonVisible.trim());
            } catch (NumberFormatException e) {
                afficherMessage("Valeur refusée", "Délai bonbon non-visible : valeur non numérique");
                return;
            }
            if (delaiNonVisible < 1) {
                afficherMessage("Valeur refusée",
                    "Le délai minimum est de 1 seconde (0 est réservé au comportement par défaut)");
                return;
            }
        }
        if (champApparitionInitiale.visible && !texteApparitionInitiale.trim().isEmpty()) {
            try {
                apparitionInitiale = Integer.parseInt(texteApparitionInitiale.trim());
            } catch (NumberFormatException e) {
                afficherMessage("Valeur refusée", "Apparition initiale visible : valeur non numérique");
                return;
            }
            if (apparitionInitiale < 0) {
                afficherMessage("Valeur refusée", "L'apparition initiale doit être de 0 seconde ou plus");
                return;
            }
        }
        Integer apparitionInitialeNonVisible = null;
        if (champApparitionInitialeNonVisible.visible && !texteApparitionInitialeNonVisible.trim().isEmpty()) {
            try {
                apparitionInitialeNonVisible = Integer.parseInt(texteApparitionInitialeNonVisible.trim());
            } catch (NumberFormatException e) {
                afficherMessage("Valeur refusée", "Apparition initiale non-visible : valeur non numérique");
                return;
            }
            if (apparitionInitialeNonVisible < 0) {
                afficherMessage("Valeur refusée", "L'apparition initiale doit être de 0 seconde ou plus");
                return;
            }
        }

        com.example.mysubmod.cartes.TypeBonbonCarte typeApplique = typeBonbonChoisi;
        com.example.mysubmod.cartes.TypeBonbonCarte typeNonVisibleApplique = typeBonbonNonVisibleChoisi;

        if (delaiVisible == null && delaiNonVisible == null && apparitionInitiale == null
            && apparitionInitialeNonVisible == null && typeApplique == null
            && typeNonVisibleApplique == null) {
            return; // Rien à appliquer (les valeurs existantes ne sont pas écrasées)
        }

        ActionEditeur action = new ActionEditeur(carte);
        for (long cle : cellulesSelectionnees) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc == null) {
                continue;
            }
            BlocCarte avant = bloc.copier();
            BlocCarte apres = bloc.copier();
            boolean changement = false;
            if (delaiVisible != null && bloc.qteBonbonVisible > 0 && bloc.delaiBonbonVisible != delaiVisible) {
                apres.delaiBonbonVisible = delaiVisible;
                changement = true;
            }
            if (delaiNonVisible != null && bloc.qteBonbonNonVisible > 0 && bloc.delaiBonbonNonVisible != delaiNonVisible) {
                apres.delaiBonbonNonVisible = delaiNonVisible;
                changement = true;
            }
            if (apparitionInitiale != null && bloc.qteBonbonVisible > 0
                && bloc.delaiApparitionInitiale != apparitionInitiale) {
                apres.delaiApparitionInitiale = apparitionInitiale;
                changement = true;
            }
            if (apparitionInitialeNonVisible != null && bloc.qteBonbonNonVisible > 0
                && bloc.delaiApparitionInitialeNonVisible != apparitionInitialeNonVisible) {
                apres.delaiApparitionInitialeNonVisible = apparitionInitialeNonVisible;
                changement = true;
            }
            if (typeApplique != null && bloc.qteBonbonVisible > 0 && bloc.typeBonbonVisible != typeApplique) {
                apres.typeBonbonVisible = typeApplique;
                changement = true;
            }
            if (typeNonVisibleApplique != null && bloc.qteBonbonNonVisible > 0
                && bloc.typeBonbonNonVisible != typeNonVisibleApplique) {
                apres.typeBonbonNonVisible = typeNonVisibleApplique;
                changement = true;
            }
            if (changement) {
                int cx = CarteDonnees.cleX(cle);
                int cz = CarteDonnees.cleZ(cle);
                action.ajouterChangement(cx, cz, avant, apres);
                carte.definirBloc(cx, cz, apres);
            }
        }
        if (!action.estVide()) {
            enregistrerAction(action);
            mettreAJourPanneauDelais();
            afficherToast("Propriétés assignées aux blocs sélectionnés", false);
        }
    }

    // ==================== Sauvegarde ====================

    private void sauvegarder() {
        String nomAssaini = CarteDonnees.assainirNom(texteNom);
        texteNom = nomAssaini;
        if (champNom != null) {
            champNom.setValue(nomAssaini);
        }
        carte.nom = nomAssaini;

        // Proposer de remplir les blocs sans élément de base à l'intérieur du périmètre Limite
        if (carte.limiteEstBoucleFermeeValide()) {
            int vides = 0;
            for (long cle : carte.calculerInterieurLimite()) {
                BlocCarte bloc = carte.blocs.get(cle);
                if (bloc == null || !bloc.type.estElementDeBase()) {
                    vides++;
                }
            }
            if (vides > 0) {
                afficherChoix("Blocs vides à l'intérieur",
                    List.of(vides + " bloc(s) à l'intérieur du périmètre Limite n'ont pas d'élément de base.",
                        "Voulez-vous les remplir automatiquement ?"),
                    List.of("Eau", "Pierre", "Annuler"),
                    java.util.Arrays.asList(
                        () -> {
                            remplirInterieurVide(TypeElementCarte.EAU);
                            poursuivreSauvegarde();
                        },
                        () -> {
                            remplirInterieurVide(TypeElementCarte.PIERRE);
                            poursuivreSauvegarde();
                        },
                        null));
                return;
            }
        }

        poursuivreSauvegarde();
    }

    private void poursuivreSauvegarde() {
        List<String> erreurs = carte.validerPourSauvegarde();
        if (!erreurs.isEmpty()) {
            afficherMessage("Sauvegarde bloquée", erreurs);
            return;
        }

        // Instantané du point envoyé : les modifications faites pendant l'aller-retour
        // réseau ne sont pas dans ce JSON et doivent rester marquées non sauvegardées
        reperePileEnvoi = pileAnnulation.peek();
        nomEnvoi = texteNom;
        jsonSauvegardeEnAttente = carte.versJson();
        envoyerSauvegarde(jsonSauvegardeEnAttente, false);
    }

    /** Au-delà de ce nombre de blocs, une action de masse (remplissage, élévation de la
     *  sélection) n'est plus annulable (l'action d'annulation copierait deux BlocCarte
     *  par cellule : gigaoctets sur une grande carte) */
    private static final int MAX_BLOCS_ACTION_ANNULABLE = 500_000;

    /**
     * Remplit les blocs sans élément de base à l'intérieur du périmètre Limite
     * avec de l'Eau ou de la Pierre (élévation 0). Action annulable, sauf pour les
     * remplissages géants où l'historique d'annulation est vidé à la place.
     * Les blocs contenant un bonbon non-visible ne peuvent pas recevoir d'Eau.
     */
    private void remplirInterieurVide(TypeElementCarte type) {
        Set<Long> interieur = carte.calculerInterieurLimite();

        int aRemplir = 0;
        for (long cle : interieur) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc != null && bloc.type.estElementDeBase()) {
                continue;
            }
            if (type == TypeElementCarte.EAU && bloc != null && bloc.qteBonbonNonVisible > 0) {
                continue;
            }
            aRemplir++;
        }
        boolean annulable = aRemplir <= MAX_BLOCS_ACTION_ANNULABLE;

        ActionEditeur action = annulable ? new ActionEditeur(carte) : null;
        for (long cle : interieur) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc != null && bloc.type.estElementDeBase()) {
                continue;
            }
            if (type == TypeElementCarte.EAU && bloc != null && bloc.qteBonbonNonVisible > 0) {
                continue; // L'eau ne peut pas contenir de bonbon non-visible
            }
            int cx = CarteDonnees.cleX(cle);
            int cz = CarteDonnees.cleZ(cle);
            BlocCarte apres = (bloc != null ? bloc : new BlocCarte()).copier();
            if (action != null) {
                action.ajouterChangement(cx, cz, apres.copier(), apres);
            }
            apres.type = type;
            apres.elevation = 0;
            carte.definirBloc(cx, cz, apres);
        }

        if (action != null) {
            if (!action.estVide()) {
                enregistrerAction(action);
            }
        } else if (aRemplir > 0) {
            // Remplissage géant : l'historique ne reflète plus l'état, on le vide,
            // et un repère jamais empilé garde l'indicateur ● allumé jusqu'à la sauvegarde
            pileAnnulation.clear();
            pileRetablissement.clear();
            reperePileSauvegarde = new ActionEditeur(carte);
            surCarteModifiee();
            afficherToast("Remplissage de " + aRemplir + " blocs appliqué (trop volumineux pour l'annulation)", false);
        }
    }

    private void envoyerSauvegarde(String json, boolean ecraserConfirme) {
        List<byte[]> morceaux = com.example.mysubmod.cartes.reseau.UtilitaireCompressionCarte
            .compresserEtDecouper(json.getBytes(StandardCharsets.UTF_8));
        if (morceaux.size() > com.example.mysubmod.cartes.GestionnaireCartes.MAX_MORCEAUX) {
            // Le serveur rejetterait le transfert sans réponse : bloquer ici avec un message clair
            jsonSauvegardeEnAttente = null;
            afficherMessage("Sauvegarde bloquée",
                "La carte est trop volumineuse pour être transmise (" + morceaux.size()
                    + " morceaux, maximum " + com.example.mysubmod.cartes.GestionnaireCartes.MAX_MORCEAUX + ")");
            return;
        }
        UUID idTransfert = UUID.randomUUID();
        for (int i = 0; i < morceaux.size(); i++) {
            GestionnaireReseau.INSTANCE.sendToServer(
                new PaquetSauvegardeCarte(idTransfert, morceaux.size(), i, morceaux.get(i), ecraserConfirme));
        }
    }

    public void surSauvegardeReussie(String nomCarte) {
        jsonSauvegardeEnAttente = null;
        // L'état sauvegardé est celui de l'envoi : si l'utilisateur a modifié la carte
        // pendant l'aller-retour réseau, l'indicateur ● reste allumé
        reperePileSauvegarde = reperePileEnvoi;
        nomSauvegarde = nomEnvoi;
        mettreAJourIndicateurSauvegarde();
        afficherToast("La carte « " + nomCarte + " » a été sauvegardée", false);
    }

    public void surDemandeConfirmationEcrasement(String nomCarte) {
        afficherConfirmation("Carte existante",
            List.of("Une carte nommée « " + nomCarte + " » existe déjà.", "Voulez-vous l'écraser ?"),
            () -> {
                if (jsonSauvegardeEnAttente != null) {
                    envoyerSauvegarde(jsonSauvegardeEnAttente, true);
                }
            },
            () -> jsonSauvegardeEnAttente = null);
    }

    public void surErreurSauvegarde(String message) {
        jsonSauvegardeEnAttente = null;
        afficherMessage("Sauvegarde bloquée", message);
    }

    /** Le fichier reçu du serveur n'a pas pu être décodé (corrompu / illisible) */
    public void surErreurChargement() {
        afficherMessage("Chargement échoué",
            "Le fichier de la carte est illisible ou corrompu (détails dans le journal du jeu)");
    }

    // ==================== Entrées souris / clavier ====================

    @Override
    public boolean mouseClicked(double sourisX, double sourisY, int bouton) {
        if (modalActif) {
            gererClicModal(sourisX, sourisY);
            return true;
        }

        // Pan avec le bouton du milieu
        if (bouton == 2) {
            panEnCours = true;
            return true;
        }

        int[] cellule = celluleSousSouris(sourisX, sourisY);
        if (cellule != null) {
            this.setFocused(null); // Retirer le focus des champs de saisie
            if (modeSelection) {
                if (bouton == 0) {
                    tracageSelection = true;
                    selectionDebutX = sourisX;
                    selectionDebutY = sourisY;
                    selectionCouranteX = sourisX;
                    selectionCouranteY = sourisY;
                    return true;
                }
            } else {
                if (bouton == 0) {
                    if (estOutilPinceau()) {
                        commencerPeinture(cellule[0], cellule[1]); // Pinceau : peinture par glissement
                    } else {
                        placerElement(cellule[0], cellule[1], false);
                    }
                    return true;
                } else if (bouton == 1) {
                    retirerElement(cellule[0], cellule[1]);
                    return true;
                }
            }
        } else if (modeSelection && bouton == 0
            && sourisX >= grilleGaucheEcran() && sourisX < grilleDroiteEcran()
            && sourisY >= hautGrille && sourisY < basGrille()) {
            // Cliquer dans le vide (hors de l'aire) désélectionne tout
            cellulesSelectionnees.clear();
            mettreAJourPanneauDelais();
        }

        return super.mouseClicked(sourisX, sourisY, bouton);
    }

    @Override
    public boolean mouseDragged(double sourisX, double sourisY, int bouton, double deltaX, double deltaY) {
        if (modalActif) {
            return true;
        }
        if (panEnCours && bouton == 2) {
            decalageX += deltaX;
            decalageY += deltaY;
            return true;
        }
        if (tracageSelection && bouton == 0) {
            selectionCouranteX = sourisX;
            selectionCouranteY = sourisY;
            return true;
        }
        // Pinceau : peindre les blocs traversés par le glissement
        if (peintureEnCours && bouton == 0) {
            int[] cellule = celluleSousSouris(sourisX, sourisY);
            if (cellule != null && (cellule[0] != dernierePeintureCx || cellule[1] != dernierePeintureCz)) {
                peindreSegment(dernierePeintureCx, dernierePeintureCz, cellule[0], cellule[1]);
                dernierePeintureCx = cellule[0];
                dernierePeintureCz = cellule[1];
            }
            return true;
        }
        return super.mouseDragged(sourisX, sourisY, bouton, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double sourisX, double sourisY, int bouton) {
        if (bouton == 2) {
            panEnCours = false;
        }
        if (tracageSelection && bouton == 0) {
            tracageSelection = false;
            finaliserSelection(sourisX, sourisY);
            return true;
        }
        if (peintureEnCours && bouton == 0) {
            terminerPeinture();
            return true;
        }
        return super.mouseReleased(sourisX, sourisY, bouton);
    }

    private void finaliserSelection(double finX, double finY) {
        double minEcranX = Math.min(selectionDebutX, finX);
        double maxEcranX = Math.max(selectionDebutX, finX);
        double minEcranY = Math.min(selectionDebutY, finY);
        double maxEcranY = Math.max(selectionDebutY, finY);

        int minCx = (int) Math.floor(ecranVersCelluleX(minEcranX));
        int maxCx = (int) Math.floor(ecranVersCelluleX(maxEcranX));
        int minCz = (int) Math.floor(ecranVersCelluleZ(minEcranY));
        int maxCz = (int) Math.floor(ecranVersCelluleZ(maxEcranY));

        cellulesSelectionnees.clear();
        for (int cx = Math.max(0, minCx); cx <= Math.min(carte.largeur - 1, maxCx); cx++) {
            for (int cz = Math.max(0, minCz); cz <= Math.min(carte.hauteur - 1, maxCz); cz++) {
                BlocCarte bloc = carte.obtenirBlocOuNull(cx, cz);
                if (bloc != null && (bloc.qteBonbonVisible > 0 || bloc.qteBonbonNonVisible > 0
                    || bloc.type == TypeElementCarte.ILE || bloc.type == TypeElementCarte.PIERRE)) {
                    cellulesSelectionnees.add(CarteDonnees.cle(cx, cz));
                }
            }
        }
        recalculerResumeSelection();
        mettreAJourPanneauDelais();
    }

    @Override
    public boolean mouseScrolled(double sourisX, double sourisY, double delta) {
        if (modalActif) {
            return true;
        }

        // Ctrl+molette : élévation d'un cran — de toute la sélection s'il y en a une,
        // sinon du bloc survolé
        if (hasControlDown()) {
            if (modeSelection && !cellulesSelectionnees.isEmpty()) {
                changerElevationSelection(delta > 0 ? 1 : -1);
                return true;
            }
            int[] cellule = celluleSousSouris(sourisX, sourisY);
            if (cellule != null) {
                changerElevation(cellule[0], cellule[1], delta > 0 ? 1 : -1);
            }
            return true;
        }

        // Molette : zoom / dézoom centré sur la souris.
        if (sourisX >= grilleGaucheEcran() && sourisX < grilleDroiteEcran()
            && sourisY >= hautGrille && sourisY < basGrille()) {
            zoomer(delta > 0 ? 1.2 : 1 / 1.2, sourisX, sourisY);
            return true;
        }

        return super.mouseScrolled(sourisX, sourisY, delta);
    }

    /** Taille de cellule qui cadre toute la carte dans la vue (avec une marge de 5 %) */
    private double tailleCelluleAjustee() {
        int largeurVue = grilleDroiteEcran() - grilleGaucheEcran();
        int hauteurVue = basGrille() - hautGrille;
        return Math.min((double) largeurVue / carte.largeur, (double) hauteurVue / carte.hauteur) * 0.95;
    }

    /**
     * Zoom / dézoom autour d'un point de l'écran (la cellule sous le pivot est conservée).
     * Le dézoom permet toujours de voir l'ensemble de la carte, quelle que soit sa taille.
     */
    private void zoomer(double facteur, double pivotX, double pivotY) {
        double celluleX = ecranVersCelluleX(pivotX);
        double celluleZ = ecranVersCelluleZ(pivotY);
        // Plancher très bas : une carte 1800×1800 doit pouvoir tenir entière dans la vue
        double tailleMin = Math.max(0.05, Math.min(3, tailleCelluleAjustee()));
        tailleCellule = Math.max(tailleMin, Math.min(48, tailleCellule * facteur));
        decalageX = pivotX - grilleGaucheEcran() - celluleX * tailleCellule;
        decalageY = pivotY - hautGrille - celluleZ * tailleCellule;
    }

    /** Boutons − / + de la barre d'état : zoom autour du centre de la vue */
    private void zoomerDepuisBoutons(double facteur) {
        zoomer(facteur, (grilleGaucheEcran() + grilleDroiteEcran()) / 2.0, (hautGrille + basGrille()) / 2.0);
    }

    /** Bouton « Ajuster » : cadre toute la carte, centrée dans la vue */
    private void ajusterVue() {
        tailleCellule = Math.max(0.05, Math.min(48, tailleCelluleAjustee()));
        decalageX = (grilleDroiteEcran() - grilleGaucheEcran() - carte.largeur * tailleCellule) / 2;
        decalageY = (basGrille() - hautGrille - carte.hauteur * tailleCellule) / 2;
    }

    @Override
    public boolean keyPressed(int touche, int scanCode, int modificateurs) {
        if (modalActif) {
            if (touche == GLFW.GLFW_KEY_ESCAPE) {
                // Échap : équivaut au dernier bouton (annulation)
                Runnable action = modalActions.isEmpty() ? null : modalActions.get(modalActions.size() - 1);
                fermerModal();
                if (action != null) {
                    action.run();
                }
                return true;
            }
            if (touche == GLFW.GLFW_KEY_ENTER && modalBoutons.size() == 1) {
                Runnable action = modalActions.isEmpty() ? null : modalActions.get(0);
                fermerModal();
                if (action != null) {
                    action.run();
                }
                return true;
            }
            return true;
        }

        // Laisser les champs de saisie gérer le clavier lorsqu'ils ont le focus
        boolean champFocus = this.getFocused() instanceof EditBox champ && champ.isFocused();

        if (!champFocus) {
            // Ctrl+Z : annuler, Ctrl+Y : rétablir
            if (hasControlDown() && touche == GLFW.GLFW_KEY_Z) {
                annuler();
                return true;
            }
            if (hasControlDown() && touche == GLFW.GLFW_KEY_Y) {
                retablir();
                return true;
            }

            // Défilement de la grille avec les flèches
            double pas = tailleCellule * 2;
            if (touche == GLFW.GLFW_KEY_LEFT) {
                decalageX += pas;
                return true;
            }
            if (touche == GLFW.GLFW_KEY_RIGHT) {
                decalageX -= pas;
                return true;
            }
            if (touche == GLFW.GLFW_KEY_UP) {
                decalageY += pas;
                return true;
            }
            if (touche == GLFW.GLFW_KEY_DOWN) {
                decalageY -= pas;
                return true;
            }
        }

        return super.keyPressed(touche, scanCode, modificateurs);
    }

    @Override
    public boolean charTyped(char caractere, int modificateurs) {
        if (modalActif) {
            return true;
        }
        return super.charTyped(caractere, modificateurs);
    }

    @Override
    public void onClose() {
        // Garde-fou : confirmer la fermeture lorsque des modifications ne sont pas sauvegardées
        if (estModifie() && !fermetureConfirmee) {
            afficherConfirmation("Modifications non sauvegardées",
                List.of("La carte contient des modifications non sauvegardées.",
                    "Voulez-vous vraiment fermer l'éditeur ?"),
                () -> {
                    fermetureConfirmee = true;
                    this.onClose();
                },
                null);
            return;
        }
        GestionnaireReseau.INSTANCE.sendToServer(new PaquetFermetureEditeurCarte());
        super.onClose();
    }

    // ==================== Rendu ====================

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        // Fond des panneaux : barre du haut, palette, inspecteur, barre d'état
        guiGraphics.fill(0, 0, this.width, hautGrille, 0xE0202020);
        guiGraphics.fill(0, hautGrille, largeurPalette, basGrille(), 0xE0181818);
        guiGraphics.fill(this.width - largeurPanneauDroit, hautGrille, this.width, basGrille(), 0xE0181818);
        guiGraphics.fill(0, basGrille(), this.width, this.height, 0xE0202020);

        dessinerGrille(guiGraphics, sourisX, sourisY);

        // Étiquettes de groupes / titres de sections et séparateurs verticaux
        for (TextePeint texte : textesPeints) {
            guiGraphics.drawString(this.font, texte.texte(), texte.x(), texte.y(), texte.couleur());
        }
        for (int[] separateur : separateursPeints) {
            guiGraphics.fill(separateur[0], separateur[1], separateur[0] + 1, separateur[2], 0xFF3A4046);
        }

        int[] cellule = modalActif ? null : celluleSousSouris(sourisX, sourisY);
        dessinerInspecteur(guiGraphics, cellule);
        dessinerBarreEtat(guiGraphics, cellule);

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);

        // Rectangle de sélection en cours
        if (tracageSelection) {
            int minX = (int) Math.min(selectionDebutX, selectionCouranteX);
            int maxX = (int) Math.max(selectionDebutX, selectionCouranteX);
            int minY = (int) Math.min(selectionDebutY, selectionCouranteY);
            int maxY = (int) Math.max(selectionDebutY, selectionCouranteY);
            guiGraphics.fill(minX, minY, maxX, maxY, 0x3000FFFF);
            dessinerCadre(guiGraphics, minX, minY, maxX, maxY, 0xFF00FFFF);
        }

        dessinerToast(guiGraphics);

        // Fenêtre modale au-dessus de tout : élevée sur l'axe Z pour que le test de
        // profondeur masque les textes de l'éditeur dessinés dans une passe ultérieure
        // (sinon ils transparaissent malgré un fond opaque, à cause du rendu par lots)
        if (modalActif) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 400);
            dessinerModal(guiGraphics, sourisX, sourisY);
            guiGraphics.pose().popPose();
        }
    }

    private void dessinerGrille(GuiGraphics guiGraphics, int sourisX, int sourisY) {
        int gauche = grilleGaucheEcran();
        int droite = grilleDroiteEcran();
        int haut = hautGrille;
        int bas = basGrille();

        guiGraphics.enableScissor(gauche, haut, droite, bas);

        // Plage de cellules visibles
        int cxMin = Math.max(0, (int) Math.floor((-decalageX) / tailleCellule) - 1);
        int cxMax = Math.min(carte.largeur - 1, (int) Math.ceil((droite - gauche - decalageX) / tailleCellule) + 1);
        int czMin = Math.max(0, (int) Math.floor((-decalageY) / tailleCellule) - 1);
        int czMax = Math.min(carte.hauteur - 1, (int) Math.ceil((bas - haut - decalageY) / tailleCellule) + 1);

        int taille = Math.max(1, (int) Math.floor(tailleCellule));

        // 1) Couleurs de base par plages horizontales de même couleur. Sous 1 px par
        //    cellule, une cellule est échantillonnée par pixel : le coût par image reste
        //    proportionnel à l'écran et non à l'aire (indispensable en 1800×1800 dézoomé)
        int pasEchantillon = Math.max(1, (int) Math.ceil(1.0 / tailleCellule));
        for (int cz = czMin; cz <= czMax; cz += pasEchantillon) {
            int czFin = Math.min(cz + pasEchantillon, czMax + 1);
            int y0 = (int) Math.floor(haut + decalageY + cz * tailleCellule);
            int y1 = (int) Math.floor(haut + decalageY + czFin * tailleCellule);
            if (y1 <= y0) {
                y1 = y0 + 1;
            }
            int debutPlage = cxMin;
            int couleurPlage = couleurBloc(carte.obtenirBlocOuNull(cxMin, cz));
            for (int cx = cxMin + pasEchantillon; cx <= cxMax + pasEchantillon; cx += pasEchantillon) {
                int couleur = cx <= cxMax ? couleurBloc(carte.obtenirBlocOuNull(cx, cz)) : 0;
                if (cx > cxMax || couleur != couleurPlage) {
                    int cxFin = Math.min(cx, cxMax + 1);
                    int x0 = (int) Math.floor(gauche + decalageX + debutPlage * tailleCellule);
                    int x1 = (int) Math.floor(gauche + decalageX + cxFin * tailleCellule);
                    if (x1 <= x0) {
                        x1 = x0 + 1;
                    }
                    guiGraphics.fill(x0, y0, x1, y1, couleurPlage);
                    debutPlage = cx;
                    couleurPlage = couleur;
                }
            }
        }

        // 1b) En fort dézoom, l'échantillonnage peut sauter un mur Limite d'une cellule
        //     d'épaisseur : les cellules Limite (liste en cache, ~périmètre) sont
        //     redessinées explicitement pour que la boucle reste toujours visible
        if (pasEchantillon > 1) {
            rafraichirStatsCarte(); // rafraîchit aussi le cache des cellules Limite
            for (long cle : cellulesLimiteCache) {
                int cx = CarteDonnees.cleX(cle);
                int cz = CarteDonnees.cleZ(cle);
                if (cx < cxMin || cx > cxMax || cz < czMin || cz > czMax) {
                    continue;
                }
                int x0 = (int) Math.floor(gauche + decalageX + cx * tailleCellule);
                int y0 = (int) Math.floor(haut + decalageY + cz * tailleCellule);
                guiGraphics.fill(x0, y0, x0 + taille, y0 + taille, 0xFFC83232);
            }
        }

        // 2) Lignes de la grille (lignes complètes, seulement à un zoom suffisant)
        if (taille >= 4) {
            int yGrille0 = (int) Math.floor(haut + decalageY + czMin * tailleCellule);
            int yGrille1 = (int) Math.floor(haut + decalageY + (czMax + 1) * tailleCellule);
            int xGrille0 = (int) Math.floor(gauche + decalageX + cxMin * tailleCellule);
            int xGrille1 = (int) Math.floor(gauche + decalageX + (cxMax + 1) * tailleCellule);
            for (int cx = cxMin; cx <= cxMax + 1; cx++) {
                int x = (int) Math.floor(gauche + decalageX + cx * tailleCellule);
                guiGraphics.fill(x, yGrille0, x + 1, yGrille1, 0xFF101010);
            }
            for (int cz = czMin; cz <= czMax + 1; cz++) {
                int y = (int) Math.floor(haut + decalageY + cz * tailleCellule);
                guiGraphics.fill(xGrille0, y, xGrille1, y + 1, 0xFF101010);
            }
        }

        // 3) Superpositions par cellule : élévations et icônes de bonbons
        if (taille >= 5) {
            for (int cx = cxMin; cx <= cxMax; cx++) {
                for (int cz = czMin; cz <= czMax; cz++) {
                    BlocCarte bloc = carte.obtenirBlocOuNull(cx, cz);
                    if (bloc == null) {
                        continue;
                    }
                    int ecranX = (int) Math.floor(gauche + decalageX + cx * tailleCellule);
                    int ecranY = (int) Math.floor(haut + decalageY + cz * tailleCellule);

                    // Chiffre d'élévation affiché en permanence sur chaque bloc Île / Pierre
                    if (bloc.type == TypeElementCarte.ILE || bloc.type == TypeElementCarte.PIERRE) {
                        dessinerTexteCellule(guiGraphics, String.valueOf(bloc.elevation),
                            ecranX + taille / 2.0f, ecranY + taille / 2.0f, taille,
                            bloc.elevation < 0 ? 0xFF9FD3FF : 0xFFFFFFFF);
                    }

                    // Icônes des bonbons avec leur quantité (couleur selon le type : Standard/Bleu/Rouge)
                    if (bloc.qteBonbonVisible > 0 && taille >= 8) {
                        int icone = Math.max(5, taille / 2 - 1);
                        int couleurIcone = bloc.typeBonbonVisible.obtenirCouleurIcone();
                        int couleurTexte = bloc.typeBonbonVisible == com.example.mysubmod.cartes.TypeBonbonCarte.STANDARD
                            ? 0xFF000000 : 0xFFFFFFFF;
                        guiGraphics.fill(ecranX + 1, ecranY + 1, ecranX + 1 + icone, ecranY + 1 + icone, couleurIcone);
                        dessinerTexteCellule(guiGraphics, String.valueOf(bloc.qteBonbonVisible),
                            ecranX + 1 + icone / 2.0f, ecranY + 1 + icone / 2.0f, icone, couleurTexte);
                    }
                    if (bloc.qteBonbonNonVisible > 0 && taille >= 8) {
                        int icone = Math.max(5, taille / 2 - 1);
                        int x0 = ecranX + taille - icone - 1;
                        int y0 = ecranY + taille - icone - 1;
                        int x1 = ecranX + taille - 1;
                        int y1 = ecranY + taille - 1;
                        // Bonbon non-visible : couleur de son type (comme le visible) mais assombrie
                        // et entourée d'un cadre noir — la couleur donne le type, l'assombrissement +
                        // le cadre + la position (coin bas-droit) signalent « non-visible » (enterré).
                        int couleurType = bloc.typeBonbonNonVisible.obtenirCouleurIcone();
                        guiGraphics.fill(x0, y0, x1, y1, assombrir(couleurType, 0.6f));
                        dessinerCadre(guiGraphics, x0, y0, x1, y1, 0xFF000000);
                        dessinerTexteCellule(guiGraphics, String.valueOf(bloc.qteBonbonNonVisible),
                            (x0 + x1) / 2.0f, (y0 + y1) / 2.0f, icone, 0xFFFFFFFF);
                    }
                }
            }
        }

        // 4) Sélection mise en évidence
        for (long cle : cellulesSelectionnees) {
            int cx = CarteDonnees.cleX(cle);
            int cz = CarteDonnees.cleZ(cle);
            if (cx < cxMin || cx > cxMax || cz < czMin || cz > czMax) {
                continue;
            }
            int x0 = (int) Math.floor(gauche + decalageX + cx * tailleCellule);
            int y0 = (int) Math.floor(haut + decalageY + cz * tailleCellule);
            guiGraphics.fill(x0, y0, x0 + taille, y0 + taille, 0x6000FFFF);
        }

        // Point d'apparition des joueurs (icône drapeau)
        if (carte.aPointApparition()
            && carte.apparitionX >= cxMin && carte.apparitionX <= cxMax
            && carte.apparitionZ >= czMin && carte.apparitionZ <= czMax) {
            int ecranX = (int) Math.floor(gauche + decalageX + carte.apparitionX * tailleCellule);
            int ecranY = (int) Math.floor(haut + decalageY + carte.apparitionZ * tailleCellule);
            dessinerTexteCellule(guiGraphics, "⚑", ecranX + taille / 2.0f, ecranY + taille / 2.0f,
                Math.max(8, taille), 0xFFFF3030);
        }

        // Contour de l'aire totale
        int aireX0 = (int) Math.floor(gauche + decalageX);
        int aireY0 = (int) Math.floor(haut + decalageY);
        int aireX1 = (int) Math.floor(gauche + decalageX + carte.largeur * tailleCellule);
        int aireY1 = (int) Math.floor(haut + decalageY + carte.hauteur * tailleCellule);
        dessinerCadre(guiGraphics, aireX0, aireY0, aireX1, aireY1, 0xFFFFFFFF);

        // Cellule survolée
        int[] cellule = celluleSousSouris(sourisX, sourisY);
        if (cellule != null && !modalActif) {
            int ecranX = (int) Math.floor(gauche + decalageX + cellule[0] * tailleCellule);
            int ecranY = (int) Math.floor(haut + decalageY + cellule[1] * tailleCellule);
            dessinerCadre(guiGraphics, ecranX, ecranY, ecranX + taille, ecranY + taille, 0xFFFFFFFF);
        }

        guiGraphics.disableScissor();
    }

    /** Assombrit une couleur ARGB (canaux RGB multipliés par le facteur, alpha conservé) */
    private static int assombrir(int argb, float facteur) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * facteur);
        int g = (int) (((argb >> 8) & 0xFF) * facteur);
        int b = (int) ((argb & 0xFF) * facteur);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int couleurBloc(BlocCarte bloc) {
        if (bloc == null || bloc.type == TypeElementCarte.VIDE) {
            return 0xFF2B2B2B;
        }
        return switch (bloc.type) {
            case EAU -> 0xFF2E64C8;
            case ILE -> 0xFF3FA34D;
            case PIERRE -> 0xFF8C8C8C;
            case LIMITE -> 0xFFC83232;
            default -> 0xFF2B2B2B;
        };
    }

    /** Dessine un texte centré et mis à l'échelle pour tenir dans une cellule */
    private void dessinerTexteCellule(GuiGraphics guiGraphics, String texte, float centreX, float centreY,
                                      int taillePixels, int couleur) {
        float echelle = Math.min(1.0f, taillePixels / 12.0f);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centreX, centreY, 0);
        guiGraphics.pose().scale(echelle, echelle, 1.0f);
        int largeurTexte = this.font.width(texte);
        guiGraphics.drawString(this.font, texte, -largeurTexte / 2, -this.font.lineHeight / 2, couleur, false);
        guiGraphics.pose().popPose();
    }

    private static void dessinerCadre(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int couleur) {
        guiGraphics.fill(x0, y0, x1, y0 + 1, couleur);
        guiGraphics.fill(x0, y1 - 1, x1, y1, couleur);
        guiGraphics.fill(x0, y0, x0 + 1, y1, couleur);
        guiGraphics.fill(x1 - 1, y0, x1, y1, couleur);
    }

    // ==================== Inspecteur (panneau latéral droit) ====================

    /**
     * Rafraîchit les statistiques affichées par l'inspecteur après une action sur la
     * carte, au plus toutes les 500 ms : sur une très grande carte, le calcul du
     * périmètre (remplissage de toute l'aire) ne doit pas s'exécuter à chaque coup
     * de pinceau.
     */
    private void rafraichirStatsCarte() {
        if (!statsObsoletes || net.minecraft.Util.getMillis() - statsDernierCalcul < 500) {
            return;
        }
        statsDernierCalcul = net.minecraft.Util.getMillis();
        statsObsoletes = false;
        statLimiteFermee = carte.limiteEstBoucleFermeeValide();
        int visibles = 0;
        int nonVisibles = 0;
        List<Long> limites = new ArrayList<>();
        for (Map.Entry<Long, BlocCarte> entree : carte.blocs.entrySet()) {
            BlocCarte bloc = entree.getValue();
            visibles += bloc.qteBonbonVisible;
            nonVisibles += bloc.qteBonbonNonVisible;
            if (bloc.type == TypeElementCarte.LIMITE) {
                limites.add(entree.getKey());
            }
        }
        statBonbonsVisibles = visibles;
        statBonbonsNonVisibles = nonVisibles;
        cellulesLimiteCache = new long[limites.size()];
        for (int i = 0; i < limites.size(); i++) {
            cellulesLimiteCache[i] = limites.get(i);
        }
    }

    /**
     * Panneau de droite permanent. Hors sélection : bloc survolé + état de la carte +
     * légende. En sélection avec bonbons : les cadres des cartes « Bonbon visible » /
     * « Bonbon non-visible » derrière les champs posés par mettreAJourPanneauDelais(),
     * puis le bloc survolé sous le bouton Appliquer si la place le permet.
     */
    private void dessinerInspecteur(GuiGraphics guiGraphics, int[] celluleSurvolee) {
        int xCarte = this.width - largeurPanneauDroit + 6;
        int largeurCarte = largeurPanneauDroit - 12;

        if (panneauDelaisVisible()) {
            guiGraphics.drawString(this.font, "§6Sélection : §7" + cellulesSelectionnees.size() + " bloc(s)",
                xCarte + 2, hautGrille + 6, 0xFFFFFF);
            int y = hautGrille + 18;
            if (champDelaiVisible.visible) {
                dessinerCarteSelection(guiGraphics, xCarte, y, largeurCarte, "Bonbon visible", COULEUR_BONBON_VISIBLE);
                y += hauteurCarteSelection + 4;
            }
            if (champDelaiNonVisible.visible) {
                dessinerCarteSelection(guiGraphics, xCarte, y, largeurCarte, "Bonbon non-visible",
                    COULEUR_BONBON_NON_VISIBLE);
            }
            // L'info du bloc survolé reste consultable pendant le réglage des délais
            if (celluleSurvolee != null) {
                dessinerCarteLignes(guiGraphics, xCarte, boutonAppliquerDelais.getY() + 20, largeurCarte,
                    "Bloc survolé", 0xFF7F8780, lignesBlocSurvole(celluleSurvolee));
            }
            return;
        }

        rafraichirStatsCarte();
        int y = hautGrille + 6;

        // En mode sélection : rappel du geste, ou résumé de la sélection (sans bonbons,
        // sinon le panneau des délais a déjà pris le relais plus haut)
        if (modeSelection) {
            List<LigneInspecteur> lignesSelection;
            if (cellulesSelectionnees.isEmpty()) {
                lignesSelection = List.of(
                    new LigneInspecteur("", "Tracez un rectangle sur", COULEUR_ETIQUETTE),
                    new LigneInspecteur("", "le terrain et les bonbons.", COULEUR_ETIQUETTE));
            } else {
                lignesSelection = new ArrayList<>();
                lignesSelection.add(new LigneInspecteur("Blocs",
                    String.valueOf(cellulesSelectionnees.size()), 0xFFFFFFFF));
                if (selectionNbTerrain > 0) {
                    lignesSelection.add(new LigneInspecteur("Élévation",
                        selectionElevationCommune == null ? "mixte"
                            : (selectionElevationCommune > 0 ? "+" : "") + selectionElevationCommune,
                        selectionElevationCommune != null && selectionElevationCommune < 0
                            ? 0xFF9FD3FF : 0xFFFFFFFF));
                    lignesSelection.add(new LigneInspecteur("", "Ctrl+molette : élévation", COULEUR_ETIQUETTE));
                }
            }
            y = dessinerCarteLignes(guiGraphics, xCarte, y, largeurCarte, "Sélection",
                COULEUR_SELECTION, lignesSelection);
        }

        List<LigneInspecteur> lignesBloc = celluleSurvolee != null ? lignesBlocSurvole(celluleSurvolee)
            : List.of(new LigneInspecteur("", "Survolez la grille…", COULEUR_TEXTE_ESTOMPE));
        y = dessinerCarteLignes(guiGraphics, xCarte, y, largeurCarte, "Bloc survolé", 0xFF7F8780, lignesBloc);

        // État de la carte : les mêmes critères que la validation à la sauvegarde
        List<LigneInspecteur> lignesEtat = List.of(
            new LigneInspecteur("Limite fermée", statLimiteFermee ? "oui ✔" : "non ✘",
                statLimiteFermee ? 0xFF5FD37E : 0xFFFF7A6B),
            new LigneInspecteur("Apparition", carte.aPointApparition() ? "placée ✔" : "absente ✘",
                carte.aPointApparition() ? 0xFF5FD37E : 0xFFFF7A6B),
            new LigneInspecteur("Bonbons vis.", String.valueOf(statBonbonsVisibles), 0xFFFFFFFF),
            new LigneInspecteur("Bonbons non-vis.", String.valueOf(statBonbonsNonVisibles), 0xFFFFFFFF));
        y = dessinerCarteLignes(guiGraphics, xCarte, y, largeurCarte, "État de la carte", 0xFF7F8780, lignesEtat);

        dessinerLegende(guiGraphics, xCarte, y, largeurCarte);
    }

    /** Lignes de la carte « Bloc survolé » : position, type, élévation, bonbons et leurs délais */
    private List<LigneInspecteur> lignesBlocSurvole(int[] cellule) {
        List<LigneInspecteur> lignes = new ArrayList<>();
        BlocCarte bloc = carte.obtenirBloc(cellule[0], cellule[1]);
        lignes.add(new LigneInspecteur("Position", "(" + cellule[0] + ", " + cellule[1] + ")", 0xFFFFFFFF));
        lignes.add(new LigneInspecteur("Type", bloc.type.obtenirNomAffichage(), 0xFFFFFFFF));
        if (bloc.type == TypeElementCarte.ILE || bloc.type == TypeElementCarte.PIERRE) {
            lignes.add(new LigneInspecteur("Élévation",
                (bloc.elevation > 0 ? "+" : "") + bloc.elevation,
                bloc.elevation < 0 ? 0xFF9FD3FF : 0xFFFFFFFF));
        }
        if (bloc.qteBonbonVisible > 0) {
            lignes.add(new LigneInspecteur("Visible",
                "×" + bloc.qteBonbonVisible + " · " + bloc.typeBonbonVisible.obtenirNomAffichage(),
                COULEUR_BONBON_VISIBLE));
            String details = detailsBonbon(bloc.delaiBonbonVisible, bloc.delaiApparitionInitiale);
            if (!details.isEmpty()) {
                lignes.add(new LigneInspecteur("", details, COULEUR_TEXTE_ESTOMPE));
            }
        }
        if (bloc.qteBonbonNonVisible > 0) {
            lignes.add(new LigneInspecteur("Non-visible",
                "×" + bloc.qteBonbonNonVisible + " · " + bloc.typeBonbonNonVisible.obtenirNomAffichage(),
                COULEUR_BONBON_NON_VISIBLE));
            String details = detailsBonbon(bloc.delaiBonbonNonVisible, bloc.delaiApparitionInitialeNonVisible);
            if (!details.isEmpty()) {
                lignes.add(new LigneInspecteur("", details, COULEUR_TEXTE_ESTOMPE));
            }
        }
        return lignes;
    }

    /** « réap. Ns · appar. Ns » (parties non nulles seulement) */
    private static String detailsBonbon(int delaiReapparition, int apparitionInitiale) {
        StringBuilder details = new StringBuilder();
        if (delaiReapparition > 0) {
            details.append("réap. ").append(delaiReapparition).append(" s");
        }
        if (apparitionInitiale > 0) {
            if (!details.isEmpty()) {
                details.append(" · ");
            }
            details.append("appar. ").append(apparitionInitiale).append(" s");
        }
        return details.toString();
    }

    /** Cadre + titre d'une carte du panneau sélection ; les rangées sont libellées à gauche des champs */
    private void dessinerCarteSelection(GuiGraphics guiGraphics, int x, int y, int largeur,
                                        String titre, int couleurTitre) {
        guiGraphics.fill(x, y, x + largeur, y + hauteurCarteSelection, COULEUR_FOND_CARTE_INSP);
        dessinerCadre(guiGraphics, x, y, x + largeur, y + hauteurCarteSelection,
            (couleurTitre & 0x00FFFFFF) | 0x60000000);
        guiGraphics.drawString(this.font, titre, x + 5, y + 5, couleurTitre);
        String[] etiquettes = {"Réapp. (s)", "Appar. (s)", "Type"};
        int yRangee = y + 16;
        for (String etiquette : etiquettes) {
            guiGraphics.drawString(this.font, etiquette, x + 4, yRangee + 3, COULEUR_ETIQUETTE);
            yRangee += pitchSelection;
        }
    }

    /**
     * Carte générique de l'inspecteur : titre + lignes clé/valeur (valeur alignée à
     * droite ; clé vide = ligne pleine largeur). Retourne le y du prochain élément,
     * inchangé si la carte ne tient pas au-dessus de la barre d'état.
     */
    private int dessinerCarteLignes(GuiGraphics guiGraphics, int x, int y, int largeur,
                                    String titre, int couleurTitre, List<LigneInspecteur> lignes) {
        int hauteur = 16 + lignes.size() * 11 + 3;
        if (y + hauteur > basGrille() - 2) {
            return y;
        }
        guiGraphics.fill(x, y, x + largeur, y + hauteur, COULEUR_FOND_CARTE_INSP);
        dessinerCadre(guiGraphics, x, y, x + largeur, y + hauteur, COULEUR_CADRE_CARTE_INSP);
        guiGraphics.drawString(this.font, titre, x + 5, y + 5, couleurTitre);
        int yLigne = y + 16;
        for (LigneInspecteur ligne : lignes) {
            if (ligne.cle().isEmpty()) {
                guiGraphics.drawString(this.font,
                    this.font.plainSubstrByWidth(ligne.valeur(), largeur - 10), x + 5, yLigne, ligne.couleurValeur());
            } else {
                guiGraphics.drawString(this.font, ligne.cle(), x + 5, yLigne, COULEUR_ETIQUETTE);
                guiGraphics.drawString(this.font, ligne.valeur(),
                    x + largeur - 5 - this.font.width(ligne.valeur()), yLigne, ligne.couleurValeur());
            }
            yLigne += 11;
        }
        return y + hauteur + 5;
    }

    /** Légende des symboles de la grille (dessinée si la place le permet) */
    private void dessinerLegende(GuiGraphics guiGraphics, int x, int y, int largeur) {
        int hauteur = 16 + 4 * 11 + 3;
        if (y + hauteur > basGrille() - 2) {
            return;
        }
        guiGraphics.fill(x, y, x + largeur, y + hauteur, COULEUR_FOND_CARTE_INSP);
        dessinerCadre(guiGraphics, x, y, x + largeur, y + hauteur, COULEUR_CADRE_CARTE_INSP);
        guiGraphics.drawString(this.font, "Légende", x + 5, y + 5, 0xFF7F8780);

        int largeurTexte = largeur - 21;
        int yLigne = y + 16;
        // Bonbon visible : carré plein, coin haut-gauche du bloc
        guiGraphics.fill(x + 5, yLigne, x + 13, yLigne + 8, COULEUR_BONBON_VISIBLE);
        guiGraphics.drawString(this.font,
            this.font.plainSubstrByWidth("haut-gauche : visible", largeurTexte), x + 17, yLigne, COULEUR_ETIQUETTE);
        yLigne += 11;
        // Bonbon non-visible : carré assombri encadré, coin bas-droit
        guiGraphics.fill(x + 5, yLigne, x + 13, yLigne + 8, assombrir(COULEUR_BONBON_VISIBLE, 0.6f));
        dessinerCadre(guiGraphics, x + 5, yLigne, x + 13, yLigne + 8, 0xFF000000);
        guiGraphics.drawString(this.font,
            this.font.plainSubstrByWidth("bas-droit : non-visible", largeurTexte), x + 17, yLigne, COULEUR_ETIQUETTE);
        yLigne += 11;
        guiGraphics.drawString(this.font, "2", x + 7, yLigne, 0xFFFFFFFF);
        guiGraphics.drawString(this.font,
            this.font.plainSubstrByWidth("chiffre : élévation", largeurTexte), x + 17, yLigne, COULEUR_ETIQUETTE);
        yLigne += 11;
        guiGraphics.drawString(this.font, "⚑", x + 5, yLigne, 0xFFFF3030);
        guiGraphics.drawString(this.font,
            this.font.plainSubstrByWidth("apparition des joueurs", largeurTexte), x + 17, yLigne, COULEUR_ETIQUETTE);
    }

    // ==================== Barre d'état ====================

    /** Coordonnées, outil actif, rappel des contrôles et niveau de zoom (16 px/bloc = 100 %) */
    private void dessinerBarreEtat(GuiGraphics guiGraphics, int[] cellule) {
        int y = this.height - HAUTEUR_BARRE_ETAT + 4;

        String coordonnees = cellule != null ? "(" + cellule[0] + ", " + cellule[1] + ")" : "—";
        guiGraphics.drawString(this.font, coordonnees, 8, y, 0xFFFFFFFF);

        String nomOutil = modeSelection ? "Sélection"
            : (outilActif != null ? outilActif.nomAffichage : "Aucun outil");
        String texteOutil = "Outil : " + nomOutil;
        // Tronqué avant la zone des boutons de zoom (ancrés à width - 136)
        int largeurMaxOutil = this.width - 140 - 82;
        if (largeurMaxOutil > 20) {
            guiGraphics.drawString(this.font,
                this.font.plainSubstrByWidth(texteOutil, largeurMaxOutil), 82, y, 0xFFE8EBE4);
        }

        int xAstuce = 82 + Math.min(this.font.width(texteOutil), Math.max(0, largeurMaxOutil)) + 12;
        int largeurMaxAstuce = this.width - 140 - xAstuce;
        if (largeurMaxAstuce > 40) {
            guiGraphics.drawString(this.font,
                this.font.plainSubstrByWidth(astuceControles(), largeurMaxAstuce), xAstuce, y, COULEUR_TEXTE_ESTOMPE);
        }

        String pourcentage = Math.round(tailleCellule / 16.0 * 100.0) + " %";
        guiGraphics.drawString(this.font, pourcentage, this.width - 98 - this.font.width(pourcentage) / 2, y, 0xFFE8EBE4);
    }

    /** Rappel des contrôles souris selon l'outil actif */
    private String astuceControles() {
        if (modeSelection) {
            if (!cellulesSelectionnees.isEmpty()) {
                return "Ctrl+molette : élévation de la sélection · clic ailleurs : désélectionner";
            }
            return "tracer un rectangle : sélectionner terrain et bonbons · clic ailleurs : désélectionner";
        }
        if (outilActif == null) {
            return "choisissez un outil · molette : zoom · clic milieu : déplacer la vue";
        }
        return switch (outilActif) {
            case EAU, ILE, PIERRE -> "glisser : peindre · clic droit : retirer · Ctrl+molette : élévation";
            case LIMITE -> "glisser : peindre · clic droit : retirer";
            case BONBON_VISIBLE, BONBON_NON_VISIBLE -> "clic : +1 bonbon · clic droit : −1 · Ctrl+molette : élévation";
            case APPARITION -> "clic : placer le point d'apparition · clic droit : retirer";
        };
    }

    // ==================== Notification non bloquante ====================

    private void dessinerToast(GuiGraphics guiGraphics) {
        if (toastTexte == null || net.minecraft.Util.getMillis() >= toastExpiration) {
            return;
        }
        int largeurMax = Math.min(360, grilleDroiteEcran() - grilleGaucheEcran() - 24);
        if (largeurMax < 60) {
            return;
        }
        List<net.minecraft.util.FormattedCharSequence> lignes =
            this.font.split(Component.literal(toastTexte), largeurMax - 20);
        int largeurTexte = 0;
        for (net.minecraft.util.FormattedCharSequence ligne : lignes) {
            largeurTexte = Math.max(largeurTexte, this.font.width(ligne));
        }
        int largeur = largeurTexte + 20;
        int hauteur = 9 + lignes.size() * 11;
        int x0 = (grilleGaucheEcran() + grilleDroiteEcran() - largeur) / 2;
        int y1 = basGrille() - 8;
        int y0 = y1 - hauteur;

        guiGraphics.fill(x0, y0, x0 + largeur, y1, toastErreur ? 0xF0301A17 : 0xF016281A);
        dessinerCadre(guiGraphics, x0, y0, x0 + largeur, y1, toastErreur ? 0xFFB0402E : 0xFF2F8F4A);
        int yLigne = y0 + 5;
        for (net.minecraft.util.FormattedCharSequence ligne : lignes) {
            guiGraphics.drawString(this.font, ligne, x0 + (largeur - this.font.width(ligne)) / 2, yLigne,
                toastErreur ? 0xFFF2C7BE : 0xFFC8EFD2);
            yLigne += 11;
        }
    }

    // ==================== Fenêtre modale ====================

    private int largeurModal() {
        return Math.min(400, this.width - 40);
    }

    private int[] geometrieModal() {
        int largeurModal = largeurModal();
        int hauteurModal = 46 + modalLignes.size() * 12 + 34;
        int x0 = (this.width - largeurModal) / 2;
        int y0 = Math.max(4, (this.height - hauteurModal) / 2);
        return new int[]{x0, y0, largeurModal, hauteurModal};
    }

    /** Position x et largeur de chaque bouton du modal */
    private int[][] geometrieBoutonsModal() {
        int[] geometrie = geometrieModal();
        int largeurBouton = 60;
        for (String texte : modalBoutons) {
            largeurBouton = Math.max(largeurBouton, this.font.width(texte) + 16);
        }
        int nombre = modalBoutons.size();
        int largeurTotale = nombre * largeurBouton + (nombre - 1) * 8;
        int xDebut = geometrie[0] + (geometrie[2] - largeurTotale) / 2;
        int[][] positions = new int[nombre][2];
        for (int i = 0; i < nombre; i++) {
            positions[i][0] = xDebut + i * (largeurBouton + 8);
            positions[i][1] = largeurBouton;
        }
        return positions;
    }

    private void dessinerModal(GuiGraphics guiGraphics, int sourisX, int sourisY) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x90000000);

        int[] geometrie = geometrieModal();
        int x0 = geometrie[0];
        int y0 = geometrie[1];
        int largeurModal = geometrie[2];
        int hauteurModal = geometrie[3];

        guiGraphics.fill(x0, y0, x0 + largeurModal, y0 + hauteurModal, 0xFF202020);
        dessinerCadre(guiGraphics, x0, y0, x0 + largeurModal, y0 + hauteurModal, 0xFFAAAAAA);

        guiGraphics.drawCenteredString(this.font, "§l" + modalTitre, x0 + largeurModal / 2, y0 + 10, 0xFFFFFF);
        int yLigne = y0 + 28;
        for (String ligne : modalLignes) {
            guiGraphics.drawCenteredString(this.font, ligne, x0 + largeurModal / 2, yLigne, 0xDDDDDD);
            yLigne += 12;
        }

        int yBoutons = y0 + hauteurModal - 26;
        int[][] boutons = geometrieBoutonsModal();
        for (int i = 0; i < modalBoutons.size(); i++) {
            dessinerBoutonModal(guiGraphics, boutons[i][0], yBoutons, boutons[i][1], modalBoutons.get(i), sourisX, sourisY);
        }
    }

    private void dessinerBoutonModal(GuiGraphics guiGraphics, int x, int y, int largeur, String texte,
                                     int sourisX, int sourisY) {
        boolean survole = sourisX >= x && sourisX < x + largeur && sourisY >= y && sourisY < y + 20;
        guiGraphics.fill(x, y, x + largeur, y + 20, survole ? 0xFF505050 : 0xFF383838);
        dessinerCadre(guiGraphics, x, y, x + largeur, y + 20, 0xFFAAAAAA);
        guiGraphics.drawCenteredString(this.font, texte, x + largeur / 2, y + 6, 0xFFFFFF);
    }

    private void gererClicModal(double sourisX, double sourisY) {
        int[] geometrie = geometrieModal();
        int yBoutons = geometrie[1] + geometrie[3] - 26;

        if (sourisY < yBoutons || sourisY >= yBoutons + 20) {
            return;
        }

        int[][] boutons = geometrieBoutonsModal();
        for (int i = 0; i < modalBoutons.size(); i++) {
            if (sourisX >= boutons[i][0] && sourisX < boutons[i][0] + boutons[i][1]) {
                Runnable action = i < modalActions.size() ? modalActions.get(i) : null;
                fermerModal();
                if (action != null) {
                    action.run();
                }
                return;
            }
        }
    }

    private void fermerModal() {
        modalActif = false;
        modalTitre = "";
        modalLignes = new ArrayList<>();
        modalBoutons = new ArrayList<>();
        modalActions = new ArrayList<>();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !modalActif;
    }

    // ==================== Habillage : textes peints et bouton de palette ====================

    /** Texte statique dessiné par render() (titres de sections, étiquettes de groupes) */
    private record TextePeint(int x, int y, String texte, int couleur) {
    }

    /** Ligne d'une carte de l'inspecteur : clé grise à gauche, valeur colorée à droite (clé vide = pleine largeur) */
    private record LigneInspecteur(String cle, String valeur, int couleurValeur) {
    }

    /**
     * Bouton d'outil de la palette : pastille reprenant la représentation de
     * l'élément dans la grille, texte aligné à gauche, et surbrillance dessinée
     * par le bouton lui-même lorsque l'outil est actif.
     */
    private static class BoutonPalette extends Button {

        enum Pastille {
            PLEINE,     // Carré de la couleur du bloc (terrains, bonbon visible)
            ENTERREE,   // Carré assombri + cadre noir (bonbon non-visible, comme dans la grille)
            DRAPEAU,    // ⚑ rouge (point d'apparition)
            SELECTION   // Cadre cyan vide (mode sélection)
        }

        private final Pastille pastille;
        private final int couleurPastille;
        private final BooleanSupplier estActif;
        private final int couleurActive;

        BoutonPalette(int x, int y, int largeur, int hauteur, Component texte, OnPress action,
                      Pastille pastille, int couleurPastille, BooleanSupplier estActif, int couleurActive) {
            super(x, y, largeur, hauteur, texte, action, Button.DEFAULT_NARRATION);
            this.pastille = pastille;
            this.couleurPastille = couleurPastille;
            this.estActif = estActif;
            this.couleurActive = couleurActive;
        }

        @Override
        public void renderString(GuiGraphics guiGraphics, Font police, int couleur) {
            // Texte aligné à gauche pour laisser la place de la pastille
            guiGraphics.drawString(police, this.getMessage(), this.getX() + 20,
                this.getY() + (this.getHeight() - 8) / 2, couleur);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
            super.renderWidget(guiGraphics, sourisX, sourisY, tickPartiel);

            int taille = 10;
            int x0 = this.getX() + 5;
            int y0 = this.getY() + (this.getHeight() - taille) / 2;
            switch (pastille) {
                case PLEINE -> {
                    guiGraphics.fill(x0, y0, x0 + taille, y0 + taille, couleurPastille);
                    dessinerCadre(guiGraphics, x0, y0, x0 + taille, y0 + taille, 0x50FFFFFF);
                }
                case ENTERREE -> {
                    guiGraphics.fill(x0, y0, x0 + taille, y0 + taille, assombrir(couleurPastille, 0.6f));
                    dessinerCadre(guiGraphics, x0, y0, x0 + taille, y0 + taille, 0xFF000000);
                }
                case DRAPEAU -> guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "⚑",
                    x0 + 1, y0 + 1, 0xFFFF5A4E);
                case SELECTION -> dessinerCadre(guiGraphics, x0, y0, x0 + taille, y0 + taille, 0xFF00E5E5);
            }

            // Outil actif : voile teinté + cadre, dessinés par-dessus le bouton
            if (estActif.getAsBoolean()) {
                guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                    this.getY() + this.getHeight(), (couleurActive & 0x00FFFFFF) | 0x28000000);
                dessinerCadre(guiGraphics, this.getX() - 1, this.getY() - 1,
                    this.getX() + this.getWidth() + 1, this.getY() + this.getHeight() + 1, couleurActive);
            }
        }
    }

    // ==================== Action annulable ====================

    /**
     * Action d'édition annulable : ensemble de changements de blocs + changements
     * de taille de l'aire et de point d'apparition.
     */
    private static class ActionEditeur {
        final Map<Long, BlocCarte> blocsAvant = new HashMap<>();
        final Map<Long, BlocCarte> blocsApres = new HashMap<>();
        final int largeurAvant;
        final int hauteurAvant;
        int largeurApres;
        int hauteurApres;
        final int apparitionAvantX;
        final int apparitionAvantZ;
        int apparitionApresX;
        int apparitionApresZ;

        ActionEditeur(CarteDonnees carte) {
            this.largeurAvant = carte.largeur;
            this.hauteurAvant = carte.hauteur;
            this.largeurApres = carte.largeur;
            this.hauteurApres = carte.hauteur;
            this.apparitionAvantX = carte.apparitionX;
            this.apparitionAvantZ = carte.apparitionZ;
            this.apparitionApresX = carte.apparitionX;
            this.apparitionApresZ = carte.apparitionZ;
        }

        void ajouterChangement(int x, int z, BlocCarte avant, BlocCarte apres) {
            long cle = CarteDonnees.cle(x, z);
            // Conserver le tout premier état « avant » si plusieurs changements sur la même cellule
            blocsAvant.putIfAbsent(cle, avant);
            blocsApres.put(cle, apres);
        }

        boolean estVide() {
            return blocsApres.isEmpty()
                && largeurAvant == largeurApres && hauteurAvant == hauteurApres
                && apparitionAvantX == apparitionApresX && apparitionAvantZ == apparitionApresZ;
        }

        void retablir(CarteDonnees carte) {
            carte.largeur = largeurApres;
            carte.hauteur = hauteurApres;
            carte.apparitionX = apparitionApresX;
            carte.apparitionZ = apparitionApresZ;
            for (Map.Entry<Long, BlocCarte> entree : blocsApres.entrySet()) {
                carte.definirBloc(CarteDonnees.cleX(entree.getKey()), CarteDonnees.cleZ(entree.getKey()),
                    entree.getValue().estVide() ? null : entree.getValue().copier());
            }
        }

        void annuler(CarteDonnees carte) {
            carte.largeur = largeurAvant;
            carte.hauteur = hauteurAvant;
            carte.apparitionX = apparitionAvantX;
            carte.apparitionZ = apparitionAvantZ;
            for (Map.Entry<Long, BlocCarte> entree : blocsAvant.entrySet()) {
                carte.definirBloc(CarteDonnees.cleX(entree.getKey()), CarteDonnees.cleZ(entree.getKey()),
                    entree.getValue().estVide() ? null : entree.getValue().copier());
            }
        }
    }
}

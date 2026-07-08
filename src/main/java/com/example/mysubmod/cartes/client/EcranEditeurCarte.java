package com.example.mysubmod.cartes.client;

import com.example.mysubmod.cartes.BlocCarte;
import com.example.mysubmod.cartes.CarteDonnees;
import com.example.mysubmod.cartes.TypeElementCarte;
import com.example.mysubmod.cartes.reseau.PaquetDemandeListeCartes;
import com.example.mysubmod.cartes.reseau.PaquetFermetureEditeurCarte;
import com.example.mysubmod.cartes.reseau.PaquetListeCartes;
import com.example.mysubmod.cartes.reseau.PaquetSauvegardeCarte;
import com.example.mysubmod.reseau.GestionnaireReseau;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Outil de création de carte : plan cartésien bloc par bloc (vue du dessus).
 * Palette à gauche, grille zoomable au centre, panneau des délais de
 * réapparition à droite en mode sélection.
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
    private static final int LARGEUR_PALETTE = 104;
    private static final int LARGEUR_PANNEAU_DROIT = 150;
    private static final int HAUT_GRILLE = 52;
    private static final int TAILLE_MORCEAU_RESEAU = 30000;

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
    private Button boutonSelection;
    private final Map<OutilPalette, Button> boutonsPalette = new EnumMap<>(OutilPalette.class);

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
        boutonsPalette.clear();

        // ----- Barre du haut, rangée 1 -----
        int x = 6;
        champNom = new EditBox(this.font, x, 6, 130, 18, Component.literal("Nom de la carte"));
        champNom.setMaxLength(CarteDonnees.LONGUEUR_MAX_NOM);
        champNom.setHint(Component.literal("Nom de la carte"));
        champNom.setValue(texteNom);
        champNom.setResponder(texte -> texteNom = texte);
        addRenderableWidget(champNom);
        x += 136;

        addRenderableWidget(Button.builder(Component.literal("Sauvegarder"), b -> sauvegarder())
            .bounds(x, 5, 80, 20).build());
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

        // ----- Barre du haut, rangée 2 -----
        x = 6;
        champLargeur = new EditBox(this.font, x, 29, 36, 18, Component.literal("Largeur"));
        champLargeur.setMaxLength(4);
        champLargeur.setValue(texteLargeur);
        champLargeur.setResponder(texte -> texteLargeur = texte);
        addRenderableWidget(champLargeur);
        x += 40;

        champHauteur = new EditBox(this.font, x, 29, 36, 18, Component.literal("Hauteur"));
        champHauteur.setMaxLength(4);
        champHauteur.setValue(texteHauteur);
        champHauteur.setResponder(texte -> texteHauteur = texte);
        addRenderableWidget(champHauteur);
        x += 40;

        addRenderableWidget(Button.builder(Component.literal("Redimensionner"), b -> redimensionnerDepuisChamps())
            .bounds(x, 28, 96, 20).build());
        x += 100;

        addRenderableWidget(Button.builder(Component.literal("Supprimer limite"), b -> supprimerLimite())
            .bounds(x, 28, 100, 20).build());
        x += 104;

        addRenderableWidget(Button.builder(Component.literal("Limite par défaut"), b -> appliquerLimiteParDefaut())
            .bounds(x, 28, 104, 20).build());

        // ----- Palette (panneau latéral gauche) -----
        int yPalette = HAUT_GRILLE + 14;
        // Compacter la palette sur les écrans courts : 9 rangées (7 outils + Sélection + Annuler/Rétablir)
        // doivent tenir entre le haut de la palette et le bas de l'écran
        int pasPalette = Math.max(18, Math.min(22, (this.height - yPalette - 6) / 9));
        int hauteurBoutonPalette = pasPalette - 2;
        for (OutilPalette outil : OutilPalette.values()) {
            final OutilPalette outilFinal = outil;
            String etiquette = switch (outil) {
                case BONBON_VISIBLE -> "Bonbon vis.";
                case BONBON_NON_VISIBLE -> "Bonbon non-vis.";
                case APPARITION -> "Apparition";
                default -> outil.nomAffichage;
            };
            Button bouton = Button.builder(Component.literal(etiquette), b -> selectionnerOutil(outilFinal))
                .bounds(4, yPalette, LARGEUR_PALETTE - 8, hauteurBoutonPalette)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(outil.nomAffichage)))
                .build();
            boutonsPalette.put(outil, bouton);
            addRenderableWidget(bouton);
            yPalette += pasPalette;
        }

        // Mode sélection (distinct du mode palette) + annuler / rétablir
        yPalette += 2;
        boutonSelection = Button.builder(Component.literal("Sélection"), b -> basculerModeSelection())
            .bounds(4, yPalette, LARGEUR_PALETTE - 8, hauteurBoutonPalette)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Mode sélection : tracer un rectangle pour sélectionner les blocs contenant des bonbons")))
            .build();
        addRenderableWidget(boutonSelection);
        yPalette += pasPalette;

        addRenderableWidget(Button.builder(Component.literal("↶"), b -> annuler())
            .bounds(4, yPalette, (LARGEUR_PALETTE - 12) / 2, hauteurBoutonPalette)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Annuler (Ctrl+Z)")))
            .build());

        addRenderableWidget(Button.builder(Component.literal("↷"), b -> retablir())
            .bounds(4 + (LARGEUR_PALETTE - 12) / 2 + 4, yPalette, (LARGEUR_PALETTE - 12) / 2, hauteurBoutonPalette)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Rétablir (Ctrl+Y)")))
            .build());

        // ----- Panneau latéral droit : délais de réapparition (rangées compactes pour
        // laisser place aux deux boutons de type sous la hauteur GUI minimale) -----
        int xDroit = this.width - LARGEUR_PANNEAU_DROIT + 8;
        champDelaiVisible = new EditBox(this.font, xDroit, HAUT_GRILLE + 26, LARGEUR_PANNEAU_DROIT - 20, 18,
            Component.literal("Délai bonbon visible (s)"));
        champDelaiVisible.setMaxLength(8);
        champDelaiVisible.setValue(texteDelaiVisible);
        champDelaiVisible.setResponder(texte -> texteDelaiVisible = texte);
        addRenderableWidget(champDelaiVisible);

        champDelaiNonVisible = new EditBox(this.font, xDroit, HAUT_GRILLE + 52, LARGEUR_PANNEAU_DROIT - 20, 18,
            Component.literal("Délai bonbon non-visible (s)"));
        champDelaiNonVisible.setMaxLength(8);
        champDelaiNonVisible.setValue(texteDelaiNonVisible);
        champDelaiNonVisible.setResponder(texte -> texteDelaiNonVisible = texte);
        addRenderableWidget(champDelaiNonVisible);

        champApparitionInitiale = new EditBox(this.font, xDroit, HAUT_GRILLE + 78, LARGEUR_PANNEAU_DROIT - 20, 18,
            Component.literal("Apparition initiale visible (s)"));
        champApparitionInitiale.setMaxLength(8);
        champApparitionInitiale.setValue(texteApparitionInitiale);
        champApparitionInitiale.setResponder(texte -> texteApparitionInitiale = texte);
        champApparitionInitiale.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
            "Secondes après le début de partie avant l'apparition du bonbon visible (0 = dès le début)")));
        addRenderableWidget(champApparitionInitiale);

        champApparitionInitialeNonVisible = new EditBox(this.font, xDroit, HAUT_GRILLE + 104, LARGEUR_PANNEAU_DROIT - 20, 18,
            Component.literal("Apparition initiale non-visible (s)"));
        champApparitionInitialeNonVisible.setMaxLength(8);
        champApparitionInitialeNonVisible.setValue(texteApparitionInitialeNonVisible);
        champApparitionInitialeNonVisible.setResponder(texte -> texteApparitionInitialeNonVisible = texte);
        champApparitionInitialeNonVisible.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
            "Secondes après le début de partie avant l'apparition du bloc bonbon non-visible (0 = dès le début)")));
        addRenderableWidget(champApparitionInitialeNonVisible);

        boutonTypeBonbon = Button.builder(Component.literal("Type vis. : (inchangé)"), b -> cyclerTypeBonbon())
            .bounds(xDroit, HAUT_GRILLE + 124, LARGEUR_PANNEAU_DROIT - 20, 18)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Type des bonbons visibles sélectionnés : Standard, ou Bleu / Rouge (Sous-mode 2, spécialisation Sous-mode 3)")))
            .build();
        addRenderableWidget(boutonTypeBonbon);

        boutonTypeBonbonNonVisible = Button.builder(Component.literal("Type non-vis. : (inchangé)"),
                b -> cyclerTypeBonbonNonVisible())
            .bounds(xDroit, HAUT_GRILLE + 144, LARGEUR_PANNEAU_DROIT - 20, 18)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Type des bonbons non-visibles sélectionnés : Standard, ou Bleu / Rouge (spécialisation Sous-mode 3)")))
            .build();
        addRenderableWidget(boutonTypeBonbonNonVisible);

        boutonAppliquerDelais = Button.builder(Component.literal("Appliquer"), b -> appliquerDelais())
            .bounds(xDroit, HAUT_GRILLE + 164, LARGEUR_PANNEAU_DROIT - 20, 18).build();
        addRenderableWidget(boutonAppliquerDelais);

        mettreAJourPanneauDelais();
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
        return LARGEUR_PALETTE;
    }

    private int grilleDroiteEcran() {
        boolean panneauDroitVisible = panneauDelaisVisible();
        return this.width - (panneauDroitVisible ? LARGEUR_PANNEAU_DROIT : 0);
    }

    private double ecranVersCelluleX(double sourisX) {
        return (sourisX - grilleGaucheEcran() - decalageX) / tailleCellule;
    }

    private double ecranVersCelluleZ(double sourisY) {
        return (sourisY - HAUT_GRILLE - decalageY) / tailleCellule;
    }

    private int[] celluleSousSouris(double sourisX, double sourisY) {
        if (sourisX < grilleGaucheEcran() || sourisX >= grilleDroiteEcran()
            || sourisY < HAUT_GRILLE || sourisY >= this.height) {
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
    }

    private void annuler() {
        if (pileAnnulation.isEmpty()) {
            return;
        }
        ActionEditeur action = pileAnnulation.pop();
        action.annuler(carte);
        pileRetablissement.push(action);
        cellulesSelectionnees.clear();
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
        synchroniserChampsTaille();
        mettreAJourPanneauDelais();
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
                afficherMessage("Placement refusé", "Aucun élément ne peut être superposé sur un bloc Limite");
            }
            return;
        }

        BlocCarte avant = existant.copier();
        BlocCarte apres = existant.copier();

        switch (outilActif) {
            case EAU -> {
                if (existant.qteBonbonNonVisible > 0) {
                    if (!silencieux) {
                        afficherMessage("Placement refusé",
                            "Impossible de placer de l'Eau sur un bloc contenant un bonbon non-visible");
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
                        afficherMessage("Placement refusé",
                            "L'Eau doit être placée à l'intérieur du périmètre Limite");
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
                        afficherMessage("Placement refusé",
                            "Un élément Limite doit être adjacent à au moins un bloc non-Limite");
                    }
                    return;
                }
                // Écrase tout le contenu sans confirmation
                apres = new BlocCarte(TypeElementCarte.LIMITE, 0);
            }
            case BONBON_VISIBLE -> {
                if (!existant.type.estElementDeBase()) {
                    if (!silencieux) {
                        afficherMessage("Placement refusé",
                            "Un bonbon visible doit être placé sur un bloc Île, Pierre ou Eau");
                    }
                    return;
                }
                if ((existant.type == TypeElementCarte.ILE || existant.type == TypeElementCarte.PIERRE)
                    && existant.elevation >= CarteDonnees.ELEVATION_MAX) {
                    if (!silencieux) {
                        afficherMessage("Placement refusé",
                            "Un bonbon visible ne peut pas être placé sur un bloc à élévation +15 : il serait inatteignable sous le plafond de la cage");
                    }
                    return;
                }
                apres.qteBonbonVisible = existant.qteBonbonVisible + 1;
            }
            case BONBON_NON_VISIBLE -> {
                if (existant.type != TypeElementCarte.ILE && existant.type != TypeElementCarte.PIERRE) {
                    if (!silencieux) {
                        afficherMessage("Placement refusé",
                            "Un bonbon non-visible ne peut être placé que sur un bloc Île ou Pierre");
                    }
                    return;
                }
                if (existant.elevation - 1 < CarteDonnees.ELEVATION_MIN) {
                    if (!silencieux) {
                        afficherMessage("Placement refusé",
                            "Élévation trop basse : le bonbon serait sous le plancher de la cage");
                    }
                    return;
                }
                apres.qteBonbonNonVisible = existant.qteBonbonNonVisible + 1;
            }
            case APPARITION -> {
                if (!carte.limiteEstBoucleFermeeValide()) {
                    if (!silencieux) {
                        afficherMessage("Placement refusé",
                            "Définissez d'abord un périmètre Limite fermé valide");
                    }
                    return;
                }
                if (!carte.calculerInterieurLimite().contains(CarteDonnees.cle(cx, cz))) {
                    if (!silencieux) {
                        afficherMessage("Placement refusé",
                            "Le point d'apparition doit être à l'intérieur du périmètre Limite");
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

    /** Ctrl+molette sur un bloc : élévation d'un cran à la fois */
    private void changerElevation(int cx, int cz, int delta) {
        BlocCarte existant = carte.obtenirBlocOuNull(cx, cz);
        if (existant == null) {
            return;
        }

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
            return; // Pas d'élévation pour les blocs vides / Limite
        }

        int nouvelleElevation = Math.max(min, Math.min(max, existant.elevation + delta));
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

    /** Bouton « Supprimer limite » : retire tous les éléments Limite */
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

    /** Bouton « Limite par défaut » : Limite sur le contour de l'aire totale (écrase sans confirmation) */
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

    /** Charge une carte existante dans l'éditeur (le nom est pré-rempli) */
    public void chargerCarte(CarteDonnees carteChargee) {
        this.carte = carteChargee;
        this.texteNom = carteChargee.nom;
        if (champNom != null) {
            champNom.setValue(texteNom);
        }
        pileAnnulation.clear();
        pileRetablissement.clear();
        cellulesSelectionnees.clear();
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
        for (long cle : cellulesSelectionnees) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc != null && bloc.qteBonbonVisible > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean selectionContientBonbonNonVisible() {
        for (long cle : cellulesSelectionnees) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc != null && bloc.qteBonbonNonVisible > 0) {
                return true;
            }
        }
        return false;
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
            typeBonbonChoisi = null;
            com.example.mysubmod.cartes.TypeBonbonCarte typeCommun = typeBonbonCommun();
            boutonTypeBonbon.setMessage(Component.literal(
                "Type vis. : " + (typeCommun != null ? typeCommun.obtenirNomAffichage() + " (inchangé)" : "— (inchangé)")));
        }
        if (aNonVisible) {
            // Type de bonbon non-visible : réinitialisé à « inchangé » à chaque nouvelle sélection
            typeBonbonNonVisibleChoisi = null;
            com.example.mysubmod.cartes.TypeBonbonCarte typeCommunNonVisible = typeBonbonNonVisibleCommun();
            boutonTypeBonbonNonVisible.setMessage(Component.literal(
                "Type non-vis. : " + (typeCommunNonVisible != null
                    ? typeCommunNonVisible.obtenirNomAffichage() + " (inchangé)" : "— (inchangé)")));
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
            typeBonbonChoisi == null ? "Type vis. : (inchangé)" : "Type vis. : " + typeBonbonChoisi.obtenirNomAffichage()));
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
            typeBonbonNonVisibleChoisi == null ? "Type non-vis. : (inchangé)"
                : "Type non-vis. : " + typeBonbonNonVisibleChoisi.obtenirNomAffichage()));
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
            afficherMessage("Propriétés appliquées", "Les propriétés ont été assignées aux blocs sélectionnés");
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

        jsonSauvegardeEnAttente = carte.versJson();
        envoyerSauvegarde(jsonSauvegardeEnAttente, false);
    }

    /**
     * Remplit les blocs sans élément de base à l'intérieur du périmètre Limite
     * avec de l'Eau ou de la Pierre (élévation 0). Action annulable.
     * Les blocs contenant un bonbon non-visible ne peuvent pas recevoir d'Eau.
     */
    private void remplirInterieurVide(TypeElementCarte type) {
        ActionEditeur action = new ActionEditeur(carte);
        for (long cle : carte.calculerInterieurLimite()) {
            BlocCarte bloc = carte.blocs.get(cle);
            if (bloc != null && bloc.type.estElementDeBase()) {
                continue;
            }
            if (type == TypeElementCarte.EAU && bloc != null && bloc.qteBonbonNonVisible > 0) {
                continue; // L'eau ne peut pas contenir de bonbon non-visible
            }
            int cx = CarteDonnees.cleX(cle);
            int cz = CarteDonnees.cleZ(cle);
            BlocCarte avant = (bloc != null ? bloc : new BlocCarte()).copier();
            BlocCarte apres = avant.copier();
            apres.type = type;
            apres.elevation = 0;
            action.ajouterChangement(cx, cz, avant, apres);
            carte.definirBloc(cx, cz, apres);
        }
        if (!action.estVide()) {
            enregistrerAction(action);
        }
    }

    private void envoyerSauvegarde(String json, boolean ecraserConfirme) {
        byte[] donnees = json.getBytes(StandardCharsets.UTF_8);
        UUID idTransfert = UUID.randomUUID();
        int nombreTotalMorceaux = Math.max(1, (int) Math.ceil((double) donnees.length / TAILLE_MORCEAU_RESEAU));

        for (int i = 0; i < nombreTotalMorceaux; i++) {
            int debut = i * TAILLE_MORCEAU_RESEAU;
            int longueur = Math.min(donnees.length - debut, TAILLE_MORCEAU_RESEAU);
            byte[] morceau = new byte[longueur];
            System.arraycopy(donnees, debut, morceau, 0, longueur);
            GestionnaireReseau.INSTANCE.sendToServer(
                new PaquetSauvegardeCarte(idTransfert, nombreTotalMorceaux, i, morceau, ecraserConfirme));
        }
    }

    public void surSauvegardeReussie(String nomCarte) {
        jsonSauvegardeEnAttente = null;
        afficherMessage("Sauvegarde réussie", "La carte « " + nomCarte + " » a été sauvegardée");
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
            && sourisX >= grilleGaucheEcran() && sourisX < grilleDroiteEcran() && sourisY >= HAUT_GRILLE) {
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
                if (bloc != null && (bloc.qteBonbonVisible > 0 || bloc.qteBonbonNonVisible > 0)) {
                    cellulesSelectionnees.add(CarteDonnees.cle(cx, cz));
                }
            }
        }
        mettreAJourPanneauDelais();
    }

    @Override
    public boolean mouseScrolled(double sourisX, double sourisY, double delta) {
        if (modalActif) {
            return true;
        }

        // Ctrl+molette sur un bloc : élévation d'un cran à la fois
        if (hasControlDown()) {
            int[] cellule = celluleSousSouris(sourisX, sourisY);
            if (cellule != null) {
                changerElevation(cellule[0], cellule[1], delta > 0 ? 1 : -1);
            }
            return true;
        }

        // Molette : zoom / dézoom centré sur la souris.
        // Le dézoom permet toujours de voir l'ensemble de la carte, quelle que soit sa taille.
        if (sourisX >= grilleGaucheEcran() && sourisX < grilleDroiteEcran() && sourisY >= HAUT_GRILLE) {
            double celluleX = ecranVersCelluleX(sourisX);
            double celluleZ = ecranVersCelluleZ(sourisY);
            double facteur = delta > 0 ? 1.2 : 1 / 1.2;
            int largeurVue = grilleDroiteEcran() - grilleGaucheEcran();
            int hauteurVue = this.height - HAUT_GRILLE;
            double tailleAjustement = Math.min((double) largeurVue / carte.largeur,
                (double) hauteurVue / carte.hauteur) * 0.95;
            double tailleMin = Math.max(0.2, Math.min(3, tailleAjustement));
            tailleCellule = Math.max(tailleMin, Math.min(48, tailleCellule * facteur));
            // Conserver la cellule sous le curseur
            decalageX = sourisX - grilleGaucheEcran() - celluleX * tailleCellule;
            decalageY = sourisY - HAUT_GRILLE - celluleZ * tailleCellule;
            return true;
        }

        return super.mouseScrolled(sourisX, sourisY, delta);
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
        GestionnaireReseau.INSTANCE.sendToServer(new PaquetFermetureEditeurCarte());
        super.onClose();
    }

    // ==================== Rendu ====================

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        // Fond des panneaux
        guiGraphics.fill(0, 0, this.width, HAUT_GRILLE, 0xE0202020);
        guiGraphics.fill(0, HAUT_GRILLE, LARGEUR_PALETTE, this.height, 0xE0181818);

        dessinerGrille(guiGraphics, sourisX, sourisY);

        // Titre de la palette
        guiGraphics.drawString(this.font, "§7Palette :", 6, HAUT_GRILLE + 3, 0xFFFFFF);

        // Panneau droit (délais)
        if (panneauDelaisVisible()) {
            dessinerPanneauDelais(guiGraphics);
        }

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);

        // Cadre de mise en évidence de l'outil actif
        for (Map.Entry<OutilPalette, Button> entree : boutonsPalette.entrySet()) {
            if (entree.getKey() == outilActif) {
                Button bouton = entree.getValue();
                dessinerCadre(guiGraphics, bouton.getX() - 2, bouton.getY() - 2,
                    bouton.getX() + bouton.getWidth() + 2, bouton.getY() + bouton.getHeight() + 2, 0xFFFFFF00);
            }
        }
        if (modeSelection && boutonSelection != null) {
            dessinerCadre(guiGraphics, boutonSelection.getX() - 2, boutonSelection.getY() - 2,
                boutonSelection.getX() + boutonSelection.getWidth() + 2,
                boutonSelection.getY() + boutonSelection.getHeight() + 2, 0xFF00FFFF);
        }

        // Infos de la cellule survolée
        int[] cellule = celluleSousSouris(sourisX, sourisY);
        if (cellule != null && !modalActif) {
            BlocCarte bloc = carte.obtenirBloc(cellule[0], cellule[1]);
            String info = "(" + cellule[0] + ", " + cellule[1] + ") " + bloc.type.obtenirNomAffichage();
            if (bloc.type == TypeElementCarte.ILE || bloc.type == TypeElementCarte.PIERRE) {
                info += " élév. " + bloc.elevation;
            }
            if (bloc.qteBonbonVisible > 0) {
                info += " | vis. ×" + bloc.qteBonbonVisible
                    + " " + bloc.typeBonbonVisible.obtenirNomAffichage()
                    + (bloc.delaiBonbonVisible > 0 ? " (réap. " + bloc.delaiBonbonVisible + "s)" : "")
                    + (bloc.delaiApparitionInitiale > 0 ? " (appar. " + bloc.delaiApparitionInitiale + "s)" : "");
            }
            if (bloc.qteBonbonNonVisible > 0) {
                info += " | non-vis. ×" + bloc.qteBonbonNonVisible
                    + " " + bloc.typeBonbonNonVisible.obtenirNomAffichage()
                    + (bloc.delaiBonbonNonVisible > 0 ? " (réap. " + bloc.delaiBonbonNonVisible + "s)" : "")
                    + (bloc.delaiApparitionInitialeNonVisible > 0
                        ? " (appar. " + bloc.delaiApparitionInitialeNonVisible + "s)" : "");
            }
            guiGraphics.drawString(this.font, info, LARGEUR_PALETTE + 6, this.height - 12, 0xFFFFFF);
        }

        // Rectangle de sélection en cours
        if (tracageSelection) {
            int minX = (int) Math.min(selectionDebutX, selectionCouranteX);
            int maxX = (int) Math.max(selectionDebutX, selectionCouranteX);
            int minY = (int) Math.min(selectionDebutY, selectionCouranteY);
            int maxY = (int) Math.max(selectionDebutY, selectionCouranteY);
            guiGraphics.fill(minX, minY, maxX, maxY, 0x3000FFFF);
            dessinerCadre(guiGraphics, minX, minY, maxX, maxY, 0xFF00FFFF);
        }

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
        int haut = HAUT_GRILLE;
        int bas = this.height;

        guiGraphics.enableScissor(gauche, haut, droite, bas);

        // Plage de cellules visibles
        int cxMin = Math.max(0, (int) Math.floor((-decalageX) / tailleCellule) - 1);
        int cxMax = Math.min(carte.largeur - 1, (int) Math.ceil((droite - gauche - decalageX) / tailleCellule) + 1);
        int czMin = Math.max(0, (int) Math.floor((-decalageY) / tailleCellule) - 1);
        int czMax = Math.min(carte.hauteur - 1, (int) Math.ceil((bas - haut - decalageY) / tailleCellule) + 1);

        int taille = Math.max(1, (int) Math.floor(tailleCellule));

        // 1) Couleurs de base par plages horizontales de même couleur
        //    (rapide même en dézoom complet sur de très grandes cartes)
        for (int cz = czMin; cz <= czMax; cz++) {
            int y0 = (int) Math.floor(haut + decalageY + cz * tailleCellule);
            int y1 = (int) Math.floor(haut + decalageY + (cz + 1) * tailleCellule);
            if (y1 <= y0) {
                y1 = y0 + 1;
            }
            int debutPlage = cxMin;
            int couleurPlage = couleurBloc(carte.obtenirBlocOuNull(cxMin, cz));
            for (int cx = cxMin + 1; cx <= cxMax + 1; cx++) {
                int couleur = cx <= cxMax ? couleurBloc(carte.obtenirBlocOuNull(cx, cz)) : 0;
                if (cx > cxMax || couleur != couleurPlage) {
                    int x0 = (int) Math.floor(gauche + decalageX + debutPlage * tailleCellule);
                    int x1 = (int) Math.floor(gauche + decalageX + cx * tailleCellule);
                    if (x1 <= x0) {
                        x1 = x0 + 1;
                    }
                    guiGraphics.fill(x0, y0, x1, y1, couleurPlage);
                    debutPlage = cx;
                    couleurPlage = couleur;
                }
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
                        guiGraphics.fill(ecranX + taille - icone - 1, ecranY + taille - icone - 1,
                            ecranX + taille - 1, ecranY + taille - 1, 0xFF9632C8);
                        dessinerTexteCellule(guiGraphics, String.valueOf(bloc.qteBonbonNonVisible),
                            ecranX + taille - 1 - icone / 2.0f, ecranY + taille - 1 - icone / 2.0f, icone, 0xFFFFFFFF);
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

    private void dessinerCadre(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int couleur) {
        guiGraphics.fill(x0, y0, x1, y0 + 1, couleur);
        guiGraphics.fill(x0, y1 - 1, x1, y1, couleur);
        guiGraphics.fill(x0, y0, x0 + 1, y1, couleur);
        guiGraphics.fill(x1 - 1, y0, x1, y1, couleur);
    }

    private void dessinerPanneauDelais(GuiGraphics guiGraphics) {
        int xPanneau = this.width - LARGEUR_PANNEAU_DROIT;
        guiGraphics.fill(xPanneau, HAUT_GRILLE, this.width, this.height, 0xE0181818);
        int nombreSelection = cellulesSelectionnees.size();
        guiGraphics.drawString(this.font, "§6Sélection : §7" + nombreSelection + " bloc(s)",
            xPanneau + 8, HAUT_GRILLE + 6, 0xFFFFFF);

        if (champDelaiVisible.visible) {
            guiGraphics.drawString(this.font, "Délai bonbon visible (s)", xPanneau + 8, HAUT_GRILLE + 16, 0xFFC81E);
        }
        if (champDelaiNonVisible.visible) {
            guiGraphics.drawString(this.font, "Délai bonbon non-vis. (s)", xPanneau + 8, HAUT_GRILLE + 42, 0xC896FF);
        }
        if (champApparitionInitiale.visible) {
            guiGraphics.drawString(this.font, "Apparition init. vis. (s)", xPanneau + 8, HAUT_GRILLE + 68, 0xFFC81E);
        }
        if (champApparitionInitialeNonVisible.visible) {
            guiGraphics.drawString(this.font, "Apparition init. non-vis. (s)", xPanneau + 8, HAUT_GRILLE + 94, 0xC896FF);
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

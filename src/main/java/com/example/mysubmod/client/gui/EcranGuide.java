package com.example.mysubmod.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Guide du jeu en jeu : écran à onglets (Aperçu / Joueur / Administrateur / Référence)
 * avec contenu défilable. Accessible à tous via la touche Guide (H par défaut,
 * Options › Commandes) ; les admins l'ouvrent aussi par le bouton « 📖 Guide du jeu »
 * du menu de contrôle. Purement client, informatif.
 */
@OnlyIn(Dist.CLIENT)
public class EcranGuide extends Screen {

    private enum Onglet {
        APERCU("Aperçu"),
        JOUEUR("Joueur"),
        ADMIN("Administrateur"),
        REFERENCE("Référence");

        final String libelle;

        Onglet(String libelle) {
            this.libelle = libelle;
        }
    }

    private static final int MARGE = 14;
    private static final int LARGEUR_ONGLET = 106;
    private static final int HAUTEUR_LIGNE = 11;

    private Onglet onglet = Onglet.APERCU;
    private int defilement = 0;

    // Géométrie de la zone de contenu (calculée à l'init)
    private int contenuX, contenuHaut, contenuBas, contenuLargeur;
    private final List<FormattedCharSequence> lignes = new ArrayList<>();
    private final List<Integer> lignesY = new ArrayList<>();
    private int hauteurTotale;

    public EcranGuide() {
        super(Component.literal("Guide du jeu"));
    }

    @Override
    protected void init() {
        super.init();

        int y = 42;
        for (Onglet o : Onglet.values()) {
            Button bouton = Button.builder(Component.literal(o.libelle), b -> {
                onglet = o;
                defilement = 0;
                rebuildWidgets();
            }).bounds(MARGE, y, LARGEUR_ONGLET, 20).build();
            bouton.active = (o != onglet); // l'onglet actif est grisé (indicateur)
            addRenderableWidget(bouton);
            y += 24;
        }

        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> this.onClose())
            .bounds(this.width / 2 - 60, this.height - 26, 120, 20).build());

        contenuX = MARGE + LARGEUR_ONGLET + 10;
        contenuHaut = 42;
        contenuBas = this.height - 32;
        contenuLargeur = this.width - contenuX - MARGE - 6; // 6 px réservés à la barre de défilement

        recomposer();
        defilement = Math.max(0, Math.min(defilement, hauteurTotale - (contenuBas - contenuHaut)));
    }

    /** Reconstruit les lignes de texte enveloppées pour l'onglet courant. */
    private void recomposer() {
        lignes.clear();
        lignesY.clear();
        List<Segment> segments = contenu(onglet);
        int y = 0;
        for (Segment seg : segments) {
            y += seg.margeAvant;
            for (FormattedCharSequence ligne : this.font.split(seg.texte, contenuLargeur)) {
                lignesY.add(y);
                lignes.add(ligne);
                y += HAUTEUR_LIGNE;
            }
        }
        hauteurTotale = y;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        // Titre + sous-titre
        guiGraphics.drawCenteredString(this.font, "§lGuide du jeu", this.width / 2, 14, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
            "§7Touche « Guide » (H, Options › Commandes) — admins : aussi via le menu M",
            this.width / 2, 26, 0xAAAAAA);

        // Cadre de la zone de contenu
        guiGraphics.fill(contenuX - 6, contenuHaut - 4, contenuX + contenuLargeur + 8, contenuBas + 4, 0x50000000);

        // Contenu défilable (découpé au rectangle)
        guiGraphics.enableScissor(contenuX - 4, contenuHaut, contenuX + contenuLargeur + 8, contenuBas);
        int viewH = contenuBas - contenuHaut;
        for (int i = 0; i < lignes.size(); i++) {
            int yEcran = contenuHaut - defilement + lignesY.get(i);
            if (yEcran + HAUTEUR_LIGNE >= contenuHaut && yEcran <= contenuBas) {
                guiGraphics.drawString(this.font, lignes.get(i), contenuX, yEcran, 0xFFFFFFFF);
            }
        }
        guiGraphics.disableScissor();

        // Barre de défilement
        if (hauteurTotale > viewH) {
            int pisteX = contenuX + contenuLargeur + 4;
            guiGraphics.fill(pisteX, contenuHaut, pisteX + 3, contenuBas, 0x40FFFFFF);
            int hauteurPouce = Math.max(20, (int) ((long) viewH * viewH / hauteurTotale));
            int deplacementMax = hauteurTotale - viewH;
            int posPouce = deplacementMax == 0 ? 0 : (int) ((long) defilement * (viewH - hauteurPouce) / deplacementMax);
            guiGraphics.fill(pisteX, contenuHaut + posPouce, pisteX + 3, contenuHaut + posPouce + hauteurPouce, 0xC0FFFFFF);
        }

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean mouseScrolled(double sourisX, double sourisY, double delta) {
        int viewH = contenuBas - contenuHaut;
        int deplacementMax = Math.max(0, hauteurTotale - viewH);
        defilement = Math.max(0, Math.min(deplacementMax, defilement - (int) (delta * 18)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== Contenu ====================

    /** Un bloc de texte (déjà stylé avec des codes §) précédé d'une marge verticale. */
    private record Segment(int margeAvant, Component texte) {
    }

    private static void titre(List<Segment> l, String texte) {
        l.add(new Segment(12, Component.literal("§6§l" + texte)));
    }

    private static void para(List<Segment> l, String texte) {
        l.add(new Segment(6, Component.literal(texte)));
    }

    private static void puce(List<Segment> l, String texte) {
        l.add(new Segment(3, Component.literal("§7• §r" + texte)));
    }

    private List<Segment> contenu(Onglet o) {
        List<Segment> l = new ArrayList<>();
        switch (o) {
            case APERCU -> construireApercu(l);
            case JOUEUR -> construireJoueur(l);
            case ADMIN -> construireAdmin(l);
            case REFERENCE -> construireReference(l);
        }
        return l;
    }

    private void construireApercu(List<Segment> l) {
        l.add(new Segment(0, Component.literal("§e§lSurvie aux bonbons")));
        para(l, "Une partie se joue dans une §ecage§r bâtie à partir d'une carte. Le but : "
            + "§esurvivre jusqu'à la fin du chronomètre§r pendant que votre santé baisse toute seule. "
            + "Pour tenir, mangez des bonbons — certains au sol, d'autres cachés dans les blocs.");

        titre(l, "Les touches essentielles");
        puce(l, "§e[M]§r — menu de contrôle (admins uniquement) : mode, cartes, journaux, guide.");
        puce(l, "§e[N]§r — en Sous-mode 3 : avant la partie (admin), régler et lancer ; pendant la partie (joueur), viser une parcelle.");
        puce(l, "§e[F]§r — faire défiler le panneau des parcelles : pages, puis masqué (Maj+F : sens inverse ; la flèche de navigation reste visible).");
        puce(l, "§e[Guide]§r — ouvre ce guide à tout moment (H par défaut).");
        para(l, "§7[M] et [N] sont fixes ; [F] et [Guide] se règlent dans Options › Commandes, "
            + "catégorie « Survie aux bonbons ».");

        titre(l, "Deux rôles");
        puce(l, "§aJoueur§r — vous participez à la partie que l'admin lance. Onglet §eJoueur§r.");
        puce(l, "§9Administrateur§r — vous créez les cartes, réglez les conditions et lancez. Onglet §eAdministrateur§r.");
    }

    private void construireJoueur(List<Segment> l) {
        l.add(new Segment(0, Component.literal("§a§lGuide du joueur")));
        para(l, "De la connexion au classement final, voici comment se déroule une partie.");

        titre(l, "1. Se connecter");
        para(l, "Un compte libre entre directement. Un compte protégé ou admin voit d'abord un écran "
            + "de mot de passe : 3 essais et 60 secondes, sinon expulsion (les échecs répétés bloquent "
            + "temporairement l'adresse). Si le serveur est plein, un joueur libre (au hasard) est "
            + "déconnecté pour laisser la place à un compte protégé. Si quelqu'un se connecte déjà "
            + "avec votre compte, le bon mot de passe vous met en file d'attente : vous êtes "
            + "déconnecté et avez 45 secondes pour revenir prendre la place.");

        titre(l, "2. Patienter : salle d'attente, puis plateforme");
        para(l, "Entre les parties, tout le monde attend sur une plateforme de pierre — impossible "
            + "d'y construire, combattre ou mourir. Votre inventaire est mis de côté et rendu à la "
            + "sortie du mode. Au lancement du Sous-mode 3, vous êtes déplacé sur une plateforme de "
            + "verre en hauteur ; la carte se construit en dessous (barre de progression). Un joueur "
            + "qui se connecte pendant une partie devient spectateur.");

        titre(l, "3. Le début de la partie");
        para(l, "Un décompte (10 s par défaut) précède la téléportation dans la cage — ou, si l'option "
            + "est active, une phase de §echoix de la parcelle de départ§r : 30 s pour cliquer une "
            + "parcelle (sa taille en blocs est indiquée ; plusieurs joueurs peuvent choisir la "
            + "même ; sans choix, elle est attribuée au hasard).");

        titre(l, "4. Survivre : santé et bonbons");
        para(l, "Votre santé baisse seule (par défaut un demi-cœur toutes les 10 secondes). "
            + "Mangez un bonbon (§eclic droit§r) pour récupérer de la vie — 1 cœur par défaut ; à vie "
            + "pleine, manger est bloqué sauf option contraire. À 1 cœur ou moins, un avertissement "
            + "§c« Santé critique »§r s'affiche.");
        puce(l, "§eBonbons visibles§r — au sol : passez dessus pour les ramasser, puis mangez-les.");
        puce(l, "§eBonbons non-visibles§r — cachés dans des blocs : minez le bloc pour les faire tomber.");
        para(l, "§7Vous pouvez replacer les blocs minés (dans la cage uniquement). Selon la carte, les "
            + "bonbons peuvent réapparaître après ramassage, apparaître en cours de partie ou expirer.");

        titre(l, "5. Se repérer : minuterie, parcelles et flèche");
        para(l, "En haut à droite : le temps restant (fond orange sous 60 s, rouge sous 30 s), puis "
            + "le panneau des parcelles et leurs bonbons restants — visibles et non-visibles, ou "
            + "comptes §9bleu§r/§crouge§r si la spécialisation est activée. Appuyez sur §e[N]§r et choisissez "
            + "une parcelle : la flèche pointe vers le centre de ses bonbons restants (recalculé à "
            + "chaque ramassage et réapparition) et disparaît à 15 blocs du but. Une parcelle vide "
            + "reste ciblable : la flèche vise alors son centre et s'éteint quand vous y entrez.");
        para(l, "§7Si la carte a beaucoup de parcelles, le panneau les répartit en pages : la touche "
            + "§e[F]§7 passe à la page suivante, puis masque le panneau après la dernière "
            + "(§eMaj+F§7 : sens inverse) ; la flèche, elle, reste visible.");

        titre(l, "6. Mort, fin et classement");
        para(l, "À 0 cœur, vous êtes éliminé — définitivement, sauf si la réapparition est activée — "
            + "et rejoignez la plateforme au-dessus de la cage ; cliquez sur la pancarte pour passer "
            + "en spectateur volant. La noyade tue instantanément par défaut. La partie finit au "
            + "temps écoulé, quand tout le monde est éliminé ou, si l'option est active, au dernier "
            + "survivant. Le classement s'affiche dans le chat (survivants d'abord), puis retour en "
            + "salle d'attente 5 secondes plus tard — votre inventaire d'avant-partie est restauré.");

        titre(l, "Les règles changent selon la config");
        para(l, "L'admin règle chaque partie : §ePvP§r, §espécialisation Bleu/Rouge§r (le 1er bonbon "
            + "coloré fixe votre camp ; en changer réduit vos soins pendant un temps — réglable, "
            + "défaut 75 % pendant 2 min 45, minuterie en haut à gauche), §eréapparition§r, "
            + "§edrop à la mort§r, §echat§r, §efaim§r, §edégâts de chute§r, §etemps de minage§r… "
            + "Les messages en jeu vous préviennent.");
    }

    private void construireAdmin(List<Segment> l) {
        l.add(new Segment(0, Component.literal("§9§lGuide de l'administrateur")));
        para(l, "Vous contrôlez les cartes, le mode actif, les conditions de partie et les journaux.");

        titre(l, "S'authentifier");
        para(l, "Connectez-vous avec votre compte admin : l'écran de mot de passe apparaît à la connexion. "
            + "Gestion des comptes via §e/submode admin …§r et §e/submode player …§r (onglet "
            + "§eRéférence§r ; « admin add/remove » demande d'être opérateur).");

        titre(l, "Menu de contrôle — [M]");
        para(l, "Réservé aux admins. Affiche le mode actuel, les joueurs connectés et la carte active. Boutons :");
        puce(l, "§eSalle d'attente§r — y ramène tout le monde.");
        puce(l, "§eSous-mode 3§r — lance le mode (carte requise) · §e📊§r — journaux · §e📖§r — ce guide.");
        puce(l, "§eCartes et parcelles§r — liste des cartes (sélectionner, supprimer, désélectionner) ou éditeur.");
        para(l, "§7Pendant la génération d'une carte ou le nettoyage de la précédente (barre de "
            + "progression à l'écran), tout changement de mode est refusé — réessayez quand la "
            + "barre a disparu.");

        titre(l, "Créer une carte (éditeur)");
        para(l, "Un seul admin à la fois dans l'éditeur. Vous dessinez sur une grille (redimensionnable, "
            + "jusqu'à 1800×1800). Palette : §eEau, Île, Pierre, Limite§r (mur, en boucle fermée), "
            + "§eParcelle§r, §eBonbon visible, Bonbon non-visible, Apparition§r.");
        puce(l, "§eClic gauche§r peint / ajoute · §eClic droit§r retire / décrémente.");
        puce(l, "§ePinceau§r (rangée §e− / + §rde la palette, ou §eMaj+molette§r) : agrandit l'aire "
            + "d'application des outils Eau, Île, Pierre, Parcelle et Bonbons, de 1×1 à 15×15 — "
            + "Limite et Apparition restent au bloc près.");
        puce(l, "§eCtrl + molette§r sur Île/Pierre : élévation d'un cran (−15 à +15 ; Eau : 0 à −15).");
        puce(l, "§eMolette§r ou §e− / + / Ajuster§r (barre du bas) : zoom · §eclic milieu / flèches§r : déplacer "
            + "la vue · §eCtrl+Z / Ctrl+Y§r : annuler / rétablir.");
        puce(l, "§eSélection§r : tracer un rectangle sur le terrain (Île/Pierre) et les bonbons. "
            + "§eCtrl+molette§r élève/abaisse tout le terrain sélectionné d'un coup ; pour les bonbons, "
            + "régler les délais (réapparition, apparition, fin, expiration) et le §etype§r "
            + "(Standard/Bleu/Rouge), puis §eAppliquer à la sélection§r.");
        puce(l, "§eParcelle§r : découpez la carte en parcelles nommées — la navigation en jeu "
            + "([N] + flèche) et le choix de la parcelle de départ s'appuient dessus. Créez une "
            + "parcelle dans le panneau de droite (molette pour faire défiler la liste), peignez "
            + "ses cellules (Eau comprise), renommez-la. §cChaque bonbon doit appartenir à une "
            + "parcelle§7, chaque parcelle est §cd'un seul tenant§7 (ses blocs se touchent, côté "
            + "ou diagonale — ⚠ dans le panneau sinon) et porte un §cnom unique§7 — la sauvegarde "
            + "est refusée sinon.");
        puce(l, "§eLimite§r : bouton « Par défaut » = mur sur tout le contour de l'aire ; "
            + "« Supprimer » retire toute la Limite.");
        para(l, "§7Le panneau de droite montre le bloc survolé, l'état de la carte (limite fermée, apparition, "
            + "totaux de bonbons) et la légende ; la barre du bas rappelle les contrôles de l'outil actif. "
            + "Un point §6●§7 sur le bouton Sauvegarder signale des modifications non sauvegardées.");
        para(l, "§7Sur la grille : bonbon visible = coin haut-gauche coloré ; non-visible = coin bas-droite "
            + "coloré mais assombri et bordé de noir (couleur = type, assombrissement = caché).");
        para(l, "§eCharger§r ouvre une carte existante ; §eImporter CSV§r remplit les bonbons depuis "
            + "un fichier (§cremplace toute la carte en cours§r). À la §esauvegarde§r, donnez un nom "
            + "(lettres, chiffres, - et _) ; l'éditeur refuse une carte invalide et indique quoi "
            + "corriger (boucle Limite fermée, apparition à l'intérieur, un sol sous chaque case, etc.).");

        titre(l, "Sélectionner la carte");
        para(l, "Choisissez la carte active dans la liste. Le Sous-mode 3 §eexige§r une carte sélectionnée.");

        titre(l, "Régler et lancer — [N]");
        para(l, "En Sous-mode 3, après la génération, appuyez sur §e[N]§r : toutes les conditions "
            + "tiennent sur un seul écran (cases à cocher et sélecteurs — clic = valeur suivante, "
            + "Maj+clic = précédente), groupées comme dans l'onglet §eRéférence§r. Les défauts "
            + "reproduisent le jeu classique ; §ePar défaut§r réinitialise. Selon la carte, certaines "
            + "options sont grisées : Spécialisation (exige des bonbons tous typés), Parcelle au "
            + "choix (exige des parcelles), Destruction blocs (imposée si bonbons non-visibles). "
            + "Le serveur refuse aussi une partie qui ne pourrait jamais se terminer (« Sans "
            + "limite » exige la dégradation, sans réapparition ni régénération). Au clic "
            + "§eLancer la partie§r : décompte, ou phase de choix de parcelle si activée.");
        para(l, "§7Presets (barre du bas du menu N) : §eSauver§r enregistre les réglages sous un nom "
            + "(écrase un homonyme), §eCharger§r les rappelle, §eSuppr§r les supprime. Stockés sur "
            + "le serveur, partagés entre admins, conservés au redémarrage.");

        titre(l, "Journaux de données");
        para(l, "Chaque partie écrit un dossier §edonnees_monsubmod/sousmode3_partie_<date-heure>/§r :");
        puce(l, "§econfig_partie.csv§r — la carte et la valeur de chaque condition choisie.");
        puce(l, "§e<joueur>_journal.csv§r — un fichier par joueur, une ligne par événement "
            + "(déplacement, bonbon, minage, santé, mort…).");
        para(l, "§7Le bouton 📊 (menu M) télécharge une session ou tout (.zip, déposé dans votre "
            + "dossier Téléchargements) ou supprime les sessions.");
    }

    private void construireReference(List<Segment> l) {
        l.add(new Segment(0, Component.literal("§e§lRéférence rapide")));

        titre(l, "Touches");
        puce(l, "§e[M]§r — menu de contrôle (admins uniquement ; touche fixe).");
        puce(l, "§e[N]§r — joueur : viser une parcelle · admin : lancer une partie (touche fixe, Sous-mode 3).");
        puce(l, "§e[F]§r — pages du panneau des parcelles, puis masqué (Options › Commandes).");
        puce(l, "§e[Guide]§r — ce guide (H par défaut, Options › Commandes).");
        puce(l, "§eClic sur la pancarte§r (plateforme) — éliminés, retardataires et admins : spectateur volant.");

        titre(l, "Conditions du menu N (défaut)");
        puce(l, "§bDurée & rythme§r : Durée 15 min · Décompte 10 s · Sans limite non.");
        puce(l, "§bSanté & survie§r : Dégradation oui · Perte ½ cœur · Intervalle 10 s · Vie max 10 cœurs · Régén. non · Réapparition non · Pénalité spé. 2:45 · Soin pénalité 75 %.");
        puce(l, "§bBonbons & minage§r : Soin 1 cœur (std, bleu, rouge) · Minage vanilla §7(sinon 0,5 à 30 s/bloc)§r.");
        puce(l, "§bEnvironnement§r : Jour permanent oui · Chute non · Noyade mortelle oui · Faim non · PvP non · Chat oui · Pluie non.");
        puce(l, "§bMode§r : Spécialisation B/R non §7(pénalité de changement de camp : voir Santé & survie)§r · Parcelle au choix non · Bonus sprint non.");
        puce(l, "§bInteractions§r : Crafting non · Destruction oui · Placement oui · Jeter non · Drop à la mort non · Manger à vie max non.");
        puce(l, "§bFin de partie§r : Éliminés classés par Survie §7(sinon Bonbons ; survivants toujours devant)§r · Dernier survivant non.");

        titre(l, "Repères techniques");
        puce(l, "Niveau de la mer : Y 100 · Cage : barrières Y 84 et 116 (jouable 85–115) · Élévation : −15 à +15.");
        puce(l, "Grille de carte : jusqu'à 1800×1800 · noms de carte et de preset : 32 caractères (lettres, chiffres, - et _).");

        titre(l, "Commandes (/submode)");
        puce(l, "§eset waiting|attente|3|sub3§r — changer de mode (admin).");
        puce(l, "§eadmin add|remove <joueur>§r §7(opérateur)§r · §eadmin list§r · §eadmin setpassword/resetblacklist/resetfailures/resetip …§r (admin).");
        puce(l, "§eplayer add <joueur> <mdp>§r · §eplayer remove|setpassword …§r · §eplayer list§r (admin).");
        puce(l, "§ecurrent§r — affiche le mode actuel (tous).");
    }
}

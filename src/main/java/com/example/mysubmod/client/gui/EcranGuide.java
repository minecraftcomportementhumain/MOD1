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
 * avec contenu défilable. Accessible à tous via la touche Guide (Options › Commandes)
 * ou le bouton « Guide » du menu de contrôle. Purement client, informatif.
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
            "§7Touche « Guide » (Options › Commandes) ou bouton Guide du menu M",
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
        puce(l, "§e[M]§r — menu de contrôle : mode, cartes, journaux (surtout pour les admins).");
        puce(l, "§e[N]§r — avant la partie (admin) : régler et lancer ; pendant la partie (joueur) : viser une zone.");
        puce(l, "§e[Guide]§r — ouvre ce guide à tout moment (H par défaut, voir Options › Commandes).");

        titre(l, "Deux rôles");
        puce(l, "§aJoueur§r — vous participez à la partie que l'admin lance. Onglet §eJoueur§r.");
        puce(l, "§9Administrateur§r — vous créez les cartes, réglez les conditions et lancez. Onglet §eAdministrateur§r.");
    }

    private void construireJoueur(List<Segment> l) {
        l.add(new Segment(0, Component.literal("§a§lGuide du joueur")));
        para(l, "De la connexion au classement final, voici comment se déroule une partie.");

        titre(l, "1. Se connecter");
        para(l, "Un compte libre entre directement. Un compte protégé ou admin voit d'abord un écran "
            + "de mot de passe. Si le serveur est plein, un joueur libre peut être déconnecté pour laisser "
            + "la place à un compte protégé ; les comptes très demandés passent par une file d'attente.");

        titre(l, "2. Patienter sur la plateforme");
        para(l, "Avant le lancement, tout le monde attend sur une plateforme de verre en hauteur ; la carte "
            + "se construit en dessous (barre de progression). Votre inventaire est mis de côté et rendu à la fin. "
            + "Un panneau permet de passer en simple spectateur.");

        titre(l, "3. Le début de la partie");
        para(l, "Un décompte de 10 secondes précède la téléportation dans la cage — ou, si l'option est active, "
            + "une phase de §echoix de l'île de départ§r (30 s, la taille de chaque île est indiquée).");

        titre(l, "4. Survivre : santé et bonbons");
        para(l, "Votre santé baisse seule (par défaut un demi-cœur toutes les 10 secondes). "
            + "Mangez un bonbon (§eclic droit§r) pour récupérer un cœur.");
        puce(l, "§eBonbons visibles§r — au sol : passez dessus pour les ramasser, puis mangez-les.");
        puce(l, "§eBonbons non-visibles§r — cachés dans des blocs : minez le bloc pour les faire tomber.");
        para(l, "§7Vous pouvez replacer les blocs minés. Selon la carte, les bonbons peuvent réapparaître.");

        titre(l, "5. Se repérer : zones et flèche");
        para(l, "Le panneau en haut à droite liste les zones et leurs bonbons restants. Appuyez sur §e[N]§r "
            + "et choisissez une zone : une flèche vous y guide et disparaît quand vous arrivez.");

        titre(l, "6. Mort, fin et classement");
        para(l, "À 0 cœur, vous passez spectateur (mort définitive, sauf si la réapparition est activée). "
            + "La noyade est mortelle par défaut. La partie finit au temps écoulé, quand tout le monde est "
            + "éliminé, ou au dernier survivant. Un classement s'affiche, puis retour en salle d'attente.");

        titre(l, "Les règles changent selon la config");
        para(l, "L'admin règle chaque partie : §ePvP§r, §espécialisation Bleu/Rouge§r (le 1er bonbon coloré fixe "
            + "votre camp ; en changer inflige une pénalité de soin), §eréapparition§r, §edrop à la mort§r, "
            + "§emonstres§r, §efaim§r, §edégâts de chute§r… Les messages en jeu vous préviennent.");
    }

    private void construireAdmin(List<Segment> l) {
        l.add(new Segment(0, Component.literal("§9§lGuide de l'administrateur")));
        para(l, "Vous contrôlez les cartes, le mode actif, les conditions de partie et les journaux.");

        titre(l, "S'authentifier");
        para(l, "Connectez-vous avec votre compte admin : l'écran de mot de passe apparaît à la connexion. "
            + "Gestion des comptes via §e/submode admin …§r et §e/submode player …§r.");

        titre(l, "Menu de contrôle — [M]");
        para(l, "Affiche le mode actuel, le nombre de joueurs et la carte active. Boutons :");
        puce(l, "§eSalle d'attente§r — y ramène tout le monde.");
        puce(l, "§eSous-mode 3§r §7(+📊)§r — lance le mode (carte requise) ; 📊 = journaux.");
        puce(l, "§eCartes et parcelles§r — sélectionner la carte active, ou ouvrir l'éditeur.");
        para(l, "§7Pendant la génération d'une carte ou le nettoyage de la précédente (barre de "
            + "progression à l'écran), tout changement de mode est refusé — réessayez quand la "
            + "barre a disparu.");

        titre(l, "Créer une carte (éditeur)");
        para(l, "Vous dessinez sur une grille. Palette : §eEau, Île, Pierre, Limite§r (mur, en boucle fermée), "
            + "§eBonbon visible, Bonbon non-visible, Apparition§r.");
        puce(l, "§eClic gauche§r peint / ajoute · §eClic droit§r retire / décrémente.");
        puce(l, "§ePinceau§r (rangée §e− / + §rde la palette, ou §eMaj+molette§r) : agrandit l'aire "
            + "d'application des outils Eau, Île, Pierre et Bonbons, de 1×1 à 15×15 — Limite et "
            + "Apparition restent au bloc près.");
        puce(l, "§eCtrl + molette§r sur Île/Pierre : élévation d'un cran (−15 à +15).");
        puce(l, "§eMolette§r ou §e− / + / Ajuster§r (barre du bas) : zoom · §eclic milieu / flèches§r : déplacer "
            + "la vue · §eCtrl+Z / Ctrl+Y§r : annuler / rétablir.");
        puce(l, "§eSélection§r : tracer un rectangle sur le terrain (Île/Pierre) et les bonbons. "
            + "§eCtrl+molette§r élève/abaisse tout le terrain sélectionné d'un coup ; pour les bonbons, "
            + "régler les délais et le §etype§r (Standard/Bleu/Rouge), puis §eAppliquer§r.");
        para(l, "§7Le panneau de droite montre le bloc survolé, l'état de la carte (limite fermée, apparition, "
            + "totaux de bonbons) et la légende ; la barre du bas rappelle les contrôles de l'outil actif. "
            + "Un point §6●§7 sur le bouton Sauvegarder signale des modifications non sauvegardées.");
        para(l, "§7Sur la grille : bonbon visible = coin haut-gauche coloré ; non-visible = coin bas-droite "
            + "coloré mais assombri et bordé de noir (couleur = type, assombrissement = caché).");
        para(l, "À la §esauvegarde§r, donnez un nom ; l'éditeur refuse une carte invalide et indique quoi "
            + "corriger (boucle Limite fermée, apparition à l'intérieur, un sol sous chaque case, etc.).");

        titre(l, "Sélectionner la carte");
        para(l, "Choisissez la carte active dans la liste. Le mode §eexige§r une carte sélectionnée.");

        titre(l, "Régler et lancer — [N]");
        para(l, "En Sous-mode 3, après la génération, appuyez sur §e[N]§r : le menu de lancement s'ouvre "
            + "(cases à cocher et sélecteurs, par groupes — voir l'onglet §eRéférence§r). Les défauts "
            + "reproduisent le jeu classique ; le bouton §ePar défaut§r réinitialise. Certaines options sont "
            + "grisées selon la carte. Au clic §eLancer§r : décompte, ou phase de choix de zone si activée.");

        titre(l, "Journaux de données");
        para(l, "Chaque partie écrit un dossier §edonnees_monsubmod/sousmode3_partie_<date>/§r :");
        puce(l, "§econfig_partie.csv§r — la carte et la valeur de chaque condition choisie.");
        puce(l, "§e<joueur>_journal.csv§r — un fichier par joueur, une ligne par événement.");
        para(l, "§7Le bouton 📊 (menu M) permet de télécharger (.zip) ou supprimer les sessions.");
    }

    private void construireReference(List<Segment> l) {
        l.add(new Segment(0, Component.literal("§e§lRéférence rapide")));

        titre(l, "Touches");
        puce(l, "§e[M]§r — menu de contrôle (admins).");
        puce(l, "§e[N]§r — joueur : viser une zone · admin : lancer une partie.");
        puce(l, "§e[Guide]§r — ce guide (H par défaut).");
        puce(l, "§eClic sur le panneau§r — passer spectateur (sur la plateforme).");

        titre(l, "Conditions du menu N (défaut)");
        puce(l, "§bDurée & rythme§r : Durée 15 min · Sans limite non · Décompte 10 s.");
        puce(l, "§bSanté§r : Dégradation oui · ½ cœur / 10 s · Vie max 10 cœurs · Régén. non · Réapparition non.");
        puce(l, "§bEnvironnement§r : Jour permanent oui · Chute non · Noyade mortelle oui · Faim non · PvP non · Pluie non.");
        puce(l, "§bMode§r : Spécialisation Bleu/Rouge non §7(carte à bonbons typés requise)§r.");
        puce(l, "§bZone de départ§r : Choix par joueur non §7(zone Île requise)§r.");
        puce(l, "§bInteractions§r : Crafting non · Destruction oui · Placement oui · Jeter non · Drop à la mort non · Manger à vie max non · Bonus sprint non.");
        puce(l, "§bFin de partie§r : Éliminés classés par Survie · Dernier survivant non.");

        titre(l, "Repères techniques");
        puce(l, "Niveau de la mer : Y 100 · Cage jouable : Y 84–116 · Élévation : −15 à +15.");
        puce(l, "Commande : §e/submode set waiting|3§r, §e/submode admin|player …§r, §e/submode current§r.");
    }
}

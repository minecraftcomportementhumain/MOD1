package com.example.mysubmod.cartes.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Import d'un fichier CSV fourni par le responsable.
 * L'import réinitialise complètement la carte en cours d'édition :
 * - la première ligne de données définit la taille de la carte : largeur,hauteur
 * - chaque ligne suivante définit un bonbon : x,z,type[,quantite]
 *   (type : « visible » ou « non-visible » ; quantité par défaut : 1)
 * Un élément Île (élévation 0) est ajouté sous chaque bonbon ; les autres blocs
 * restent Vides (le remplissage Eau/Pierre est proposé à la sauvegarde).
 * La Limite n'est pas générée automatiquement.
 */
@OnlyIn(Dist.CLIENT)
public class EcranImportCsvCarte extends Screen {
    private final EcranEditeurCarte editeur;

    private EditBox champChemin;
    private Button boutonImporter;

    private String messageStatut = "";
    private boolean statutErreur = false;

    // Résultat de l'analyse
    private int largeurLue;
    private int hauteurLue;
    private List<int[]> bonbonsLus; // [x, z, visible(1/0), quantite]

    public EcranImportCsvCarte(EcranEditeurCarte editeur) {
        super(Component.literal("Importer un fichier CSV"));
        this.editeur = editeur;
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int debutY = 90;

        champChemin = new EditBox(this.font, centreX - 200, debutY, 400, 20, Component.literal("Chemin du fichier"));
        champChemin.setHint(Component.literal("C:\\chemin\\vers\\fichier.csv"));
        champChemin.setMaxLength(260);
        champChemin.setResponder(this::surChangementChemin);
        this.addRenderableWidget(champChemin);

        boutonImporter = Button.builder(
            Component.literal("Importer"),
            bouton -> importer()
        ).bounds(centreX - 105, debutY + 40, 100, 20).build();
        boutonImporter.active = false;
        this.addRenderableWidget(boutonImporter);

        this.addRenderableWidget(Button.builder(
            Component.literal("Annuler"),
            bouton -> this.onClose()
        ).bounds(centreX + 5, debutY + 40, 100, 20).build());
    }

    private void surChangementChemin(String chemin) {
        boutonImporter.active = false;
        messageStatut = "";
        statutErreur = false;
        bonbonsLus = null;

        String cheminFichier = chemin.trim();
        if (cheminFichier.length() < 5) {
            return;
        }

        try {
            Path fichier = Paths.get(cheminFichier);
            if (!Files.exists(fichier) || !Files.isRegularFile(fichier)) {
                return;
            }

            String contenu = Files.readString(fichier);
            analyserCsv(contenu);
        } catch (Exception e) {
            messageStatut = "ERREUR : " + e.getMessage();
            statutErreur = true;
        }
    }

    private void analyserCsv(String contenu) {
        String[] lignes = contenu.split("\\r?\\n");
        int largeur = -1;
        int hauteur = -1;
        List<int[]> bonbons = new ArrayList<>();
        int numeroLigne = 0;

        for (String ligneBrute : lignes) {
            numeroLigne++;
            String ligne = ligneBrute.trim();
            if (ligne.isEmpty() || ligne.startsWith("#")) {
                continue;
            }

            String[] parties = ligne.split("[,;]");
            try {
                if (largeur < 0) {
                    // Première ligne de données : la taille de la carte en blocs
                    if (parties.length < 2) {
                        throw new IllegalArgumentException("taille attendue : largeur,hauteur");
                    }
                    largeur = Integer.parseInt(parties[0].trim());
                    hauteur = Integer.parseInt(parties[1].trim());
                    if (largeur < 1 || hauteur < 1) {
                        throw new IllegalArgumentException("taille minimale : 1×1 bloc");
                    }
                    continue;
                }

                // Lignes de bonbons : x,z,type[,quantite]
                if (parties.length < 3) {
                    throw new IllegalArgumentException("format attendu : x,z,type[,quantite]");
                }
                int x = Integer.parseInt(parties[0].trim());
                int z = Integer.parseInt(parties[1].trim());
                int visible = analyserTypeBonbon(parties[2].trim());
                int quantite = parties.length >= 4 && !parties[3].trim().isEmpty()
                    ? Integer.parseInt(parties[3].trim()) : 1;

                if (x < 0 || x >= largeur || z < 0 || z >= hauteur) {
                    throw new IllegalArgumentException("position (" + x + "," + z + ") hors de l'aire "
                        + largeur + "×" + hauteur);
                }
                if (quantite < 1) {
                    throw new IllegalArgumentException("quantité invalide : " + quantite);
                }

                bonbons.add(new int[]{x, z, visible, quantite});
            } catch (Exception e) {
                messageStatut = "ERREUR ligne " + numeroLigne + " : " + e.getMessage();
                statutErreur = true;
                return;
            }
        }

        if (largeur < 0) {
            messageStatut = "ERREUR : aucune ligne de taille trouvée (largeur,hauteur)";
            statutErreur = true;
            return;
        }

        largeurLue = largeur;
        hauteurLue = hauteur;
        bonbonsLus = bonbons;
        messageStatut = "✓ Fichier valide : carte " + largeur + "×" + hauteur + ", " + bonbons.size() + " ligne(s) de bonbons";
        statutErreur = false;
        boutonImporter.active = true;
    }

    /** Retourne 1 pour visible, 0 pour non-visible */
    private int analyserTypeBonbon(String type) {
        String normalise = type.toLowerCase()
            .replace("é", "e")
            .replace("-", "_")
            .replace(" ", "_");
        if (normalise.equals("visible") || normalise.equals("v") || normalise.equals("1")) {
            return 1;
        }
        if (normalise.equals("non_visible") || normalise.equals("nonvisible") || normalise.equals("invisible")
            || normalise.equals("nv") || normalise.equals("0") || normalise.equals("cache") || normalise.equals("hidden")) {
            return 0;
        }
        throw new IllegalArgumentException("type de bonbon inconnu : « " + type + " » (attendu : visible / non-visible)");
    }

    private void importer() {
        if (bonbonsLus == null) {
            return;
        }
        // L'import réinitialise complètement la carte existante
        editeur.appliquerImportCsv(largeurLue, hauteurLue, bonbonsLus);
        this.minecraft.setScreen(editeur);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(editeur);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);

        int centreX = this.width / 2;

        guiGraphics.drawCenteredString(this.font, this.title, centreX, 25, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
            "§cAttention : l'import réinitialise complètement la carte en cours", centreX, 45, 0xFF8888);
        guiGraphics.drawString(this.font, "Chemin du fichier :", centreX - 200, 75, 0xFFFFFF);

        if (!messageStatut.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, messageStatut, centreX, 145,
                statutErreur ? 0xFF5555 : 0x55FF55);
        }

        guiGraphics.drawCenteredString(this.font,
            "§7Format : 1re ligne « largeur,hauteur » puis « x,z,type[,quantite] »", centreX, 170, 0x888888);
        guiGraphics.drawCenteredString(this.font,
            "§7type : visible | non-visible — un élément Île est ajouté sous chaque bonbon", centreX, 182, 0x888888);
        guiGraphics.drawCenteredString(this.font,
            "§7Les autres blocs restent vides ; la Limite n'est pas générée automatiquement", centreX, 194, 0x888888);

        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public boolean keyPressed(int touche, int scanCode, int modificateurs) {
        if (touche == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(touche, scanCode, modificateurs);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

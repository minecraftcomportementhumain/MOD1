package com.example.mysubmod.sousmodes.sousmode2.client;

import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetTeleversementFichierBonbons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EcranTeleversementFichierBonbons extends Screen {
    private EditBox champChemin;
    private Button boutonTeleversement;
    private Button boutonAnnuler;
    private Button boutonAleatoire;
    private String contenuCharge = "";
    private String nomFichierCharge = "";

    public EcranTeleversementFichierBonbons() {
        super(Component.literal("Télécharger un fichier d'apparition des bonbons"));
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int debutY = 100;
        int largeurBouton = 140;
        int hauteurBouton = 20;

        // Champ d'entrée du chemin du fichier (champ unique)
        champChemin = new EditBox(this.font, centreX - 200, debutY, 400, 20, Component.literal("Chemin du fichier"));
        champChemin.setHint(Component.literal("C:\\chemin\\vers\\fichier.txt"));
        champChemin.setMaxLength(256);
        champChemin.setResponder(this::surChangementChemin);
        this.addRenderableWidget(champChemin);

        // Bouton de téléversement
        boutonTeleversement = Button.builder(
                        Component.literal("Charger au serveur"),
                        button -> televerserFichier()
                )
                .bounds(centreX - largeurBouton - 10, debutY + 40, largeurBouton, hauteurBouton)
                .build();
        boutonTeleversement.active = false;
        this.addRenderableWidget(boutonTeleversement);

        // Bouton d'annulation
        boutonAnnuler = Button.builder(
                        Component.literal("Annuler"),
                        button -> onClose()
                )
                .bounds(centreX - largeurBouton/2 - 10, debutY + 70, largeurBouton, hauteurBouton)
                .build();
        this.addRenderableWidget(boutonAnnuler);


        // Créer un fichier aléatoire
        boutonAleatoire = Button.builder(
                        Component.literal("Générer un fichier"),
                        button -> televerserAleatoire()
                )
                .bounds(centreX + 10, debutY + 40, largeurBouton, hauteurBouton)
                .build();
        this.addRenderableWidget(boutonAleatoire);
    }

    private void surChangementChemin(String nouveauChemin) {
        // Réinitialiser l'état immédiatement
        boutonTeleversement.active = false;
        contenuCharge = "";
        nomFichierCharge = "";

        String cheminFichier = nouveauChemin.trim();

        if (cheminFichier.isEmpty()) {
            return;
        }

        // Ne pas valider jusqu'à ce que le chemin semble complet (a une extension ou est assez long)
        if (cheminFichier.length() < 5) {
            return;
        }

        try {
            Path chemin = Paths.get(cheminFichier);

            // Vérifier si le fichier existe - sinon, retourner sans planter
            if (!Files.exists(chemin)) {
                return;
            }

            if (!Files.isRegularFile(chemin)) {
                return;
            }

            String contenu = Files.readString(chemin);
            String nomFichier = chemin.getFileName().toString();

            // S'assurer de l'extension .txt
            if (!nomFichier.toLowerCase().endsWith(".txt")) {
                nomFichier += ".txt";
            }

            // Vérifier si le fichier est vide
            if (contenu.trim().isEmpty()) {
                nomFichierCharge = "ERREUR: Le fichier est vide";
                return;
            }

            // Valider le contenu du fichier
            ResultatValidation validation = validerContenuFichier(contenu, chemin);

            if (validation.aLignesInvalides) {
                // Le fichier a des lignes invalides - le garder invalide, ne pas permettre le téléversement
                nomFichierCharge = "ERREUR: Fichier invalide (lignes marquées INVALIDE)";
            } else {
                // Le fichier est valide - permettre le téléversement
                contenuCharge = contenu;
                nomFichierCharge = nomFichier;
                boutonTeleversement.active = true;
            }

        } catch (Exception e) {
            // Attraper toutes les exceptions pour éviter les plantages pendant la saisie/collage
            // Réinitialiser l'état silencieusement
        }
    }

    private ResultatValidation validerContenuFichier(String contenu, Path cheminFichier) {
        String[] lignes = contenu.split("\\r?\\n");
        List<String> lignesModifiees = new ArrayList<>();
        boolean aLignesInvalides = false;

        for (String ligne : lignes) {
            String ligneTrimee = ligne.trim();

            // Garder les commentaires et lignes vides tels quels
            if (ligneTrimee.isEmpty() || ligneTrimee.startsWith("#")) {
                lignesModifiees.add(ligne);
                continue;
            }

            // Ignorer les lignes déjà marquées INVALIDE (ne pas les re-commenter)
            if (ligneTrimee.startsWith("INVALIDE:")) {
                lignesModifiees.add(ligne);
                aLignesInvalides = true;
                continue;
            }

            // Valider la ligne
            try {
                validerLigne(ligneTrimee);
                lignesModifiees.add(ligne);
            } catch (Exception e) {
                // Ligne invalide - ajouter un commentaire d'erreur mais garder la ligne invalide (pas de # au début)
                lignesModifiees.add("INVALIDE: " + ligne + " - Erreur: " + e.getMessage());
                aLignesInvalides = true;
            }
        }

        // S'il y avait des lignes invalides, réécrire le fichier avec des commentaires
        if (aLignesInvalides) {
            try {
                Files.write(cheminFichier, lignesModifiees);
            } catch (IOException e) {
                // Ignorer les erreurs d'écriture
            }
        }

        return new ResultatValidation(aLignesInvalides);
    }

    private void validerLigne(String ligne) throws Exception {
        String[] parties = ligne.split(",");
        if (parties.length != 6) {
            throw new IllegalArgumentException("Format attendu: temps,nombre,x,y,z,type");
        }

        int tempsSecondes = Integer.parseInt(parties[0].trim());
        int nombreBonbons = Integer.parseInt(parties[1].trim());
        int x = Integer.parseInt(parties[2].trim());
        int y = Integer.parseInt(parties[3].trim());
        int z = Integer.parseInt(parties[4].trim());
        String type = parties[5].trim().toUpperCase();

        // Valider la plage de temps (0-900 secondes = 15 minutes)
        if (tempsSecondes < 0 || tempsSecondes > 900) {
            throw new IllegalArgumentException("Le temps doit être entre 0 et 900 secondes");
        }

        // Valider le nombre de bonbons (1-100)
        if (nombreBonbons <= 0 || nombreBonbons > 100) {
            throw new IllegalArgumentException("Le nombre de bonbons doit être entre 1 et 100");
        }

        // Valider la coordonnée Y (hauteur)
        if (y < 100 || y > 120) {
            throw new IllegalArgumentException("La coordonnée Y doit être entre 100 et 120");
        }

        // Valider que la position est sur une île
        if (!estPositionSurIle(x, z)) {
            throw new IllegalArgumentException(String.format("La position (%d,%d,%d) n'est sur aucune île", x, y, z));
        }

        // Valider le type de ressource (A ou B)
        if (!type.equals("A") && !type.equals("B")) {
            throw new IllegalArgumentException("Le type doit être A ou B");
        }
    }

    private boolean estPositionSurIle(int x, int z) {
        // Centres et rayons des îles
        int[][] donneesIles = {
            {0, -360, 30},      // PETITE: centre (0,-360), rayon 30
            {360, 0, 45},       // MOYENNE: centre (360,0), rayon 45
            {0, 360, 60},       // GRANDE: centre (0,360), rayon 60
            {-360, 0, 75}       // TRÈS_GRANDE: centre (-360,0), rayon 75
        };

        for (int[] ile : donneesIles) {
            int centreX = ile[0];
            int centreZ = ile[1];
            int demiTaille = ile[2];

            int minX = centreX - demiTaille;
            int maxX = centreX + demiTaille;
            int minZ = centreZ - demiTaille;
            int maxZ = centreZ + demiTaille;

            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                return true;
            }
        }

        return false;
    }

    private static class ResultatValidation {
        final boolean aLignesInvalides;

        ResultatValidation(boolean aLignesInvalides) {
            this.aLignesInvalides = aLignesInvalides;
        }
    }

    private void televerserFichier() {
        if (nomFichierCharge.isEmpty() || contenuCharge.isEmpty()) {
            return;
        }

        envoyerFichierParMorceaux(nomFichierCharge, contenuCharge);
        onClose();
    }

    private void televerserAleatoire(){
        this.minecraft.setScreen(new EcranFichierBonbonsAleatoire());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centreX = this.width / 2;
        int debutY = 100;

        // Titre
        guiGraphics.drawCenteredString(this.font, this.title, centreX, 30, 0xFFFFFF);

        // Instructions
        String instructions = "Entrez le chemin complet vers votre fichier de configuration";
        guiGraphics.drawCenteredString(this.font, instructions, centreX, 50, 0xCCCCCC);

        // Étiquette
        guiGraphics.drawString(this.font, "Chemin du fichier:", centreX - 200, debutY - 15, 0xFFFFFF);

        // Indicateur de statut
        if (!nomFichierCharge.isEmpty()) {
            if (nomFichierCharge.startsWith("ERREUR:")) {
                // Message d'erreur en rouge
                guiGraphics.drawCenteredString(this.font, nomFichierCharge, centreX, debutY + 30, 0xFF5555);
            } else {
                // Message de succès en vert
                String statut = "✓ Fichier chargé: " + nomFichierCharge;
                guiGraphics.drawCenteredString(this.font, statut, centreX, debutY + 30, 0x55FF55);
            }
        }

        // Aide au format en bas
        String texteFormat = "Format requis: temps_en_secondes,nombre_bonbons,x,y,z,type (A ou B)";
        guiGraphics.drawCenteredString(this.font, texteFormat, centreX, debutY + 100, 0x888888);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public void envoyerFichierParMorceaux(String nomFichier, String contenu){
        byte[] donnees = contenu.getBytes(StandardCharsets.UTF_8);
        final int TAILLE_MAX_MORCEAU = 1000;
        UUID idTransfert = UUID.randomUUID();
        int nombreTotalMorceaux = (int) Math.ceil((double) donnees.length / TAILLE_MAX_MORCEAU);

        for (int i = 0; i < nombreTotalMorceaux; i++) {
            int debut = i * TAILLE_MAX_MORCEAU;
            int longueur = Math.min(donnees.length - debut, TAILLE_MAX_MORCEAU);
            byte[] donneesMorceau = new byte[longueur];
            System.arraycopy(donnees, debut, donneesMorceau, 0, longueur);

            // Envoyer le nouveau paquet de morceau:
            PaquetTeleversementFichierBonbons paquet = new PaquetTeleversementFichierBonbons(idTransfert, nomFichier, nombreTotalMorceaux, i, donneesMorceau);
            GestionnaireReseau.INSTANCE.sendToServer(paquet);
        }

    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
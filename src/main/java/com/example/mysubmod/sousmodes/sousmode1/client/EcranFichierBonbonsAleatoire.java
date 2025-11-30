package com.example.mysubmod.sousmodes.sousmode1.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EcranFichierBonbonsAleatoire extends Screen {
    private static final int TAILLE_CHAMP = 100;
    private static final int LARGEUR_BOUTON = 80;
    private static final int HAUTEUR_BOUTON = 20;
    private static final int ESPACEMENT = 20;

    private EditBox champFichier;
    private EditBox champGraine;
    private EditBox champTemps;
    private EditBox champDistance;
    private Button boutonCreer;
    private Button boutonRetour;


    public EcranFichierBonbonsAleatoire() {
        super(Component.literal("Générer un fichier aléatoire pour les apparitions des bonbons"));
    }

    @Override
    protected void init() {
        super.init();

        int centreX = this.width / 2;
        int debutY = 80;

        // Nom du fichier
        champFichier = new EditBox(this.font, centreX-TAILLE_CHAMP-ESPACEMENT, debutY, TAILLE_CHAMP, HAUTEUR_BOUTON, Component.literal("Nom du fichier"));
        this.addRenderableWidget(champFichier);

        // Graine
        champGraine = new EditBox(this.font, centreX-TAILLE_CHAMP-ESPACEMENT, debutY+ESPACEMENT*3, TAILLE_CHAMP, HAUTEUR_BOUTON, Component.literal("Graine"));
        this.addRenderableWidget(champGraine);

        // Temps entre apparitions
        champTemps = new EditBox(this.font, centreX+ESPACEMENT, debutY, TAILLE_CHAMP, HAUTEUR_BOUTON, Component.literal("Temps entre apparitions"));
        this.addRenderableWidget(champTemps);

        // Distance entre apparitions
        champDistance = new EditBox(this.font, centreX+ESPACEMENT, debutY+ESPACEMENT*3, TAILLE_CHAMP, HAUTEUR_BOUTON, Component.literal("Distance entre apparitions"));
        this.addRenderableWidget(champDistance);

        // Bouton créer
        boutonCreer = Button.builder(
                        Component.literal("Générer"),
                        button -> creerFichier()
                )
                .bounds(centreX - LARGEUR_BOUTON - ESPACEMENT/2, debutY + HAUTEUR_BOUTON*5, LARGEUR_BOUTON, HAUTEUR_BOUTON)
                .build();
        this.addRenderableWidget(boutonCreer);

        // Bouton retour
        boutonRetour = Button.builder(
                        Component.literal("Retour"),
                        button -> afficherEcranTeleversement()
                )
                .bounds(centreX + ESPACEMENT/2, debutY + HAUTEUR_BOUTON*5, LARGEUR_BOUTON, HAUTEUR_BOUTON)
                .build();
        this.addRenderableWidget(boutonRetour);

    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centreX = this.width / 2;
        int debutY = 80;

        // Titre
        guiGraphics.drawCenteredString(this.font, this.title, centreX, 30, 0xFFFFFF);

        // Instructions
        String instructions = "Entrez les valeurs voulues";
        guiGraphics.drawCenteredString(this.font, instructions, centreX, 50, 0xCCCCCC);

        // Étiquette Nom du fichier
        guiGraphics.drawString(this.font, "Nom du fichier", centreX - TAILLE_CHAMP - ESPACEMENT, debutY - 15, 0xFFFFFF);

        // Étiquette Graine
        guiGraphics.drawString(this.font, "Graine(optionnel)", centreX - TAILLE_CHAMP - ESPACEMENT, debutY - 15 + ESPACEMENT*3, 0xFFFFFF);

        // Étiquette Temps entre apparitions
        guiGraphics.drawString(this.font, "Temps entre apparitions", centreX + ESPACEMENT, debutY - 15, 0xFFFFFF);

        // Étiquette Distance entre apparitions
        guiGraphics.drawString(this.font, "Distance entre apparitions", centreX + ESPACEMENT, debutY - 15 + ESPACEMENT*3, 0xFFFFFF);


        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void afficherEcranTeleversement(){
        this.minecraft.setScreen(new EcranTeleversementFichierBonbons());
    }

    private void creerFichier(){
        // On suppose qu'il y a x îles allant de la plus petite à la plus grande où 1 spawner = plus petite et 2 spawners = île suivante...
        int nombreIles = 4;

        try {
            String fichier = champFichier.getValue();
            int graine;
            Random aleatoire = new Random();
            if(champGraine.getValue().isEmpty()){
                 graine = aleatoire.nextInt();
            }else{
                 graine = Integer.parseInt(champGraine.getValue());
            }
            int temps = Integer.parseInt(champTemps.getValue());
            int distance = Integer.parseInt(champDistance.getValue());
            // Si la distance est supérieure à la distance possible entre le dernier spawn, on le spawn quand même
            if(!(fichier.isEmpty() || (temps < 0 || temps > 900) || distance < 0)){
                aleatoire.setSeed(graine);

                String contenu = """
                # Fichier de configuration d'apparition des bonbons
                """+
                """
                # FICHIER GÉNÉRÉ Graine : \
                """+graine+ ", Distance entre apparitions:" + distance + ", temps entre apparition: " + temps + "\n"+
                """
                # Format: temps_en_secondes,nombre_bonbons,x,y,z
                # Temps: 0-900 secondes (15 minutes max)
                # Nombre de bonbons: 1-100 max
                # Coordonnées: doivent être sur une des 4 îles
                # Y (hauteur): 100-120 (îles à Y=100, +20 pour relief futur)
                # Îles: PETITE(0,-360), MOYENNE(360,0), GRANDE(0,360), TRÈS_GRANDE(-360,0)

                """;

                Path fichierDefaut = Paths.get(System.getProperty("user.home")+"/Downloads/", fichier+".txt");

                int[][] donneesIles = {
                        {0, -360, 30},
                        {360, 0, 45},
                        {0, 360, 60},
                        {-360, 0, 75}
                };

                for(int compteurIle = 0; compteurIle < nombreIles; compteurIle++){
                    contenu += "#Île numéro "+(compteurIle+1)+"\n";

                    int minX = donneesIles[compteurIle][0] - donneesIles[compteurIle][2];
                    int maxX = donneesIles[compteurIle][0] + donneesIles[compteurIle][2];
                    int minZ = donneesIles[compteurIle][1] - donneesIles[compteurIle][2];
                    int maxZ = donneesIles[compteurIle][1] + donneesIles[compteurIle][2];

                    int compteurTemps = temps;
                    List<Point> liste = new ArrayList<>();

                    while(compteurTemps <= 900){

                        for(int compteurSpawner = 0; compteurSpawner <= compteurIle; compteurSpawner++){
                            int x = aleatoire.nextInt(minX, maxX);
                            int z = aleatoire.nextInt(minZ, maxZ);
                            Point point = new Point(x, z);

                            int tentatives = 0;
                            boolean tropProche = true;

                            // Trouver un point qui n'est pas trop proche des points existants
                            while(tropProche && tentatives < 2){
                                tropProche = false;

                                if(compteurSpawner > 0) {
                                    for(Point p : liste){
                                        double dist = Math.sqrt(Math.pow(p.getX()-x, 2) + Math.pow(p.getY()-z, 2));
                                        if(dist < distance){
                                            tropProche = true;
                                            // Générer de nouvelles coordonnées
                                            x = aleatoire.nextInt(minX, maxX);
                                            z = aleatoire.nextInt(minZ, maxZ);
                                            point = new Point(x, z);
                                            tentatives++;
                                            break;
                                        }
                                    }
                                } else {
                                    // Premier spawner
                                    tropProche = false;
                                }
                            }

                            liste.add(point);
                            contenu += compteurTemps+",1,"+x+",101,"+z+"\n";
                            if(compteurSpawner != 0){
                                liste.clear();
                            }
                        }

                        compteurTemps += temps;
                    }
                }
                Files.writeString(fichierDefaut, contenu);

                afficherEcranTeleversement();
            }else{
                throw new Exception();
            }

        }catch(Exception e){
            System.out.println("ERREUR");
            System.out.println(e.getMessage());
            System.out.println(e);
            this.minecraft.player.sendSystemMessage(Component.literal("§cErreur de la génération du fichier " + champFichier.getValue() + ".txt"));
            onClose();
        }

    }
}

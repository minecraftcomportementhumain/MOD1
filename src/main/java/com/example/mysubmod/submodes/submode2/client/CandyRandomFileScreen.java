package com.example.mysubmod.submodes.submode2.client;

import com.example.mysubmod.submodes.submode2.client.CandyFileUploadScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
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

public class CandyRandomFileScreen extends Screen {
    private static final int EDITBOX_SIZE = 100;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 15;

    private EditBox fileBox;
    private EditBox seedBox;
    private EditBox timeBox;
    private EditBox distanceBox;
    private CustomSlider customSlider;
    private Button createButton;
    private Button returnButton;


    public CandyRandomFileScreen() {
        super(Component.literal("Générer un fichier aléatoire pour les spawns des bonbons"));
    }

    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int startY = 80;

        //File name
        fileBox = new EditBox(this.font, centerX-EDITBOX_SIZE-SPACING, startY, EDITBOX_SIZE, BUTTON_HEIGHT, Component.literal("File name"));
        this.addRenderableWidget(fileBox);

        //Seed
        seedBox = new EditBox(this.font, centerX-EDITBOX_SIZE-SPACING, startY+SPACING*3, EDITBOX_SIZE, BUTTON_HEIGHT, Component.literal("Seed"));
        this.addRenderableWidget(seedBox);

        //Time between spawns
        timeBox = new EditBox(this.font, centerX+SPACING, startY, EDITBOX_SIZE, BUTTON_HEIGHT, Component.literal("Time between spawns"));
        this.addRenderableWidget(timeBox);

        //Distance between spawns
        distanceBox = new EditBox(this.font, centerX+SPACING, startY+SPACING*3, EDITBOX_SIZE, BUTTON_HEIGHT, Component.literal("Distance between spawns"));
        this.addRenderableWidget(distanceBox);

        // Add a slider to the screen
        customSlider = this.addRenderableWidget(new CustomSlider(
                centerX+SPACING, // x position (centered)
                startY+SPACING*6, // y position (centered)
                EDITBOX_SIZE, // width
                BUTTON_HEIGHT, // height
                0.5 // initial value (0.0 to 1.0)
        ));

        // Create button
        createButton = Button.builder(
                        Component.literal("Générer"),
                        button -> createFile()
                )
                .bounds(centerX - BUTTON_WIDTH - SPACING/2, startY + BUTTON_HEIGHT*6, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(createButton);

        // Return button
        returnButton = Button.builder(
                        Component.literal("Retour"),
                        button -> setUploadScreen()
                )
                .bounds(centerX + SPACING/2, startY + BUTTON_HEIGHT*6, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(returnButton);

    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int startY = 80;

        // Title
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 30, 0xFFFFFF);

        // Instructions
        String instructions = "Entrez les valeurs voulues";
        guiGraphics.drawCenteredString(this.font, instructions, centerX, 50, 0xCCCCCC);

        // Label File name
        guiGraphics.drawString(this.font, "Nom du fichier", centerX - EDITBOX_SIZE - SPACING, startY - 15, 0xFFFFFF);

        // Label Seed
        guiGraphics.drawString(this.font, "Seed(optionnel)", centerX - EDITBOX_SIZE - SPACING, startY - 15 + SPACING*3, 0xFFFFFF);

        // Label Time between spawns
        guiGraphics.drawString(this.font, "Temps entre spawns", centerX + SPACING, startY - 15, 0xFFFFFF);

        // Label Distance between spawns
        guiGraphics.drawString(this.font, "Distance entre spawns", centerX + SPACING, startY - 15+SPACING*3, 0xFFFFFF);

        // Label Distance between spawns
        guiGraphics.drawString(this.font, "Pourcentage de chance de types(A:B)", centerX + SPACING, startY - 15 + SPACING *6, 0xFFFFFF);


        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void setUploadScreen(){
        this.minecraft.setScreen(new CandyFileUploadScreen());
    }

    private static class CustomSlider extends AbstractSliderButton {

        public CustomSlider(int x, int y, int width, int height, double initialValue) {
            super(x, y, width, height, Component.empty(), initialValue);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            // Update the slider's display text
            int percentageA = (int)(this.value * 100);
            int percentageB = 100-percentageA;
            this.setMessage(Component.literal(percentageA + ":"+percentageB));
        }

        @Override
        protected void applyValue() {
            // This is called when the slider value changes
            // Add your logic here (e.g., update a config value)
            System.out.println("Slider value changed to: " + (int)(this.value*100));
        }

        // Optional: Get the current value
        public int getValue() {
            return (int)(this.value*100);
        }

        // Optional: Set a specific value programmatically
        public void setValue(double value) {
            this.value = Math.max(0.0, Math.min(1.0, value));
            this.updateMessage();
        }
    }

    private void createFile(){
        //Assume that theres x islands and goes smallest to biggest where 1 spawner = smallest and 2 spawners = next island...
        int nbIslands = 4;

        try {
            String file = fileBox.getValue();
            int seed;
            Random random = new Random();
            if(seedBox.getValue().isEmpty()){
                 seed = random.nextInt();
            }else{
                 seed = Integer.parseInt(seedBox.getValue());
            }
            int time = Integer.parseInt(timeBox.getValue());
            int distance = Integer.parseInt(distanceBox.getValue());
            int pourcentage = customSlider.getValue();
            int pourcentageB = 100 - pourcentage;

            //If distance is higher than possible distance between last spawn than just spawn it anyway
            if(!(file.isEmpty() || (time < 0 || time > 900) || distance < 0 || (pourcentage < 0 || pourcentage > 100))){
                random.setSeed(seed);

                String content = """
                # Fichier de configuration d'apparition des bonbons
                """+
                """            
                # FICHIER GÉNÉRER Seed : \
                """+seed+ ", Distance entre spawns: " + distance + ", temps entre spawn: " + time +
                        "Pourcentage de chance de type:  "+ pourcentage + ":" + pourcentageB + "\n" +
                """
                # Format: temps_en_secondes,nombre_bonbons,x,y,z,type
                # Temps: 0-900 secondes (15 minutes max)
                # Nombre de bonbons: 1-100 max
                # Coordonnées: doivent être sur une des 4 îles
                # Y (hauteur): 100-120 (îles à Y=100, +20 pour relief futur)
                # Îles: SMALL(0,-360), MEDIUM(360,0), LARGE(0,360), EXTRA_LARGE(-360,0)
    
                """;

                Path defaultFile = Paths.get(System.getProperty("user.home")+"/Downloads/", file+".txt");

                int[][] islandData = {
                        {0, -360, 30},
                        {360, 0, 45},
                        {0, 360, 60},
                        {-360, 0, 75}
                };

                for(int islandCounter = 0; islandCounter < nbIslands; islandCounter++){
                    content += "#Island number "+(islandCounter+1)+"\n";

                    int minX = islandData[islandCounter][0] - islandData[islandCounter][2];
                    int maxX = islandData[islandCounter][0] + islandData[islandCounter][2];
                    int minZ = islandData[islandCounter][1] - islandData[islandCounter][2];
                    int maxZ = islandData[islandCounter][1] + islandData[islandCounter][2];

                    int timeCounter = time;
                    List<Point> list = new ArrayList<>();

                    while(timeCounter <= 900){

                        for(int spawnerCounter = 0; spawnerCounter <= islandCounter; spawnerCounter++){
                            int x = random.nextInt(minX, maxX);
                            int z = random.nextInt(minZ, maxZ);
                            Point point = new Point(x, z);

                            int attempts = 0;
                            boolean tooClose = true;

                            //Find a point that not too close to existing points
                            while(tooClose && attempts < 2){
                                tooClose = false;

                                if(spawnerCounter > 0) {
                                    for(Point p : list){
                                        double dist = Math.sqrt(Math.pow(p.getX()-x, 2) + Math.pow(p.getY()-z, 2));
                                        if(dist < distance){
                                            tooClose = true;
                                            // Generate new coordinates
                                            x = random.nextInt(minX, maxX);
                                            z = random.nextInt(minZ, maxZ);
                                            point = new Point(x, z);
                                            attempts++;
                                            break;
                                        }
                                    }
                                } else {
                                    // First spawner
                                    tooClose = false;
                                }
                            }

                            list.add(point);
                            char type = 'A';
                            int randomTypeNumber = random.nextInt(0,100);
                            System.out.println(randomTypeNumber +" "+ pourcentage);
                            if(randomTypeNumber > pourcentage){
                                type='B';
                            }
                            content += timeCounter+",1,"+x+",101,"+z+","+type+"\n";
                            if(spawnerCounter != 0){
                                list.clear();
                            }
                        }

                        timeCounter += time;
                    }
                }
                Files.writeString(defaultFile, content);

                setUploadScreen();
            }else{
                throw new Exception();
            }

        }catch(Exception e){
            System.out.println("ERREUR");
            System.out.println(e.getMessage());
            System.out.println(e);
            this.minecraft.player.sendSystemMessage(Component.literal("§cErreur de la génération du fichier " + fileBox.getValue() + ".txt"));
            onClose();
        }

    }
}

package com.example.mysubmod.sousmodes.sousmode3.client;

import com.example.mysubmod.sousmodes.sousmode3.ConfigPartieSousMode3;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Sous-écran du menu N : santé rendue par chaque type de bonbon (standard, Bleu, Rouge),
 * en points de vie (2 points = 1 cœur, 0 = aucun soin). Modifie directement la config
 * vivante de {@link EcranConfigurationPartieSousMode3} et y retourne à la fermeture.
 */
@OnlyIn(Dist.CLIENT)
public class EcranSoinBonbonsSousMode3 extends Screen {

    /** Valeurs proposées, en points de vie (2 = 1 cœur, comportement historique). */
    private static final float[] SOINS_POINTS = {0, 1, 2, 3, 4, 6, 8, 12, 16, 20};

    private final Screen parent;
    private final ConfigPartieSousMode3 config;

    public EcranSoinBonbonsSousMode3(Screen parent, ConfigPartieSousMode3 config) {
        super(Component.literal("Soin des bonbons"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        super.init();
        int largeur = 180;
        int x = (this.width - largeur) / 2;
        int y = this.height / 2 - 44;

        selecteurSoin(x, y, largeur, "Standard: ",
            () -> config.soinBonbonStandard, v -> config.soinBonbonStandard = v);
        selecteurSoin(x, y + 24, largeur, "Bleu: ",
            () -> config.soinBonbonBleu, v -> config.soinBonbonBleu = v);
        selecteurSoin(x, y + 48, largeur, "Rouge: ",
            () -> config.soinBonbonRouge, v -> config.soinBonbonRouge = v);

        addRenderableWidget(Button.builder(Component.literal("Retour"),
                b -> this.minecraft.setScreen(parent))
            .bounds(x, y + 80, largeur, 20).build());
    }

    /** Bouton cyclique (clic = valeur suivante, Maj+clic = précédente), comme le menu N. */
    private void selecteurSoin(int x, int y, int largeur, String prefixe,
                               Supplier<Float> getter, Consumer<Float> setter) {
        addRenderableWidget(Button.builder(Component.literal(prefixe + coeurs(getter.get())), b -> {
            int idx = indexDe(getter.get());
            idx = (idx + (Screen.hasShiftDown() ? SOINS_POINTS.length - 1 : 1)) % SOINS_POINTS.length;
            setter.accept(SOINS_POINTS[idx]);
            b.setMessage(Component.literal(prefixe + coeurs(getter.get())));
        }).bounds(x, y, largeur, 20).build());
    }

    private static int indexDe(float valeur) {
        for (int i = 0; i < SOINS_POINTS.length; i++) {
            if (SOINS_POINTS[i] == valeur) {
                return i;
            }
        }
        return 0;
    }

    /** Formate des points de vie en cœurs (2 points = 1 cœur), sans « .0 » superflu. */
    private static String coeurs(float points) {
        if (points == 0) {
            return "aucun soin";
        }
        double h = points / 2.0;
        return (h == Math.floor(h) ? String.valueOf((int) h) : String.valueOf(h)) + " cœur(s)";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int sourisX, int sourisY, float tickPartiel) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 70, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
            "§7Santé rendue par bonbon consommé (0 = aucun soin)",
            this.width / 2, this.height / 2 - 58, 0xAAAAAA);
        super.render(guiGraphics, sourisX, sourisY, tickPartiel);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

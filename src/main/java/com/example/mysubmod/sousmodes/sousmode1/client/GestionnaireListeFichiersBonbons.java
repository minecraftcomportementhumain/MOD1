package com.example.mysubmod.sousmodes.sousmode1.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class GestionnaireListeFichiersBonbons {
    private static GestionnaireListeFichiersBonbons instance;
    private List<String> fichiersDisponibles = new ArrayList<>();
    private boolean possedeListe = false;

    private GestionnaireListeFichiersBonbons() {}

    public static GestionnaireListeFichiersBonbons obtenirInstance() {
        if (instance == null) {
            instance = new GestionnaireListeFichiersBonbons();
        }
        return instance;
    }

    public void definirListeFichiers(List<String> fichiers) {
        this.fichiersDisponibles = new ArrayList<>(fichiers);
        this.possedeListe = !fichiers.isEmpty();
    }

    public List<String> obtenirListeFichiers() {
        return new ArrayList<>(fichiersDisponibles);
    }

    public boolean possedeListe() {
        return possedeListe;
    }

    public void effacer() {
        this.fichiersDisponibles.clear();
        this.possedeListe = false;
    }
}

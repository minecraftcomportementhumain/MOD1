package com.example.mysubmod.miseajour;

import com.example.mysubmod.MonSubMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration du système de mise à jour automatique.
 *
 * Fichier : config/mysubmod-miseajour.json (créé avec des valeurs par défaut au
 * premier démarrage). Le système est DÉSACTIVÉ par défaut : l'administrateur doit
 * mettre {@code active} à true ET renseigner {@code commandeRelance} (la commande qui
 * relance le serveur, p.ex. « run.bat ») avant qu'il ne fonctionne.
 */
public class ConfigMiseAJour {

    /** Active la vérification/mise à jour automatique. */
    public boolean active = false;
    /** Dépôt GitHub « proprietaire/nom ». */
    public String depot = "minecraftcomportementhumain/MOD1";
    /** Tag de la release à suivre (mobile, réutilisé à chaque build). */
    public String tag = "latest";
    /** Intervalle de sondage en minutes. */
    public int intervalleMinutes = 2;
    /**
     * Commande qui relance le serveur après remplacement du jar (exécutée depuis
     * le dossier du serveur). Exemple Windows : « run.bat ». Vide = pas de relance.
     */
    public String commandeRelance = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path fichierConfig() {
        return FMLPaths.CONFIGDIR.get().resolve("mysubmod-miseajour.json");
    }

    /** Charge la config, en créant le fichier par défaut s'il n'existe pas. */
    public static ConfigMiseAJour charger() {
        Path fichier = fichierConfig();
        if (!Files.exists(fichier)) {
            ConfigMiseAJour defaut = new ConfigMiseAJour();
            defaut.sauvegarder();
            return defaut;
        }
        try (Reader lecteur = Files.newBufferedReader(fichier, StandardCharsets.UTF_8)) {
            JsonObject racine = GSON.fromJson(lecteur, JsonObject.class);
            ConfigMiseAJour config = new ConfigMiseAJour();
            if (racine != null) {
                if (racine.has("active")) config.active = racine.get("active").getAsBoolean();
                if (racine.has("depot")) config.depot = racine.get("depot").getAsString();
                if (racine.has("tag")) config.tag = racine.get("tag").getAsString();
                if (racine.has("intervalleMinutes")) config.intervalleMinutes = Math.max(1, racine.get("intervalleMinutes").getAsInt());
                if (racine.has("commandeRelance")) config.commandeRelance = racine.get("commandeRelance").getAsString();
            }
            return config;
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Échec du chargement de la config de mise à jour, valeurs par défaut utilisées", e);
            return new ConfigMiseAJour();
        }
    }

    public void sauvegarder() {
        try {
            Path fichier = fichierConfig();
            Files.createDirectories(fichier.getParent());
            try (Writer ecrivain = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
                GSON.toJson(this, ecrivain);
            }
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Échec de la sauvegarde de la config de mise à jour", e);
        }
    }
}

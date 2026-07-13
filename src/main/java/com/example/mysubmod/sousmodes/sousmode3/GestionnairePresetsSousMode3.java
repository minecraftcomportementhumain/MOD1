package com.example.mysubmod.sousmodes.sousmode3;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.utilitaire.UtilitaireCheminSecurise;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Presets de conditions de partie du Sous-mode 3 : un admin sauvegarde la config du menu N
 * sous un nom, la recharge plus tard. Stockage serveur (un fichier JSON par preset, partagé
 * entre admins et conservé au redémarrage), à côté du répertoire des cartes.
 *
 * <p>La (dé)sérialisation est réflexive (Gson sur le POJO {@link ConfigPartieSousMode3}) :
 * toute nouvelle option du menu N est incluse sans code supplémentaire ici. Les valeurs sont
 * bornées au chargement (fichier édité à la main, ancien preset).</p>
 */
public class GestionnairePresetsSousMode3 {

    private static final String REPERTOIRE = "presets_sm3";
    /** Longueur max d'un nom de preset (au-delà, l'UI et le stockage refusent). */
    public static final int LONGUEUR_MAX_NOM = 32;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static GestionnairePresetsSousMode3 instance;

    private GestionnairePresetsSousMode3() {
    }

    public static GestionnairePresetsSousMode3 getInstance() {
        if (instance == null) {
            instance = new GestionnairePresetsSousMode3();
        }
        return instance;
    }

    private Path repertoire() {
        Path rep = Paths.get(REPERTOIRE);
        if (!Files.exists(rep)) {
            try {
                Files.createDirectories(rep);
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Échec de la création du répertoire des presets", e);
            }
        }
        return rep;
    }

    /** Vrai si le nom est acceptable pour un preset (sûr et non vide, longueur bornée). */
    public static boolean nomValide(String nom) {
        return nom != null && !nom.isBlank() && nom.length() <= LONGUEUR_MAX_NOM
            && UtilitaireCheminSecurise.nomFichierSur(nom);
    }

    /** Noms des presets disponibles, triés (liste vide en cas d'erreur). Seuls les noms
     *  valides sont listés : un fichier déposé à la main avec un nom trop long ferait
     *  déborder le writeUtf borné du paquet Charger/Supprimer côté client. */
    public List<String> listerPresets() {
        try (var fichiers = Files.list(repertoire())) {
            return fichiers
                .filter(c -> c.toString().endsWith(".json"))
                .map(c -> {
                    String f = c.getFileName().toString();
                    return f.substring(0, f.length() - ".json".length());
                })
                .filter(GestionnairePresetsSousMode3::nomValide)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la liste des presets", e);
            return new ArrayList<>();
        }
    }

    /** Sauvegarde {@code config} sous {@code nom} (écrase un homonyme). Retourne vrai si réussi. */
    public boolean sauvegarder(String nom, ConfigPartieSousMode3 config) {
        if (!nomValide(nom) || config == null) {
            return false;
        }
        Path fichier = UtilitaireCheminSecurise.resoudreConfine(repertoire(), nom + ".json");
        if (fichier == null) {
            return false;
        }
        try {
            Files.writeString(fichier, GSON.toJson(config), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la sauvegarde du preset {}", nom, e);
            return false;
        }
    }

    /** Charge le preset {@code nom} (valeurs bornées), ou null s'il est absent/illisible. */
    public ConfigPartieSousMode3 charger(String nom) {
        Path fichier = UtilitaireCheminSecurise.resoudreConfine(repertoire(), nom + ".json");
        if (fichier == null || !Files.exists(fichier)) {
            return null;
        }
        try {
            ConfigPartieSousMode3 config = GSON.fromJson(
                Files.readString(fichier, StandardCharsets.UTF_8), ConfigPartieSousMode3.class);
            if (config == null) {
                return null;
            }
            config.borner();
            return config;
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Échec du chargement du preset {}", nom, e);
            return null;
        }
    }

    /** Supprime le preset {@code nom}. Retourne vrai s'il a été retiré. */
    public boolean supprimer(String nom) {
        Path fichier = UtilitaireCheminSecurise.resoudreConfine(repertoire(), nom + ".json");
        if (fichier == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(fichier);
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la suppression du preset {}", nom, e);
            return false;
        }
    }
}

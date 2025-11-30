package com.example.mysubmod.authentification;

import com.example.mysubmod.MonSubMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Stockage centralisé des identifiants partagé entre GestionnaireAuthAdmin et GestionnaireAuth
 * Cela assure que les deux gestionnaires travaillent toujours avec les mêmes données en mémoire
 */
public class StockageIdentifiants {
    private static StockageIdentifiants instance;
    private final File fichierIdentifiants;
    private final Gson gson;
    private JsonObject identifiants;

    private StockageIdentifiants() {
        this.fichierIdentifiants = new File("identifiants_auth.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        chargerIdentifiants();
    }

    public static StockageIdentifiants getInstance() {
        if (instance == null) {
            instance = new StockageIdentifiants();
        }
        return instance;
    }

    private void chargerIdentifiants() {
        if (!fichierIdentifiants.exists()) {
            creerIdentifiantsParDefaut();
            return;
        }

        try (Reader reader = new FileReader(fichierIdentifiants, StandardCharsets.UTF_8)) {
            identifiants = gson.fromJson(reader, JsonObject.class);
            if (identifiants == null) {
                identifiants = new JsonObject();
            }
            assurerToutesSections();
            MonSubMod.JOURNALISEUR.info("Identifiants chargés depuis identifiants_auth.json");
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec du chargement des identifiants", e);
            creerIdentifiantsParDefaut();
        }
    }

    private void assurerToutesSections() {
        if (!identifiants.has("admins")) {
            identifiants.add("admins", new JsonObject());
        }
        if (!identifiants.has("protected_players")) {
            identifiants.add("protected_players", new JsonObject());
        }
        if (!identifiants.has("blacklist")) {
            identifiants.add("blacklist", new JsonObject());
        }
        if (!identifiants.has("ipBlacklist")) {
            identifiants.add("ipBlacklist", new JsonObject());
        }
        if (!identifiants.has("account_blacklist")) {
            identifiants.add("account_blacklist", new JsonObject());
        }
        if (!identifiants.has("ip_blacklist")) {
            identifiants.add("ip_blacklist", new JsonObject());
        }
    }

    private void creerIdentifiantsParDefaut() {
        identifiants = new JsonObject();
        identifiants.add("admins", new JsonObject());
        identifiants.add("protected_players", new JsonObject());
        identifiants.add("blacklist", new JsonObject());
        identifiants.add("ipBlacklist", new JsonObject());
        identifiants.add("account_blacklist", new JsonObject());
        identifiants.add("ip_blacklist", new JsonObject());
        sauvegarder();
        MonSubMod.JOURNALISEUR.info("Créé identifiants_auth.json par défaut");
    }

    /**
     * Obtient l'objet d'identifiants partagé
     */
    public JsonObject obtenirIdentifiants() {
        return identifiants;
    }

    /**
     * Sauvegarde les identifiants sur le disque
     */
    public synchronized void sauvegarder() {
        try (Writer writer = new FileWriter(fichierIdentifiants, StandardCharsets.UTF_8)) {
            gson.toJson(identifiants, writer);
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la sauvegarde des identifiants", e);
        }
    }

    /**
     * Recharge les identifiants depuis le disque (pour rafraîchissement manuel si nécessaire)
     */
    public synchronized void recharger() {
        chargerIdentifiants();
    }
}

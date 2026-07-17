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
        } catch (IOException | com.google.gson.JsonParseException e) {
            // JsonParseException (non vérifiée) survient sur un JSON malformé — exactement la
            // corruption qu'une écriture interrompue peut produire. Sans ce catch, elle
            // s'échappait de chargerIdentifiants/getInstance et tuait l'initialisation de toute
            // la couche d'auth au démarrage. On sauvegarde le fichier fautif pour diagnostic,
            // puis on repart sur des identifiants par défaut afin que le serveur démarre.
            MonSubMod.JOURNALISEUR.error("Échec du chargement des identifiants (illisible ou corrompu) — recréation par défaut", e);
            sauvegarderFichierCorrompu();
            creerIdentifiantsParDefaut();
        }
    }

    /**
     * Copie best-effort du fichier d'identifiants corrompu/illisible vers un « .corrompu »
     * horodaté, avant qu'il soit écrasé par les défauts, pour permettre une récupération manuelle.
     */
    private void sauvegarderFichierCorrompu() {
        try {
            if (fichierIdentifiants.exists()) {
                File sauvegarde = new File(fichierIdentifiants.getAbsolutePath()
                    + ".corrompu-" + System.currentTimeMillis());
                java.nio.file.Files.copy(fichierIdentifiants.toPath(), sauvegarde.toPath());
                MonSubMod.JOURNALISEUR.warn("Fichier d'identifiants fautif sauvegardé sous {}", sauvegarde.getName());
            }
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.warn("Impossible de sauvegarder le fichier d'identifiants fautif : {}", e.toString());
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
    }

    private void creerIdentifiantsParDefaut() {
        identifiants = new JsonObject();
        identifiants.add("admins", new JsonObject());
        identifiants.add("protected_players", new JsonObject());
        identifiants.add("blacklist", new JsonObject());
        identifiants.add("ipBlacklist", new JsonObject());
        identifiants.add("account_blacklist", new JsonObject());
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
        // Écriture atomique : on écrit dans un fichier temporaire, puis on le déplace par-dessus
        // la cible. identifiants_auth.json n'est ainsi jamais laissé à moitié écrit si la JVM
        // s'arrête en cours de route (l'ancien FileWriter en place produisait, lui, un JSON
        // tronqué illisible au redémarrage). Même idiome que GestionnaireMiseAJour.ecrireEtat.
        File fichierTemporaire = new File(fichierIdentifiants.getAbsolutePath() + ".tmp");
        try (Writer writer = new FileWriter(fichierTemporaire, StandardCharsets.UTF_8)) {
            gson.toJson(identifiants, writer);
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la sauvegarde des identifiants (écriture temporaire)", e);
            return;
        }
        try {
            try {
                java.nio.file.Files.move(fichierTemporaire.toPath(), fichierIdentifiants.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // Système de fichiers sans déplacement atomique : repli non atomique.
                java.nio.file.Files.move(fichierTemporaire.toPath(), fichierIdentifiants.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec du remplacement atomique des identifiants", e);
            return;
        }
        restreindrePermissions();
    }

    /**
     * Restreint l'accès au fichier d'identifiants au seul propriétaire (best-effort,
     * multiplateforme). Contient des hachages de mots de passe et des adresses IP.
     */
    private void restreindrePermissions() {
        try {
            fichierIdentifiants.setReadable(false, false);
            fichierIdentifiants.setReadable(true, true);
            fichierIdentifiants.setWritable(false, false);
            fichierIdentifiants.setWritable(true, true);
        } catch (SecurityException e) {
            // Best-effort : ignorer si le gestionnaire de sécurité l'interdit
        }
    }

    /**
     * Recharge les identifiants depuis le disque (pour rafraîchissement manuel si nécessaire)
     */
    public synchronized void recharger() {
        chargerIdentifiants();
    }
}

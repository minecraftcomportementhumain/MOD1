package com.example.mysubmod.auth;

import com.example.mysubmod.MySubMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Centralized credentials storage shared between AdminAuthManager and AuthManager
 * This ensures both managers always work with the same data in memory
 */
public class CredentialsStore {
    private static CredentialsStore instance;
    private final File credentialsFile;
    private final Gson gson;
    private JsonObject credentials;

    private CredentialsStore() {
        this.credentialsFile = new File("auth_credentials.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadCredentials();
    }

    public static CredentialsStore getInstance() {
        if (instance == null) {
            instance = new CredentialsStore();
        }
        return instance;
    }

    private void loadCredentials() {
        if (!credentialsFile.exists()) {
            createDefaultCredentials();
            return;
        }

        try (Reader reader = new FileReader(credentialsFile, StandardCharsets.UTF_8)) {
            credentials = gson.fromJson(reader, JsonObject.class);
            if (credentials == null) {
                credentials = new JsonObject();
            }
            ensureAllSections();
            MySubMod.LOGGER.info("Loaded credentials from auth_credentials.json");
        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to load credentials", e);
            createDefaultCredentials();
        }
    }

    private void ensureAllSections() {
        if (!credentials.has("admins")) {
            credentials.add("admins", new JsonObject());
        }
        if (!credentials.has("protected_players")) {
            credentials.add("protected_players", new JsonObject());
        }
        if (!credentials.has("blacklist")) {
            credentials.add("blacklist", new JsonObject());
        }
        if (!credentials.has("ipBlacklist")) {
            credentials.add("ipBlacklist", new JsonObject());
        }
        if (!credentials.has("account_blacklist")) {
            credentials.add("account_blacklist", new JsonObject());
        }
        if (!credentials.has("ip_blacklist")) {
            credentials.add("ip_blacklist", new JsonObject());
        }
    }

    private void createDefaultCredentials() {
        credentials = new JsonObject();
        credentials.add("admins", new JsonObject());
        credentials.add("protected_players", new JsonObject());
        credentials.add("blacklist", new JsonObject());
        credentials.add("ipBlacklist", new JsonObject());
        credentials.add("account_blacklist", new JsonObject());
        credentials.add("ip_blacklist", new JsonObject());
        save();
        MySubMod.LOGGER.info("Created default auth_credentials.json");
    }

    /**
     * Get the shared credentials object
     */
    public JsonObject getCredentials() {
        return credentials;
    }

    /**
     * Save credentials to disk
     */
    public synchronized void save() {
        try (Writer writer = new FileWriter(credentialsFile, StandardCharsets.UTF_8)) {
            gson.toJson(credentials, writer);
        } catch (IOException e) {
            MySubMod.LOGGER.error("Failed to save credentials", e);
        }
    }

    /**
     * Reload credentials from disk (for manual refresh if needed)
     */
    public synchronized void reload() {
        loadCredentials();
    }
}

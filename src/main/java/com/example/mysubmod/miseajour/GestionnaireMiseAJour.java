package com.example.mysubmod.miseajour;

import com.example.mysubmod.MonSubMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mise à jour automatique du mod depuis une release GitHub.
 *
 * Toutes les {@code intervalleMinutes} minutes, interroge l'API GitHub pour la release
 * suivie. Si l'asset .jar a changé (identifiant différent de celui déjà appliqué), il est
 * téléchargé dans un dossier de staging, puis un script « updater.bat » est lancé de façon
 * détachée et le serveur s'arrête. Le script attend la fermeture de la JVM (libération du
 * verrou de fichier), sauvegarde l'ancien jar, met le nouveau en place et relance le serveur.
 *
 * Garde-fou : le mod écrit un drapeau {@code boot-ok.flag} au démarrage réussi. Le script
 * attend ce drapeau ; s'il n'apparaît pas dans le délai imparti (nouveau jar défaillant),
 * il restaure l'ancien jar (rollback) et marque la release comme mauvaise pour ne pas boucler.
 */
public class GestionnaireMiseAJour {

    private static GestionnaireMiseAJour instance;

    private final ScheduledExecutorService ordonnanceur =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MonSubMod-MiseAJour");
            t.setDaemon(true);
            return t;
        });

    private final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private MinecraftServer serveur;
    private boolean demarre = false;
    private boolean avertissementConfigEmis = false;

    private GestionnaireMiseAJour() {
    }

    public static synchronized GestionnaireMiseAJour getInstance() {
        if (instance == null) {
            instance = new GestionnaireMiseAJour();
        }
        return instance;
    }

    // ==================== Cycle de vie ====================

    /** Appelé au démarrage du serveur : confirme une mise à jour en attente, écrit le
     *  drapeau de démarrage réussi, puis démarre le sondage périodique. */
    public synchronized void auDemarrage(MinecraftServer serveur) {
        this.serveur = serveur;

        // Uniquement sur serveur dédié : jamais côté client (partie solo intégrée)
        if (!serveur.isDedicatedServer()) {
            return;
        }

        Path jar = localiserJarActuel();
        if (jar != null) {
            confirmerMiseAJourEnAttente(jar);
            ecrireDrapeauDemarrage(jar);
        }

        ConfigMiseAJour config = ConfigMiseAJour.charger();
        if (!config.active) {
            MonSubMod.JOURNALISEUR.info("Mise à jour automatique désactivée (config/mysubmod-miseajour.json : active=false)");
            return;
        }
        if (jar == null) {
            MonSubMod.JOURNALISEUR.warn("Mise à jour automatique inactive : le mod n'est pas chargé depuis un .jar (environnement de développement ?)");
            return;
        }

        if (!demarre) {
            demarre = true;
            long periode = Math.max(1, config.intervalleMinutes);
            ordonnanceur.scheduleAtFixedRate(this::verifierSansThrow, 1, periode * 60L, TimeUnit.SECONDS);
            MonSubMod.JOURNALISEUR.info("Mise à jour automatique active (dépôt {}, tag {}, toutes les {} min)",
                config.depot, config.tag, periode);
        }
    }

    public synchronized void arreter() {
        // Ne pas couper l'ordonnanceur (daemon) brutalement pendant un arrêt normal ;
        // il s'éteindra avec la JVM. Rien à faire ici pour l'instant.
    }

    private void verifierSansThrow() {
        try {
            verifier();
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.warn("Échec de la vérification de mise à jour : {}", e.toString());
        }
    }

    // ==================== Vérification / téléchargement ====================

    private void verifier() throws Exception {
        ConfigMiseAJour config = ConfigMiseAJour.charger();
        if (!config.active) {
            return;
        }
        if (config.commandeRelance == null || config.commandeRelance.isBlank()) {
            if (!avertissementConfigEmis) {
                MonSubMod.JOURNALISEUR.warn("Mise à jour : « commandeRelance » n'est pas renseignée dans la config — mise à jour désactivée tant que le serveur ne sait pas comment se relancer.");
                avertissementConfigEmis = true;
            }
            return;
        }

        Path jarActuel = localiserJarActuel();
        if (jarActuel == null) {
            return;
        }
        Path dossierMaj = dossierMaj(jarActuel);
        Files.createDirectories(dossierMaj);

        // Interroger l'API GitHub
        String api = "https://api.github.com/repos/" + config.depot + "/releases/tags/" + config.tag;
        HttpRequest requete = HttpRequest.newBuilder(URI.create(api))
            .header("User-Agent", "MonSubMod-MiseAJour")
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(20))
            .GET().build();
        HttpResponse<String> reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
        if (reponse.statusCode() != 200) {
            MonSubMod.JOURNALISEUR.warn("Mise à jour : réponse GitHub {} pour {}", reponse.statusCode(), api);
            return;
        }

        JsonObject release = JsonParser.parseString(reponse.body()).getAsJsonObject();
        JsonArray assets = release.has("assets") ? release.getAsJsonArray("assets") : new JsonArray();
        String nomJarActuel = jarActuel.getFileName().toString().toLowerCase();
        JsonObject assetJar = null;
        JsonObject secours = null;
        for (int i = 0; i < assets.size(); i++) {
            JsonObject a = assets.get(i).getAsJsonObject();
            String nom = (a.has("name") ? a.get("name").getAsString() : "").toLowerCase();
            if (!nom.endsWith(".jar")) {
                continue;
            }
            // Ignorer les jars secondaires (sources / dev)
            if (nom.contains("-sources") || nom.contains("-dev") || nom.contains("-slim")) {
                continue;
            }
            if (nom.equals(nomJarActuel)) {
                assetJar = a; // correspondance exacte du nom de fichier : priorité
                break;
            }
            if (secours == null) {
                secours = a;
            }
        }
        if (assetJar == null) {
            assetJar = secours;
        }
        if (assetJar == null) {
            MonSubMod.JOURNALISEUR.warn("Mise à jour : aucun asset .jar dans la release {} de {}", config.tag, config.depot);
            return;
        }

        String idAsset = assetJar.get("id").getAsString();
        String nomAsset = assetJar.get("name").getAsString();
        String urlTelechargement = assetJar.get("browser_download_url").getAsString();
        long taille = assetJar.has("size") ? assetJar.get("size").getAsLong() : -1;

        // Déjà appliqué ? Marqué comme mauvais ?
        if (idAsset.equals(lireEtat(dossierMaj))) {
            return; // à jour
        }
        if (Files.exists(dossierMaj.resolve("mauvais-" + idAsset + ".marker"))) {
            return; // release défaillante, on ne réessaie pas
        }

        MonSubMod.JOURNALISEUR.info("Mise à jour : nouvelle version détectée (asset {}, {}), téléchargement…", idAsset, nomAsset);

        // Télécharger dans le staging
        Path staging = dossierMaj.resolve("staging");
        Files.createDirectories(staging);
        Path cible = staging.resolve(jarActuel.getFileName().toString());
        HttpRequest reqDl = HttpRequest.newBuilder(URI.create(urlTelechargement))
            .header("User-Agent", "MonSubMod-MiseAJour")
            .timeout(Duration.ofMinutes(5))
            .GET().build();
        HttpResponse<Path> repDl = client.send(reqDl,
            HttpResponse.BodyHandlers.ofFile(cible, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE));
        if (repDl.statusCode() != 200) {
            MonSubMod.JOURNALISEUR.warn("Mise à jour : échec du téléchargement (HTTP {})", repDl.statusCode());
            return;
        }
        if (taille > 0 && Files.size(cible) != taille) {
            MonSubMod.JOURNALISEUR.warn("Mise à jour : taille téléchargée ({}) ≠ attendue ({}), abandon", Files.size(cible), taille);
            Files.deleteIfExists(cible);
            return;
        }

        // Enregistrer la mise à jour en attente et lancer le script de remplacement
        ecrireEnAttente(dossierMaj, idAsset);
        lancerUpdaterEtArreter(config, jarActuel, cible, dossierMaj, idAsset);
    }

    // ==================== Application (script externe + arrêt) ====================

    private void lancerUpdaterEtArreter(ConfigMiseAJour config, Path jarActuel, Path staging,
                                        Path dossierMaj, String idAsset) throws Exception {
        Path backupDir = dossierMaj.resolve("backup");
        Files.createDirectories(backupDir);
        Path backup = backupDir.resolve(jarActuel.getFileName().toString() + ".bak");
        Path drapeauBoot = dossierMaj.resolve("boot-ok.flag");
        Path mauvaisMarker = dossierMaj.resolve("mauvais-" + idAsset + ".marker");
        Path bat = dossierMaj.resolve("updater.bat");
        String dossierServeur = System.getProperty("user.dir");

        String contenuBat = genererBat(jarActuel, staging, backup, drapeauBoot, mauvaisMarker,
            dossierServeur, config.commandeRelance);
        Files.writeString(bat, contenuBat, StandardCharsets.UTF_8);

        MonSubMod.JOURNALISEUR.warn("Mise à jour : nouveau build prêt. Lancement du script de remplacement et arrêt du serveur pour appliquer…");

        // Lancer le .bat dans une nouvelle console indépendante (survit à l'arrêt de la JVM)
        new ProcessBuilder("cmd", "/c", "start", "\"MAJ MonSubMod\"", "/min",
            "cmd", "/c", bat.toAbsolutePath().toString())
            .directory(new java.io.File(dossierServeur))
            .start();

        // Arrêter proprement le serveur sur le thread serveur
        if (serveur != null) {
            serveur.execute(() -> serveur.halt(false));
        }
    }

    private static String genererBat(Path oldJar, Path newJar, Path backup, Path drapeauBoot,
                                     Path mauvaisMarker, String dossierServeur, String commandeRelance) {
        // Chemins entre guillemets ; %% échappé pour le fichier .bat final.
        return "@echo off\r\n"
            + "setlocal\r\n"
            + "set \"OLD=" + oldJar.toAbsolutePath() + "\"\r\n"
            + "set \"NEW=" + newJar.toAbsolutePath() + "\"\r\n"
            + "set \"BACKUP=" + backup.toAbsolutePath() + "\"\r\n"
            + "set \"BOOT=" + drapeauBoot.toAbsolutePath() + "\"\r\n"
            + "set \"BADMARK=" + mauvaisMarker.toAbsolutePath() + "\"\r\n"
            + "set \"SRV=" + dossierServeur + "\"\r\n"
            + "echo [MAJ] Attente de la fermeture du serveur (liberation du jar)...\r\n"
            + ":waitlock\r\n"
            + "( call ) 1>>\"%OLD%\" 2>nul && goto libre || ( ping -n 3 127.0.0.1 >nul & goto waitlock )\r\n"
            + ":libre\r\n"
            + "echo [MAJ] Sauvegarde de l'ancien jar...\r\n"
            + "copy /y \"%OLD%\" \"%BACKUP%\" >nul\r\n"
            + "echo [MAJ] Remplacement par le nouveau build...\r\n"
            + "move /y \"%NEW%\" \"%OLD%\" >nul\r\n"
            + "if errorlevel 1 ( echo [MAJ] Echec du remplacement, restauration. & copy /y \"%BACKUP%\" \"%OLD%\" >nul )\r\n"
            + "del \"%BOOT%\" >nul 2>&1\r\n"
            + "echo [MAJ] Relance du serveur...\r\n"
            + "cd /d \"%SRV%\"\r\n"
            + "start \"\" " + commandeRelance + "\r\n"
            + "echo [MAJ] Attente de la confirmation de demarrage (max 5 min)...\r\n"
            + "set /a n=0\r\n"
            + ":waitboot\r\n"
            + "if exist \"%BOOT%\" goto ok\r\n"
            + "timeout /t 5 /nobreak >nul\r\n"
            + "set /a n+=1\r\n"
            + "if %n% lss 60 goto waitboot\r\n"
            + "echo [MAJ] Pas de confirmation - rollback vers l'ancien jar.\r\n"
            + "set /a m=0\r\n"
            + ":waitlock2\r\n"
            + "( call ) 1>>\"%OLD%\" 2>nul && goto rollback || ( ping -n 3 127.0.0.1 >nul & set /a m+=1 & if %m% lss 40 goto waitlock2 )\r\n"
            + "echo [MAJ] Verrou toujours actif : le serveur tourne probablement, rollback annule.\r\n"
            + "goto fin\r\n"
            + ":rollback\r\n"
            + "copy /y \"%BACKUP%\" \"%OLD%\" >nul\r\n"
            + "echo mauvais> \"%BADMARK%\"\r\n"
            + "cd /d \"%SRV%\"\r\n"
            + "start \"\" " + commandeRelance + "\r\n"
            + "echo [MAJ] Ancien jar restaure et serveur relance.\r\n"
            + "goto fin\r\n"
            + ":ok\r\n"
            + "echo [MAJ] Mise a jour appliquee avec succes.\r\n"
            + ":fin\r\n"
            + "endlocal\r\n";
    }

    // ==================== État / drapeaux ====================

    private void confirmerMiseAJourEnAttente(Path jarActuel) {
        try {
            Path dossierMaj = dossierMaj(jarActuel);
            Path enAttente = dossierMaj.resolve("pending.txt");
            if (Files.exists(enAttente)) {
                String idAsset = Files.readString(enAttente, StandardCharsets.UTF_8).trim();
                if (!idAsset.isEmpty()) {
                    ecrireEtat(dossierMaj, idAsset);
                    MonSubMod.JOURNALISEUR.info("Mise à jour appliquée et confirmée (asset {})", idAsset);
                }
                Files.deleteIfExists(enAttente);
            }
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.warn("Mise à jour : impossible de confirmer l'état en attente : {}", e.toString());
        }
    }

    private void ecrireDrapeauDemarrage(Path jarActuel) {
        try {
            Path dossierMaj = dossierMaj(jarActuel);
            Files.createDirectories(dossierMaj);
            Files.writeString(dossierMaj.resolve("boot-ok.flag"),
                Long.toString(System.nanoTime()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.warn("Mise à jour : impossible d'écrire le drapeau de démarrage : {}", e.toString());
        }
    }

    private void ecrireEnAttente(Path dossierMaj, String idAsset) throws Exception {
        Files.writeString(dossierMaj.resolve("pending.txt"), idAsset, StandardCharsets.UTF_8);
    }

    private String lireEtat(Path dossierMaj) {
        try {
            Path etat = dossierMaj.resolve("state.txt");
            if (Files.exists(etat)) {
                return Files.readString(etat, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception ignore) {
        }
        return "";
    }

    private void ecrireEtat(Path dossierMaj, String idAsset) throws Exception {
        Files.createDirectories(dossierMaj);
        Files.move(
            Files.writeString(dossierMaj.resolve("state.tmp"), idAsset, StandardCharsets.UTF_8),
            dossierMaj.resolve("state.txt"),
            StandardCopyOption.REPLACE_EXISTING);
    }

    // ==================== Utilitaires ====================

    private static Path dossierMaj(Path jarActuel) {
        return jarActuel.getParent().resolve(".updates");
    }

    /** Emplacement du .jar chargé, ou null si le mod ne tourne pas depuis un .jar. */
    private Path localiserJarActuel() {
        // Méthode fiable sous Forge : le chemin du fichier du mod via ModList
        // (CodeSource.getLocation() n'est pas fiable avec le classloader modulaire de Forge).
        try {
            net.minecraftforge.forgespi.language.IModFileInfo info =
                net.minecraftforge.fml.ModList.get().getModFileById(MonSubMod.ID_MOD);
            if (info != null && info.getFile() != null) {
                Path chemin = info.getFile().getFilePath();
                if (estJarExistant(chemin)) {
                    return chemin;
                }
            }
        } catch (Throwable t) {
            MonSubMod.JOURNALISEUR.warn("Mise à jour : ModList indisponible ({}), repli sur CodeSource", t.toString());
        }
        // Repli : emplacement de la classe (utile hors du classloader modulaire).
        try {
            URI uri = GestionnaireMiseAJour.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path chemin = Path.of(uri);
            if (estJarExistant(chemin)) {
                return chemin;
            }
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.warn("Mise à jour : localisation du jar impossible : {}", e.toString());
        }
        return null;
    }

    private static boolean estJarExistant(Path chemin) {
        return chemin != null
            && chemin.getFileName() != null
            && chemin.getFileName().toString().toLowerCase().endsWith(".jar")
            && Files.exists(chemin);
    }
}

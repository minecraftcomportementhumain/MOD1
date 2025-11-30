package com.example.mysubmod.sousmodes.sousmode2.donnees;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetTeleversementFichierBonbons;
import com.example.mysubmod.sousmodes.sousmode2.TypeRessource;
import net.minecraft.core.BlockPos;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gestion des fichiers de configuration de spawn pour le Sous-mode 2
 * Format: temps,nombre,x,y,z,type
 * où type = A ou B
 */
public class GestionnaireFichiersApparitionBonbons {
    private static final String REPERTOIRE_APPARITION_BONBONS = "configs_apparition_bonbons_sousmode2";
    private static GestionnaireFichiersApparitionBonbons instance;
    private static final Map<UUID, ConcurrentHashMap<Integer, byte[]>> pendingUploads = new ConcurrentHashMap<>();

    private GestionnaireFichiersApparitionBonbons() {}

    public static GestionnaireFichiersApparitionBonbons getInstance() {
        if (instance == null) {
            instance = new GestionnaireFichiersApparitionBonbons();
        }
        return instance;
    }

    public void assurerRepertoireExiste() {
        Path configDir = Paths.get(REPERTOIRE_APPARITION_BONBONS);
        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
                creerFichierParDefaut();
                MonSubMod.JOURNALISEUR.info("Répertoire de configs d'apparition des bonbons Sous-mode 2 créé: {}", configDir.toAbsolutePath());
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Échec de création du répertoire de configs d'apparition des bonbons Sous-mode 2", e);
            }
        }
    }

    private void creerFichierParDefaut() {
        String defaultContent = """
            # Fichier de configuration d'apparition des bonbons - SOUS-MODE 2
            # Format: temps_en_secondes,nombre_bonbons,x,y,z,type
            # Temps: 0-900 secondes (15 minutes max)
            # Nombre de bonbons: 1-100 max
            # Coordonnées: doivent être sur une des 4 îles
            # Y (hauteur): 100-120 (îles à Y=100, +20 pour relief futur)
            # Type: A ou B (Bonbon Bleu ou Bonbon Rouge)
            # Îles: SMALL(0,-360), MEDIUM(360,0), LARGE(0,360), EXTRA_LARGE(-360,0)

            # Exemple: Mix de Type A et B sur différentes îles
            60,5,0,101,-360,A
            60,5,360,101,0,B
            120,3,0,101,360,A
            120,3,-360,101,0,B
            180,2,0,101,-360,B
            180,2,360,101,0,A
            240,4,0,101,360,B
            240,4,-360,101,0,A
            300,10,0,101,-360,A
            300,10,360,101,0,B
            360,8,0,101,360,A
            360,8,-360,101,0,B
            420,15,0,101,-360,B
            420,15,360,101,0,A
            480,12,0,101,360,B
            480,12,-360,101,0,A
            """;

        try {
            Path defaultFile = Paths.get(REPERTOIRE_APPARITION_BONBONS, "default.txt");
            Files.writeString(defaultFile, defaultContent);
            MonSubMod.JOURNALISEUR.info("Fichier de config d'apparition des bonbons Sous-mode 2 par défaut créé");
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de création du fichier de config d'apparition des bonbons Sous-mode 2 par défaut", e);
        }
    }

    public List<String> obtenirFichiersDisponibles() {
        try {
            Path configDir = Paths.get(REPERTOIRE_APPARITION_BONBONS);
            if (!Files.exists(configDir)) {
                assurerRepertoireExiste();
            }

            return Files.list(configDir)
                .filter(chemin -> chemin.toString().endsWith(".txt"))
                .map(chemin -> chemin.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de listage des fichiers de config d'apparition des bonbons Sous-mode 2", e);
            return new ArrayList<>();
        }
    }

    public List<EntreeApparitionBonbon> chargerConfigApparition(String cheminOuNomFichier) {
        List<EntreeApparitionBonbon> entrees = new ArrayList<>();

        Path fichierConfig;
        if (cheminOuNomFichier.contains(File.separator) || cheminOuNomFichier.contains("/")) {
            fichierConfig = Paths.get(cheminOuNomFichier);
        } else {
            fichierConfig = Paths.get(REPERTOIRE_APPARITION_BONBONS, cheminOuNomFichier);
        }

        if (!Files.exists(fichierConfig)) {
            MonSubMod.JOURNALISEUR.error("Fichier de config d'apparition Sous-mode 2 non trouvé: {}", cheminOuNomFichier);
            return entrees;
        }

        List<String> lignesModifiees = new ArrayList<>();
        boolean aLignesInvalides = false;

        try (BufferedReader lecteur = Files.newBufferedReader(fichierConfig)) {
            String ligne;
            int numeroLigne = 0;

            while ((ligne = lecteur.readLine()) != null) {
                numeroLigne++;
                String ligneTrimee = ligne.trim();

                if (ligneTrimee.isEmpty() || ligneTrimee.startsWith("#")) {
                    lignesModifiees.add(ligne);
                    continue;
                }

                try {
                    EntreeApparitionBonbon entree = analyserLigne(ligneTrimee);
                    if (entree != null) {
                        entrees.add(entree);
                        lignesModifiees.add(ligne);
                    }
                } catch (Exception e) {
                    MonSubMod.JOURNALISEUR.warn("Ligne invalide {} dans {}: {} ({})", numeroLigne, cheminOuNomFichier, ligneTrimee, e.getMessage());
                    lignesModifiees.add("# INVALIDE: " + ligne + " # Erreur: " + e.getMessage());
                    aLignesInvalides = true;
                }
            }

            MonSubMod.JOURNALISEUR.info("Chargé {} entrées d'apparition Sous-mode 2 depuis {}", entrees.size(), cheminOuNomFichier);

            if (aLignesInvalides) {
                try {
                    Files.write(fichierConfig, lignesModifiees);
                    MonSubMod.JOURNALISEUR.info("Lignes invalides commentées dans {}", cheminOuNomFichier);
                } catch (IOException erreurEcriture) {
                    MonSubMod.JOURNALISEUR.error("Échec de mise à jour du fichier avec les lignes commentées: {}", cheminOuNomFichier, erreurEcriture);
                }
            }

        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de lecture du fichier de config d'apparition Sous-mode 2: {}", cheminOuNomFichier, e);
        }

        return entrees;
    }

    private EntreeApparitionBonbon analyserLigne(String ligne) {
        String[] parties = ligne.split(",");
        if (parties.length != 6) {
            throw new IllegalArgumentException("Format attendu: temps,nombre,x,y,z,type (A ou B)");
        }

        int tempsSecondes = Integer.parseInt(parties[0].trim());
        int nombreBonbons = Integer.parseInt(parties[1].trim());
        int x = Integer.parseInt(parties[2].trim());
        int y = Integer.parseInt(parties[3].trim());
        int z = Integer.parseInt(parties[4].trim());
        String chaineType = parties[5].trim();

        // Valider la plage de temps (0-900 secondes = 15 minutes)
        if (tempsSecondes < 0 || tempsSecondes > 900) {
            throw new IllegalArgumentException("Le temps doit être entre 0 et 900 secondes");
        }

        // Valider le nombre de bonbons (1-100)
        if (nombreBonbons <= 0 || nombreBonbons > 100) {
            throw new IllegalArgumentException("Le nombre de bonbons doit être entre 1 et 100");
        }

        // Valider la coordonnée Y (hauteur)
        if (y < 100 || y > 120) {
            throw new IllegalArgumentException("La coordonnée Y doit être entre 100 et 120");
        }

        // Valider le type de ressource
        TypeRessource type = TypeRessource.depuisChaine(chaineType);
        if (type == null) {
            throw new IllegalArgumentException("Le type doit être A ou B, reçu: " + chaineType);
        }

        BlockPos position = new BlockPos(x, y, z);

        // Valider que les coordonnées sont sur l'une des 4 îles
        if (!estPositionSurIle(position)) {
            throw new IllegalArgumentException(String.format(
                "La position (%d,%d,%d) n'est sur aucune île", x, y, z));
        }

        return new EntreeApparitionBonbon(tempsSecondes, nombreBonbons, position, type);
    }

    private boolean estPositionSurIle(BlockPos pos) {
        // Island centers and radii (half-sizes)
        int[][] islandData = {
            {0, -360, 30},      // SMALL
            {360, 0, 45},       // MEDIUM
            {0, 360, 60},       // LARGE
            {-360, 0, 75}       // EXTRA_LARGE
        };

        for (int[] island : islandData) {
            int centerX = island[0];
            int centerZ = island[1];
            int halfSize = island[2];

            int minX = centerX - halfSize;
            int maxX = centerX + halfSize;
            int minZ = centerZ - halfSize;
            int maxZ = centerZ + halfSize;

            if (pos.getX() >= minX && pos.getX() <= maxX &&
                pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                return true;
            }
        }

        return false;
    }

    public boolean sauvegarderFichierTeleverse(String nomFichier, String contenu) {
        try {
            assurerRepertoireExiste();
            Path fichierConfig = Paths.get(REPERTOIRE_APPARITION_BONBONS, nomFichier);

            // Valider le contenu
            String[] lignes = contenu.split("\\r?\\n");
            List<EntreeApparitionBonbon> entreesTest = new ArrayList<>();

            for (String ligne : lignes) {
                ligne = ligne.trim();
                if (ligne.isEmpty() || ligne.startsWith("#")) {
                    continue;
                }

                try {
                    EntreeApparitionBonbon entree = analyserLigne(ligne);
                    if (entree != null) {
                        entreesTest.add(entree);
                    }
                } catch (Exception e) {
                    MonSubMod.JOURNALISEUR.warn("Ligne invalide dans le fichier Sous-mode 2 téléversé: {} ({})", ligne, e.getMessage());
                    return false;
                }
            }

            if (entreesTest.isEmpty()) {
                MonSubMod.JOURNALISEUR.warn("Le fichier Sous-mode 2 téléversé ne contient aucune entrée valide");
                return false;
            }

            Files.writeString(fichierConfig, contenu);
            MonSubMod.JOURNALISEUR.info("Config d'apparition Sous-mode 2 téléversée sauvegardée avec succès: {}", nomFichier);
            return true;

        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de sauvegarde de la config d'apparition Sous-mode 2 téléversée", e);
            return false;
        }
    }

    public int gererMorceau(PaquetTeleversementFichierBonbons paquet) {

        // Stocker le morceau
        pendingUploads
                .computeIfAbsent(paquet.obtenirIdTransfert(), k -> new ConcurrentHashMap<>())
                .put(paquet.obtenirIndexMorceau(), paquet.obtenirDonneesMorceau());
        Path fichierConfig = Path.of("");
        // Vérifier si tous les morceaux sont reçus
        if (pendingUploads.get(paquet.obtenirIdTransfert()).size() == paquet.obtenirNombreTotalMorceaux()) {
            // Réassembler
            byte[] donneesCompletes = combinerTableauxOctets(pendingUploads.get(paquet.obtenirIdTransfert()), paquet.obtenirNombreTotalMorceaux());
            String contenuFichier = new String(donneesCompletes, StandardCharsets.UTF_8);
            try {
                assurerRepertoireExiste();
                String nomFichier = paquet.obtenirNomFichier();
                fichierConfig = Paths.get(REPERTOIRE_APPARITION_BONBONS, nomFichier);

                // Valider le contenu
                String[] lignes = contenuFichier.split("\\r?\\n");
                List<EntreeApparitionBonbon> entreesTest = new ArrayList<>();

                for (String ligne : lignes) {
                    ligne = ligne.trim();
                    if (ligne.isEmpty() || ligne.startsWith("#")) {
                        continue;
                    }

                    try {
                        EntreeApparitionBonbon entree = analyserLigne(ligne);
                        if (entree != null) {
                            entreesTest.add(entree);
                        }
                    } catch (Exception e) {
                        MonSubMod.JOURNALISEUR.warn("Ligne invalide dans le fichier Sous-mode 2 téléversé: {} ({})", ligne, e.getMessage());
                        return -1;
                    }
                }

                if (entreesTest.isEmpty()) {
                    MonSubMod.JOURNALISEUR.warn("Le fichier Sous-mode 2 téléversé ne contient aucune entrée valide");
                    return -1;
                }

                Files.writeString(fichierConfig, contenuFichier);
                MonSubMod.JOURNALISEUR.info("Config d'apparition Sous-mode 2 téléversée sauvegardée avec succès: {}", nomFichier);
                // Nettoyer le stockage
                pendingUploads.remove(paquet.obtenirIdTransfert());
                return 0;

            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Échec de sauvegarde de la config d'apparition Sous-mode 2 téléversée", e);
                return -1;
            }
        }
        // Encore en attente de morceaux
        return 1;
    }

    private static byte[] combinerTableauxOctets(Map<Integer, byte[]> morceaux, int totalMorceaux) {
        ByteArrayOutputStream fluxSortie = new ByteArrayOutputStream();
        for (int i = 0; i < totalMorceaux; i++) {
            try {
                fluxSortie.write(morceaux.get(i));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return fluxSortie.toByteArray();
    }

    public String obtenirFichierParDefaut() {
        List<String> fichiers = obtenirFichiersDisponibles();
        if (fichiers.isEmpty()) {
            assurerRepertoireExiste();
            fichiers = obtenirFichiersDisponibles();
        }

        if (fichiers.contains("default.txt")) {
            return "default.txt";
        }

        return fichiers.isEmpty() ? null : fichiers.get(0);
    }

    public boolean supprimerFichier(String nomFichier) {
        try {
            if ("default.txt".equals(nomFichier)) {
                MonSubMod.JOURNALISEUR.warn("Impossible de supprimer le fichier default.txt");
                return false;
            }

            Path fichierConfig = Paths.get(REPERTOIRE_APPARITION_BONBONS, nomFichier);
            if (!Files.exists(fichierConfig)) {
                MonSubMod.JOURNALISEUR.warn("Le fichier à supprimer n'existe pas: {}", nomFichier);
                return false;
            }

            Files.delete(fichierConfig);
            MonSubMod.JOURNALISEUR.info("Fichier de config d'apparition Sous-mode 2 supprimé avec succès: {}", nomFichier);
            return true;

        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de suppression du fichier de config d'apparition Sous-mode 2: {}", nomFichier, e);
            return false;
        }
    }
}

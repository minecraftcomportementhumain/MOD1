package com.example.mysubmod.sousmodes.sousmode1.donnees;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetTeleversementFichierBonbons;
import net.minecraft.core.BlockPos;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GestionnaireFichiersApparitionBonbons {
    private static final String REPERTOIRE_CONFIG_BONBONS = "configs_apparition_bonbons";
    private static GestionnaireFichiersApparitionBonbons instance;
    private static final Map<UUID, ConcurrentHashMap<Integer, byte[]>> telechargementsPendants = new ConcurrentHashMap<>();

    private GestionnaireFichiersApparitionBonbons() {}

    public static GestionnaireFichiersApparitionBonbons getInstance() {
        if (instance == null) {
            instance = new GestionnaireFichiersApparitionBonbons();
        }
        return instance;
    }

    public void assurerRepertoireExiste() {
        Path repertoireConfig = Paths.get(REPERTOIRE_CONFIG_BONBONS);
        if (!Files.exists(repertoireConfig)) {
            try {
                Files.createDirectories(repertoireConfig);
                creerFichierParDefaut();
                MonSubMod.JOURNALISEUR.info("Répertoire de configuration d'apparition des bonbons créé : {}", repertoireConfig.toAbsolutePath());
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Échec de la création du répertoire de configuration d'apparition des bonbons", e);
            }
        }
    }

    private void creerFichierParDefaut() {
        String contenuParDefaut = """
            # Fichier de configuration d'apparition des bonbons
            # Format: temps_en_secondes,nombre_bonbons,x,y,z
            # Temps: 0-900 secondes (15 minutes max)
            # Nombre de bonbons: 1-100 max
            # Coordonnées: doivent être sur une des 4 îles
            # Y (hauteur): 100-120 (îles à Y=100, +20 pour relief futur)
            # Îles: SMALL(0,-360), MEDIUM(360,0), LARGE(0,360), EXTRA_LARGE(-360,0)

            # Exemple: Apparition progressive sur différentes îles
            60,5,0,101,-360
            120,3,360,101,0
            180,2,0,101,360
            240,4,-360,101,0
            300,10,0,101,-360
            360,8,360,101,0
            420,15,0,101,360
            480,12,-360,101,0
            540,20,0,101,-360
            600,18,360,101,0
            660,25,0,101,360
            720,22,-360,101,0
            780,30,0,101,-360
            840,28,360,101,0
            """;

        try {
            Path fichierParDefaut = Paths.get(REPERTOIRE_CONFIG_BONBONS, "default.txt");
            Files.writeString(fichierParDefaut, contenuParDefaut);
            MonSubMod.JOURNALISEUR.info("Fichier de configuration d'apparition des bonbons par défaut créé");
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la création du fichier de configuration d'apparition des bonbons par défaut", e);
        }
    }

    public List<String> obtenirFichiersDisponibles() {
        try {
            Path repertoireConfig = Paths.get(REPERTOIRE_CONFIG_BONBONS);
            if (!Files.exists(repertoireConfig)) {
                assurerRepertoireExiste();
            }

            return Files.list(repertoireConfig)
                .filter(chemin -> chemin.toString().endsWith(".txt"))
                .map(chemin -> chemin.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la liste des fichiers de configuration d'apparition des bonbons", e);
            return new ArrayList<>();
        }
    }

    public List<EntreeApparitionBonbon> chargerConfigApparition(String cheminOuNomFichier) {
        List<EntreeApparitionBonbon> entrees = new ArrayList<>();

        // Supporter à la fois le nom de fichier et le chemin complet
        Path fichierConfig;
        if (cheminOuNomFichier.contains(File.separator) || cheminOuNomFichier.contains("/")) {
            // Chemin complet fourni
            fichierConfig = Paths.get(cheminOuNomFichier);
        } else {
            // Juste le nom de fichier fourni
            fichierConfig = Paths.get(REPERTOIRE_CONFIG_BONBONS, cheminOuNomFichier);
        }

        if (!Files.exists(fichierConfig)) {
            MonSubMod.JOURNALISEUR.error("Fichier de configuration d'apparition des bonbons introuvable : {}", cheminOuNomFichier);
            return entrees;
        }

        List<String> toutesLesLignes = new ArrayList<>();
        List<String> lignesModifiees = new ArrayList<>();
        boolean contientLignesInvalides = false;

        try (BufferedReader lecteur = Files.newBufferedReader(fichierConfig)) {
            String ligne;
            int numeroLigne = 0;

            while ((ligne = lecteur.readLine()) != null) {
                numeroLigne++;
                toutesLesLignes.add(ligne);
                String ligneTrimee = ligne.trim();

                // Sauter les commentaires et les lignes vides
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
                    MonSubMod.JOURNALISEUR.warn("Ligne invalide {} dans {} : {} ({})", numeroLigne, cheminOuNomFichier, ligneTrimee, e.getMessage());
                    // Commenter la ligne invalide avec le message d'erreur
                    lignesModifiees.add("# INVALIDE: " + ligne + " # Erreur: " + e.getMessage());
                    contientLignesInvalides = true;
                }
            }

            MonSubMod.JOURNALISEUR.info("Chargé {} entrées d'apparition de bonbons depuis {}", entrees.size(), cheminOuNomFichier);

            // S'il y avait des lignes invalides, réécrire le fichier avec les commentaires
            if (contientLignesInvalides) {
                try {
                    Files.write(fichierConfig, lignesModifiees);
                    MonSubMod.JOURNALISEUR.info("Commenté {} lignes invalides dans {}",
                        (toutesLesLignes.size() - entrees.size() - (int)toutesLesLignes.stream().filter(l -> l.trim().isEmpty() || l.trim().startsWith("#")).count()),
                        cheminOuNomFichier);
                } catch (IOException ecrireEx) {
                    MonSubMod.JOURNALISEUR.error("Échec de la mise à jour du fichier avec les lignes commentées : {}", cheminOuNomFichier, ecrireEx);
                }
            }

        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la lecture du fichier de configuration d'apparition des bonbons : {}", cheminOuNomFichier, e);
        }

        return entrees;
    }

    private EntreeApparitionBonbon analyserLigne(String ligne) {
        String[] parties = ligne.split(",");
        if (parties.length != 5) {
            throw new IllegalArgumentException("Format attendu : temps,nombre,x,y,z");
        }

        int tempsSecondes = Integer.parseInt(parties[0].trim());
        int nombreBonbons = Integer.parseInt(parties[1].trim());
        int x = Integer.parseInt(parties[2].trim());
        int y = Integer.parseInt(parties[3].trim());
        int z = Integer.parseInt(parties[4].trim());

        // Valider la plage de temps (0-900 secondes = 15 minutes)
        if (tempsSecondes < 0 || tempsSecondes > 900) {
            throw new IllegalArgumentException("Le temps doit être entre 0 et 900 secondes");
        }

        // Valider le nombre de bonbons (1-100)
        if (nombreBonbons <= 0 || nombreBonbons > 100) {
            throw new IllegalArgumentException("Le nombre de bonbons doit être entre 1 et 100");
        }

        // Valider la coordonnée Y (hauteur)
        // Les îles sont à Y=100, rien en dessous n'est valide, permettre jusqu'à +20 pour le terrain futur
        if (y < 100 || y > 120) {
            throw new IllegalArgumentException("La coordonnée Y doit être entre 100 et 120");
        }

        net.minecraft.core.BlockPos position = new net.minecraft.core.BlockPos(x, y, z);

        // Valider que les coordonnées sont sur une des 4 îles
        if (!estPositionSurIle(position)) {
            throw new IllegalArgumentException(String.format(
                "La position (%d,%d,%d) n'est sur aucune île", x, y, z));
        }

        return new EntreeApparitionBonbon(tempsSecondes, nombreBonbons, position);
    }

    /**
     * Vérifier si une position est sur une des 4 îles
     * Les îles sont carrées et à : SMALL(0,-360), MEDIUM(360,0), LARGE(0,360), EXTRA_LARGE(-360,0)
     */
    private boolean estPositionSurIle(net.minecraft.core.BlockPos pos) {
        // Centres d'îles et rayons (demi-tailles)
        // SMALL: 60x60 (rayon 30), MEDIUM: 90x90 (rayon 45), LARGE: 120x120 (rayon 60), EXTRA_LARGE: 150x150 (rayon 75)
        int[][] donneesIles = {
            {0, -360, 30},      // SMALL: centre (0,-360), rayon 30
            {360, 0, 45},       // MEDIUM: centre (360,0), rayon 45
            {0, 360, 60},       // LARGE: centre (0,360), rayon 60
            {-360, 0, 75}       // EXTRA_LARGE: centre (-360,0), rayon 75
        };

        for (int[] ile : donneesIles) {
            int centreX = ile[0];
            int centreZ = ile[1];
            int demiTaille = ile[2];

            // Vérifier si la position est dans les limites carrées
            int minX = centreX - demiTaille;
            int maxX = centreX + demiTaille;
            int minZ = centreZ - demiTaille;
            int maxZ = centreZ + demiTaille;

            if (pos.getX() >= minX && pos.getX() <= maxX &&
                pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                return true;
            }
        }

        return false;
    }

    public int gererMorceau(PaquetTeleversementFichierBonbons paquet) {

        // Stocker le fragment
        telechargementsPendants
                .computeIfAbsent(paquet.obtenirIdTransfert(), k -> new ConcurrentHashMap<>())
                .put(paquet.obtenirIndexMorceau(), paquet.obtenirDonneesMorceau());
        Path fichierConfig = Path.of("");
        // Vérifier si tous les fragments sont reçus
        if (telechargementsPendants.get(paquet.obtenirIdTransfert()).size() == paquet.obtenirNombreTotalMorceaux()) {
            // Réassembler
            byte[] donneesCompletes = combinerTableauxOctets(telechargementsPendants.get(paquet.obtenirIdTransfert()), paquet.obtenirNombreTotalMorceaux());
            String contenuFichier = new String(donneesCompletes, StandardCharsets.UTF_8);
            try {
            assurerRepertoireExiste();
            String nomFichier = paquet.obtenirNomFichier();
            fichierConfig = Paths.get(REPERTOIRE_CONFIG_BONBONS, nomFichier);

            // Valider le contenu en essayant de l'analyser
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
                    MonSubMod.JOURNALISEUR.warn("Ligne invalide dans le fichier téléchargé : {} ({})", ligne, e.getMessage());
                    return -1;
                }
            }

            if (entreesTest.isEmpty()) {
                MonSubMod.JOURNALISEUR.warn("Le fichier téléchargé ne contient aucune entrée valide");
                return -1;
            }

            Files.writeString(fichierConfig, contenuFichier);
            MonSubMod.JOURNALISEUR.info("Configuration d'apparition des bonbons téléchargée enregistrée avec succès : {}", nomFichier);

            // Nettoyer le stockage
            telechargementsPendants.remove(paquet.obtenirIdTransfert());
            return 0;
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de l'enregistrement de la configuration d'apparition des bonbons téléchargée", e);
            return -1;
        }
        }
        // Toujours en attente de plus
        return 1;
    }

    private static byte[] combinerTableauxOctets(Map<Integer, byte[]> fragments, int totalFragments) {
        ByteArrayOutputStream fluxSortie = new ByteArrayOutputStream();
        for (int i = 0; i < totalFragments; i++) {
            try {
                fluxSortie.write(fragments.get(i));
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

        // Préférer default.txt s'il existe
        if (fichiers.contains("default.txt")) {
            return "default.txt";
        }

        // Sinon retourner le premier fichier disponible
        return fichiers.isEmpty() ? null : fichiers.get(0);
    }

    public boolean supprimerFichier(String nomFichier) {
        try {
            // Ne pas permettre de supprimer default.txt
            if ("default.txt".equals(nomFichier)) {
                MonSubMod.JOURNALISEUR.warn("Impossible de supprimer le fichier default.txt");
                return false;
            }

            Path fichierConfig = Paths.get(REPERTOIRE_CONFIG_BONBONS, nomFichier);
            if (!Files.exists(fichierConfig)) {
                MonSubMod.JOURNALISEUR.warn("Le fichier à supprimer n'existe pas : {}", nomFichier);
                return false;
            }

            Files.delete(fichierConfig);
            MonSubMod.JOURNALISEUR.info("Fichier de configuration d'apparition des bonbons supprimé avec succès : {}", nomFichier);
            return true;

        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la suppression du fichier de configuration d'apparition des bonbons : {}", nomFichier, e);
            return false;
        }
    }
}

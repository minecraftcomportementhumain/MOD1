package com.example.mysubmod.cartes;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.cartes.reseau.UtilitaireCompressionCarte;
import com.example.mysubmod.utilitaire.UtilitaireCheminSecurise;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gestionnaire serveur des cartes : persistance JSON (un fichier par carte),
 * carte sélectionnée, verrou d'accès à l'outil de création (un seul admin à la fois).
 */
public class GestionnaireCartes {
    private static final String REPERTOIRE_CARTES = "cartes_monsubmod";
    /** Nombre maximal de morceaux d'un transfert (public : l'éditeur vérifie avant l'envoi). */
    public static final int MAX_MORCEAUX = 1024;
    private static final int MAX_TRANSFERTS_SIMULTANES = 16;
    private static GestionnaireCartes instance;

    private String carteSelectionnee = null; // Nom de la carte active (null = aucune)

    // Verrou de l'éditeur : un seul admin à la fois
    private UUID editeurVerrouilleePar = null;
    private String nomAdminEditeur = null;

    // Réassemblage des sauvegardes envoyées en morceaux par les clients
    private final Map<UUID, ConcurrentHashMap<Integer, byte[]>> transfertsEnCours = new ConcurrentHashMap<>();

    private GestionnaireCartes() {
    }

    public static GestionnaireCartes getInstance() {
        if (instance == null) {
            instance = new GestionnaireCartes();
        }
        return instance;
    }

    public void assurerRepertoireExiste() {
        Path repertoire = Paths.get(REPERTOIRE_CARTES);
        if (!Files.exists(repertoire)) {
            try {
                Files.createDirectories(repertoire);
                MonSubMod.JOURNALISEUR.info("Répertoire des cartes créé : {}", repertoire.toAbsolutePath());
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Échec de la création du répertoire des cartes", e);
            }
        }
    }

    public List<String> obtenirCartesDisponibles() {
        try {
            assurerRepertoireExiste();
            return Files.list(Paths.get(REPERTOIRE_CARTES))
                .filter(chemin -> chemin.toString().endsWith(".json"))
                .map(chemin -> {
                    String nomFichier = chemin.getFileName().toString();
                    return nomFichier.substring(0, nomFichier.length() - ".json".length());
                })
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la liste des cartes", e);
            return new ArrayList<>();
        }
    }

    public boolean carteExiste(String nomCarte) {
        if (nomCarte == null || nomCarte.isEmpty()) {
            return false;
        }
        Path fichier = UtilitaireCheminSecurise.resoudreConfine(Paths.get(REPERTOIRE_CARTES), nomCarte + ".json");
        return fichier != null && Files.exists(fichier);
    }

    public CarteDonnees chargerCarte(String nomCarte) {
        String json = lireJsonCarte(nomCarte);
        if (json == null) {
            return null;
        }
        try {
            return CarteDonnees.depuisJson(json);
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Échec du chargement de la carte {}", nomCarte, e);
            return null;
        }
    }

    /**
     * Lit le JSON brut d'une carte sans le décoder (envoi direct vers l'éditeur,
     * relecture de la seed à la sauvegarde). Retourne null si la carte n'existe pas.
     */
    public String lireJsonCarte(String nomCarte) {
        try {
            Path fichier = UtilitaireCheminSecurise.resoudreConfine(Paths.get(REPERTOIRE_CARTES), nomCarte + ".json");
            if (fichier == null) {
                MonSubMod.JOURNALISEUR.warn("Nom de carte invalide (chargement rejeté) : {}", nomCarte);
                return null;
            }
            if (!Files.exists(fichier)) {
                MonSubMod.JOURNALISEUR.warn("Carte introuvable : {}", nomCarte);
                return null;
            }
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Échec de la lecture de la carte {}", nomCarte, e);
            return null;
        }
    }

    /**
     * Sauvegarde une carte reçue d'un client. La seed est générée à la première sauvegarde
     * (conservée si la carte est écrasée) et les zones sont recalculées et nommées.
     * Retourne null en cas de succès, sinon un message d'erreur.
     */
    public String sauvegarderCarte(CarteDonnees carte) {
        List<String> erreurs = carte.validerPourSauvegarde();
        if (!erreurs.isEmpty()) {
            return String.join(" ; ", erreurs);
        }

        try {
            assurerRepertoireExiste();

            // Seed fixe générée automatiquement à la première sauvegarde de la carte
            // (relue en flux depuis le fichier existant, sans décoder toute la carte)
            String jsonExistant = carteExiste(carte.nom) ? lireJsonCarte(carte.nom) : null;
            long seedExistante = jsonExistant != null ? CarteDonnees.seedDepuisJson(jsonExistant) : 0;
            if (seedExistante != 0) {
                carte.seed = seedExistante;
            } else if (carte.seed == 0) {
                carte.seed = new java.util.Random().nextLong();
                if (carte.seed == 0) {
                    carte.seed = 1;
                }
            }

            // Zones : conservées telles quelles si dessinées manuellement dans l'éditeur
            // (versJson les redérive des blocs), sinon nommées automatiquement
            if (!carte.zonesManuelles) {
                carte.recalculerZones();
            }

            Path fichier = UtilitaireCheminSecurise.resoudreConfine(Paths.get(REPERTOIRE_CARTES), carte.nom + ".json");
            if (fichier == null) {
                return "Nom de carte invalide";
            }
            Files.writeString(fichier, carte.versJson(), StandardCharsets.UTF_8);
            MonSubMod.JOURNALISEUR.info("Carte sauvegardée : {} ({}x{}, {} blocs, {} zones)",
                carte.nom, carte.largeur, carte.hauteur, carte.blocs.size(), carte.zones.size());
            return null;
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Échec de la sauvegarde de la carte {}", carte.nom, e);
            return "Erreur interne lors de la sauvegarde";
        }
    }

    public boolean supprimerCarte(String nomCarte) {
        try {
            Path fichier = UtilitaireCheminSecurise.resoudreConfine(Paths.get(REPERTOIRE_CARTES), nomCarte + ".json");
            if (fichier == null) {
                MonSubMod.JOURNALISEUR.warn("Nom de carte invalide (suppression rejetée) : {}", nomCarte);
                return false;
            }
            if (!Files.exists(fichier)) {
                return false;
            }
            Files.delete(fichier);
            if (nomCarte.equals(carteSelectionnee)) {
                carteSelectionnee = null;
            }
            MonSubMod.JOURNALISEUR.info("Carte supprimée : {}", nomCarte);
            return true;
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Échec de la suppression de la carte {}", nomCarte, e);
            return false;
        }
    }

    // ==================== Carte sélectionnée ====================

    public String obtenirCarteSelectionnee() {
        return carteSelectionnee;
    }

    public void definirCarteSelectionnee(String nomCarte) {
        if (nomCarte == null || nomCarte.isEmpty()) {
            carteSelectionnee = null;
            MonSubMod.JOURNALISEUR.info("Carte désélectionnée");
        } else if (carteExiste(nomCarte)) {
            carteSelectionnee = nomCarte;
            MonSubMod.JOURNALISEUR.info("Carte sélectionnée : {}", nomCarte);
        } else {
            MonSubMod.JOURNALISEUR.warn("Tentative de sélection d'une carte inexistante : {}", nomCarte);
        }
    }

    public boolean aCarteSelectionnee() {
        return carteSelectionnee != null && carteExiste(carteSelectionnee);
    }

    // ==================== Verrou de l'éditeur ====================

    /**
     * Tente de verrouiller l'éditeur pour cet admin.
     * Retourne null en cas de succès, sinon le nom de l'admin qui utilise déjà l'éditeur.
     */
    public synchronized String verrouillerEditeur(UUID idAdmin, String nomAdmin) {
        if (editeurVerrouilleePar != null && !editeurVerrouilleePar.equals(idAdmin)) {
            return nomAdminEditeur != null ? nomAdminEditeur : "un autre admin";
        }
        editeurVerrouilleePar = idAdmin;
        nomAdminEditeur = nomAdmin;
        MonSubMod.JOURNALISEUR.info("Éditeur de carte verrouillé par {}", nomAdmin);
        return null;
    }

    public synchronized void libererEditeur(UUID idAdmin) {
        if (editeurVerrouilleePar != null && editeurVerrouilleePar.equals(idAdmin)) {
            MonSubMod.JOURNALISEUR.info("Éditeur de carte libéré par {}", nomAdminEditeur);
            editeurVerrouilleePar = null;
            nomAdminEditeur = null;
        }
    }

    public synchronized void libererEditeurSiDeconnecte(UUID idAdmin) {
        libererEditeur(idAdmin);
    }

    // ==================== Transferts en morceaux ====================

    /**
     * Enregistre un morceau d'une carte envoyée par un client.
     * Retourne le JSON complet (décompressé si le client l'a envoyé en GZIP) quand tous
     * les morceaux sont arrivés, null tant que le transfert est incomplet ou rejeté par
     * les gardes anti-abus. Lève IllegalArgumentException si les données réassemblées
     * sont corrompues ou trop volumineuses : l'appelant doit signaler l'échec au client
     * (null seul signifierait « en attente » et la sauvegarde échouerait en silence).
     */
    public String gererMorceauCarte(UUID idTransfert, int indexMorceau, int nombreTotalMorceaux, byte[] donnees) {
        if (nombreTotalMorceaux <= 0 || nombreTotalMorceaux > MAX_MORCEAUX
            || indexMorceau < 0 || indexMorceau >= nombreTotalMorceaux) {
            return null;
        }
        if (!transfertsEnCours.containsKey(idTransfert)
            && transfertsEnCours.size() >= MAX_TRANSFERTS_SIMULTANES) {
            MonSubMod.JOURNALISEUR.warn("Trop de transferts de carte simultanés — rejet");
            return null;
        }

        transfertsEnCours
            .computeIfAbsent(idTransfert, k -> new ConcurrentHashMap<>())
            .put(indexMorceau, donnees);

        Map<Integer, byte[]> morceaux = transfertsEnCours.get(idTransfert);
        if (morceaux.size() == nombreTotalMorceaux) {
            transfertsEnCours.remove(idTransfert);
            int tailleTotale = 0;
            for (int i = 0; i < nombreTotalMorceaux; i++) {
                byte[] morceau = morceaux.get(i);
                if (morceau == null) {
                    MonSubMod.JOURNALISEUR.error("Morceau manquant {} dans le transfert de carte {}", i, idTransfert);
                    return null;
                }
                tailleTotale += morceau.length;
            }
            byte[] complet = new byte[tailleTotale];
            int position = 0;
            for (int i = 0; i < nombreTotalMorceaux; i++) {
                byte[] morceau = morceaux.get(i);
                System.arraycopy(morceau, 0, complet, position, morceau.length);
                position += morceau.length;
            }
            return new String(UtilitaireCompressionCarte.decompresserSiGzip(complet), StandardCharsets.UTF_8);
        }
        return null;
    }
}

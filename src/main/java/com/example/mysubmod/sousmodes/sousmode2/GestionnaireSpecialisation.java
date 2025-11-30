package com.example.mysubmod.sousmodes.sousmode2;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSynchronisationPenalite;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère la spécialisation des joueurs et les pénalités de changement
 */
public class GestionnaireSpecialisation {
    private static GestionnaireSpecialisation instance;

    // Spécialisation actuelle de chaque joueur
    private final Map<UUID, TypeRessource> specialisationJoueur = new ConcurrentHashMap<>();

    // Temps de fin de la pénalité pour chaque joueur (en ms)
    private final Map<UUID, Long> tempsFinPenaliteJoueur = new ConcurrentHashMap<>();

    // Constante: durée de la pénalité en millisecondes (2 minutes 45 secondes)
    private static final long DUREE_PENALITE_MS = 2 * 60 * 1000 + 45 * 1000; // 165000 ms

    // Multiplicateur de santé pendant la pénalité
    private static final float MULTIPLICATEUR_SANTE_PENALITE = 0.75f; // 75% au lieu de 100% (25% de réduction)

    private GestionnaireSpecialisation() {}

    public static GestionnaireSpecialisation getInstance() {
        if (instance == null) {
            instance = new GestionnaireSpecialisation();
        }
        return instance;
    }

    /**
     * Réinitialise toutes les données pour une nouvelle partie
     */
    public void reinitialiser() {
        specialisationJoueur.clear();
        tempsFinPenaliteJoueur.clear();
        MonSubMod.JOURNALISEUR.info("GestionnaireSpecialisation réinitialisé pour nouvelle partie");
    }

    /**
     * Vérifie si un joueur a une spécialisation
     */
    public boolean aSpecialisation(UUID idJoueur) {
        return specialisationJoueur.containsKey(idJoueur);
    }

    /**
     * Obtient la spécialisation actuelle d'un joueur
     */
    public TypeRessource obtenirSpecialisation(UUID idJoueur) {
        return specialisationJoueur.get(idJoueur);
    }

    /**
     * Vérifie si un joueur est actuellement sous pénalité
     * Utilise une marge de tolérance pour éviter les problèmes de timing
     */
    public boolean aPenalite(UUID idJoueur) {
        Long tempsFin = tempsFinPenaliteJoueur.get(idJoueur);
        if (tempsFin == null) return false;

        long maintenant = System.currentTimeMillis();
        // Ajouter une marge de 100ms pour éviter les problèmes de synchronisation
        // Si le temps restant est <= 100ms, considérer la pénalité comme expirée
        if (maintenant >= tempsFin - 100) {
            // Pénalité expirée ou sur le point d'expirer, on la retire
            tempsFinPenaliteJoueur.remove(idJoueur);
            return false;
        }
        return true;
    }

    /**
     * Obtient le temps restant de pénalité en millisecondes
     * Retourne 0 si pas de pénalité
     * Utilise la même marge de tolérance que aPenalite()
     */
    public long obtenirTempsRestantPenalite(UUID idJoueur) {
        Long tempsFin = tempsFinPenaliteJoueur.get(idJoueur);
        if (tempsFin == null) return 0;

        long maintenant = System.currentTimeMillis();
        long restant = tempsFin - maintenant;

        // Si le temps restant est <= 100ms, considérer la pénalité comme expirée
        if (restant <= 100) {
            tempsFinPenaliteJoueur.remove(idJoueur);
            return 0;
        }
        return restant;
    }

    /**
     * Gère la collecte d'une ressource par un joueur
     * Retourne le multiplicateur de santé à appliquer (1.0 normal, 0.75 pénalité)
     *
     * @param joueur Le joueur qui collecte
     * @param typeRessource Le type de ressource collectée
     * @return Le multiplicateur de santé (0.75 si pénalité, 1.0 sinon)
     */
    public float gererCollecteRessource(ServerPlayer joueur, TypeRessource typeRessource) {
        UUID idJoueur = joueur.getUUID();

        // Première collecte: définir la spécialisation
        if (!aSpecialisation(idJoueur)) {
            specialisationJoueur.put(idJoueur, typeRessource);
            joueur.sendSystemMessage(Component.literal(
                "§a§lSpécialisation définie: " + typeRessource.obtenirNomAffichage() + "\n" +
                "§7Collecter l'autre type de ressource activera une pénalité de 2 minutes 45 secondes."
            ));

            // Envoyer la spécialisation au client pour l'affichage du HUD
            com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSyncSpecialisation(typeRessource)
            );

            MonSubMod.JOURNALISEUR.info("Joueur {} spécialisé en {}", joueur.getName().getString(), typeRessource);
            return 1.0f; // Pas de pénalité à la première collecte
        }

        TypeRessource specialisationActuelle = specialisationJoueur.get(idJoueur);

        // Même type que la spécialisation: OK
        if (specialisationActuelle == typeRessource) {
            // Vérifier si le joueur a une pénalité active
            if (aPenalite(idJoueur)) {
                return MULTIPLICATEUR_SANTE_PENALITE; // 75% de santé
            }
            return 1.0f; // 100% de santé
        }

        // Type différent: changement de spécialisation + pénalité
        MonSubMod.JOURNALISEUR.info("Joueur {} a changé de spécialisation de {} vers {} - application de la pénalité",
            joueur.getName().getString(), specialisationActuelle, typeRessource);

        // Changer la spécialisation
        specialisationJoueur.put(idJoueur, typeRessource);

        // Envoyer le changement de spécialisation au client pour l'affichage du HUD
        com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
            new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSyncSpecialisation(typeRessource)
        );

        // Appliquer la pénalité de 2 minutes 45 secondes
        long maintenant = System.currentTimeMillis();
        long finPenalite = maintenant + DUREE_PENALITE_MS;
        tempsFinPenaliteJoueur.put(idJoueur, finPenalite);

        // Notifier le joueur
        joueur.sendSystemMessage(Component.literal(
            "§c§lChangement de spécialisation!\n" +
            "§eVous collectez maintenant: " + typeRessource.obtenirNomAffichage() + "\n" +
            "§c§lPénalité activée: 2 minutes 45 secondes\n" +
            "§7Toutes les ressources restaurent 75% de santé."
        ));

        // Synchroniser avec le client pour activer le HUD
        GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
            new PaquetSynchronisationPenalite(true, idJoueur, finPenalite));

        return MULTIPLICATEUR_SANTE_PENALITE; // 75% de santé pour cette collecte
    }

    /**
     * Obtient le multiplicateur de santé actuel pour un joueur
     * Utilisé pour l'affichage ou la vérification
     */
    public float obtenirMultiplicateurSanteActuel(UUID idJoueur) {
        return aPenalite(idJoueur) ? MULTIPLICATEUR_SANTE_PENALITE : 1.0f;
    }

    /**
     * Retire un joueur du système (déconnexion/fin de partie)
     */
    public void retirerJoueur(UUID idJoueur) {
        specialisationJoueur.remove(idJoueur);
        tempsFinPenaliteJoueur.remove(idJoueur);
    }

    /**
     * Migre les données de spécialisation d'un ancien UUID vers un nouveau UUID
     * Utilisé lors de la reconnexion avec un UUID différent (système de file d'attente)
     */
    public void migrerDonneesJoueur(UUID ancienUUID, UUID nouvelUUID) {
        // Migrer la spécialisation
        TypeRessource specialisation = specialisationJoueur.remove(ancienUUID);
        if (specialisation != null) {
            specialisationJoueur.put(nouvelUUID, specialisation);
            MonSubMod.JOURNALISEUR.info("  - Migration de spécialisation: {}", specialisation);
        }

        // Migrer le temps de fin de pénalité
        Long tempsFin = tempsFinPenaliteJoueur.remove(ancienUUID);
        if (tempsFin != null) {
            tempsFinPenaliteJoueur.put(nouvelUUID, tempsFin);
            MonSubMod.JOURNALISEUR.info("  - Migration de pénalité spécialisation");
        }
    }

    /**
     * Synchronise l'état de pénalité avec le client
     * À appeler lors de la reconnexion du joueur
     */
    public void synchroniserPenaliteAvecClient(ServerPlayer joueur) {
        UUID idJoueur = joueur.getUUID();
        boolean penaliteActive = aPenalite(idJoueur);

        // Obtenir le temps de fin de pénalité si actif
        long finPenalite = 0;
        if (penaliteActive) {
            finPenalite = tempsFinPenaliteJoueur.getOrDefault(idJoueur, 0L);
        }

        // Envoyer le paquet de synchronisation
        GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
            new PaquetSynchronisationPenalite(penaliteActive, idJoueur, finPenalite));

        if (penaliteActive) {
            long restant = obtenirTempsRestantPenalite(idJoueur);
            MonSubMod.JOURNALISEUR.info("État de pénalité synchronisé pour le joueur {} - {} restant",
                joueur.getName().getString(), formaterTemps(restant));
        }
    }

    /**
     * Synchronise la spécialisation du joueur avec le client
     * À appeler lors de la reconnexion du joueur pour restaurer l'affichage HUD
     */
    public void synchroniserSpecialisationAvecClient(ServerPlayer joueur) {
        UUID idJoueur = joueur.getUUID();
        TypeRessource specialisation = specialisationJoueur.get(idJoueur);

        if (specialisation != null) {
            // Envoyer la spécialisation au client pour l'affichage du HUD
            GestionnaireReseau.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> joueur),
                new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSyncSpecialisation(specialisation)
            );
            MonSubMod.JOURNALISEUR.info("Spécialisation {} synchronisée pour le joueur reconnecté {}",
                specialisation, joueur.getName().getString());
        }
    }

    /**
     * Obtient la durée de la pénalité en millisecondes (pour info)
     */
    public static long obtenirDureePenalite() {
        return DUREE_PENALITE_MS;
    }

    /**
     * Formate le temps restant en minutes:secondes
     * Ex: 165000 ms → "2:45"
     */
    public static String formaterTemps(long millisecondes) {
        long secondes = millisecondes / 1000;
        long minutes = secondes / 60;
        long secondesRestantes = secondes % 60;
        return String.format("%d:%02d", minutes, secondesRestantes);
    }
}

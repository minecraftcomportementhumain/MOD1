package com.example.mysubmod.submodes.submode2;

import com.example.mysubmod.MySubMod;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.submode2.network.PenaltySyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère la spécialisation des joueurs et les pénalités de changement
 */
public class SpecializationManager {
    private static SpecializationManager instance;

    // Spécialisation actuelle de chaque joueur
    private final Map<UUID, ResourceType> playerSpecialization = new ConcurrentHashMap<>();

    // Temps de fin de la pénalité pour chaque joueur (en ms)
    private final Map<UUID, Long> playerPenaltyEndTime = new ConcurrentHashMap<>();

    // Constante: durée de la pénalité en millisecondes (2 minutes 45 secondes)
    private static final long PENALTY_DURATION_MS = 2 * 60 * 1000 + 45 * 1000; // 165000 ms

    // Multiplicateur de santé pendant la pénalité
    private static final float PENALTY_HEALTH_MULTIPLIER = 0.75f; // 75% au lieu de 100% (25% de réduction)

    private SpecializationManager() {}

    public static SpecializationManager getInstance() {
        if (instance == null) {
            instance = new SpecializationManager();
        }
        return instance;
    }

    /**
     * Réinitialise toutes les données pour une nouvelle partie
     */
    public void reset() {
        playerSpecialization.clear();
        playerPenaltyEndTime.clear();
        MySubMod.LOGGER.info("SpecializationManager reset for new game");
    }

    /**
     * Vérifie si un joueur a une spécialisation
     */
    public boolean hasSpecialization(UUID playerId) {
        return playerSpecialization.containsKey(playerId);
    }

    /**
     * Obtient la spécialisation actuelle d'un joueur
     */
    public ResourceType getSpecialization(UUID playerId) {
        return playerSpecialization.get(playerId);
    }

    /**
     * Vérifie si un joueur est actuellement sous pénalité
     */
    public boolean hasPenalty(UUID playerId) {
        Long endTime = playerPenaltyEndTime.get(playerId);
        if (endTime == null) return false;

        long now = System.currentTimeMillis();
        if (now >= endTime) {
            // Pénalité expirée, on la retire
            playerPenaltyEndTime.remove(playerId);
            return false;
        }
        return true;
    }

    /**
     * Obtient le temps restant de pénalité en millisecondes
     * Retourne 0 si pas de pénalité
     */
    public long getRemainingPenaltyTime(UUID playerId) {
        Long endTime = playerPenaltyEndTime.get(playerId);
        if (endTime == null) return 0;

        long now = System.currentTimeMillis();
        long remaining = endTime - now;

        if (remaining <= 0) {
            playerPenaltyEndTime.remove(playerId);
            return 0;
        }
        return remaining;
    }

    /**
     * Gère la collecte d'une ressource par un joueur
     * Retourne le multiplicateur de santé à appliquer (1.0 normal, 0.75 pénalité)
     *
     * @param player Le joueur qui collecte
     * @param resourceType Le type de ressource collectée
     * @return Le multiplicateur de santé (0.75 si pénalité, 1.0 sinon)
     */
    public float handleResourceCollection(ServerPlayer player, ResourceType resourceType) {
        UUID playerId = player.getUUID();

        // Première collecte: définir la spécialisation
        if (!hasSpecialization(playerId)) {
            playerSpecialization.put(playerId, resourceType);
            player.sendSystemMessage(Component.literal(
                "§a§lSpécialisation définie: " + resourceType.getDisplayName() + "\n" +
                "§7Collecter l'autre type de ressource activera une pénalité de 2 minutes 45 secondes."
            ));

            // Send specialization to client for HUD display
            com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.example.mysubmod.submodes.submode2.network.SpecializationSyncPacket(resourceType)
            );

            MySubMod.LOGGER.info("Player {} specialized in {}", player.getName().getString(), resourceType);
            return 1.0f; // Pas de pénalité à la première collecte
        }

        ResourceType currentSpecialization = playerSpecialization.get(playerId);

        // Même type que la spécialisation: OK
        if (currentSpecialization == resourceType) {
            // Vérifier si le joueur a une pénalité active
            if (hasPenalty(playerId)) {
                return PENALTY_HEALTH_MULTIPLIER; // 75% de santé
            }
            return 1.0f; // 100% de santé
        }

        // Type différent: changement de spécialisation + pénalité
        MySubMod.LOGGER.info("Player {} changed specialization from {} to {} - applying penalty",
            player.getName().getString(), currentSpecialization, resourceType);

        // Changer la spécialisation
        playerSpecialization.put(playerId, resourceType);

        // Send specialization change to client for HUD display
        com.example.mysubmod.network.NetworkHandler.INSTANCE.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.example.mysubmod.submodes.submode2.network.SpecializationSyncPacket(resourceType)
        );

        // Appliquer la pénalité de 2 minutes 45 secondes
        long now = System.currentTimeMillis();
        long penaltyEnd = now + PENALTY_DURATION_MS;
        playerPenaltyEndTime.put(playerId, penaltyEnd);

        // Notifier le joueur
        player.sendSystemMessage(Component.literal(
            "§c§lChangement de spécialisation!\n" +
            "§eVous collectez maintenant: " + resourceType.getDisplayName() + "\n" +
            "§c§lPénalité activée: 2 minutes 45 secondes\n" +
            "§7Toutes les ressources restaurent 75% de santé."
        ));

        // Synchroniser avec le client pour activer le HUD
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new PenaltySyncPacket(true, playerId, penaltyEnd));

        return PENALTY_HEALTH_MULTIPLIER; // 75% de santé pour cette collecte
    }

    /**
     * Obtient le multiplicateur de santé actuel pour un joueur
     * Utilisé pour l'affichage ou la vérification
     */
    public float getCurrentHealthMultiplier(UUID playerId) {
        return hasPenalty(playerId) ? PENALTY_HEALTH_MULTIPLIER : 1.0f;
    }

    /**
     * Retire un joueur du système (déconnexion/fin de partie)
     */
    public void removePlayer(UUID playerId) {
        playerSpecialization.remove(playerId);
        playerPenaltyEndTime.remove(playerId);
    }

    /**
     * Synchronise l'état de pénalité avec le client
     * À appeler lors de la reconnexion du joueur
     */
    public void syncPenaltyWithClient(ServerPlayer player) {
        UUID playerId = player.getUUID();
        boolean hasPenalty = hasPenalty(playerId);

        // Get penalty end time if active
        long penaltyEnd = 0;
        if (hasPenalty) {
            penaltyEnd = playerPenaltyEndTime.getOrDefault(playerId, 0L);
        }

        // Envoyer le packet de synchronisation
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new PenaltySyncPacket(hasPenalty, playerId, penaltyEnd));

        if (hasPenalty) {
            long remaining = getRemainingPenaltyTime(playerId);
            MySubMod.LOGGER.info("Synced penalty state for player {} - {} remaining",
                player.getName().getString(), formatTime(remaining));
        }
    }

    /**
     * Obtient la durée de la pénalité en millisecondes (pour info)
     */
    public static long getPenaltyDuration() {
        return PENALTY_DURATION_MS;
    }

    /**
     * Formate le temps restant en minutes:secondes
     * Ex: 165000 ms → "2:45"
     */
    public static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
}

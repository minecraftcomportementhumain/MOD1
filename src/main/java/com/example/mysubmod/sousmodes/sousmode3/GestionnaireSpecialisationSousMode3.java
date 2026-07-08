package com.example.mysubmod.sousmodes.sousmode3;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetSyncSpecialisation;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetSynchronisationPenalite;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spécialisation Bleu/Rouge du Sous-mode 3 (port du système du Sous-mode 2, activé par
 * l'option « Spécialisation » du menu N) : la première consommation d'un bonbon typé fixe
 * la spécialisation du joueur ; consommer l'autre type change la spécialisation et applique
 * une pénalité temporaire (soins réduits).
 *
 * <p>Contrairement au Sous-mode 2, la durée de la pénalité et le multiplicateur de soin
 * proviennent de {@link ConfigPartieSousMode3} (bornés côté serveur). Les paquets de
 * synchronisation client (spécialisation, minuterie de pénalité) sont ceux du Sous-mode 2 :
 * leur affichage n'est pas lié au mode et le HUD des zones sait afficher la spécialisation.</p>
 */
public class GestionnaireSpecialisationSousMode3 {
    private static GestionnaireSpecialisationSousMode3 instance;

    private final Map<UUID, TypeRessource> specialisationJoueur = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tempsFinPenaliteJoueur = new ConcurrentHashMap<>();

    private GestionnaireSpecialisationSousMode3() {
    }

    public static GestionnaireSpecialisationSousMode3 getInstance() {
        if (instance == null) {
            instance = new GestionnaireSpecialisationSousMode3();
        }
        return instance;
    }

    private static ConfigPartieSousMode3 config() {
        return GestionnaireSousMode3.getInstance().obtenirConfig();
    }

    private static long dureePenaliteMs() {
        return config().dureePenaliteSpecialisationSecondes * 1000L;
    }

    private static float multiplicateurPenalite() {
        return config().multiplicateurSantePenalite;
    }

    /** Réinitialise toutes les données (début et fin de partie). */
    public void reinitialiser() {
        specialisationJoueur.clear();
        tempsFinPenaliteJoueur.clear();
    }

    public boolean aSpecialisation(UUID idJoueur) {
        return specialisationJoueur.containsKey(idJoueur);
    }

    public TypeRessource obtenirSpecialisation(UUID idJoueur) {
        return specialisationJoueur.get(idJoueur);
    }

    /** Pénalité active ? (marge de 100 ms, comme au Sous-mode 2, pour éviter les effets de timing) */
    public boolean aPenalite(UUID idJoueur) {
        Long tempsFin = tempsFinPenaliteJoueur.get(idJoueur);
        if (tempsFin == null) {
            return false;
        }
        if (System.currentTimeMillis() >= tempsFin - 100) {
            tempsFinPenaliteJoueur.remove(idJoueur);
            return false;
        }
        return true;
    }

    public long obtenirTempsRestantPenalite(UUID idJoueur) {
        Long tempsFin = tempsFinPenaliteJoueur.get(idJoueur);
        if (tempsFin == null) {
            return 0;
        }
        long restant = tempsFin - System.currentTimeMillis();
        if (restant <= 100) {
            tempsFinPenaliteJoueur.remove(idJoueur);
            return 0;
        }
        return restant;
    }

    public float obtenirMultiplicateurSanteActuel(UUID idJoueur) {
        return aPenalite(idJoueur) ? multiplicateurPenalite() : 1.0f;
    }

    /**
     * Gère la consommation d'une ressource typée : fixe la spécialisation à la première
     * consommation, applique la pénalité en cas de changement de type. Retourne le
     * multiplicateur de soin à appliquer à cette consommation.
     */
    public float gererCollecteRessource(ServerPlayer joueur, TypeRessource typeRessource) {
        UUID idJoueur = joueur.getUUID();

        if (!aSpecialisation(idJoueur)) {
            specialisationJoueur.put(idJoueur, typeRessource);
            joueur.sendSystemMessage(Component.literal(
                "§a§lSpécialisation définie: " + typeRessource.obtenirNomAffichage() + "\n"
                    + "§7Consommer l'autre type de ressource activera une pénalité de "
                    + formaterTemps(dureePenaliteMs()) + "."));
            envoyerSyncSpecialisation(joueur, typeRessource);
            if (GestionnaireSousMode3.getInstance().obtenirEnregistreurDonnees() != null) {
                GestionnaireSousMode3.getInstance().obtenirEnregistreurDonnees()
                    .enregistrerChangementSpecialisation(joueur, null, typeRessource.name(), 0);
            }
            MonSubMod.JOURNALISEUR.info("Joueur {} spécialisé en {} (Sous-mode 3)",
                joueur.getName().getString(), typeRessource);
            return 1.0f;
        }

        TypeRessource specialisationActuelle = specialisationJoueur.get(idJoueur);
        if (specialisationActuelle == typeRessource) {
            return aPenalite(idJoueur) ? multiplicateurPenalite() : 1.0f;
        }

        // Changement de spécialisation : pénalité
        MonSubMod.JOURNALISEUR.info("Joueur {} a changé de spécialisation de {} vers {} (Sous-mode 3)",
            joueur.getName().getString(), specialisationActuelle, typeRessource);
        specialisationJoueur.put(idJoueur, typeRessource);
        envoyerSyncSpecialisation(joueur, typeRessource);
        if (GestionnaireSousMode3.getInstance().obtenirEnregistreurDonnees() != null) {
            GestionnaireSousMode3.getInstance().obtenirEnregistreurDonnees()
                .enregistrerChangementSpecialisation(joueur, specialisationActuelle.name(),
                    typeRessource.name(), dureePenaliteMs());
        }

        long finPenalite = System.currentTimeMillis() + dureePenaliteMs();
        tempsFinPenaliteJoueur.put(idJoueur, finPenalite);

        int pourcentage = Math.round(multiplicateurPenalite() * 100);
        joueur.sendSystemMessage(Component.literal(
            "§c§lChangement de spécialisation!\n"
                + "§eVous collectez maintenant: " + typeRessource.obtenirNomAffichage() + "\n"
                + "§c§lPénalité activée: " + formaterTemps(dureePenaliteMs()) + "\n"
                + "§7Toutes les ressources restaurent " + pourcentage + "% de santé."));

        GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
            new PaquetSynchronisationPenalite(true, idJoueur, finPenalite));

        return multiplicateurPenalite();
    }

    /** Migre les données lors d'une reconnexion avec un UUID différent (file d'attente). */
    public void migrerDonneesJoueur(UUID ancienUUID, UUID nouvelUUID) {
        TypeRessource specialisation = specialisationJoueur.remove(ancienUUID);
        if (specialisation != null) {
            specialisationJoueur.put(nouvelUUID, specialisation);
        }
        Long tempsFin = tempsFinPenaliteJoueur.remove(ancienUUID);
        if (tempsFin != null) {
            tempsFinPenaliteJoueur.put(nouvelUUID, tempsFin);
        }
    }

    /** Resynchronise l'affichage client (spécialisation + minuterie de pénalité) à la reconnexion. */
    public void synchroniserAvecClient(ServerPlayer joueur) {
        UUID idJoueur = joueur.getUUID();
        TypeRessource specialisation = specialisationJoueur.get(idJoueur);
        if (specialisation != null) {
            envoyerSyncSpecialisation(joueur, specialisation);
        }
        boolean penaliteActive = aPenalite(idJoueur);
        long finPenalite = penaliteActive ? tempsFinPenaliteJoueur.getOrDefault(idJoueur, 0L) : 0L;
        GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
            new PaquetSynchronisationPenalite(penaliteActive, idJoueur, finPenalite));
    }

    private static void envoyerSyncSpecialisation(ServerPlayer joueur, TypeRessource type) {
        GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
            new PaquetSyncSpecialisation(type));
    }

    /** 165000 ms → « 2:45 » */
    public static String formaterTemps(long millisecondes) {
        long secondes = millisecondes / 1000;
        return String.format("%d:%02d", secondes / 60, secondes % 60);
    }
}

package com.example.mysubmod.sousmodes.sousmode3;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Conditions de partie configurables par l'admin au lancement du Sous-mode 3 (menu N).
 *
 * <p>Les valeurs par défaut reproduisent <b>exactement</b> le comportement historique du
 * sous-mode : une config laissée telle quelle ne change donc rien au jeu. Toutes les
 * conditions imposées par la carte (géographie, niveau de la mer, cage, point d'apparition,
 * emplacement/quantité/type/délais des bonbons, zones) restent hors de cette config.</p>
 *
 * <p>La classe est neutre (pas de {@code @OnlyIn}) : l'écran client la construit à partir des
 * cases à cocher et le serveur l'applique après l'avoir reçue via
 * {@link com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetLancerPartieConfigureeSousMode3}.</p>
 */
public class ConfigPartieSousMode3 {

    // ==================== Groupe 1 : Durée & rythme ====================
    /** Durée de la partie en minutes (ignorée si {@link #sansLimiteTemps}). Défaut historique : 15. */
    public int dureePartieMinutes = 15;
    /** Partie sans limite de temps (survie infinie). */
    public boolean sansLimiteTemps = false;
    /** Décompte (secondes) entre le clic « Lancer » et la téléportation. Défaut historique : 10. */
    public int decompteSecondes = 10;

    // ==================== Groupe 2 : Santé & survie ====================
    /** Dégradation périodique de la santé activée. Défaut historique : oui. */
    public boolean degradationSante = true;
    /** Points de vie retirés à chaque tick de dégradation (1 cœur = 2 points). Défaut historique : 1.0. */
    public float perteSanteParTick = 1.0f;
    /** Période de la dégradation, en secondes. Défaut historique : 10. */
    public int intervalleDegradationSecondes = 10;
    /** Régénération naturelle par la nourriture (gamerule naturalRegeneration). Défaut historique : non. */
    public boolean regenerationNaturelle = false;
    /** À la mort, réapparition au point de départ au lieu du mode spectateur définitif. Défaut : non. */
    public boolean reapparitionAutorisee = false;
    /** Santé maximale des joueurs, en points (20 = 10 cœurs). Défaut : 20. */
    public int santeMaxPoints = 20;

    // ==================== Groupe 3 : Mécaniques importées (SM1/SM2) ====================
    /** Spécialisation Bleu/Rouge (Sous-mode 2). Nécessite une carte à bonbons typés. Défaut : non. */
    public boolean specialisation = false;
    /** Durée de la pénalité de changement de spécialisation, en secondes. Défaut SM2 : 165 (2 min 45). */
    public int dureePenaliteSpecialisationSecondes = 165;
    /** Multiplicateur de soin sous pénalité de spécialisation. Défaut SM2 : 0.75. */
    public float multiplicateurSantePenalite = 0.75f;
    /** Bonus de vitesse au sprint (Sous-mode 2). Défaut : non. */
    public boolean bonusSprint = false;
    /** Les joueurs choisissent leur zone de départ (parties sur carte SM1/2). Nécessite ≥1 zone Île. Défaut : non. */
    public boolean selectionZoneDepart = false;

    // ==================== Groupe 4 : Environnement & règles Minecraft ====================
    /** Jour permanent (sinon cycle jour/nuit normal). Défaut historique : oui. */
    public boolean jourPermanent = true;
    /** Dégâts de chute. Défaut : non. */
    public boolean degatsChute = false;
    /** La noyade tue le joueur (mécanique du sous-mode). Défaut historique : oui. */
    public boolean noyadeMortelle = true;
    /** Faim / perte de nourriture naturelle. Défaut : non. */
    public boolean faim = false;
    /** PvP entre joueurs. Défaut : non. */
    public boolean pvp = false;
    /** Apparition de monstres hostiles. Défaut : non. */
    public boolean monstresHostiles = false;
    /** Pluie (sinon temps clair). Défaut : non. */
    public boolean pluie = false;

    // ==================== Groupe 5 : Fin de partie & victoire ====================
    /**
     * Ordre des joueurs éliminés dans le classement final : {@code true} = par temps de survie
     * (défaut, comportement historique), {@code false} = par nombre de bonbons ramassés.
     * Les survivants sont toujours classés avant les éliminés et triés par bonbons.
     */
    public boolean classementParSurvie = true;
    /** Fin anticipée dès qu'il ne reste qu'un seul survivant. Défaut : non. */
    public boolean finAuDernierSurvivant = false;

    // ==================== Groupe 6 : Interactions & inventaire ====================
    /** Artisanat (grille 2×2 d'inventaire) autorisé. Défaut : non. */
    public boolean crafting = false;
    /** Destruction de bloc (minage) autorisée. Forcée à {@code true} si la carte a des bonbons non-visibles. Défaut : oui. */
    public boolean destructionBloc = true;
    /** Placement de bloc autorisé (replacer les blocs minés). Défaut : oui. */
    public boolean placementBloc = true;
    /** Jeter des objets (drop) autorisé. Défaut historique : non. */
    public boolean dropObjet = false;
    /** Manger un bonbon même à vie pleine (lève le blocage « dépasse le max ») ; soin plafonné au max. Défaut : non. */
    public boolean mangerDepasseMax = false;

    // ==================== Bornage / validation ====================

    /**
     * Ramène toutes les valeurs numériques dans des bornes sûres. Appelée côté serveur
     * après réception (défense en profondeur : le client ne doit jamais imposer de valeur folle).
     */
    public void borner() {
        dureePartieMinutes = clampInt(dureePartieMinutes, 1, 240);
        decompteSecondes = clampInt(decompteSecondes, 0, 120);
        perteSanteParTick = clampFloat(perteSanteParTick, 0.5f, 20.0f);
        intervalleDegradationSecondes = clampInt(intervalleDegradationSecondes, 1, 300);
        santeMaxPoints = clampInt(santeMaxPoints, 2, 40);
        dureePenaliteSpecialisationSecondes = clampInt(dureePenaliteSpecialisationSecondes, 0, 900);
        multiplicateurSantePenalite = clampFloat(multiplicateurSantePenalite, 0.1f, 1.0f);
    }

    /**
     * Vérifie qu'une partie lancée avec cette config peut se terminer d'elle-même.
     * <ul>
     *   <li>Avec limite de temps : toujours (la minuterie termine).</li>
     *   <li>Sans limite de temps : seulement si la mort est définitive (pas de réapparition),
     *       qu'une cause de mort garantie existe (dégradation de la santé) et que la
     *       régénération naturelle ne peut pas la neutraliser (la régén vanilla soigne
     *       plus vite que la dégradation par défaut).</li>
     * </ul>
     */
    public boolean peutSeTerminer() {
        if (!sansLimiteTemps) {
            return true;
        }
        return !reapparitionAutorisee && degradationSante && !regenerationNaturelle;
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clampFloat(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ==================== Sérialisation réseau ====================

    /** Écrit la config dans un tampon (ordre miroir de {@link #lire}). */
    public void ecrire(FriendlyByteBuf tampon) {
        // Groupe 1
        tampon.writeVarInt(dureePartieMinutes);
        tampon.writeBoolean(sansLimiteTemps);
        tampon.writeVarInt(decompteSecondes);
        // Groupe 2
        tampon.writeBoolean(degradationSante);
        tampon.writeFloat(perteSanteParTick);
        tampon.writeVarInt(intervalleDegradationSecondes);
        tampon.writeBoolean(regenerationNaturelle);
        tampon.writeBoolean(reapparitionAutorisee);
        tampon.writeVarInt(santeMaxPoints);
        // Groupe 3
        tampon.writeBoolean(specialisation);
        tampon.writeVarInt(dureePenaliteSpecialisationSecondes);
        tampon.writeFloat(multiplicateurSantePenalite);
        tampon.writeBoolean(bonusSprint);
        tampon.writeBoolean(selectionZoneDepart);
        // Groupe 4
        tampon.writeBoolean(jourPermanent);
        tampon.writeBoolean(degatsChute);
        tampon.writeBoolean(noyadeMortelle);
        tampon.writeBoolean(faim);
        tampon.writeBoolean(pvp);
        tampon.writeBoolean(monstresHostiles);
        tampon.writeBoolean(pluie);
        // Groupe 5
        tampon.writeBoolean(classementParSurvie);
        tampon.writeBoolean(finAuDernierSurvivant);
        // Groupe 6
        tampon.writeBoolean(crafting);
        tampon.writeBoolean(destructionBloc);
        tampon.writeBoolean(placementBloc);
        tampon.writeBoolean(dropObjet);
        tampon.writeBoolean(mangerDepasseMax);
    }

    /** Lit une config depuis un tampon (ordre miroir de {@link #ecrire}). */
    public static ConfigPartieSousMode3 lire(FriendlyByteBuf tampon) {
        ConfigPartieSousMode3 c = new ConfigPartieSousMode3();
        // Groupe 1
        c.dureePartieMinutes = tampon.readVarInt();
        c.sansLimiteTemps = tampon.readBoolean();
        c.decompteSecondes = tampon.readVarInt();
        // Groupe 2
        c.degradationSante = tampon.readBoolean();
        c.perteSanteParTick = tampon.readFloat();
        c.intervalleDegradationSecondes = tampon.readVarInt();
        c.regenerationNaturelle = tampon.readBoolean();
        c.reapparitionAutorisee = tampon.readBoolean();
        c.santeMaxPoints = tampon.readVarInt();
        // Groupe 3
        c.specialisation = tampon.readBoolean();
        c.dureePenaliteSpecialisationSecondes = tampon.readVarInt();
        c.multiplicateurSantePenalite = tampon.readFloat();
        c.bonusSprint = tampon.readBoolean();
        c.selectionZoneDepart = tampon.readBoolean();
        // Groupe 4
        c.jourPermanent = tampon.readBoolean();
        c.degatsChute = tampon.readBoolean();
        c.noyadeMortelle = tampon.readBoolean();
        c.faim = tampon.readBoolean();
        c.pvp = tampon.readBoolean();
        c.monstresHostiles = tampon.readBoolean();
        c.pluie = tampon.readBoolean();
        // Groupe 5
        c.classementParSurvie = tampon.readBoolean();
        c.finAuDernierSurvivant = tampon.readBoolean();
        // Groupe 6
        c.crafting = tampon.readBoolean();
        c.destructionBloc = tampon.readBoolean();
        c.placementBloc = tampon.readBoolean();
        c.dropObjet = tampon.readBoolean();
        c.mangerDepasseMax = tampon.readBoolean();
        return c;
    }
}

package com.example.mysubmod.sousmodes.sousmode3.donnees;

import com.example.mysubmod.MonSubMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enregistreur de données basé sur CSV pour Sous-mode 3 (même système de logs
 * que pour les autres sous-modes).
 * Format: horodatage,joueur,type_evenement,x,y,z,sante,donnees_supplementaires
 */
public class EnregistreurDonneesSousMode3 {
    private final Map<String, FileWriter> enregistreursJoueurs = new ConcurrentHashMap<>();
    private String idSessionPartie;
    private File repertoirePartie;
    private static final DateTimeFormatter FORMAT_HORODATAGE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public void demarrerNouvellePartie() {
        idSessionPartie = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        File repertoireMods = new File(".", "donnees_monsubmod");
        repertoirePartie = new File(repertoireMods, "sousmode3_partie_" + idSessionPartie);

        if (!repertoirePartie.exists()) {
            repertoirePartie.mkdirs();
        }

        MonSubMod.JOURNALISEUR.info("Enregistrement des données CSV démarré pour la session de partie Sous-mode 3 : {}", idSessionPartie);
    }

    public void terminerPartie() {
        for (FileWriter enregistreur : enregistreursJoueurs.values()) {
            try {
                enregistreur.close();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la fermeture de l'enregistreur de joueur", e);
            }
        }
        enregistreursJoueurs.clear();

        MonSubMod.JOURNALISEUR.info("Enregistrement des données terminé pour la session de partie Sous-mode 3 : {}", idSessionPartie);
    }

    private FileWriter obtenirEnregistreurJoueur(ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString();
        return enregistreursJoueurs.computeIfAbsent(nomJoueur, nom -> {
            try {
                File fichierJoueur = new File(repertoirePartie, nom + "_journal.csv");
                FileWriter enregistreur = new FileWriter(fichierJoueur, true);
                enregistreur.write("horodatage,joueur,type_evenement,x,y,z,sante,donnees_supplementaires\n");
                enregistreur.flush();
                return enregistreur;
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la création de l'enregistreur de joueur pour {}", nom, e);
                return null;
            }
        });
    }

    private void ecrireLigne(ServerPlayer joueur, String typeEvenement, String donneesSupplementaires) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur == null) {
            return;
        }
        try {
            String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
            String ligneCsv = String.format(Locale.US, "%s,%s,%s,%.2f,%.2f,%.2f,%.1f,%s\n",
                horodatage,
                echapperCsv(joueur.getName().getString()),
                echapperCsv(typeEvenement), // « RECONNECTE (-3 ticks, -1.5 PV) » contient une virgule
                joueur.getX(),
                joueur.getY(),
                joueur.getZ(),
                joueur.getHealth(),
                echapperCsv(donneesSupplementaires != null ? donneesSupplementaires : ""));
            enregistreur.write(ligneCsv);
            enregistreur.flush();
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de {} pour le joueur {}",
                typeEvenement, joueur.getName().getString(), e);
        }
    }

    /** Échappe un champ CSV : entoure de guillemets et double les guillemets internes si
     *  le champ contient une virgule, un guillemet ou un saut de ligne. Les données
     *  supplémentaires peuvent contenir des noms de parcelles à caractères arbitraires,
     *  qui casseraient sinon la structure du fichier (injection CSV). */
    private static String echapperCsv(String champ) {
        if (champ == null) {
            return "";
        }
        if (champ.indexOf(',') < 0 && champ.indexOf('"') < 0
            && champ.indexOf('\n') < 0 && champ.indexOf('\r') < 0) {
            return champ;
        }
        return '"' + champ.replace("\"", "\"\"") + '"';
    }

    public void enregistrerPositionJoueur(ServerPlayer joueur) {
        ecrireLigne(joueur, "DEPLACEMENT", "");
    }

    public void enregistrerConsommationBonbon(ServerPlayer joueur) {
        ecrireLigne(joueur, "BONBON_CONSOMME", "");
    }

    /** Consommation d'un bonbon avec contexte de spécialisation (option du menu N) */
    public void enregistrerConsommationBonbon(ServerPlayer joueur, String type, boolean penalite, float soin) {
        ecrireLigne(joueur, "BONBON_CONSOMME", String.format(Locale.US,
            "type=%s;penalite=%b;soin=%.2f", type, penalite, soin));
    }

    /** Spécialisation définie ou changée (ancienne = vide à la première consommation typée) */
    public void enregistrerChangementSpecialisation(ServerPlayer joueur, String ancienne, String nouvelle,
                                                    long dureePenaliteMs) {
        ecrireLigne(joueur, "SPECIALISATION", String.format(Locale.US,
            "ancienne=%s;nouvelle=%s;penalite_ms=%d", ancienne != null ? ancienne : "", nouvelle, dureePenaliteMs));
    }

    /** Choix de la zone de départ (option du menu N) — mode MANUELLE ou AUTOMATIQUE */
    public void enregistrerSelectionZone(ServerPlayer joueur, String zone, String mode) {
        ecrireLigne(joueur, "SELECTION_ZONE", "zone=" + zone + ";mode=" + mode);
    }

    /** Réapparition au point de départ après une mort (option du menu N) */
    public void enregistrerReapparition(ServerPlayer joueur) {
        ecrireLigne(joueur, "REAPPARITION", "");
    }

    /** Objet jeté volontairement au sol (option « jeter des objets ») */
    public void enregistrerObjetJete(ServerPlayer joueur, String objet, int quantite) {
        ecrireLigne(joueur, "OBJET_JETE", String.format(Locale.US, "objet=%s;quantite=%d", objet, quantite));
    }

    /** Inventaire déposé au sol à la mort (option « drop de l'inventaire à la mort ») */
    public void enregistrerDropInventaireMort(ServerPlayer joueur, int nombrePiles) {
        ecrireLigne(joueur, "INVENTAIRE_DEPOSE", String.format(Locale.US, "piles=%d", nombrePiles));
    }

    public void enregistrerCollecteBonbon(ServerPlayer joueur, BlockPos position) {
        ecrireLigne(joueur, "BONBON_RAMASSE", String.format(Locale.US, "pos_bonbon=%d;%d;%d",
            position.getX(), position.getY(), position.getZ()));
    }

    public void enregistrerMinageBonbonCache(ServerPlayer joueur, BlockPos position, int quantite) {
        ecrireLigne(joueur, "BONBON_NON_VISIBLE_MINE", String.format(Locale.US, "pos_bloc=%d;%d;%d;quantite=%d",
            position.getX(), position.getY(), position.getZ(), quantite));
    }

    public void enregistrerMinageBloc(ServerPlayer joueur, BlockPos position, String nomBloc) {
        ecrireLigne(joueur, "BLOC_MINE", String.format(Locale.US, "pos_bloc=%d;%d;%d;bloc=%s",
            position.getX(), position.getY(), position.getZ(), nomBloc));
    }

    public void enregistrerPlacementBloc(ServerPlayer joueur, BlockPos position, String nomBloc) {
        ecrireLigne(joueur, "BLOC_PLACE", String.format(Locale.US, "pos_bloc=%d;%d;%d;bloc=%s",
            position.getX(), position.getY(), position.getZ(), nomBloc));
    }

    public void enregistrerChangementSante(ServerPlayer joueur, float ancienneSante, float nouvelleSante) {
        ecrireLigne(joueur, "CHANGEMENT_SANTE",
            String.format(Locale.US, "ancienne_sante=%.1f;nouvelle_sante=%.1f", ancienneSante, nouvelleSante));
    }

    public void enregistrerMortJoueur(ServerPlayer joueur) {
        ecrireLigne(joueur, "MORT", "");
    }

    /** Mort avec cause (SANTE, NOYADE, ou identifiant de dégât vanilla : fall, player, starve...) */
    public void enregistrerMortJoueur(ServerPlayer joueur, String cause) {
        ecrireLigne(joueur, "MORT", "cause=" + cause);
    }

    /**
     * Écrit le fichier `config_partie.csv` de la session : la carte jouée et la valeur de
     * chaque condition de partie choisie au menu N. Rend chaque session auto-descriptive
     * pour l'analyse des données.
     */
    public void enregistrerConfigPartie(com.example.mysubmod.sousmodes.sousmode3.ConfigPartieSousMode3 config,
                                        String nomCarte) {
        if (repertoirePartie == null || config == null) {
            return;
        }
        File fichier = new File(repertoirePartie, "config_partie.csv");
        try (FileWriter enregistreur = new FileWriter(fichier, false)) {
            enregistreur.write("option,valeur\n");
            enregistreur.write("horodatage," + LocalDateTime.now().format(FORMAT_HORODATAGE) + "\n");
            enregistreur.write("carte," + (nomCarte != null ? nomCarte : "") + "\n");
            enregistreur.write("duree_partie_minutes," + config.dureePartieMinutes + "\n");
            enregistreur.write("sans_limite_temps," + config.sansLimiteTemps + "\n");
            enregistreur.write("decompte_secondes," + config.decompteSecondes + "\n");
            enregistreur.write("degradation_sante," + config.degradationSante + "\n");
            enregistreur.write(String.format(Locale.US, "perte_sante_par_tick,%.1f%n", config.perteSanteParTick));
            enregistreur.write("intervalle_degradation_secondes," + config.intervalleDegradationSecondes + "\n");
            enregistreur.write("regeneration_naturelle," + config.regenerationNaturelle + "\n");
            enregistreur.write("reapparition_autorisee," + config.reapparitionAutorisee + "\n");
            enregistreur.write("sante_max_points," + config.santeMaxPoints + "\n");
            enregistreur.write("specialisation," + config.specialisation + "\n");
            enregistreur.write("duree_penalite_specialisation_secondes," + config.dureePenaliteSpecialisationSecondes + "\n");
            enregistreur.write(String.format(Locale.US, "multiplicateur_sante_penalite,%.2f%n", config.multiplicateurSantePenalite));
            enregistreur.write("bonus_sprint," + config.bonusSprint + "\n");
            enregistreur.write("selection_zone_depart," + config.selectionZoneDepart + "\n");
            enregistreur.write("jour_permanent," + config.jourPermanent + "\n");
            enregistreur.write("degats_chute," + config.degatsChute + "\n");
            enregistreur.write("noyade_mortelle," + config.noyadeMortelle + "\n");
            enregistreur.write("faim," + config.faim + "\n");
            enregistreur.write("pvp," + config.pvp + "\n");
            enregistreur.write("chat_joueurs," + config.chatJoueurs + "\n");
            enregistreur.write("pluie," + config.pluie + "\n");
            enregistreur.write("classement_par_survie," + config.classementParSurvie + "\n");
            enregistreur.write("fin_au_dernier_survivant," + config.finAuDernierSurvivant + "\n");
            enregistreur.write("crafting," + config.crafting + "\n");
            enregistreur.write("destruction_bloc," + config.destructionBloc + "\n");
            enregistreur.write("placement_bloc," + config.placementBloc + "\n");
            enregistreur.write("drop_objet," + config.dropObjet + "\n");
            enregistreur.write("drop_inventaire_mort," + config.dropInventaireMort + "\n");
            enregistreur.write("manger_depasse_max," + config.mangerDepasseMax + "\n");
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors de l'écriture de la config de partie", e);
        }
        MonSubMod.JOURNALISEUR.info("Config de partie enregistrée dans {}", fichier.getPath());
    }

    public void enregistrerActionJoueur(ServerPlayer joueur, String action) {
        ecrireLigne(joueur, action, "");
    }

    public String obtenirIdSessionPartie() {
        return idSessionPartie;
    }

    public File obtenirRepertoirePartie() {
        return repertoirePartie;
    }
}

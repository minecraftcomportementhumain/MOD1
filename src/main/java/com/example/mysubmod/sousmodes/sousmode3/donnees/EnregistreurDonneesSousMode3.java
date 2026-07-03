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
                joueur.getName().getString(),
                typeEvenement,
                joueur.getX(),
                joueur.getY(),
                joueur.getZ(),
                joueur.getHealth(),
                donneesSupplementaires != null ? donneesSupplementaires : "");
            enregistreur.write(ligneCsv);
            enregistreur.flush();
        } catch (IOException e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de {} pour le joueur {}",
                typeEvenement, joueur.getName().getString(), e);
        }
    }

    public void enregistrerPositionJoueur(ServerPlayer joueur) {
        ecrireLigne(joueur, "DEPLACEMENT", "");
    }

    public void enregistrerConsommationBonbon(ServerPlayer joueur) {
        ecrireLigne(joueur, "BONBON_CONSOMME", "");
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

package com.example.mysubmod.sousmodes.sousmode1.donnees;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.sousmodes.sousmode1.iles.TypeIle;
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
 * Enregistreur de données basé sur CSV pour Sous-mode 1
 * Format: horodatage,joueur,type_evenement,x,y,z,sante,donnees_supplementaires
 */
public class EnregistreurDonneesSousMode1 {
    private final Map<String, FileWriter> enregistreursJoueurs = new ConcurrentHashMap<>();
    private String idSessionPartie;
    private File repertoirePartie;
    private static final DateTimeFormatter FORMAT_HORODATAGE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public void demarrerNouvellePartie() {
        idSessionPartie = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        // Créer le répertoire de la partie
        File repertoireMods = new File(".", "donnees_monsubmod");
        repertoirePartie = new File(repertoireMods, "sousmode1_partie_" + idSessionPartie);

        if (!repertoirePartie.exists()) {
            repertoirePartie.mkdirs();
        }

        MonSubMod.JOURNALISEUR.info("Enregistrement des données CSV démarré pour la session de partie Sous-mode 1 : {}", idSessionPartie);
    }

    public void terminerPartie() {
        // Fermer tous les enregistreurs de joueurs
        for (FileWriter enregistreur : enregistreursJoueurs.values()) {
            try {
                enregistreur.close();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la fermeture de l'enregistreur de joueur", e);
            }
        }
        enregistreursJoueurs.clear();

        MonSubMod.JOURNALISEUR.info("Enregistrement des données terminé pour la session de partie Sous-mode 1 : {}", idSessionPartie);
    }

    private FileWriter obtenirEnregistreurJoueur(ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString();
        return enregistreursJoueurs.computeIfAbsent(nomJoueur, nom -> {
            try {
                File fichierJoueur = new File(repertoirePartie, nom + "_journal.csv");
                FileWriter enregistreur = new FileWriter(fichierJoueur, true);

                // Écrire l'en-tête CSV
                enregistreur.write("horodatage,joueur,type_evenement,x,y,z,sante,donnees_supplementaires\n");
                enregistreur.flush();
                return enregistreur;
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la création de l'enregistreur de joueur pour {}", nom, e);
                return null;
            }
        });
    }

    /**
     * Enregistrer le changement de position du joueur
     */
    public void enregistrerPositionJoueur(ServerPlayer joueur) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String ligneCsv = String.format(Locale.US, "%s,%s,DEPLACEMENT,%.2f,%.2f,%.2f,%.1f,\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth());
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de la position pour le joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer la consommation de bonbon (manger)
     */
    public void enregistrerConsommationBonbon(ServerPlayer joueur) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String ligneCsv = String.format(Locale.US, "%s,%s,BONBON_CONSOMME,%.2f,%.2f,%.2f,%.1f,\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth());
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de la consommation de bonbon pour le joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer la collecte de bonbon (ramassage)
     */
    public void enregistrerCollecteBonbon(ServerPlayer joueur, BlockPos position) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String donneesSupplementaires = String.format(Locale.US, "pos_bonbon=%d;%d;%d",
                    position.getX(), position.getY(), position.getZ());
                String ligneCsv = String.format(Locale.US, "%s,%s,BONBON_RAMASSE,%.2f,%.2f,%.2f,%.1f,%s\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth(),
                    donneesSupplementaires);
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de la collecte de bonbon pour le joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer le changement de santé (dégradation ou guérison)
     */
    public void enregistrerChangementSante(ServerPlayer joueur, float ancienneSante, float nouvelleSante) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String donneesSupplementaires = String.format(Locale.US, "ancienne_sante=%.1f;nouvelle_sante=%.1f", ancienneSante, nouvelleSante);
                String ligneCsv = String.format(Locale.US, "%s,%s,CHANGEMENT_SANTE,%.2f,%.2f,%.2f,%.1f,%s\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    nouvelleSante,
                    donneesSupplementaires);
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement du changement de santé pour le joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer la mort du joueur
     */
    public void enregistrerMortJoueur(ServerPlayer joueur) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String ligneCsv = String.format(Locale.US, "%s,%s,MORT,%.2f,%.2f,%.2f,0.0,\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ());
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de la mort du joueur pour {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer la sélection d'île au début de la partie
     */
    public void enregistrerSelectionIle(ServerPlayer joueur, TypeIle ile) {
        enregistrerSelectionIle(joueur, ile, "MANUELLE");
    }

    /**
     * Enregistrer la sélection d'île au début de la partie avec le type de sélection
     */
    public void enregistrerSelectionIle(ServerPlayer joueur, TypeIle ile, String typeSelection) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String donneesSupplementaires = String.format(Locale.US, "ile=%s;type_selection=%s", ile.name(), typeSelection);
                String ligneCsv = String.format(Locale.US, "%s,%s,SELECTION_ILE,%.2f,%.2f,%.2f,%.1f,%s\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth(),
                    donneesSupplementaires);
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de la sélection d'île pour le joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer les actions du joueur (événements de connexion/déconnexion)
     */
    public void enregistrerActionJoueur(ServerPlayer joueur, String action) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String ligneCsv = String.format(Locale.US, "%s,%s,%s,%.2f,%.2f,%.2f,%.1f,\n",
                    horodatage,
                    joueur.getName().getString(),
                    action, // CONNECTE, DECONNECTE, RECONNECTE
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth());
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de l'action du joueur pour {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer l'apparition de bonbon (événement global, non spécifique au joueur)
     */
    public void enregistrerApparitionBonbon(BlockPos position) {
        // Cela pourrait être enregistré dans un CSV d'événements globaux séparé si nécessaire
    }

    public String obtenirIdSessionPartie() {
        return idSessionPartie;
    }
}

package com.example.mysubmod.sousmodes.sousmode2.donnees;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.sousmodes.sousmode2.TypeRessource;
import com.example.mysubmod.sousmodes.sousmode2.iles.TypeIle;
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
 * Enregistreur de données CSV pour SousMode2
 * Format: horodatage,joueur,type_evenement,x,y,z,sante,donnees_supplementaires
 * Étendu pour suivre les types de ressources (A/B), les changements de spécialisation et les pénalités
 */
public class EnregistreurDonneesSousMode2 {
    private final Map<String, FileWriter> enregistreursJoueurs = new ConcurrentHashMap<>();
    private String idSessionPartie;
    private File repertoirePartie;
    private static final DateTimeFormatter FORMAT_HORODATAGE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public void demarrerNouvellePartie() {
        idSessionPartie = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        // Créer le répertoire de partie
        File repertoireMods = new File(".", "donnees_monsubmod");
        repertoirePartie = new File(repertoireMods, "sousmode2_partie_" + idSessionPartie);

        if (!repertoirePartie.exists()) {
            repertoirePartie.mkdirs();
        }

        MonSubMod.JOURNALISEUR.info("Démarrage de l'enregistrement CSV pour la session SousMode2: {}", idSessionPartie);
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

        MonSubMod.JOURNALISEUR.info("Fin de l'enregistrement des données pour la session SousMode2: {}", idSessionPartie);
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
                MonSubMod.JOURNALISEUR.error("Erreur lors de la création de l'enregistreur pour le joueur {}", nom, e);
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
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de la position du joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer la consommation de bonbon (manger) avec le type de ressource
     */
    public void enregistrerConsommationBonbon(ServerPlayer joueur, TypeRessource typeRessource) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String donneesSupp = String.format(Locale.US, "type_ressource=%s", typeRessource.name());
                String ligneCsv = String.format(Locale.US, "%s,%s,BONBON_CONSOMME,%.2f,%.2f,%.2f,%.1f,%s\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth(),
                    donneesSupp);
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de la consommation de bonbon du joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer le ramassage de bonbon avec le type de ressource
     */
    public void enregistrerRamassageBonbon(ServerPlayer joueur, BlockPos position, TypeRessource typeRessource) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String donneesSupp = String.format(Locale.US, "pos_bonbon=%d;%d;%d;type_ressource=%s",
                    position.getX(), position.getY(), position.getZ(), typeRessource.name());
                String ligneCsv = String.format(Locale.US, "%s,%s,BONBON_RAMASSE,%.2f,%.2f,%.2f,%.1f,%s\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth(),
                    donneesSupp);
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement du ramassage de bonbon du joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer l'initialisation de la spécialisation (première ressource collectée)
     */
    public void enregistrerInitialisationSpecialisation(ServerPlayer joueur, TypeRessource typeRessource) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String donneesSupp = String.format(Locale.US, "specialisation=%s;premiere_collecte=vrai", typeRessource.name());
                String ligneCsv = String.format(Locale.US, "%s,%s,INIT_SPECIALISATION,%.2f,%.2f,%.2f,%.1f,%s\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth(),
                    donneesSupp);
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de l'init. de spécialisation du joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer le changement de spécialisation (passage d'un type à un autre)
     */
    public void enregistrerChangementSpecialisation(ServerPlayer joueur, TypeRessource ancienType, TypeRessource nouveauType) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String donneesSupp = String.format(Locale.US, "ancienne_specialisation=%s;nouvelle_specialisation=%s", ancienType.name(), nouveauType.name());
                String ligneCsv = String.format(Locale.US, "%s,%s,CHANGEMENT_SPECIALISATION,%.2f,%.2f,%.2f,%.1f,%s\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth(),
                    donneesSupp);
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement du changement de spécialisation du joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer l'application d'une pénalité (quand le joueur change de spécialisation)
     */
    public void enregistrerPenaliteAppliquee(ServerPlayer joueur, long dureeMs) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String donneesSupp = String.format(Locale.US, "duree_penalite_ms=%d;duree_penalite_sec=%d", dureeMs, dureeMs / 1000);
                String ligneCsv = String.format(Locale.US, "%s,%s,PENALITE_APPLIQUEE,%.2f,%.2f,%.2f,%.1f,%s\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth(),
                    donneesSupp);
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de la pénalité appliquée au joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer l'expiration d'une pénalité
     */
    public void enregistrerPenaliteExpiree(ServerPlayer joueur) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String ligneCsv = String.format(Locale.US, "%s,%s,PENALITE_EXPIREE,%.2f,%.2f,%.2f,%.1f,\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth());
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de la pénalité expirée du joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer le changement de santé avec le contexte du type de ressource (pour suivre les effets de pénalité)
     */
    public void enregistrerChangementSante(ServerPlayer joueur, float ancienneSante, float nouvelleSante, TypeRessource typeRessource, boolean aPenalite) {
        FileWriter enregistreur = obtenirEnregistreurJoueur(joueur);
        if (enregistreur != null) {
            try {
                String horodatage = LocalDateTime.now().format(FORMAT_HORODATAGE);
                String donneesSupp = String.format(Locale.US, "ancienne_sante=%.1f;nouvelle_sante=%.1f;type_ressource=%s;a_penalite=%s",
                    ancienneSante, nouvelleSante, typeRessource.name(), aPenalite ? "vrai" : "faux");
                String ligneCsv = String.format(Locale.US, "%s,%s,CHANGEMENT_SANTE,%.2f,%.2f,%.2f,%.1f,%s\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    nouvelleSante,
                    donneesSupp);
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement du changement de santé du joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer la mort d'un joueur
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
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de la mort du joueur {}", joueur.getName().getString(), e);
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
                String donneesSupp = String.format(Locale.US, "ile=%s;type_selection=%s", ile.name(), typeSelection);
                String ligneCsv = String.format(Locale.US, "%s,%s,SELECTION_ILE,%.2f,%.2f,%.2f,%.1f,%s\n",
                    horodatage,
                    joueur.getName().getString(),
                    joueur.getX(),
                    joueur.getY(),
                    joueur.getZ(),
                    joueur.getHealth(),
                    donneesSupp);
                enregistreur.write(ligneCsv);
                enregistreur.flush();
            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de la sélection d'île du joueur {}", joueur.getName().getString(), e);
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
                MonSubMod.JOURNALISEUR.error("Erreur lors de l'enregistrement de l'action du joueur {}", joueur.getName().getString(), e);
            }
        }
    }

    /**
     * Enregistrer l'apparition d'un bonbon avec le type de ressource (événement global, pas spécifique au joueur)
     */
    public void enregistrerApparitionBonbon(BlockPos position, TypeRessource typeRessource) {
        // Ceci pourrait être enregistré dans un CSV d'événements globaux séparé si nécessaire
        MonSubMod.JOURNALISEUR.info("Apparition de bonbon SousMode2: {} à ({},{},{}) - Type: {}",
            typeRessource.obtenirNomAffichage(), position.getX(), position.getY(), position.getZ(), typeRessource.name());
    }

    public String obtenirIdSessionPartie() {
        return idSessionPartie;
    }

}

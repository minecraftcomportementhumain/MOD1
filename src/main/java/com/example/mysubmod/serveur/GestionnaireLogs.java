package com.example.mysubmod.serveur;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.reseau.PaquetListeLogs;
import com.example.mysubmod.reseau.GestionnaireReseau;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GestionnaireLogs {
    private static final String REPERTOIRE_BASE_LOGS = "donnees_monsubmod";

    public static void envoyerListeLogs(ServerPlayer joueur) {
        envoyerListeLogs(joueur, 1); // Par défaut Sous-mode 1 pour rétrocompatibilité
    }

    public static void envoyerListeLogs(ServerPlayer joueur, int numeroSubMode) {
        List<String> dossiersLogs = obtenirDossiersLogs(numeroSubMode);
        GestionnaireReseau.INSTANCE.send(
            PacketDistributor.PLAYER.with(() -> joueur),
            new PaquetListeLogs(dossiersLogs)
        );
    }

    public static List<String> obtenirDossiersLogs() {
        return obtenirDossiersLogs(1); // Par défaut Sous-mode 1 pour rétrocompatibilité
    }

    public static List<String> obtenirDossiersLogs(int numeroSubMode) {
        List<String> dossiers = new ArrayList<>();
        File repertoireBase = new File(REPERTOIRE_BASE_LOGS);

        if (!repertoireBase.exists() || !repertoireBase.isDirectory()) {
            return dossiers;
        }

        File[] fichiers = repertoireBase.listFiles();
        if (fichiers == null) {
            return dossiers;
        }

        String prefixe = "sousmode" + numeroSubMode + "_partie_";
        for (File fichier : fichiers) {
            if (fichier.isDirectory() && fichier.getName().startsWith(prefixe)) {
                dossiers.add(fichier.getName());
            }
        }

        // Trier par nom (qui inclut l'horodatage) - plus récent en premier
        dossiers.sort(Comparator.reverseOrder());

        return dossiers;
    }

    public static void telechargerLogs(ServerPlayer joueur, String nomDossier, boolean telechargerTout) {
        try {
            File repertoireBase = new File(REPERTOIRE_BASE_LOGS);
            if (!repertoireBase.exists()) {
                joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cAucun dossier de logs trouvé"));
                return;
            }

            if (telechargerTout) {
                envoyerTousLogsAuClient(joueur, repertoireBase);
            } else if (nomDossier != null) {
                envoyerDossierUniqueAuClient(joueur, new File(repertoireBase, nomDossier));
            }
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors du téléchargement des logs", e);
            joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cErreur lors du téléchargement des logs"));
        }
    }

    private static void envoyerTousLogsAuClient(ServerPlayer joueur, File repertoireBase) throws IOException {
        // Créer le ZIP en mémoire
        java.io.ByteArrayOutputStream fluxSortieTableauOctets = new java.io.ByteArrayOutputStream();

        try (ZipOutputStream fluxSortieZip = new ZipOutputStream(fluxSortieTableauOctets)) {
            File[] dossiers = repertoireBase.listFiles();
            if (dossiers != null) {
                for (File dossier : dossiers) {
                    if (dossier.isDirectory() && dossier.getName().startsWith("sousmode1_partie_")) {
                        ajouterDossierAuZip(dossier, dossier.getName(), fluxSortieZip);
                    }
                }
            }
        }

        // Envoyer les données ZIP au client
        byte[] donneesZip = fluxSortieTableauOctets.toByteArray();
        GestionnaireReseau.INSTANCE.send(
            PacketDistributor.PLAYER.with(() -> joueur),
            new com.example.mysubmod.reseau.PaquetDonneesLogs("all_logs.zip", donneesZip)
        );

        MonSubMod.JOURNALISEUR.info("Envoi du ZIP de tous les logs ({} octets) au client {}", donneesZip.length, joueur.getName().getString());
    }

    private static void envoyerDossierUniqueAuClient(ServerPlayer joueur, File dossier) throws IOException {
        if (!dossier.exists() || !dossier.isDirectory()) {
            joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cDossier non trouvé: " + dossier.getName()));
            return;
        }

        // Créer le ZIP en mémoire
        java.io.ByteArrayOutputStream fluxSortieTableauOctets = new java.io.ByteArrayOutputStream();

        try (ZipOutputStream fluxSortieZip = new ZipOutputStream(fluxSortieTableauOctets)) {
            ajouterDossierAuZip(dossier, dossier.getName(), fluxSortieZip);
        }

        // Envoyer les données ZIP au client
        byte[] donneesZip = fluxSortieTableauOctets.toByteArray();
        String nomFichier = dossier.getName() + ".zip";

        GestionnaireReseau.INSTANCE.send(
            PacketDistributor.PLAYER.with(() -> joueur),
            new com.example.mysubmod.reseau.PaquetDonneesLogs(nomFichier, donneesZip)
        );

        MonSubMod.JOURNALISEUR.info("Envoi du dossier de logs {} ({} octets) au client {}", dossier.getName(), donneesZip.length, joueur.getName().getString());
    }

    private static void ajouterDossierAuZip(File dossier, String cheminParent, ZipOutputStream fluxSortieZip) throws IOException {
        File[] fichiers = dossier.listFiles();
        if (fichiers == null) return;

        for (File fichier : fichiers) {
            if (fichier.isDirectory()) {
                ajouterDossierAuZip(fichier, cheminParent + "/" + fichier.getName(), fluxSortieZip);
            } else {
                try (FileInputStream fluxEntreeFichier = new FileInputStream(fichier)) {
                    ZipEntry entreeZip = new ZipEntry(cheminParent + "/" + fichier.getName());
                    fluxSortieZip.putNextEntry(entreeZip);

                    byte[] tampon = new byte[1024];
                    int longueur;
                    while ((longueur = fluxEntreeFichier.read(tampon)) > 0) {
                        fluxSortieZip.write(tampon, 0, longueur);
                    }

                    fluxSortieZip.closeEntry();
                }
            }
        }
    }

    public static void supprimerLogs(ServerPlayer joueur, String nomDossier, boolean supprimerTout) {
        try {
            File repertoireBase = new File(REPERTOIRE_BASE_LOGS);
            if (!repertoireBase.exists()) {
                joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cAucun dossier de logs trouvé"));
                return;
            }

            if (supprimerTout) {
                supprimerTousLogs(joueur, repertoireBase);
            } else if (nomDossier != null) {
                supprimerDossierUnique(joueur, new File(repertoireBase, nomDossier));
            }
        } catch (Exception e) {
            MonSubMod.JOURNALISEUR.error("Erreur lors de la suppression des logs", e);
            joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cErreur lors de la suppression des logs"));
        }
    }

    private static void supprimerTousLogs(ServerPlayer joueur, File repertoireBase) throws IOException {
        int nombreSupprimes = 0;
        File[] dossiers = repertoireBase.listFiles();

        if (dossiers != null) {
            for (File dossier : dossiers) {
                if (dossier.isDirectory() && dossier.getName().startsWith("sousmode1_partie_")) {
                    supprimerRepertoire(dossier);
                    nombreSupprimes++;
                }
            }
        }

        joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "§aTous les logs ont été supprimés (" + nombreSupprimes + " dossier(s))"));
        MonSubMod.JOURNALISEUR.info("Tous les dossiers de logs supprimés: {} dossiers", nombreSupprimes);
    }

    private static void supprimerDossierUnique(ServerPlayer joueur, File dossier) throws IOException {
        if (!dossier.exists() || !dossier.isDirectory()) {
            joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cDossier non trouvé: " + dossier.getName()));
            return;
        }

        supprimerRepertoire(dossier);

        joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "§aDossier supprimé: " + dossier.getName()));
        MonSubMod.JOURNALISEUR.info("Dossier de logs supprimé: {}", dossier.getName());
    }

    private static void supprimerRepertoire(File repertoire) throws IOException {
        Path chemin = repertoire.toPath();

        Files.walkFileTree(chemin, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path fichier, BasicFileAttributes attributs) throws IOException {
                Files.delete(fichier);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path rep, IOException exc) throws IOException {
                Files.delete(rep);
                return FileVisitResult.CONTINUE;
            }
        });
    }

}

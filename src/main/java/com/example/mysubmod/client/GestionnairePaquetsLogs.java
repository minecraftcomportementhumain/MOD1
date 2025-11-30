package com.example.mysubmod.client;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.client.gui.EcranGestionLogs;
import com.example.mysubmod.reseau.PaquetListeLogs;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class GestionnairePaquetsLogs {
    public static void gererPaquetListeLogs(PaquetListeLogs paquet) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new EcranGestionLogs(paquet.obtenirDossiersJournaux()));
        });
    }

    public static void gererDonneesLog(String nomFichier, byte[] données) {
        Minecraft.getInstance().execute(() -> {
            try {
                // Sauvegarder dans le dossier Téléchargements du client
                String dossierUtilisateur = System.getProperty("user.home");
                File dossierTéléchargements = new File(dossierUtilisateur, "Downloads");

                if (!dossierTéléchargements.exists()) {
                    dossierTéléchargements = new File(dossierUtilisateur); // Repli sur le dossier utilisateur
                }

                File fichierSortie = new File(dossierTéléchargements, nomFichier);

                try (FileOutputStream fos = new FileOutputStream(fichierSortie)) {
                    fos.write(données);
                }

                Minecraft.getInstance().player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§aJournaux téléchargés dans: " + fichierSortie.getAbsolutePath()));
                MonSubMod.JOURNALISEUR.info("Fichier de journalisation téléchargé vers: {}", fichierSortie.getAbsolutePath());

            } catch (IOException e) {
                MonSubMod.JOURNALISEUR.error("Erreur lors de la sauvegarde du fichier de journalisation téléchargé", e);
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("§cErreur lors de la sauvegarde du fichier"));
                }
            }
        });
    }
}

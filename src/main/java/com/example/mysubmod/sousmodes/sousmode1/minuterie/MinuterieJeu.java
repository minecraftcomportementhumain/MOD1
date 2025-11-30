package com.example.mysubmod.sousmodes.sousmode1.minuterie;

import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode1.GestionnaireSousMode1;
import com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMinuterieJeu;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Timer;
import java.util.TimerTask;

public class MinuterieJeu {
    private final int TOTAL_MINUTES;
    private final MinecraftServer serveur;
    private Timer minuteur;
    private int secondesRestantes;

    public MinuterieJeu(int minutes, MinecraftServer serveur) {
        this.TOTAL_MINUTES = minutes;
        this.serveur = serveur;
        this.secondesRestantes = minutes * 60;
    }

    public void demarrer() {
        minuteur = new Timer();
        minuteur.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Vérifier si le serveur est toujours valide avant d'exécuter
                if (serveur == null || serveur.isStopped()) {
                    arreter(); // Arrêter le minuteur si le serveur est invalide
                    return;
                }

                serveur.execute(() -> {
                    secondesRestantes--;

                    // Envoyer la mise à jour du minuteur uniquement aux joueurs authentifiés
                    try {
                        if (serveur != null && !serveur.isStopped() && serveur.getPlayerList() != null) {
                            for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                                    new PaquetMinuterieJeu(secondesRestantes));
                            }
                        }
                    } catch (Exception e) {
                        // Ignorer les erreurs réseau pendant l'arrêt du serveur
                    }

                    // Afficher les étapes de temps importantes
                    if (secondesRestantes == 300) { // 5 minutes restantes
                        diffuserAvertissementTemps("§e5 minutes restantes !");
                    } else if (secondesRestantes == 120) { // 2 minutes restantes
                        diffuserAvertissementTemps("§c2 minutes restantes !");
                    } else if (secondesRestantes == 60) { // 1 minute restante
                        diffuserAvertissementTemps("§c§l1 MINUTE RESTANTE !");
                    } else if (secondesRestantes == 30) { // 30 secondes restantes
                        diffuserAvertissementTemps("§c§l30 SECONDES !");
                    } else if (secondesRestantes <= 10 && secondesRestantes > 0) { // Compte à rebours final
                        diffuserAvertissementTemps("§c§l" + secondesRestantes + " !");
                    }

                    if (secondesRestantes <= 0) {
                        teleporterTousVersSpectateur();
                        terminerPartie();
                    }
                });
            }
        }, 1000, 1000); // Commencer après 1 seconde, puis chaque seconde
    }

    public void arreter() {
        if (minuteur != null) {
            minuteur.cancel();
            minuteur = null;
        }
    }

    private void diffuserAvertissementTemps(String message) {
        try {
            if (serveur != null && !serveur.isStopped() && serveur.getPlayerList() != null) {
                for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                    joueur.sendSystemMessage(Component.literal(message));
                }
            }
        } catch (Exception e) {
            // Ignorer les erreurs pendant l'arrêt du serveur
        }
    }

    private void teleporterTousVersSpectateur() {
        try {
            if (serveur != null && !serveur.isStopped() && serveur.getPlayerList() != null) {
                // Téléporter tous les joueurs vivants vers la plateforme spectateur
                for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                    if (GestionnaireSousMode1.getInstance().estJoueurVivant(joueur.getUUID())) {
                        GestionnaireSousMode1.getInstance().teleporterVersSpectateur(joueur);
                    }
                }
            }
        } catch (Exception e) {
            // Ignorer les erreurs pendant l'arrêt du serveur
        }
    }

    private void terminerPartie() {
        arreter();
        GestionnaireSousMode1.getInstance().terminerPartie(serveur);
    }

    public int obtenirSecondesRestantes() {
        return secondesRestantes;
    }

    public String obtenirTempsFormate() {
        int minutes = secondesRestantes / 60;
        int secondes = secondesRestantes % 60;
        return String.format("%02d:%02d", minutes, secondes);
    }
}

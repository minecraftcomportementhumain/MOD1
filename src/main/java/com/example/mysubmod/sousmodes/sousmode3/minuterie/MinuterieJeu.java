package com.example.mysubmod.sousmodes.sousmode3.minuterie;

import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.sousmode3.GestionnaireSousMode3;
import com.example.mysubmod.sousmodes.sousmode3.reseau.PaquetMinuterieJeuSousMode3;
import com.example.mysubmod.utilitaire.UtilitaireFiltreJoueurs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Minuterie de partie du Sous-mode 3 (reprend la base du Sous-mode 1).
 */
public class MinuterieJeu {
    private final MinecraftServer serveur;
    private Timer minuteur;
    private int secondesRestantes;

    public MinuterieJeu(int minutes, MinecraftServer serveur) {
        this.serveur = serveur;
        this.secondesRestantes = minutes * 60;
    }

    public void demarrer() {
        minuteur = new Timer("SousMode3-MinuterieJeu", true);
        minuteur.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (serveur == null || serveur.isStopped()) {
                    arreter();
                    return;
                }

                serveur.execute(() -> {
                    secondesRestantes--;

                    try {
                        if (!serveur.isStopped() && serveur.getPlayerList() != null) {
                            for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                                    new PaquetMinuterieJeuSousMode3(secondesRestantes));
                            }
                        }
                    } catch (Exception e) {
                        // Ignorer les erreurs réseau pendant l'arrêt du serveur
                    }

                    if (secondesRestantes == 300) {
                        diffuserAvertissementTemps("§e5 minutes restantes !");
                    } else if (secondesRestantes == 120) {
                        diffuserAvertissementTemps("§c2 minutes restantes !");
                    } else if (secondesRestantes == 60) {
                        diffuserAvertissementTemps("§c§l1 MINUTE RESTANTE !");
                    } else if (secondesRestantes == 30) {
                        diffuserAvertissementTemps("§c§l30 SECONDES !");
                    } else if (secondesRestantes <= 10 && secondesRestantes > 0) {
                        diffuserAvertissementTemps("§c§l" + secondesRestantes + " !");
                    }

                    if (secondesRestantes <= 0) {
                        teleporterTousVersSpectateur();
                        terminerPartie();
                    }
                });
            }
        }, 1000, 1000);
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
                for (ServerPlayer joueur : UtilitaireFiltreJoueurs.obtenirJoueursAuthentifies(serveur)) {
                    if (GestionnaireSousMode3.getInstance().estJoueurVivant(joueur.getUUID())) {
                        GestionnaireSousMode3.getInstance().teleporterVersSpectateur(joueur);
                    }
                }
            }
        } catch (Exception e) {
            // Ignorer les erreurs pendant l'arrêt du serveur
        }
    }

    private void terminerPartie() {
        arreter();
        GestionnaireSousMode3.getInstance().terminerPartie(serveur);
    }

    public int obtenirSecondesRestantes() {
        return secondesRestantes;
    }
}

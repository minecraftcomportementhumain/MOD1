package com.example.mysubmod.utilitaire;

import com.example.mysubmod.authentification.GestionnaireAuthAdmin;
import com.example.mysubmod.authentification.GestionnaireAuth;
import com.example.mysubmod.authentification.GestionnaireSalleAttente;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe utilitaire pour filtrer les joueurs et exclure les joueurs restreints
 * (candidats en file d'attente et joueurs protégés/admins non authentifiés)
 * du traitement des submodes
 */
public class UtilitaireFiltreJoueurs {

    /**
     * Obtenir tous les joueurs authentifiés sur le serveur.
     * Exclut:
     * - Candidats en file d'attente (noms temporaires _Q_)
     * - Joueurs protégés non authentifiés
     * - Admins non authentifiés
     *
     * Utiliser cette méthode au lieu de server.getPlayerList().getPlayers() dans les submodes.
     */
    public static List<ServerPlayer> obtenirJoueursAuthentifies(MinecraftServer serveur) {
        List<ServerPlayer> joueursAuthentifies = new ArrayList<>();

        for (ServerPlayer joueur : serveur.getPlayerList().getPlayers()) {
            if (!estJoueurRestreint(joueur)) {
                joueursAuthentifies.add(joueur);
            }
        }

        return joueursAuthentifies;
    }

    /**
     * Vérifier si un joueur est restreint (ne devrait pas être traité par les submodes)
     * Retourne vrai pour:
     * - Joueurs dans le lobby d'attente (en attente d'authentification)
     * - Candidats en file d'attente (noms temporaires _Q_)
     * - Joueurs protégés non authentifiés
     * - Admins non authentifiés
     */
    public static boolean estJoueurRestreint(ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString();
        GestionnaireSalleAttente salleAttente = GestionnaireSalleAttente.getInstance();

        // Vérifier si le joueur est dans le lobby d'attente (en attente d'authentification)
        // C'est la vérification primaire - s'ils sont dans le lobby d'attente, ils sont restreints
        boolean dansLobbyAttente = salleAttente.estDansLobbyStationnement(joueur.getUUID());
        if (dansLobbyAttente) {
            com.example.mysubmod.MonSubMod.JOURNALISEUR.info("DEBUG: Joueur {} est restreint (dans le lobby d'attente)", nomJoueur);
            return true;
        }

        // Vérifier s'il s'agit d'un candidat en file (nom temporaire)
        if (salleAttente.estNomFileTemporaire(nomJoueur)) {
            return true;
        }

        // Vérifier s'il s'agit d'un protégé/admin non authentifié
        GestionnaireAuth gestionnaireAuth = GestionnaireAuth.getInstance();
        GestionnaireAuthAdmin gestionnaireAuthAdmin = GestionnaireAuthAdmin.getInstance();

        GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(nomJoueur);
        boolean estProtegeOuAdmin = (typeCompte == GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE ||
                                      typeCompte == GestionnaireAuth.TypeCompte.ADMIN);

        if (estProtegeOuAdmin) {
            if (typeCompte == GestionnaireAuth.TypeCompte.ADMIN) {
                return !gestionnaireAuthAdmin.estAuthentifie(joueur);
            } else {
                return !gestionnaireAuth.estAuthentifie(joueur.getUUID());
            }
        }

        return false;
    }

}

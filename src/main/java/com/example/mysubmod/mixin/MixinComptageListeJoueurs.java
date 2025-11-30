package com.example.mysubmod.mixin;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.authentification.GestionnaireAuthAdmin;
import com.example.mysubmod.authentification.GestionnaireAuth;
import com.example.mysubmod.authentification.GestionnaireSalleAttente;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/**
 * Mixin pour remplacer le compte de joueurs afin d'exclure les joueurs non authentifiés ET les admins authentifiés
 * Compte seulement: FREE_PLAYER + PROTECTED_PLAYER authentifiés
 * Les admins ne comptent pas dans la limite du serveur
 */
@Mixin(value = PlayerList.class, priority = 1001)
public abstract class MixinComptageListeJoueurs {

    @Shadow
    public abstract List<ServerPlayer> getPlayers();

    /**
     * @author MonSubMod
     * @reason Filtrer les joueurs non authentifiés et les admins du compte de joueurs
     */
    @Overwrite
    public int getPlayerCount() {
        GestionnaireSalleAttente salleAttente = GestionnaireSalleAttente.getInstance();
        GestionnaireAuth gestionnaireAuth = GestionnaireAuth.getInstance();
        GestionnaireAuthAdmin gestionnaireAuthAdmin = GestionnaireAuthAdmin.getInstance();

        int compte = 0;
        for (ServerPlayer joueur : getPlayers()) {
            String nomJoueur = joueur.getName().getString();

            // Ignorer les candidats en file d'attente (noms temporaires)
            if (salleAttente.estNomFileTemporaire(nomJoueur)) {
                continue;
            }

            // Ignorer les joueurs dans le lobby d'attente (non authentifiés)
            if (salleAttente.estDansLobbyStationnement(joueur.getUUID())) {
                continue;
            }

            // Ignorer les admins authentifiés - ils ne comptent pas dans la limite du serveur
            GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(nomJoueur);
            if (typeCompte == GestionnaireAuth.TypeCompte.ADMIN && gestionnaireAuthAdmin.estAuthentifie(joueur)) {
                continue;
            }

            // Compter ce joueur (FREE_PLAYER ou PROTECTED_PLAYER authentifié)
            compte++;
        }

        return compte;
    }
}

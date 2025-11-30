package com.example.mysubmod.mixin;

import com.example.mysubmod.MonSubMod;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class MixinMessagesConnexionDeconnexion {

    @Shadow
    public abstract void broadcastSystemMessage(Component message, boolean overlay);

    /**
     * Supprimer les messages de connexion pour les candidats temporaires en file d'attente
     */
    @Redirect(
        method = "placeNewPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
        )
    )
    private void redirigerDiffusionConnexion(PlayerList instance, Component message, boolean overlay, Connection connection, ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString();

        // Vérifier s'il s'agit d'un VRAI candidat temporaire en file d'attente (pas juste un nom commençant par "_Q_")
        com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();
        if (salleAttente.estNomFileTemporaire(nomJoueur)) {
            MonSubMod.JOURNALISEUR.info("MIXIN: Suppression du message de connexion pour le candidat en file d'attente: {}", nomJoueur);
            // Ne pas diffuser le message
            return;
        }

        // Vérifier s'il s'agit d'un protégé/admin non authentifié
        com.example.mysubmod.authentification.GestionnaireAuth gestionnaireAuth = com.example.mysubmod.authentification.GestionnaireAuth.getInstance();
        com.example.mysubmod.authentification.GestionnaireAuthAdmin gestionnaireAuthAdmin = com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance();

        com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(nomJoueur);
        boolean estProtegeOuAdmin = (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE ||
                                      typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN);

        if (estProtegeOuAdmin) {
            boolean estAuthentifie = false;
            if (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN) {
                estAuthentifie = gestionnaireAuthAdmin.estAuthentifie(joueur);
            } else {
                estAuthentifie = gestionnaireAuth.estAuthentifie(joueur.getUUID());
            }

            if (!estAuthentifie) {
                MonSubMod.JOURNALISEUR.info("MIXIN: Suppression du message de connexion pour protégé/admin non authentifié: {}", nomJoueur);
                // Ne pas diffuser le message
                return;
            }
        }

        // Joueur normal - diffuser comme d'habitude
        instance.broadcastSystemMessage(message, overlay);
    }
}

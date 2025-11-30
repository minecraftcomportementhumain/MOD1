package com.example.mysubmod.mixin;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.authentification.GestionnaireAuth;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(value = PlayerList.class, priority = 900)
public abstract class MixinServeurPlein {

    @Shadow
    public abstract int getMaxPlayers();

    @Shadow
    public abstract java.util.List<net.minecraft.server.level.ServerPlayer> getPlayers();

    /**
     * Intercepter canPlayerLogin pour permettre aux comptes protégés de contourner la vérification de serveur plein
     * SEULEMENT s'il y a au moins un FREE_PLAYER à éjecter
     */
    @Inject(
        method = "canPlayerLogin",
        at = @At("HEAD"),
        cancellable = true
    )
    private void surConnexionJoueur(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
        String nomJoueur = profile.getName();

        // Vérifier s'il s'agit d'un nom temporaire (candidat en file d'attente)
        String nomCompteReel = nomJoueur;
        if (nomJoueur.startsWith("_Q_")) {
            com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();
            String nomOriginal = salleAttente.obtenirNomCompteOriginal(nomJoueur);
            if (nomOriginal != null) {
                nomCompteReel = nomOriginal;
                MonSubMod.JOURNALISEUR.info("MIXIN PlayerList: Nom temporaire détecté {} -> compte réel: {}", nomJoueur, nomCompteReel);
            }
        }

        // Vérifier le type de compte (utiliser le nom de compte réel)
        GestionnaireAuth gestionnaireAuth = GestionnaireAuth.getInstance();
        GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(nomCompteReel);
        boolean estCompteProtege = (typeCompte == GestionnaireAuth.TypeCompte.ADMIN ||
                                      typeCompte == GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE);

        int joueursMax = getMaxPlayers();
        com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();

        // TOUJOURS compter les VRAIS joueurs (exclure les candidats en file, les admins authentifiés, et les joueurs non authentifiés)
        // Cela assure que les joueurs non authentifiés ne comptent pas dans la capacité du serveur
        int nombreJoueursReels = 0;
        int nombreCandidatsFile = 0;
        int nombreAdmins = 0;
        int nombreNonAuthentifies = 0;
        for (net.minecraft.server.level.ServerPlayer joueur : getPlayers()) {
            String nom = joueur.getName().getString();
            if (salleAttente.estNomFileTemporaire(nom)) {
                nombreCandidatsFile++;
            } else if (salleAttente.estDansLobbyStationnement(joueur.getUUID())) {
                // Joueur dans le lobby d'attente (non authentifié) - ne pas compter comme joueur réel
                nombreNonAuthentifies++;
            } else {
                // Vérifier s'il s'agit d'un admin authentifié (les admins authentifiés ne comptent pas vers max_players pour PERSONNE)
                GestionnaireAuth.TypeCompte type = gestionnaireAuth.obtenirTypeCompte(nom);
                boolean estAdminAuthentifie = (type == GestionnaireAuth.TypeCompte.ADMIN &&
                                                com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().estAuthentifie(joueur));

                if (estAdminAuthentifie) {
                    nombreAdmins++;
                } else {
                    nombreJoueursReels++;
                }
            }
        }

        // Si nombreJoueursReels < joueursMax, autoriser la connexion pour TOUT LE MONDE (les admins et non authentifiés ne comptent pas)
        if (nombreJoueursReels < joueursMax) {
            MonSubMod.JOURNALISEUR.info("MIXIN PlayerList: Le serveur a de l'espace ({}/{} joueurs réels, {} admins, {} non authentifiés, {} candidats en file) - autorisation de {} à se connecter",
                nombreJoueursReels, joueursMax, nombreAdmins, nombreNonAuthentifies, nombreCandidatsFile, nomCompteReel);
            cir.setReturnValue(null);
            return;
        }

        // Le serveur est vraiment plein (nombreJoueursReels >= joueursMax)
        // Seuls les comptes protégés peuvent contourner en éjectant un FREE_PLAYER
        if (estCompteProtege) {
            // Vérifier si quelqu'un s'authentifie déjà sur ce compte (scénario de candidat en file)
            if (salleAttente.estCompteEnCoursAuthentification(nomCompteReel) && salleAttente.aPlaceDansFile(nomCompteReel)) {
                MonSubMod.JOURNALISEUR.info("MIXIN PlayerList: Serveur plein ({}/{} joueurs réels, {} admins, {} non authentifiés, {} candidats en file) mais autorisation du candidat en file pour {} (quelqu'un s'authentifie)",
                    nombreJoueursReels, joueursMax, nombreAdmins, nombreNonAuthentifies, nombreCandidatsFile, nomCompteReel);
                // Autoriser la connexion pour vérification du mot de passe et file d'attente
                cir.setReturnValue(null);
                return;
            }

            // Vérifier s'il y a au moins un FREE_PLAYER en ligne à éjecter (exclure les candidats en file, admins, et non authentifiés)
            boolean aJoueurLibre = false;
            for (net.minecraft.server.level.ServerPlayer joueur : getPlayers()) {
                String nom = joueur.getName().getString();
                // Ignorer les candidats en file - ce ne sont PAS des joueurs réels et ne doivent jamais être considérés pour l'éjection
                if (salleAttente.estNomFileTemporaire(nom)) {
                    continue;
                }

                // Ignorer les joueurs non authentifiés - ce ne sont PAS des joueurs réels et ne doivent jamais être considérés pour l'éjection
                if (salleAttente.estDansLobbyStationnement(joueur.getUUID())) {
                    continue;
                }

                // Ignorer les admins authentifiés - ils ne comptent pas et ne peuvent pas être éjectés
                GestionnaireAuth.TypeCompte type = gestionnaireAuth.obtenirTypeCompte(nom);
                boolean estAdminAuthentifie = (type == GestionnaireAuth.TypeCompte.ADMIN &&
                                                com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().estAuthentifie(joueur));
                if (estAdminAuthentifie) {
                    continue;
                }

                if (type == GestionnaireAuth.TypeCompte.JOUEUR_LIBRE) {
                    aJoueurLibre = true;
                    break;
                }
            }

            if (aJoueurLibre) {
                MonSubMod.JOURNALISEUR.info("MIXIN PlayerList: Serveur plein ({}/{} joueurs réels, {} admins, {} non authentifiés, {} candidats en file) mais autorisation du compte protégé {} à contourner la limite (va éjecter un joueur FREE)",
                    nombreJoueursReels, joueursMax, nombreAdmins, nombreNonAuthentifies, nombreCandidatsFile, nomCompteReel);

                // Retourner null pour autoriser la connexion (null = pas d'erreur, peut rejoindre)
                cir.setReturnValue(null);
            } else {
                MonSubMod.JOURNALISEUR.warn("MIXIN PlayerList: Serveur plein ({}/{} joueurs réels, {} admins, {} non authentifiés, {} candidats en file) avec seulement des comptes protégés - refus du compte protégé {}",
                    nombreJoueursReels, joueursMax, nombreAdmins, nombreNonAuthentifies, nombreCandidatsFile, nomCompteReel);
                // Ne pas définir la valeur de retour - laisser vanilla le gérer (affichera le message "serveur plein")
            }
        } else {
            // FREE_PLAYER et le serveur est vraiment plein - refuser
            MonSubMod.JOURNALISEUR.info("MIXIN PlayerList: Serveur plein ({}/{} joueurs réels, {} admins, {} non authentifiés, {} candidats en file) - refus du joueur libre {}",
                nombreJoueursReels, joueursMax, nombreAdmins, nombreNonAuthentifies, nombreCandidatsFile, nomCompteReel);
            // Ne pas définir la valeur de retour - laisser vanilla le gérer (affichera le message "serveur plein")
        }
    }
}

package com.example.mysubmod.mixin;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.authentification.GestionnaireAuthAdmin;
import com.example.mysubmod.authentification.GestionnaireAuth;
import com.example.mysubmod.authentification.GestionnaireSalleAttente;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Cache les joueurs non authentifiés et les admins authentifiés des clients
 * Cela assure que le compte de joueurs dans le menu M est correct
 */
@Mixin(PlayerList.class)
public abstract class MixinFiltreDiffusionListeJoueurs {

    @Shadow
    public abstract List<ServerPlayer> getPlayers();

    /**
     * Après qu'un joueur est placé, cache les joueurs non authentifiés les uns des autres
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("TAIL")
    )
    private void cacherJoueursNonAuthentifies(Connection connection, ServerPlayer joueurQuiRejoint, CallbackInfo ci) {
        GestionnaireSalleAttente salleAttente = GestionnaireSalleAttente.getInstance();
        GestionnaireAuth gestionnaireAuth = GestionnaireAuth.getInstance();
        GestionnaireAuthAdmin gestionnaireAuthAdmin = GestionnaireAuthAdmin.getInstance();

        net.minecraft.server.MinecraftServer serveur = joueurQuiRejoint.getServer();
        if (serveur == null) return;

        serveur.execute(() -> {
            // Étape 1: Cacher le joueur qui rejoint des autres clients s'il est non authentifié/admin
            String nomJoueurQuiRejoint = joueurQuiRejoint.getName().getString();
            boolean doitCacherJoueurQuiRejoint = false;
            String raison = "";

            if (salleAttente.estNomFileTemporaire(nomJoueurQuiRejoint)) {
                doitCacherJoueurQuiRejoint = true;
                raison = "candidat file d'attente";
            } else if (salleAttente.estDansLobbyStationnement(joueurQuiRejoint.getUUID())) {
                doitCacherJoueurQuiRejoint = true;
                raison = "non authentifié";
            } else {
                GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(nomJoueurQuiRejoint);
                if (typeCompte == GestionnaireAuth.TypeCompte.ADMIN) {
                    doitCacherJoueurQuiRejoint = true;
                    raison = "admin";
                }
            }

            if (doitCacherJoueurQuiRejoint) {
                MonSubMod.JOURNALISEUR.info("Cache le joueur {} ({}) des clients", nomJoueurQuiRejoint, raison);
                ClientboundPlayerInfoRemovePacket paquetRetrait = new ClientboundPlayerInfoRemovePacket(
                    List.of(joueurQuiRejoint.getUUID())
                );

                int nombreClients = 0;
                for (ServerPlayer client : getPlayers()) {
                    client.connection.send(paquetRetrait);
                    nombreClients++;
                }
                MonSubMod.JOURNALISEUR.info("Paquet REMOVE envoyé pour {} à {} clients", nomJoueurQuiRejoint, nombreClients);
            }

            // Étape 2: Cacher les joueurs non authentifiés/admin existants du joueur qui rejoint
            List<java.util.UUID> joueursACacher = new java.util.ArrayList<>();
            for (ServerPlayer joueurExistant : getPlayers()) {
                if (joueurExistant.getUUID().equals(joueurQuiRejoint.getUUID())) {
                    continue; // Ignorer le joueur qui rejoint lui-même
                }

                String nomJoueurExistant = joueurExistant.getName().getString();
                boolean doitCacherExistant = false;

                if (salleAttente.estNomFileTemporaire(nomJoueurExistant)) {
                    doitCacherExistant = true;
                } else if (salleAttente.estDansLobbyStationnement(joueurExistant.getUUID())) {
                    doitCacherExistant = true;
                } else {
                    GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(nomJoueurExistant);
                    if (typeCompte == GestionnaireAuth.TypeCompte.ADMIN && gestionnaireAuthAdmin.estAuthentifie(joueurExistant)) {
                        doitCacherExistant = true;
                    }
                }

                if (doitCacherExistant) {
                    joueursACacher.add(joueurExistant.getUUID());
                }
            }

            if (!joueursACacher.isEmpty()) {
                ClientboundPlayerInfoRemovePacket paquetCacherExistants = new ClientboundPlayerInfoRemovePacket(joueursACacher);
                joueurQuiRejoint.connection.send(paquetCacherExistants);
                MonSubMod.JOURNALISEUR.info("Paquet REMOVE envoyé pour {} joueurs existants à {}", joueursACacher.size(), nomJoueurQuiRejoint);
            }
        });
    }
}

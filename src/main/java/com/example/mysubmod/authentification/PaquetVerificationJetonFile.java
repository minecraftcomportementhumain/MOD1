package com.example.mysubmod.authentification;

import com.example.mysubmod.MonSubMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet envoyé du client au serveur avec jeton de file stocké
 * Envoyé automatiquement lors de la connexion pendant la fenêtre de monopole
 */
public class PaquetVerificationJetonFile {
    private final String nomCompte;
    private final String jeton;

    public PaquetVerificationJetonFile(String nomCompte, String jeton) {
        this.nomCompte = nomCompte;
        this.jeton = jeton;
    }

    public static void encode(PaquetVerificationJetonFile paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.nomCompte, 50);
        tampon.writeUtf(paquet.jeton, 10);
    }

    public static PaquetVerificationJetonFile decode(FriendlyByteBuf tampon) {
        return new PaquetVerificationJetonFile(
            tampon.readUtf(50),
            tampon.readUtf(10)
        );
    }

    public static void traiter(PaquetVerificationJetonFile paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null) return;

            GestionnaireSalleAttente gestionnaireSalleAttente = GestionnaireSalleAttente.getInstance();
            String nomJoueur = joueur.getName().getString();

            MonSubMod.JOURNALISEUR.info("Vérification de jeton reçue de {} pour compte {} avec jeton {}",
                nomJoueur, paquet.nomCompte, paquet.jeton);

            // Vérifie que le joueur essaie d'accéder au bon compte
            if (!nomJoueur.equalsIgnoreCase(paquet.nomCompte)) {
                MonSubMod.JOURNALISEUR.warn("Non-correspondance vérification jeton - joueur: {}, compte: {}",
                    nomJoueur, paquet.nomCompte);
                return;
            }

            // Vérifie IP + jeton
            String adresseIP = joueur.getIpAddress();
            boolean autorise = gestionnaireSalleAttente.estAutoriseeAvecJeton(paquet.nomCompte, adresseIP, paquet.jeton);

            if (autorise) {
                MonSubMod.JOURNALISEUR.info("Vérification de jeton réussie pour {} depuis IP {} - auto-authentification",
                    paquet.nomCompte, adresseIP);

                // Jeton valide - consomme l'autorisation et efface la fenêtre de monopole
                gestionnaireSalleAttente.consommerAutorisation(paquet.nomCompte, adresseIP);
                gestionnaireSalleAttente.effacerFilePourCompte(paquet.nomCompte); // Détruit la fenêtre de monopole, retour à la normale

                // Efface le jeton du stockage client (envoie paquet pour le retirer)
                com.example.mysubmod.reseau.GestionnaireReseau.sendToPlayer(
                    new PaquetJetonFile(paquet.nomCompte, "", 0, 0), // Jeton vide = effacer
                    joueur
                );

                // Auto-authentifie le joueur (jeton = mot de passe déjà vérifié)
                com.example.mysubmod.authentification.GestionnaireAuth gestionnaireAuth = com.example.mysubmod.authentification.GestionnaireAuth.getInstance();
                com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(paquet.nomCompte);

                if (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN) {
                    com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().authentifierParJeton(joueur);
                } else {
                    gestionnaireAuth.authentifierParJeton(joueur);
                }

                // Efface tout suivi de tentatives IP pour ce compte (auth réussie)
                gestionnaireAuth.effacerTentativesIpPourComptePublic(paquet.nomCompte, adresseIP);

                // Rend le joueur visible
                joueur.setInvisible(false);

                // Envoie la réponse de succès
                com.example.mysubmod.reseau.GestionnaireReseau.sendToPlayer(
                    new PaquetReponseAuthAdmin(true, 0, "Authentification automatique réussie"), joueur);

                // Met à jour le statut admin
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.reseau.PaquetStatutAdmin(
                        com.example.mysubmod.sousmodes.GestionnaireSousModes.getInstance().estAdmin(joueur))
                );

                joueur.sendSystemMessage(Component.literal(
                    "§a§lAuthentification automatique réussie!\n\n§eBienvenue, " + paquet.nomCompte + "."
                ));

                MonSubMod.JOURNALISEUR.info("Joueur {} auto-authentifié via jeton, fenêtre de monopole détruite, compte suit maintenant les règles normales", paquet.nomCompte);
            } else {
                MonSubMod.JOURNALISEUR.warn("Vérification de jeton échouée pour {} depuis IP {} avec jeton {}",
                    paquet.nomCompte, adresseIP, paquet.jeton);

                // Marque ce joueur comme "échec de vérification jeton" pour que onPlayerLogout n'appelle pas autoriserProchainDansFile
                // La fenêtre de monopole doit rester active pour le véritable détenteur du jeton
                gestionnaireSalleAttente.marquerJoueurVerificationJetonEchouee(joueur.getUUID());

                // Jeton invalide - kick le joueur
                joueur.getServer().execute(() -> {
                    joueur.connection.disconnect(Component.literal(
                        "§c§lJeton de connexion invalide\n\n" +
                        "§eVotre jeton n'est pas valide pour cette fenêtre de monopole."
                    ));
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

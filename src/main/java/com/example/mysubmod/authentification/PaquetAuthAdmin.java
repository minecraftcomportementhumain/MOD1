package com.example.mysubmod.authentification;

import com.example.mysubmod.MonSubMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquet envoyé du client au serveur avec tentative d'authentification
 */
public class PaquetAuthAdmin {
    private final String motDePasse;

    public PaquetAuthAdmin(String motDePasse) {
        this.motDePasse = motDePasse;
    }

    public static void encode(PaquetAuthAdmin paquet, FriendlyByteBuf tampon) {
        tampon.writeUtf(paquet.motDePasse, 50);
    }

    public static PaquetAuthAdmin decode(FriendlyByteBuf tampon) {
        return new PaquetAuthAdmin(tampon.readUtf(50));
    }

    public static void traiter(PaquetAuthAdmin paquet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur == null) return;

            GestionnaireAuthAdmin gestionnaireAuthAdmin = GestionnaireAuthAdmin.getInstance();
            GestionnaireAuth gestionnaireAuth = GestionnaireAuth.getInstance();
            GestionnaireSalleAttente gestionnaireSalleAttente = GestionnaireSalleAttente.getInstance();

            String nomJoueur = joueur.getName().getString();

            // Vérifie si le joueur est dans la salle d'attente
            if (!gestionnaireSalleAttente.estDansLobbyStationnement(joueur.getUUID())) {
                MonSubMod.JOURNALISEUR.warn("Joueur {} a tenté l'auth mais n'est pas dans la salle d'attente", nomJoueur);
                return;
            }

            String typeCompte = gestionnaireSalleAttente.obtenirTypeCompte(joueur.getUUID());
            boolean estAdmin = "ADMINISTRATEUR".equals(typeCompte);
            boolean estCandidatFile = gestionnaireSalleAttente.estCandidatFile(joueur.getUUID());

            // Pour les candidats de file, vérifie le mot de passe AVEC suivi des échecs (mêmes règles IP+compte s'appliquent)
            if (estCandidatFile) {
                // Obtient le nom de compte réel (nomJoueur pourrait être temporaire)
                String nomCompteReel = gestionnaireSalleAttente.obtenirNomCompteOriginal(nomJoueur);
                if (nomCompteReel == null) {
                    nomCompteReel = nomJoueur; // Repli si non trouvé
                }
                final String nomCompteFinal = nomCompteReel;
                String adresseIP = joueur.getIpAddress();

                MonSubMod.JOURNALISEUR.info("Traitement de l'authentification candidat file pour {} (nom temp: {})",
                    nomCompteReel, nomJoueur);

                // Vérifie si l'IP est déjà blacklistée pour ce compte
                if (!estAdmin && gestionnaireAuth.estIpSurListeNoirePourCompte(nomCompteReel, adresseIP)) {
                    long tempsRestant = gestionnaireAuth.obtenirTempsRestantListeNoireIpPourCompte(nomCompteReel, adresseIP);
                    long minutes = tempsRestant / 60000;
                    long secondes = (tempsRestant % 60000) / 1000;

                    gestionnaireSalleAttente.retirerCandidatFilePublic(nomCompteReel, joueur.getUUID());
                    gestionnaireSalleAttente.supprimerNomTemporaire(nomJoueur);

                    joueur.getServer().execute(() -> {
                        joueur.connection.disconnect(net.minecraft.network.chat.Component.literal(
                            "§4§lIP Bloquée\n\n§cTrop de tentatives de connexion échouées.\n§7Temps restant: §e" + minutes + "m " + secondes + "s"));
                    });
                    return;
                }

                // Vérifie le mot de passe et suit les tentatives pour les joueurs protégés
                boolean motDePasseCorrect;
                if (estAdmin) {
                    motDePasseCorrect = gestionnaireAuthAdmin.verifierMotDePasseSeul(nomCompteReel, paquet.motDePasse);
                } else {
                    // Utilise la méthode de suivi qui compte les tentatives par IP+compte
                    int resultat = gestionnaireAuth.tenterConnexionCandidatFile(nomCompteReel, adresseIP, paquet.motDePasse);
                    motDePasseCorrect = (resultat == 0);

                    if (resultat == -1) {
                        // Mauvais mot de passe - obtient les tentatives restantes et notifie (ne kick pas, le laisse réessayer)
                        int restantes = gestionnaireAuth.obtenirTentativesRestantesJoueurProtege(nomCompteReel, adresseIP);

                        MonSubMod.JOURNALISEUR.warn("Candidat file {} (temp: {}) a entré un mauvais mot de passe (restantes: {})",
                            nomCompteReel, nomJoueur, restantes);

                        // Envoie la réponse d'échec à ce candidat de file
                        joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c§lMot de passe incorrect! Tentatives restantes: " + restantes));
                        com.example.mysubmod.reseau.GestionnaireReseau.sendToPlayer(
                            new PaquetReponseAuthAdmin(false, restantes, "Mot de passe incorrect"), joueur);

                        // Diffuse aux autres clients avec même IP+compte (incluant les non-candidats de file)
                        java.util.List<java.util.UUID> autresJoueurs = gestionnaireSalleAttente.obtenirJoueursMemeIpEtCompte(nomCompteReel, adresseIP, joueur.getUUID());
                        for (java.util.UUID autreUuid : autresJoueurs) {
                            net.minecraft.server.level.ServerPlayer autreJoueur = joueur.getServer().getPlayerList().getPlayer(autreUuid);
                            if (autreJoueur != null) {
                                com.example.mysubmod.reseau.GestionnaireReseau.sendToPlayer(
                                    new PaquetReponseAuthAdmin(false, restantes, ""), autreJoueur);
                            }
                        }
                        return;
                    } else if (resultat == -2) {
                        // IP blacklistée
                        long tempsRestant = gestionnaireAuth.obtenirTempsRestantListeNoireIpPourCompte(nomCompteReel, adresseIP);
                        long minutes = tempsRestant / 60000;
                        long secondes = (tempsRestant % 60000) / 1000;

                        gestionnaireSalleAttente.retirerCandidatFilePublic(nomCompteReel, joueur.getUUID());
                        gestionnaireSalleAttente.supprimerNomTemporaire(nomJoueur);
                        MonSubMod.JOURNALISEUR.warn("Candidat file {} (temp: {}) IP blacklistée pour compte",
                            nomCompteReel, nomJoueur);

                        // Expulser tous les autres clients avec même IP+compte
                        java.util.List<java.util.UUID> autresJoueurs = gestionnaireSalleAttente.obtenirJoueursMemeIpEtCompte(nomCompteReel, adresseIP, joueur.getUUID());
                        for (java.util.UUID autreUuid : autresJoueurs) {
                            net.minecraft.server.level.ServerPlayer autreJoueur = joueur.getServer().getPlayerList().getPlayer(autreUuid);
                            if (autreJoueur != null) {
                                final long mins = minutes;
                                final long secs = secondes;
                                joueur.getServer().execute(() -> {
                                    autreJoueur.connection.disconnect(net.minecraft.network.chat.Component.literal(
                                        "§4§lIP Bloquée\n\n§cTrop de tentatives de connexion échouées.\n§7Temps restant: §e" + mins + "m " + secs + "s"));
                                });
                            }
                        }

                        joueur.getServer().execute(() -> {
                            joueur.connection.disconnect(net.minecraft.network.chat.Component.literal(
                                "§4§lIP Bloquée\n\n§cTrop de tentatives de connexion échouées.\n§7Temps restant: §e" + minutes + "m " + secondes + "s"));
                        });
                        return;
                    }
                }

                if (motDePasseCorrect) {
                    // Mot de passe correct - efface les tentatives pour cette IP+compte et tente d'ajouter à la file
                    if (!estAdmin) {
                        gestionnaireAuth.effacerTentativesIpPourComptePublic(nomCompteReel, adresseIP);
                    }

                    int position = gestionnaireSalleAttente.promouvoirCandidatFileVersFile(nomCompteReel, joueur.getUUID(), joueur.getIpAddress());

                    if (position == -1) {
                        // File complète - quelqu'un d'autre est entré en premier
                        // Nettoie le mappage de nom temporaire
                        gestionnaireSalleAttente.supprimerNomTemporaire(nomJoueur);
                        MonSubMod.JOURNALISEUR.warn("Candidat file {} rejeté - file complète (quelqu'un d'autre a entré le mot de passe en premier)", nomCompteReel);

                        // Expulser le joueur avec message de rejet
                        joueur.connection.disconnect(net.minecraft.network.chat.Component.literal(
                            "§c§lFile d'attente complète\n\n" +
                            "§eUn autre joueur a entré le mot de passe avant vous.\n" +
                            "§7La file d'attente est limitée à §e1 personne§7."
                        ));
                        return;
                    }

                    long[] fenetreMonopole = gestionnaireSalleAttente.obtenirFenetreMonopole(nomCompteReel, joueur.getIpAddress());
                    String jeton = gestionnaireSalleAttente.obtenirJetonFile(nomCompteReel, joueur.getIpAddress());

                    // Ne nettoie pas le mappage de nom temporaire ici - il sera nettoyé dans onPlayerLogout
                    // Cela permet au gestionnaire de déconnexion d'accéder toujours au nom de compte original

                    MonSubMod.JOURNALISEUR.info("Candidat file {} promu en file à position {} avec jeton {}",
                        nomCompteReel, position, jeton);

                    // Crée IMMÉDIATEMENT l'entrée whitelist - n'attend pas onPlayerLogout
                    // Cela assure que l'IP est autorisée quand le client se reconnecte
                    gestionnaireSalleAttente.autoriserCandidatFileImmediatement(nomCompteReel, joueur.getIpAddress());

                    // Marque ce joueur pour que onPlayerLogout n'appelle pas authorizeNextInQueue
                    // La whitelist est déjà créée ci-dessus
                    gestionnaireSalleAttente.marquerJoueurVerificationJetonEchouee(joueur.getUUID());

                    // Expulser le joueur en authentification (indiv1) - il est remplacé
                    gestionnaireSalleAttente.expulserJoueurEnAuthentification(nomCompteReel, joueur.getServer());

                    // Expulser tous les autres candidats de file (file maintenant complète)
                    gestionnaireSalleAttente.expulserCandidatsFileRestants(nomCompteReel, joueur.getServer());

                    // Envoie le jeton au client AVANT d'expulser
                    if (fenetreMonopole != null && jeton != null) {
                        com.example.mysubmod.reseau.GestionnaireReseau.sendToPlayer(
                            new PaquetJetonFile(nomCompteReel, jeton, fenetreMonopole[0], fenetreMonopole[1]),
                            joueur
                        );
                        MonSubMod.JOURNALISEUR.info("Jeton {} envoyé au client pour {}", jeton, nomCompteReel);
                    }

                    // Délai plus long pour assurer que le paquet est envoyé avant déconnexion
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        // Ignorer
                    }

                    // Expulser le joueur avec info de file (jeton déjà envoyé au client via paquet)
                    joueur.getServer().execute(() -> {
                        String message = String.format(
                            "§a§lMot de passe correct!\n\n" +
                            "§eVous êtes en file d'attente\n" +
                            "§7Position: §f%d\n\n" +
                            "§6Reconnectez-vous, vous avez 45 secondes pour vous connecter.\n\n" +
                            "§aVotre client a reçu un token de connexion automatique.",
                            position
                        );
                        joueur.connection.disconnect(net.minecraft.network.chat.Component.literal(message));
                    });
                }
                return;
            }

            // Flux d'authentification normal (pas un candidat de file)
            int resultat;

            if (estAdmin) {
                // Authentification admin
                resultat = gestionnaireAuthAdmin.tenterConnexion(joueur, paquet.motDePasse);
            } else {
                // Authentification joueur protégé (avec suivi de blacklist)
                resultat = gestionnaireAuth.tenterConnexionJoueurProtege(joueur, paquet.motDePasse);
            }

            if (resultat == 0) {
                // Succès - Expulser les candidats de file D'ABORD, puis retire de la salle d'attente
                // IMPORTANT: Doit expulser avant d'effacer la file, sinon les candidats sont retirés de la Map
                gestionnaireSalleAttente.expulserCandidatsFileRestants(nomJoueur, joueur.getServer(), "auth_success");

                gestionnaireSalleAttente.retirerJoueur(joueur.getUUID(), joueur.serverLevel());
                gestionnaireSalleAttente.effacerFilePourCompte(nomJoueur);

                joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§a§lAuthentification réussie! Bienvenue, " + nomJoueur + "."));

                // Envoie la réponse de succès
                com.example.mysubmod.reseau.GestionnaireReseau.sendToPlayer(
                    new PaquetReponseAuthAdmin(true, 0, "Authentification réussie"), joueur);

                // Met à jour le statut admin maintenant que le joueur est authentifié
                com.example.mysubmod.reseau.GestionnaireReseau.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.reseau.PaquetStatutAdmin(
                        com.example.mysubmod.sousmodes.GestionnaireSousModes.getInstance().estAdmin(joueur))
                );

            } else if (resultat == -1) {
                // Mauvais mot de passe
                String adresseIP = joueur.getIpAddress();
                int restantes = estAdmin ?
                    gestionnaireAuthAdmin.obtenirTentativesRestantesParNom(nomJoueur) :
                    gestionnaireAuth.obtenirTentativesRestantesJoueurProtege(nomJoueur, adresseIP);
                joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c§lMot de passe incorrect! Tentatives restantes: " + restantes));

                // Envoie la réponse d'échec avec tentatives restantes
                com.example.mysubmod.reseau.GestionnaireReseau.sendToPlayer(
                    new PaquetReponseAuthAdmin(false, restantes, "Mot de passe incorrect"), joueur);

                // Diffuse le compte de tentatives mis à jour aux autres clients avec même IP+compte
                if (!estAdmin) {
                    java.util.List<java.util.UUID> autresJoueurs = gestionnaireSalleAttente.obtenirJoueursMemeIpEtCompte(nomJoueur, adresseIP, joueur.getUUID());
                    for (java.util.UUID autreUuid : autresJoueurs) {
                        net.minecraft.server.level.ServerPlayer autreJoueur = joueur.getServer().getPlayerList().getPlayer(autreUuid);
                        if (autreJoueur != null) {
                            com.example.mysubmod.reseau.GestionnaireReseau.sendToPlayer(
                                new PaquetReponseAuthAdmin(false, restantes, ""), autreJoueur);
                            MonSubMod.JOURNALISEUR.debug("Diffusé mise à jour tentative à {} (restantes: {})", autreJoueur.getName().getString(), restantes);
                        }
                    }
                }

            } else if (resultat == -2) {
                // Tentatives max - IP sur liste noire pour ce compte (3 minutes)
                String adresseIP = joueur.getIpAddress();
                long tempsRestant = estAdmin ?
                    gestionnaireAuthAdmin.obtenirTempsRestantListeNoire(nomJoueur) :
                    gestionnaireAuth.obtenirTempsRestantListeNoireIpPourCompte(nomJoueur, adresseIP);
                long minutes = tempsRestant / 60000;
                long secondes = (tempsRestant % 60000) / 1000;

                joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§4§lTrop de tentatives échouées! IP bloquée pour " + minutes + " minute(s)."));

                // Expulser tous les autres clients avec même IP+compte
                if (!estAdmin) {
                    java.util.List<java.util.UUID> autresJoueurs = gestionnaireSalleAttente.obtenirJoueursMemeIpEtCompte(nomJoueur, adresseIP, joueur.getUUID());
                    for (java.util.UUID autreUuid : autresJoueurs) {
                        net.minecraft.server.level.ServerPlayer autreJoueur = joueur.getServer().getPlayerList().getPlayer(autreUuid);
                        if (autreJoueur != null) {
                            final long mins = minutes;
                            final long secs = secondes;
                            joueur.getServer().execute(() -> {
                                autreJoueur.connection.disconnect(net.minecraft.network.chat.Component.literal(
                                    "§4§lIP Bloquée\n\n§cTrop de tentatives de connexion échouées.\n§7Temps restant: §e" + mins + "m " + secs + "s"));
                            });
                            MonSubMod.JOURNALISEUR.info("Expulsé {} à cause de liste noire IP pour compte {}", autreJoueur.getName().getString(), nomJoueur);
                        }
                    }
                }

                // Expulser le joueur
                joueur.getServer().execute(() -> {
                    joueur.connection.disconnect(net.minecraft.network.chat.Component.literal(
                        "§4§lIP Bloquée\n\n§cTrop de tentatives de connexion échouées.\n§7Temps restant: §e" + minutes + "m " + secondes + "s"));
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

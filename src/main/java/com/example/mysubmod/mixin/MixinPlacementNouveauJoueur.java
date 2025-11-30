package com.example.mysubmod.mixin;

import com.example.mysubmod.MonSubMod;
import com.example.mysubmod.authentification.GestionnaireAuthAdmin;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class MixinPlacementNouveauJoueur {

    @Shadow @Final
    Connection connection;

    @Shadow
    GameProfile gameProfile;

    @Shadow @Final
    MinecraftServer server;

    // Stocker le nom de compte original pour les candidats en file d'attente
    private String nomCompteOriginal = null;

    @Inject(
        method = "handleAcceptedLogin",
        at = @At("HEAD"),
        cancellable = true
    )
    private void surGestionConnexionAccepteeAvantEjection(CallbackInfo ci) {
        MonSubMod.JOURNALISEUR.info("=== MIXIN APPELÉ: ServerLoginPacketListenerImpl.handleAcceptedLogin (AVANT VANILLA) ===");

        String nomJoueur = this.gameProfile.getName();
        MonSubMod.JOURNALISEUR.info("MIXIN: Traitement de la connexion pour le joueur: {}", nomJoueur);

        // SÉCURITÉ: Bloquer les joueurs libres tentant d'exploiter le préfixe "_Q_"
        // Seuls les candidats en file créés par notre système devraient avoir ce préfixe
        if (nomJoueur.startsWith("_Q_")) {
            com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();
            if (!salleAttente.estNomFileTemporaire(nomJoueur)) {
                // Ce n'est PAS un candidat en file légitime - bloquer la connexion
                MonSubMod.JOURNALISEUR.warn("MIXIN: SÉCURITÉ - Blocage de l'utilisation non autorisée du préfixe réservé '_Q_' par {}", nomJoueur);
                this.connection.send(new ClientboundLoginDisconnectPacket(
                    Component.literal("§c§lNom invalide\n\n§7Le préfixe '_Q_' est réservé au système.\n§7Veuillez choisir un autre nom.")
                ));
                this.connection.disconnect(Component.literal("Nom invalide - préfixe réservé"));
                ci.cancel();
                return;
            }
        }

        GestionnaireAuthAdmin gestionnaireAuthAdmin = GestionnaireAuthAdmin.getInstance();
        com.example.mysubmod.authentification.GestionnaireAuth gestionnaireAuth = com.example.mysubmod.authentification.GestionnaireAuth.getInstance();

        // Vérifier s'il s'agit d'un compte protégé (admin ou joueur protégé)
        com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte = gestionnaireAuth.obtenirTypeCompte(nomJoueur);
        boolean estCompteProtege = (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN ||
                                      typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE);

        MonSubMod.JOURNALISEUR.info("MIXIN: Type de compte du joueur {}: {}, estProtégé: {}", nomJoueur, typeCompte, estCompteProtege);

        // Obtenir la liste de joueurs pour vérifier les connexions existantes
        PlayerList listeJoueurs = this.server.getPlayerList();

        // SYSTÈME DE PRIORITÉ: Si le serveur est plein et c'est un compte protégé, autoriser la connexion
        // L'éjection réelle se produira dans GestionnaireEvenementsServeur après que le joueur rejoigne
        int joueursActuels = listeJoueurs.getPlayerCount();
        int joueursMax = listeJoueurs.getMaxPlayers();

        if (joueursActuels >= joueursMax && estCompteProtege) {
            MonSubMod.JOURNALISEUR.info("MIXIN: Serveur plein ({}/{}) mais autorisation du compte protégé {} à se connecter. Va éjecter un joueur FREE après la connexion.",
                joueursActuels, joueursMax, nomJoueur);
            // Ne pas annuler - autoriser la connexion à continuer
            // Le GestionnaireEvenementsServeur va éjecter un joueur FREE après que ce joueur rejoigne
        }

        // Vérifier en itérant sur tous les joueurs pour trouver un doublon par NOM (car l'UUID est null à ce stade)
        ServerPlayer joueurExistant = null;
        for (ServerPlayer joueur : listeJoueurs.getPlayers()) {
            if (joueur.getName().getString().equalsIgnoreCase(nomJoueur)) {
                joueurExistant = joueur;
                break;
            }
        }

        if (joueurExistant != null) {
            MonSubMod.JOURNALISEUR.info("MIXIN: Joueur existant trouvé avec le nom {}", nomJoueur);

            // Obtenir l'adresse IP pour le système de file d'attente
            String adresseIP = this.connection.getRemoteAddress().toString();

            if (estCompteProtege) {
                // Compte protégé (admin ou joueur protégé)
                boolean estAuthentifie = false;
                if (gestionnaireAuthAdmin.estCompteAdmin(nomJoueur)) {
                    estAuthentifie = gestionnaireAuthAdmin.estAuthentifie(joueurExistant);
                } else {
                    estAuthentifie = gestionnaireAuth.estAuthentifie(joueurExistant.getUUID());
                }

                if (estAuthentifie) {
                    // Refuser la nouvelle connexion - garder l'utilisateur authentifié (admin ou joueur protégé)
                    String etiquetteCompte = gestionnaireAuthAdmin.estCompteAdmin(nomJoueur) ? "administrateur" : "joueur protégé";
                    MonSubMod.JOURNALISEUR.warn("MIXIN: Refus de connexion pour {} - {} déjà authentifié", nomJoueur, etiquetteCompte);
                    this.connection.send(new ClientboundLoginDisconnectPacket(
                        Component.literal(String.format("§c§lConnexion refusée\n\n§eUn %s authentifié utilise déjà ce compte.", etiquetteCompte))
                    ));
                    this.connection.disconnect(Component.literal("Utilisateur authentifié déjà connecté"));
                    ci.cancel();
                } else {
                    // Non authentifié - vérifier l'autorisation, le statut de la file ou demander le mot de passe
                    com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();

                    // Vérifier si la protection d'authentification est active
                    long protectionRestante = 0;
                    if (gestionnaireAuthAdmin.estCompteAdmin(nomJoueur)) {
                        protectionRestante = gestionnaireAuthAdmin.obtenirTempsRestantProtection(nomJoueur);
                    } else {
                        protectionRestante = gestionnaireAuth.obtenirTempsRestantProtection(nomJoueur);
                    }

                    if (protectionRestante > 0 && !salleAttente.estAutorisee(nomJoueur, adresseIP)) {
                        // Protection active et IP non autorisée - BLOQUER
                        long secondesRestantes = (protectionRestante + 999) / 1000;
                        MonSubMod.JOURNALISEUR.warn("MIXIN: IP {} bloquée pour {} - protection d'authentification active ({} secondes restantes)",
                            adresseIP, nomJoueur, secondesRestantes);
                        this.connection.send(new ClientboundLoginDisconnectPacket(
                            Component.literal(String.format(
                                "§c§lCompte en cours d'utilisation\n\n" +
                                "§eQuelqu'un s'authentifie actuellement sur ce compte.\n" +
                                "§7Temps restant: §e%d seconde(s)",
                                secondesRestantes
                            ))
                        ));
                        this.connection.disconnect(Component.literal("Protection d'authentification active"));
                        ci.cancel();
                        return;
                    }

                    if (salleAttente.estAutorisee(nomJoueur, adresseIP)) {
                        // IP autorisée depuis la fenêtre de monopole
                        // NOTE: NE PAS mettre à jour la protection ici - attendre la vérification du jeton
                        // La protection sera définie par PaquetVerificationJetonFile après une vérification réussie du jeton
                        // Cela empêche la protection de persister si la vérification du jeton échoue
                        MonSubMod.JOURNALISEUR.info("MIXIN: IP {} autorisée pour {} - autorisation de connexion (vérification du jeton en attente, protection NON mise à jour)", adresseIP, nomJoueur);
                        // Ne pas annuler - laisser vanilla éjecter l'ancien joueur
                    } else if (salleAttente.estCompteEnCoursAuthentification(nomJoueur) && salleAttente.aPlaceDansFile(nomJoueur)) {
                        // Quelqu'un s'authentifie ET il y a de la place dans la file - assigner un nom temporaire pour éviter d'éjecter le joueur existant
                        // Max 16 caractères pour la limite de nom d'utilisateur Minecraft
                        // Format: _Q_<nomCourt>_<horodateCourt>
                        String horodateCourt = String.valueOf(System.currentTimeMillis() % 100000); // 5 derniers chiffres
                        String nomCourt = nomJoueur.length() > 7 ? nomJoueur.substring(0, 7) : nomJoueur;
                        String nomTemporaire = "_Q_" + nomCourt + "_" + horodateCourt;

                        // Enregistrer le mapping du nom temporaire
                        salleAttente.enregistrerNomTemporaire(nomTemporaire, nomJoueur);

                        // Modifier le GameProfile pour utiliser le nom temporaire
                        this.gameProfile = new com.mojang.authlib.GameProfile(
                            this.gameProfile.getId(),
                            nomTemporaire
                        );

                        MonSubMod.JOURNALISEUR.info("MIXIN: IP {} se connecte en tant que candidat en file pour {} avec le nom temporaire: {}",
                            adresseIP, nomJoueur, nomTemporaire);

                        // Ne pas annuler - les laisser se connecter avec le nom temporaire et vérifier le mot de passe
                    } else if (!salleAttente.estCompteEnCoursAuthentification(nomJoueur)) {
                        // Personne ne s'authentifie - refuser la connexion (impossible de rejoindre la file si personne ne s'authentifie)
                        MonSubMod.JOURNALISEUR.warn("MIXIN: IP {} rejetée - aucune authentification en cours pour {}", adresseIP, nomJoueur);
                        this.connection.send(new ClientboundLoginDisconnectPacket(
                            Component.literal("§c§lConnexion refusée\n\n§eCe compte n'est pas en cours d'authentification.\n§7Impossible de rejoindre la file d'attente.")
                        ));
                        this.connection.disconnect(Component.literal("Aucune authentification en cours"));
                        ci.cancel();
                    } else {
                        // Quelqu'un s'authentifie mais la file est pleine - refuser la connexion
                        MonSubMod.JOURNALISEUR.warn("MIXIN: IP {} rejetée - file d'attente complète pour {}", adresseIP, nomJoueur);
                        this.connection.send(new ClientboundLoginDisconnectPacket(
                            Component.literal("§c§lConnexion refusée\n\n§eFile d'attente complète pour ce compte.\n§7Maximum: 1 personne en attente.")
                        ));
                        this.connection.disconnect(Component.literal("File d'attente complète"));
                        ci.cancel();
                    }
                }
            } else {
                // Joueur libre - refuser la nouvelle connexion (pas de file pour les joueurs libres)
                MonSubMod.JOURNALISEUR.warn("MIXIN: Refus de connexion pour {} - joueur libre déjà connecté", nomJoueur);
                this.connection.send(new ClientboundLoginDisconnectPacket(
                    Component.literal("§c§lConnexion refusée\n\n§eCe compte est déjà utilisé par un autre joueur.")
                ));
                this.connection.disconnect(Component.literal("Joueur déjà connecté"));
                ci.cancel();
            }
        } else {
            // Aucun joueur existant avec ce nom
            MonSubMod.JOURNALISEUR.info("MIXIN: Aucun joueur existant trouvé avec le nom {}", nomJoueur);

            // Pour les comptes protégés, vérifier s'il y a une file active avec autorisation IP
            if (estCompteProtege) {
                com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();
                String adresseIP = this.connection.getRemoteAddress().toString();

                // Vérifier si la protection d'authentification est active (30 secondes après que quelqu'un ait commencé à s'authentifier)
                long protectionRestante = 0;
                if (gestionnaireAuthAdmin.estCompteAdmin(nomJoueur)) {
                    protectionRestante = gestionnaireAuthAdmin.obtenirTempsRestantProtection(nomJoueur);
                } else {
                    protectionRestante = gestionnaireAuth.obtenirTempsRestantProtection(nomJoueur);
                }

                if (protectionRestante > 0 && !salleAttente.estAutorisee(nomJoueur, adresseIP)) {
                    // Protection active mais IP non autorisée - BLOQUER la connexion
                    long secondesRestantes = (protectionRestante + 999) / 1000; // Arrondir vers le haut
                    MonSubMod.JOURNALISEUR.warn("MIXIN: IP {} bloquée pour {} - protection d'authentification active ({} secondes restantes)",
                        adresseIP, nomJoueur, secondesRestantes);
                    this.connection.send(new ClientboundLoginDisconnectPacket(
                        Component.literal(String.format(
                            "§c§lCompte en cours d'utilisation\n\n" +
                            "§eQuelqu'un s'authentifie actuellement sur ce compte.\n" +
                            "§7Temps restant: §e%d seconde(s)",
                            secondesRestantes
                        ))
                    ));
                    this.connection.disconnect(Component.literal("Protection d'authentification active"));
                    ci.cancel();
                    return;
                }

                // Vérifier si cette IP est autorisée (depuis la fenêtre de monopole)
                if (salleAttente.estAutorisee(nomJoueur, adresseIP)) {
                    // IP autorisée depuis la fenêtre de monopole
                    // NOTE: NE PAS mettre à jour la protection ici - attendre la vérification du jeton
                    // La protection sera définie par PaquetVerificationJetonFile après une vérification réussie du jeton
                    // Cela empêche la protection de persister si la vérification du jeton échoue
                    MonSubMod.JOURNALISEUR.info("MIXIN: Aucun joueur existant, IP {} autorisée pour {} - autorisation de connexion (vérification du jeton en attente, protection NON mise à jour)", adresseIP, nomJoueur);
                    // Ne pas annuler - autoriser la connexion
                } else if (salleAttente.aUneFile(nomJoueur)) {
                    // File d'attente existe mais cette IP n'est pas autorisée
                    MonSubMod.JOURNALISEUR.warn("MIXIN: IP {} rejetée - file d'attente existe pour {} mais IP non autorisée", adresseIP, nomJoueur);
                    this.connection.send(new ClientboundLoginDisconnectPacket(
                        Component.literal("§c§lConnexion refusée\n\n§eUne file d'attente existe pour ce compte.\n§7Seule l'IP autorisée peut se connecter pendant la fenêtre de monopole.")
                    ));
                    this.connection.disconnect(Component.literal("IP non autorisée"));
                    ci.cancel();
                } else {
                    // Pas de file, pas d'autorisation - autoriser l'authentification normale
                    MonSubMod.JOURNALISEUR.info("MIXIN: Pas de file pour {}, autorisation de connexion pour authentification", nomJoueur);
                }
            } else {
                // Joueur libre - autoriser la connexion
                MonSubMod.JOURNALISEUR.info("MIXIN: Joueur libre {}, autorisation de connexion", nomJoueur);
            }
        }
    }
}

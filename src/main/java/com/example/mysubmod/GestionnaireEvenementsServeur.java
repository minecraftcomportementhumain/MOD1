package com.example.mysubmod;

import com.example.mysubmod.commandes.CommandeSousMode;
import com.example.mysubmod.reseau.PaquetStatutAdmin;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.reseau.PaquetChangementSousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = MonSubMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GestionnaireEvenementsServeur {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandeSousMode.register(event.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer joueur) {
            com.example.mysubmod.authentification.GestionnaireAuthAdmin gestAuthAdmin = com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance();
            com.example.mysubmod.authentification.GestionnaireAuth gestAuth = com.example.mysubmod.authentification.GestionnaireAuth.getInstance();
            com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();

            String nomJoueur = joueur.getName().getString();

            // Vérifier si c'est un nom temporaire (candidat file d'attente) OU protégé/admin non authentifié
            boolean estCandidatFile = false;
            boolean estRestreintNonAuth = false; // Protégé/Admin non authentifié
            String nomCompteReel = nomJoueur;

            if (nomJoueur.startsWith("_Q_")) {
                // C'est un nom temporaire - obtenir le nom de compte original
                nomCompteReel = salleAttente.obtenirNomCompteOriginal(nomJoueur);
                if (nomCompteReel != null) {
                    estCandidatFile = true;
                    MonSubMod.JOURNALISEUR.info("Candidat file d'attente détecté avec nom temporaire: {} -> compte réel: {}",
                        nomJoueur, nomCompteReel);
                }
            } else {
                // Vérifier si c'est un compte protégé/admin qui n'est pas authentifié
                com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte = gestAuth.obtenirTypeCompte(nomJoueur);
                boolean estProtegeOuAdmin = (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE ||
                                              typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN);

                if (estProtegeOuAdmin) {
                    // Vérifier l'authentification
                    boolean estAuthentifie = false;
                    if (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN) {
                        estAuthentifie = gestAuthAdmin.estAuthentifie(joueur);
                    } else {
                        estAuthentifie = gestAuth.estAuthentifie(joueur.getUUID());
                    }

                    if (!estAuthentifie) {
                        estRestreintNonAuth = true;
                        MonSubMod.JOURNALISEUR.info("Protégé/admin non authentifié détecté: {}", nomJoueur);
                    }
                }
            }

            // Appliquer les restrictions aux candidats file ET aux protégés/admin non authentifiés
            if (estCandidatFile || estRestreintNonAuth) {
                // Déterminer le type de compte pour la salle d'attente
                com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte = gestAuth.obtenirTypeCompte(nomCompteReel);
                String chaineTypeCompte;
                if (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN) {
                    chaineTypeCompte = "ADMINISTRATEUR";
                } else if (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE) {
                    chaineTypeCompte = "JOUEUR_PROTEGE";
                } else {
                    chaineTypeCompte = "TEMPORAIRE"; // Pour les candidats file avec comptes joueurs libres
                }

                // Rendre INVISIBLE d'abord
                joueur.setInvisible(true);

                // Téléporter vers la plateforme d'authentification isolée à (10000, 200, 10000) AVANT d'ajouter à la salle d'attente
                net.minecraft.server.level.ServerLevel monde = joueur.getServer().overworld();
                net.minecraft.core.BlockPos posPlateforme = new net.minecraft.core.BlockPos(10000, 200, 10000);

                // Créer une plateforme bedrock 3x3
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        monde.setBlock(
                            posPlateforme.offset(x, -1, z),
                            net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(),
                            3
                        );
                    }
                }

                // Téléporter le joueur au centre de la plateforme
                joueur.teleportTo(monde, 10000.5, 200, 10000.5, 0, 0);

                // Ajouter à la salle d'attente APRÈS la téléportation pour éviter l'expulsion par vérification de position
                salleAttente.ajouterJoueur(joueur, chaineTypeCompte);

                // Effacer tous les éléments d'interface Sous-mode 1 (minuterie, compteurs de bonbons) du client
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMinuterieJeu(-1)); // -1 = désactiver
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.sousmodes.sousmode1.reseau.PaquetMiseAJourCompteurBonbons(new java.util.HashMap<>())); // Map vide = effacer

                // Effacer tous les éléments d'interface Sous-mode 2 (minuterie, compteurs de bonbons, minuterie de pénalité) du client
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMinuterieJeu(-1)); // -1 = désactiver
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetMiseAJourCompteurBonbons(new java.util.HashMap<>())); // Map vide = effacer
                GestionnaireReseau.INSTANCE.send(PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.sousmodes.sousmode2.reseau.PaquetSynchronisationPenalite(false, joueur.getUUID())); // false = désactiver

                String type = estCandidatFile ? "Candidat file d'attente" : "Protégé/admin non authentifié";
                MonSubMod.JOURNALISEUR.info("{} {} rendu invisible, téléporté vers la plateforme d'authentification isolée, et ajouté à la salle d'attente", type, nomJoueur);
            }

            GestionnaireReseau.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> joueur),
                new PaquetChangementSousMode(GestionnaireSousModes.getInstance().obtenirModeActuel())
            );

            // Vérifier le type de compte en utilisant le nom de compte RÉEL (pas le nom temporaire)
            com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte = gestAuth.obtenirTypeCompte(nomCompteReel);
            MonSubMod.JOURNALISEUR.info("Vérification de connexion du joueur {} (réel: {}): typeCompte={}", nomJoueur, nomCompteReel, typeCompte);

            // NOTE: Le système d'expulsion prioritaire est maintenant géré APRÈS l'authentification dans handleAuthenticationTransition()
            // Les joueurs dans la salle d'attente (en cours d'authentification) n'expulsent personne - seulement après authentification réussie

            // Vérifier si ce joueur se connecte depuis la file (IP autorisée, nécessite vérification de jeton)
            boolean vientDeLaFile = salleAttente.estAutorisee(nomCompteReel, joueur.getIpAddress());
            if (vientDeLaFile && !estCandidatFile) {
                // Le joueur se connecte pendant la fenêtre de monopole - demander le jeton
                MonSubMod.JOURNALISEUR.info("Joueur {} se connectant depuis la file - demande de vérification de jeton", nomCompteReel);
                GestionnaireReseau.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.authentification.PaquetDemandeJetonFile(nomCompteReel)
                );
                // Ne pas mettre dans la salle d'attente encore - attendre la vérification du jeton
                // La vérification du jeton se produira dans le gestionnaire PaquetVerificationJetonFile
                return;
            }

            // Si le compte nécessite une authentification, le mettre dans la salle d'attente
            if (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN ||
                typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE) {

                // Vérifier si l'IP est sur liste noire pour ce compte (utiliser le nom de compte réel)
                boolean estListeNoire = false;
                long tempsRestant = 0;
                String adresseIp = joueur.getIpAddress();

                if (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN) {
                    estListeNoire = gestAuthAdmin.estSurListeNoire(nomCompteReel);
                    if (estListeNoire) {
                        tempsRestant = gestAuthAdmin.obtenirTempsRestantListeNoire(nomCompteReel);
                    }
                } else if (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE) {
                    estListeNoire = gestAuth.estIpSurListeNoirePourCompte(nomCompteReel, adresseIp);
                    if (estListeNoire) {
                        tempsRestant = gestAuth.obtenirTempsRestantListeNoireIpPourCompte(nomCompteReel, adresseIp);
                    }
                }

                if (estListeNoire) {
                    long minutes = tempsRestant / 60000;
                    long secondes = (tempsRestant % 60000) / 1000;
                    joueur.getServer().execute(() -> {
                        joueur.connection.disconnect(Component.literal(
                            "§4§lIP Bloquée\n\n§cTrop de tentatives échouées pour ce compte.\n§7Temps restant: §e" + minutes + "m " + secondes + "s"
                        ));
                    });
                    return;
                }

                // Déterminer la chaîne de type de compte
                String chaineTypeCompte = typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN ? "ADMINISTRATEUR" : "JOUEUR_PROTEGE";

                // Mettre le joueur dans la salle d'attente avec un délai de 60s (si pas déjà ajouté)
                if (!salleAttente.estDansLobbyStationnement(joueur.getUUID())) {
                    salleAttente.ajouterJoueur(joueur, chaineTypeCompte);
                }

                if (estCandidatFile) {
                    // C'est un candidat file - le marquer comme tel en utilisant le nom de compte RÉEL
                    // Protection DoS: Vérifier les limites avant d'ajouter (peut évincer les anciens candidats)
                    boolean ajoute = salleAttente.ajouterCandidatFile(nomCompteReel, joueur.getUUID(), joueur.getIpAddress(), joueur.server);
                    if (!ajoute) {
                        // Limites dépassées et aucun candidat évincable - expulser le joueur
                        MonSubMod.JOURNALISEUR.warn("Joueur {} (IP: {}) rejeté - limites de protection DoS dépassées pour {} (aucun candidat évincable)",
                            joueur.getUUID(), joueur.getIpAddress(), nomCompteReel);
                        joueur.connection.disconnect(Component.literal(
                            "§c§lLimite de tentatives dépassée\n\n" +
                            "§eTrop de tentatives de connexion depuis votre IP.\n" +
                            "§7Limite par compte: §e5 tentatives parallèles§7\n" +
                            "§7Limite globale: §e10 comptes différents§7\n\n" +
                            "§7Tous les candidats actuels sont récents (<20s).\n" +
                            "§7Veuillez réessayer plus tard."
                        ));
                        return;
                    }
                    MonSubMod.JOURNALISEUR.info("Joueur {} (nom temporaire: {}) marqué comme CANDIDAT FILE D'ATTENTE pour {}",
                        joueur.getUUID(), nomJoueur, nomCompteReel);
                }

                // Obtenir les tentatives restantes pour cette IP sur ce compte (utiliser le nom de compte réel)
                int tentativesRestantes;
                if (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN) {
                    tentativesRestantes = com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().obtenirTentativesRestantesParNom(nomCompteReel);
                } else {
                    tentativesRestantes = gestAuth.obtenirTentativesRestantesJoueurProtege(nomCompteReel, adresseIp);
                }

                // Envoyer la demande d'authentification avec le type de compte et le délai
                GestionnaireReseau.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> joueur),
                    new com.example.mysubmod.authentification.PaquetDemandeAuthAdmin(chaineTypeCompte, tentativesRestantes, 60)
                );

                MonSubMod.JOURNALISEUR.info("Joueur {} ajouté à la salle d'attente en tant que {} (candidat file: {})",
                    nomJoueur, chaineTypeCompte, estCandidatFile);
            }

            // Envoyer le statut admin (sera false pour non authentifié)
            GestionnaireReseau.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> joueur),
                new PaquetStatutAdmin(GestionnaireSousModes.getInstance().estAdmin(joueur))
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer joueur) {
            String nomJoueur = joueur.getName().getString();
            com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();
            com.example.mysubmod.authentification.GestionnaireAuth gestAuth = com.example.mysubmod.authentification.GestionnaireAuth.getInstance();

            // Vérifier si c'est un nom temporaire et obtenir le nom de compte réel
            String nomCompteReel = nomJoueur;
            if (nomJoueur.startsWith("_Q_")) {
                String nomOriginal = salleAttente.obtenirNomCompteOriginal(nomJoueur);
                if (nomOriginal != null) {
                    nomCompteReel = nomOriginal;
                    MonSubMod.JOURNALISEUR.info("Déconnexion: Nom temporaire détecté {} -> compte réel: {}", nomJoueur, nomCompteReel);
                }
            }

            // Vérifier si le joueur était dans la salle d'attente (non authentifié)
            boolean etaitDansSalle = salleAttente.estDansLobbyStationnement(joueur.getUUID());

            // Vérifier si le joueur était authentifié (pas dans salle = jouait normalement)
            boolean etaitAuthentifie = !etaitDansSalle && gestAuth.estAuthentifie(joueur.getUUID());

            // Obtenir le temps restant AVANT de retirer le joueur (utiliser nomJoueur pour la recherche de session)
            long tempsRestant = etaitDansSalle ? salleAttente.obtenirTempsRestantPourCompte(nomJoueur) : 0;

            // Effacer l'état d'authentification quand le joueur se déconnecte
            com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().gererDeconnexion(joueur);
            gestAuth.gererDeconnexion(joueur);
            salleAttente.retirerJoueur(joueur.getUUID(), joueur.serverLevel());

            // Nettoyer le mapping de nom temporaire si c'était un VRAI candidat file
            if (salleAttente.estNomFileTemporaire(nomJoueur)) {
                salleAttente.supprimerNomTemporaire(nomJoueur);
                MonSubMod.JOURNALISEUR.info("Mapping de nom temporaire nettoyé pour {}", nomJoueur);
            }

            // Vérifier si le joueur a échoué la vérification de jeton - si oui, ne pas appeler authorizeNextInQueue
            // La fenêtre de monopole devrait rester active pour le vrai détenteur du jeton
            boolean verificationJetonEchouee = salleAttente.aEchoueVerificationJeton(joueur.getUUID());
            salleAttente.effacerEchecVerificationJeton(joueur.getUUID()); // Nettoyer le flag

            // Si le joueur était dans la salle (non authentifié), autoriser le suivant dans la file avec le temps restant
            // Utiliser le nom de compte RÉEL pour la recherche dans la file
            if (etaitDansSalle && !verificationJetonEchouee) {
                MonSubMod.JOURNALISEUR.info("Joueur {} (réel: {}) déconnecté de la salle d'attente avec {}ms restantes - autorisation du suivant dans la file",
                    nomJoueur, nomCompteReel, tempsRestant);
                salleAttente.autoriserProchainDansFile(nomCompteReel, tempsRestant);
            } else if (verificationJetonEchouee) {
                MonSubMod.JOURNALISEUR.info("Joueur {} a échoué la vérification de jeton - N'appelle PAS authorizeNextInQueue (fenêtre de monopole préservée)",
                    nomJoueur);
            } else if (etaitAuthentifie) {
                // Le joueur était authentifié et jouait - nettoyer tout état de file/whitelist restant
                // Cela garantit que le compte est entièrement disponible pour de nouvelles connexions
                MonSubMod.JOURNALISEUR.info("Joueur authentifié {} déconnecté - nettoyage de l'état de file pour le compte", nomCompteReel);
                salleAttente.effacerFilePourCompte(nomCompteReel);
            }
        }
    }

    /**
     * Système d'expulsion prioritaire: Si le serveur est plein (10 joueurs) et qu'un compte protégé se connecte,
     * expulser un JOUEUR_LIBRE aléatoire pour libérer de la place
     * NOTE: Les candidats file, les admins authentifiés et les joueurs non authentifiés ne sont PAS comptés comme vrais joueurs
     */
    public static void verifierEtExpulserPourPriorite(ServerPlayer joueurProtege, com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte) {
        int joueursMax = joueurProtege.server.getMaxPlayers();
        java.util.List<ServerPlayer> joueursEnLigne = joueurProtege.server.getPlayerList().getPlayers();
        com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();
        com.example.mysubmod.authentification.GestionnaireAuth gestAuth = com.example.mysubmod.authentification.GestionnaireAuth.getInstance();
        com.example.mysubmod.authentification.GestionnaireAuthAdmin gestAuthAdmin = com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance();

        // Compter seulement les vrais joueurs (exclure candidats file, admins authentifiés, et joueurs non authentifiés)
        int compteVraisJoueurs = 0;
        int compteAdmins = 0;
        int compteCandidatsFile = 0;
        int compteNonAuthentifies = 0;
        for (ServerPlayer p : joueursEnLigne) {
            String nom = p.getName().getString();
            if (salleAttente.estNomFileTemporaire(nom)) {
                compteCandidatsFile++;
            } else if (salleAttente.estDansLobbyStationnement(p.getUUID())) {
                // Joueur dans la salle d'attente (non authentifié) - ne pas compter comme vrai joueur
                compteNonAuthentifies++;
            } else {
                // Vérifier si c'est un admin authentifié (les admins authentifiés ne comptent pas dans max_players)
                com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte type = gestAuth.obtenirTypeCompte(nom);
                boolean estAdminAuthentifie = (type == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN &&
                                                gestAuthAdmin.estAuthentifie(p));

                if (estAdminAuthentifie) {
                    compteAdmins++;
                } else {
                    compteVraisJoueurs++;
                }
            }
        }

        // Vérifier si le serveur est plein (incluant le joueur protégé qui vient de rejoindre)
        // Si nous avons exactement joueursMax, nous devons expulser quelqu'un pour libérer de la place
        if (compteVraisJoueurs < joueursMax) {
            return; // Pas plein, pas d'expulsion nécessaire
        }

        MonSubMod.JOURNALISEUR.info("Serveur plein ({}/{} vrais joueurs, {} admins, {} non authentifiés, {} candidats file), recherche de joueurs libres à expulser pour le compte protégé {}",
            compteVraisJoueurs, joueursMax, compteAdmins, compteNonAuthentifies, compteCandidatsFile, joueurProtege.getName().getString());

        // Trouver tous les comptes FREE_PLAYER actuellement en ligne (exclure le joueur protégé qui vient de rejoindre, les candidats file, les admins, et les non authentifiés)
        java.util.List<ServerPlayer> joueursLibres = new java.util.ArrayList<>();

        for (ServerPlayer p : joueursEnLigne) {
            if (p.getUUID().equals(joueurProtege.getUUID())) {
                continue; // Passer le joueur protégé qui vient de rejoindre
            }

            String nom = p.getName().getString();

            // Passer les candidats file - ils ne sont PAS de vrais joueurs et ne devraient jamais être expulsés
            if (salleAttente.estNomFileTemporaire(nom)) {
                continue;
            }

            // Passer les joueurs non authentifiés - ils ne sont PAS de vrais joueurs et ne devraient jamais être expulsés
            if (salleAttente.estDansLobbyStationnement(p.getUUID())) {
                continue;
            }

            // Passer les admins authentifiés - ils ne comptent pas et ne peuvent pas être expulsés
            com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte type = gestAuth.obtenirTypeCompte(nom);
            boolean estAdminAuthentifie = (type == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN &&
                                            gestAuthAdmin.estAuthentifie(p));
            if (estAdminAuthentifie) {
                continue;
            }

            if (type == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_LIBRE) {
                joueursLibres.add(p);
            }
        }

        if (joueursLibres.isEmpty()) {
            MonSubMod.JOURNALISEUR.warn("Serveur plein mais aucun joueur libre à expulser pour {}", joueurProtege.getName().getString());
            return;
        }

        // Expulser un joueur libre aléatoire
        java.util.Random aleatoire = new java.util.Random();
        ServerPlayer joueurVictime = joueursLibres.get(aleatoire.nextInt(joueursLibres.size()));

        String chaineTypeCompte = typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN ? "administrateur" : "joueur protégé";

        MonSubMod.JOURNALISEUR.info("Expulsion du joueur libre {} pour libérer de la place pour le compte protégé {}",
            joueurVictime.getName().getString(), joueurProtege.getName().getString());

        joueurVictime.getServer().execute(() -> {
            joueurVictime.connection.disconnect(Component.literal(
                "§e§lServeur Complet\n\n" +
                "§7Un " + chaineTypeCompte + " s'est connecté.\n" +
                "§7Vous avez été déconnecté pour libérer une place."
            ));
        });
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }

        // Garder les candidats file et les protégés/admin non authentifiés invisibles
        com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();
        com.example.mysubmod.authentification.GestionnaireAuth gestAuth = com.example.mysubmod.authentification.GestionnaireAuth.getInstance();
        com.example.mysubmod.authentification.GestionnaireAuthAdmin gestAuthAdmin = com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance();

        for (ServerPlayer joueur : event.getServer().getPlayerList().getPlayers()) {
            String nomJoueur = joueur.getName().getString();
            boolean doitEtreInvisible = false;

            // Vérifier si candidat file
            if (salleAttente.estNomFileTemporaire(nomJoueur)) {
                doitEtreInvisible = true;
            } else {
                // Vérifier si protégé/admin non authentifié
                com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte typeCompte = gestAuth.obtenirTypeCompte(nomJoueur);
                boolean estProtegeOuAdmin = (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.JOUEUR_PROTEGE ||
                                              typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN);

                if (estProtegeOuAdmin) {
                    boolean estAuthentifie = false;
                    if (typeCompte == com.example.mysubmod.authentification.GestionnaireAuth.TypeCompte.ADMIN) {
                        estAuthentifie = gestAuthAdmin.estAuthentifie(joueur);
                    } else {
                        estAuthentifie = gestAuth.estAuthentifie(joueur.getUUID());
                    }

                    if (!estAuthentifie) {
                        doitEtreInvisible = true;
                    }
                }
            }

            // S'assurer qu'ils restent invisibles s'ils doivent l'être
            if (doitEtreInvisible && !joueur.isInvisible()) {
                joueur.setInvisible(true);
            }
        }
    }

    /**
     * Expulser le candidat file et le retirer de la file
     */
    private static void expulserCandidatFile(ServerPlayer joueur, String raison) {
        String nomJoueur = joueur.getName().getString();
        com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();

        // Obtenir le nom de compte réel
        String nomCompteReel = salleAttente.obtenirNomCompteOriginal(nomJoueur);
        if (nomCompteReel == null) {
            nomCompteReel = nomJoueur;
        }

        MonSubMod.JOURNALISEUR.warn("Expulsion du candidat file {} (compte: {}) - Raison: {}", nomJoueur, nomCompteReel, raison);

        // Retirer du statut de candidat file
        salleAttente.retirerCandidatFilePublic(nomCompteReel, joueur.getUUID());

        // Expulser le joueur
        joueur.getServer().execute(() -> {
            joueur.connection.disconnect(Component.literal(
                "§c§lAction non autorisée\n\n" +
                "§7Vous ne pouvez pas effectuer d'actions pendant l'authentification.\n" +
                "§7Vous avez été retiré de la file d'attente.\n\n" +
                "§eRaison: §f" + raison
            ));
        });
    }

    /**
     * Vérifier si le joueur est un candidat file OU un protégé/admin non authentifié (les deux sont restreints)
     */
    /**
     * Vérifier si le joueur est un VRAI candidat file (nom temporaire _Q_)
     * PAS les comptes protégés/admin réguliers en attente d'authentification
     */
    private static boolean estCandidatFile(ServerPlayer joueur) {
        String nomJoueur = joueur.getName().getString();
        com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();

        // Retourner true SEULEMENT pour les noms de candidats file temporaires (_Q_)
        // Les comptes protégés/admin réguliers ne devraient PAS être restreints par ces gestionnaires d'événements
        return salleAttente.estNomFileTemporaire(nomJoueur);
    }

    /**
     * Expulser les candidats file s'ils tentent de bouger
     * S'applique SEULEMENT aux VRAIS candidats file (noms temporaires _Q_), pas aux comptes protégés/admin réguliers
     */
    @SubscribeEvent
    public static void onPlayerMove(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) return;

        // Vérifier le mouvement SEULEMENT pour les VRAIS candidats file (noms temporaires)
        // PAS pour les comptes protégés/admin réguliers en attente d'authentification
        String nomJoueur = joueur.getName().getString();
        com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();
        if (!salleAttente.estNomFileTemporaire(nomJoueur)) return;

        // Vérifier si le joueur a bougé de manière significative (plus de 0.1 blocs)
        double dx = joueur.getX() - joueur.xOld;
        double dy = joueur.getY() - joueur.yOld;
        double dz = joueur.getZ() - joueur.zOld;
        double distanceCarre = dx * dx + dy * dy + dz * dz;

        if (distanceCarre > 0.01) { // 0.1 * 0.1
            expulserCandidatFile(joueur, "Mouvement détecté");
        }
    }

    /**
     * Expulser les candidats file s'ils tentent d'interagir avec quoi que ce soit
     * S'applique SEULEMENT aux VRAIS candidats file (noms temporaires _Q_), pas aux comptes protégés/admin réguliers
     */
    @SubscribeEvent
    public static void onPlayerInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) return;

        // Vérifier l'interaction SEULEMENT pour les VRAIS candidats file (noms temporaires)
        String nomJoueur = joueur.getName().getString();
        com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();
        if (!salleAttente.estNomFileTemporaire(nomJoueur)) return;

        expulserCandidatFile(joueur, "Interaction détectée");
        event.setCanceled(true);
    }

    /**
     * Expulser les candidats file s'ils tentent d'attaquer
     * S'applique SEULEMENT aux VRAIS candidats file (noms temporaires _Q_), pas aux comptes protégés/admin réguliers
     */
    @SubscribeEvent
    public static void onPlayerAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) return;

        // Vérifier les attaques SEULEMENT pour les VRAIS candidats file (noms temporaires)
        String nomJoueur = joueur.getName().getString();
        com.example.mysubmod.authentification.GestionnaireSalleAttente salleAttente = com.example.mysubmod.authentification.GestionnaireSalleAttente.getInstance();
        if (!salleAttente.estNomFileTemporaire(nomJoueur)) return;

        expulserCandidatFile(joueur, "Attaque détectée");
        event.setCanceled(true);
    }

    /**
     * Expulser les candidats file s'ils tentent de casser des blocs
     */
    @SubscribeEvent
    public static void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer joueur)) return;
        if (!estCandidatFile(joueur)) return;

        expulserCandidatFile(joueur, "Tentative de casser un bloc");
        event.setCanceled(true);
    }

    /**
     * Expulser les candidats file s'ils tentent de placer des blocs
     */
    @SubscribeEvent
    public static void onBlockPlace(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) return;
        if (!estCandidatFile(joueur)) return;

        expulserCandidatFile(joueur, "Tentative de placer un bloc");
        event.setCanceled(true);
    }

    /**
     * Expulser les candidats file s'ils tentent de jeter des items
     */
    @SubscribeEvent
    public static void onItemDrop(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer joueur)) return;
        if (!estCandidatFile(joueur)) return;

        expulserCandidatFile(joueur, "Tentative de jeter un item");
        event.setCanceled(true);
    }

    /**
     * Expulser les candidats file s'ils tentent de ramasser des items
     */
    @SubscribeEvent
    public static void onItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) return;
        if (!estCandidatFile(joueur)) return;

        expulserCandidatFile(joueur, "Tentative de ramasser un item");
        event.setCanceled(true);
    }
}

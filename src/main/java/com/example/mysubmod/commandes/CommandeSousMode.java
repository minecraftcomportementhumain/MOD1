package com.example.mysubmod.commandes;

import com.example.mysubmod.reseau.PaquetStatutAdmin;
import com.example.mysubmod.reseau.GestionnaireReseau;
import com.example.mysubmod.sousmodes.SousMode;
import com.example.mysubmod.sousmodes.GestionnaireSousModes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public class CommandeSousMode {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("submode")
            .then(Commands.literal("set")
                .then(Commands.argument("mode", StringArgumentType.string())
                    .executes(CommandeSousMode::definirSousMode)))
            .then(Commands.literal("admin")
                .then(Commands.literal("add")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(CommandeSousMode::ajouterAdmin)))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(CommandeSousMode::retirerAdmin)))
                .then(Commands.literal("list")
                    .executes(CommandeSousMode::listerAdmins))
                .then(Commands.literal("setpassword")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .then(Commands.argument("password", StringArgumentType.string())
                            .executes(CommandeSousMode::definirMotDePasseAdmin))))
                .then(Commands.literal("resetblacklist")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(CommandeSousMode::reinitialiserListeNoire)))
                .then(Commands.literal("resetfailures")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(CommandeSousMode::reinitialiserEchecs)))
                .then(Commands.literal("resetip")
                    .then(Commands.argument("ip", StringArgumentType.string())
                        .executes(CommandeSousMode::reinitialiserListeNoireIP))))
            .then(Commands.literal("player")
                .then(Commands.literal("add")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .then(Commands.argument("password", StringArgumentType.string())
                            .executes(CommandeSousMode::ajouterJoueurProtege))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(CommandeSousMode::retirerJoueurProtege)))
                .then(Commands.literal("list")
                    .executes(CommandeSousMode::listerJoueursProteges))
                .then(Commands.literal("setpassword")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .then(Commands.argument("password", StringArgumentType.string())
                            .executes(CommandeSousMode::definirMotDePasseJoueurProtege)))))
            .then(Commands.literal("current")
                .executes(CommandeSousMode::obtenirModeActuel))
        );
    }

    private static int definirSousMode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String chaineMode = StringArgumentType.getString(context, "mode");
        ServerPlayer joueur = context.getSource().getPlayerOrException();

        SousMode mode;
        switch (chaineMode.toLowerCase()) {
            case "waiting":
            case "attente":
                mode = SousMode.SALLE_ATTENTE;
                break;
            case "1":
            case "sub1":
                mode = SousMode.SOUS_MODE_1;
                break;
            case "2":
            case "sub2":
                mode = SousMode.SOUS_MODE_2;
                break;
            default:
                context.getSource().sendFailure(Component.literal("Mode invalide. Utilisez: waiting, 1, ou 2"));
                return 0;
        }

        if (GestionnaireSousModes.getInstance().changerSousMode(mode, joueur)) {
            context.getSource().sendSuccess(() -> Component.literal("Sous-mode changé vers: " + mode.obtenirNomAffichage()), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Vous n'avez pas les permissions pour changer de sous-mode"));
            return 0;
        }
    }

    private static int ajouterAdmin(CommandContext<CommandSourceStack> context) {
        if (!context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("Vous devez être administrateur du serveur pour utiliser cette commande"));
            return 0;
        }

        String nomJoueur = StringArgumentType.getString(context, "player");
        net.minecraft.server.MinecraftServer serveur = context.getSource().getServer();

        // Ajouter l'admin et déconnecter le joueur (fait dans la méthode addAdmin)
        GestionnaireSousModes.getInstance().ajouterAdmin(nomJoueur, serveur);

        context.getSource().sendSuccess(() -> Component.literal("Joueur " + nomJoueur + " ajouté comme admin et déconnecté"), true);
        return 1;
    }

    private static int retirerAdmin(CommandContext<CommandSourceStack> context) {
        if (!context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("Vous devez être administrateur du serveur pour utiliser cette commande"));
            return 0;
        }

        String nomJoueur = StringArgumentType.getString(context, "player");
        net.minecraft.server.MinecraftServer serveur = context.getSource().getServer();

        // Retirer l'admin et déconnecter le joueur (fait dans la méthode removeAdmin)
        GestionnaireSousModes.getInstance().retirerAdmin(nomJoueur, serveur);

        context.getSource().sendSuccess(() -> Component.literal("Admin " + nomJoueur + " supprimé et déconnecté"), true);
        return 1;
    }

    private static int listerAdmins(CommandContext<CommandSourceStack> context) {
        var admins = GestionnaireSousModes.getInstance().obtenirAdmins();
        if (admins.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Aucun admin configuré"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("Admins: " + String.join(", ", admins)), false);
        }
        return 1;
    }

    private static int definirMotDePasseAdmin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer joueur = context.getSource().getPlayerOrException();
        String joueurCible = StringArgumentType.getString(context, "player");

        com.example.mysubmod.authentification.GestionnaireAuthAdmin gestionnaireAuth = com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance();

        // Vérifier si le joueur est un admin authentifié
        if (!gestionnaireAuth.estAuthentifie(joueur)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String motDePasse = StringArgumentType.getString(context, "password");

        // Définir le mot de passe
        gestionnaireAuth.definirMotDePasseAdmin(joueurCible, motDePasse);

        context.getSource().sendSuccess(() ->
            Component.literal("§aMot de passe défini pour l'admin " + joueurCible), true);
        return 1;
    }

    private static int reinitialiserListeNoire(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer joueur = context.getSource().getPlayerOrException();

        // Vérifier si le joueur est un admin authentifié
        if (!com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().estAuthentifie(joueur)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String joueurCible = StringArgumentType.getString(context, "player");

        // Réinitialiser la liste noire
        com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().reinitialiserListeNoire(joueurCible);

        context.getSource().sendSuccess(() ->
            Component.literal("§aBlacklist réinitialisée pour " + joueurCible), true);
        return 1;
    }

    private static int reinitialiserEchecs(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer joueur = context.getSource().getPlayerOrException();

        // Vérifier si le joueur est un admin authentifié
        if (!com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().estAuthentifie(joueur)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String joueurCible = StringArgumentType.getString(context, "player");

        // Réinitialiser le compteur d'échecs
        com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().reinitialiserCompteurEchecs(joueurCible);

        context.getSource().sendSuccess(() ->
            Component.literal("§aCompteur d'échecs réinitialisé pour " + joueurCible), true);
        return 1;
    }

    private static int reinitialiserListeNoireIP(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer joueur = context.getSource().getPlayerOrException();

        // Vérifier si le joueur est un admin authentifié
        if (!com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().estAuthentifie(joueur)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String adresseIP = StringArgumentType.getString(context, "ip");

        // Réinitialiser la liste noire IP
        com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().reinitialiserListeNoireIP(adresseIP);

        context.getSource().sendSuccess(() ->
            Component.literal("§aBlacklist IP réinitialisée pour " + adresseIP), true);
        return 1;
    }

    private static int obtenirModeActuel(CommandContext<CommandSourceStack> context) {
        SousMode modeActuel = GestionnaireSousModes.getInstance().obtenirModeActuel();
        context.getSource().sendSuccess(() -> Component.literal("Mode actuel: " + modeActuel.obtenirNomAffichage()), false);
        return 1;
    }

    // ========== Commandes pour joueurs protégés ==========

    private static int ajouterJoueurProtege(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer joueur = context.getSource().getPlayerOrException();

        // Vérifier si le joueur est un admin authentifié
        if (!com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().estAuthentifie(joueur)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String nomJoueur = StringArgumentType.getString(context, "player");
        String motDePasse = StringArgumentType.getString(context, "password");

        // Ajouter le joueur protégé
        boolean succes = com.example.mysubmod.authentification.GestionnaireAuth.getInstance().ajouterJoueurProtege(nomJoueur, motDePasse);

        if (succes) {
            context.getSource().sendSuccess(() ->
                Component.literal("§aJoueur protégé ajouté: " + nomJoueur), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cÉchec: Le joueur existe déjà ou le mot de passe est invalide"));
            return 0;
        }
    }

    private static int retirerJoueurProtege(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer joueur = context.getSource().getPlayerOrException();

        // Vérifier si le joueur est un admin authentifié
        if (!com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().estAuthentifie(joueur)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String nomJoueur = StringArgumentType.getString(context, "player");

        // Retirer le joueur protégé
        boolean succes = com.example.mysubmod.authentification.GestionnaireAuth.getInstance().retirerJoueurProtege(nomJoueur);

        if (succes) {
            context.getSource().sendSuccess(() ->
                Component.literal("§aJoueur protégé retiré: " + nomJoueur), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cÉchec: Le joueur n'existe pas"));
            return 0;
        }
    }

    private static int listerJoueursProteges(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer joueur = context.getSource().getPlayerOrException();

        // Vérifier si le joueur est un admin authentifié
        if (!com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().estAuthentifie(joueur)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        java.util.List<String> joueursProteges = com.example.mysubmod.authentification.GestionnaireAuth.getInstance().listerJoueursProteges();

        if (joueursProteges.isEmpty()) {
            context.getSource().sendSuccess(() ->
                Component.literal("§eAucun joueur protégé enregistré"), false);
        } else {
            context.getSource().sendSuccess(() ->
                Component.literal("§aJoueurs protégés (" + joueursProteges.size() + "/10):"), false);
            for (String nom : joueursProteges) {
                context.getSource().sendSuccess(() ->
                    Component.literal("  §7- §f" + nom), false);
            }
        }

        return 1;
    }

    private static int definirMotDePasseJoueurProtege(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer joueur = context.getSource().getPlayerOrException();

        // Vérifier si le joueur est un admin authentifié
        if (!com.example.mysubmod.authentification.GestionnaireAuthAdmin.getInstance().estAuthentifie(joueur)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String nomJoueur = StringArgumentType.getString(context, "player");
        String motDePasse = StringArgumentType.getString(context, "password");

        // Définir le mot de passe
        boolean succes = com.example.mysubmod.authentification.GestionnaireAuth.getInstance().definirMotDePasseJoueurProtege(nomJoueur, motDePasse);

        if (succes) {
            context.getSource().sendSuccess(() ->
                Component.literal("§aMot de passe mis à jour pour " + nomJoueur), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cÉchec: Le joueur n'existe pas ou le mot de passe est invalide"));
            return 0;
        }
    }
}
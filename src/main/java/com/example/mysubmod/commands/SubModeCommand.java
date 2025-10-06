package com.example.mysubmod.commands;

import com.example.mysubmod.network.AdminStatusPacket;
import com.example.mysubmod.network.NetworkHandler;
import com.example.mysubmod.submodes.SubMode;
import com.example.mysubmod.submodes.SubModeManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public class SubModeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("submode")
            .then(Commands.literal("set")
                .then(Commands.argument("mode", StringArgumentType.string())
                    .executes(SubModeCommand::setSubMode)))
            .then(Commands.literal("admin")
                .then(Commands.literal("add")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(SubModeCommand::addAdmin)))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(SubModeCommand::removeAdmin)))
                .then(Commands.literal("list")
                    .executes(SubModeCommand::listAdmins))
                .then(Commands.literal("setpassword")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .then(Commands.argument("password", StringArgumentType.string())
                            .executes(SubModeCommand::setAdminPassword))))
                .then(Commands.literal("resetblacklist")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(SubModeCommand::resetBlacklist)))
                .then(Commands.literal("resetfailures")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(SubModeCommand::resetFailures)))
                .then(Commands.literal("resetip")
                    .then(Commands.argument("ip", StringArgumentType.string())
                        .executes(SubModeCommand::resetIPBlacklist))))
            .then(Commands.literal("player")
                .then(Commands.literal("add")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .then(Commands.argument("password", StringArgumentType.string())
                            .executes(SubModeCommand::addProtectedPlayer))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(SubModeCommand::removeProtectedPlayer)))
                .then(Commands.literal("list")
                    .executes(SubModeCommand::listProtectedPlayers))
                .then(Commands.literal("setpassword")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .then(Commands.argument("password", StringArgumentType.string())
                            .executes(SubModeCommand::setProtectedPlayerPassword)))))
            .then(Commands.literal("current")
                .executes(SubModeCommand::getCurrentMode))
        );
    }

    private static int setSubMode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String modeString = StringArgumentType.getString(context, "mode");
        ServerPlayer player = context.getSource().getPlayerOrException();

        SubMode mode;
        switch (modeString.toLowerCase()) {
            case "waiting":
            case "attente":
                mode = SubMode.WAITING_ROOM;
                break;
            case "1":
            case "sub1":
                mode = SubMode.SUB_MODE_1;
                break;
            case "2":
            case "sub2":
                mode = SubMode.SUB_MODE_2;
                break;
            default:
                context.getSource().sendFailure(Component.literal("Mode invalide. Utilisez: waiting, 1, ou 2"));
                return 0;
        }

        if (SubModeManager.getInstance().changeSubMode(mode, player)) {
            context.getSource().sendSuccess(() -> Component.literal("Sous-mode changé vers: " + mode.getDisplayName()), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Vous n'avez pas les permissions pour changer de sous-mode"));
            return 0;
        }
    }

    private static int addAdmin(CommandContext<CommandSourceStack> context) {
        if (!context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("Vous devez être administrateur du serveur pour utiliser cette commande"));
            return 0;
        }

        String playerName = StringArgumentType.getString(context, "player");
        net.minecraft.server.MinecraftServer server = context.getSource().getServer();

        // Add admin and disconnect player (done in addAdmin method)
        SubModeManager.getInstance().addAdmin(playerName, server);

        context.getSource().sendSuccess(() -> Component.literal("Joueur " + playerName + " ajouté comme admin et déconnecté"), true);
        return 1;
    }

    private static int removeAdmin(CommandContext<CommandSourceStack> context) {
        if (!context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("Vous devez être administrateur du serveur pour utiliser cette commande"));
            return 0;
        }

        String playerName = StringArgumentType.getString(context, "player");
        net.minecraft.server.MinecraftServer server = context.getSource().getServer();

        // Remove admin and disconnect player (done in removeAdmin method)
        SubModeManager.getInstance().removeAdmin(playerName, server);

        context.getSource().sendSuccess(() -> Component.literal("Admin " + playerName + " supprimé et déconnecté"), true);
        return 1;
    }

    private static int listAdmins(CommandContext<CommandSourceStack> context) {
        var admins = SubModeManager.getInstance().getAdmins();
        if (admins.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Aucun admin configuré"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("Admins: " + String.join(", ", admins)), false);
        }
        return 1;
    }

    private static int setAdminPassword(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String targetPlayer = StringArgumentType.getString(context, "player");

        com.example.mysubmod.auth.AdminAuthManager authManager = com.example.mysubmod.auth.AdminAuthManager.getInstance();

        // Check if player is authenticated admin
        if (!authManager.isAuthenticated(player)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String password = StringArgumentType.getString(context, "password");

        // Set the password
        authManager.setAdminPassword(targetPlayer, password);

        context.getSource().sendSuccess(() ->
            Component.literal("§aMot de passe défini pour l'admin " + targetPlayer), true);
        return 1;
    }

    private static int resetBlacklist(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // Check if player is authenticated admin
        if (!com.example.mysubmod.auth.AdminAuthManager.getInstance().isAuthenticated(player)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String targetPlayer = StringArgumentType.getString(context, "player");

        // Reset blacklist
        com.example.mysubmod.auth.AdminAuthManager.getInstance().resetBlacklist(targetPlayer);

        context.getSource().sendSuccess(() ->
            Component.literal("§aBlacklist réinitialisée pour " + targetPlayer), true);
        return 1;
    }

    private static int resetFailures(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // Check if player is authenticated admin
        if (!com.example.mysubmod.auth.AdminAuthManager.getInstance().isAuthenticated(player)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String targetPlayer = StringArgumentType.getString(context, "player");

        // Reset failure count
        com.example.mysubmod.auth.AdminAuthManager.getInstance().resetFailureCount(targetPlayer);

        context.getSource().sendSuccess(() ->
            Component.literal("§aCompteur d'échecs réinitialisé pour " + targetPlayer), true);
        return 1;
    }

    private static int resetIPBlacklist(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // Check if player is authenticated admin
        if (!com.example.mysubmod.auth.AdminAuthManager.getInstance().isAuthenticated(player)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String ipAddress = StringArgumentType.getString(context, "ip");

        // Reset IP blacklist
        com.example.mysubmod.auth.AdminAuthManager.getInstance().resetIPBlacklist(ipAddress);

        context.getSource().sendSuccess(() ->
            Component.literal("§aBlacklist IP réinitialisée pour " + ipAddress), true);
        return 1;
    }

    private static int getCurrentMode(CommandContext<CommandSourceStack> context) {
        SubMode current = SubModeManager.getInstance().getCurrentMode();
        context.getSource().sendSuccess(() -> Component.literal("Mode actuel: " + current.getDisplayName()), false);
        return 1;
    }

    // ========== Protected Player Commands ==========

    private static int addProtectedPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // Check if player is authenticated admin
        if (!com.example.mysubmod.auth.AdminAuthManager.getInstance().isAuthenticated(player)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String playerName = StringArgumentType.getString(context, "player");
        String password = StringArgumentType.getString(context, "password");

        // Add protected player
        boolean success = com.example.mysubmod.auth.AuthManager.getInstance().addProtectedPlayer(playerName, password);

        if (success) {
            context.getSource().sendSuccess(() ->
                Component.literal("§aJoueur protégé ajouté: " + playerName), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cÉchec: Le joueur existe déjà ou le mot de passe est invalide"));
            return 0;
        }
    }

    private static int removeProtectedPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // Check if player is authenticated admin
        if (!com.example.mysubmod.auth.AdminAuthManager.getInstance().isAuthenticated(player)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String playerName = StringArgumentType.getString(context, "player");

        // Remove protected player
        boolean success = com.example.mysubmod.auth.AuthManager.getInstance().removeProtectedPlayer(playerName);

        if (success) {
            context.getSource().sendSuccess(() ->
                Component.literal("§aJoueur protégé retiré: " + playerName), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cÉchec: Le joueur n'existe pas"));
            return 0;
        }
    }

    private static int listProtectedPlayers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // Check if player is authenticated admin
        if (!com.example.mysubmod.auth.AdminAuthManager.getInstance().isAuthenticated(player)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        java.util.List<String> protectedPlayers = com.example.mysubmod.auth.AuthManager.getInstance().listProtectedPlayers();

        if (protectedPlayers.isEmpty()) {
            context.getSource().sendSuccess(() ->
                Component.literal("§eAucun joueur protégé enregistré"), false);
        } else {
            context.getSource().sendSuccess(() ->
                Component.literal("§aJoueurs protégés (" + protectedPlayers.size() + "/10):"), false);
            for (String name : protectedPlayers) {
                context.getSource().sendSuccess(() ->
                    Component.literal("  §7- §f" + name), false);
            }
        }

        return 1;
    }

    private static int setProtectedPlayerPassword(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // Check if player is authenticated admin
        if (!com.example.mysubmod.auth.AdminAuthManager.getInstance().isAuthenticated(player)) {
            context.getSource().sendFailure(Component.literal("§cVous devez être un administrateur authentifié pour utiliser cette commande"));
            return 0;
        }

        String playerName = StringArgumentType.getString(context, "player");
        String password = StringArgumentType.getString(context, "password");

        // Set password
        boolean success = com.example.mysubmod.auth.AuthManager.getInstance().setProtectedPlayerPassword(playerName, password);

        if (success) {
            context.getSource().sendSuccess(() ->
                Component.literal("§aMot de passe mis à jour pour " + playerName), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cÉchec: Le joueur n'existe pas ou le mot de passe est invalide"));
            return 0;
        }
    }
}
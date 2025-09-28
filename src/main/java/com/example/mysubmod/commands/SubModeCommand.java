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
                    .executes(SubModeCommand::listAdmins)))
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
        SubModeManager.getInstance().addAdmin(playerName);

        ServerPlayer targetPlayer = context.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (targetPlayer != null) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> targetPlayer), new AdminStatusPacket(true));
        }

        context.getSource().sendSuccess(() -> Component.literal("Joueur " + playerName + " ajouté comme admin"), true);
        return 1;
    }

    private static int removeAdmin(CommandContext<CommandSourceStack> context) {
        if (!context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("Vous devez être administrateur du serveur pour utiliser cette commande"));
            return 0;
        }

        String playerName = StringArgumentType.getString(context, "player");
        SubModeManager.getInstance().removeAdmin(playerName);

        ServerPlayer targetPlayer = context.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (targetPlayer != null) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> targetPlayer), new AdminStatusPacket(false));
        }

        context.getSource().sendSuccess(() -> Component.literal("Admin " + playerName + " supprimé"), true);
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

    private static int getCurrentMode(CommandContext<CommandSourceStack> context) {
        SubMode current = SubModeManager.getInstance().getCurrentMode();
        context.getSource().sendSuccess(() -> Component.literal("Mode actuel: " + current.getDisplayName()), false);
        return 1;
    }
}
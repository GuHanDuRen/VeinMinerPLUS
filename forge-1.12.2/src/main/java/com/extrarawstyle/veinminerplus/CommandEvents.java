package com.extrarawstyle.veinminerplus;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.command.CommandHandler;

public final class CommandEvents {
    private CommandEvents() {
    }

    public static void register(CommandHandler manager) {
        manager.registerCommand(new VeinMinerCommand());
    }

    private static final class VeinMinerCommand extends CommandBase {
        @Override
        public String getName() {
            return "veinminerplus";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/veinminerplus gui | /veinminerplus debug <true|false>";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 1 && "gui".equalsIgnoreCase(args[0])) {
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                NetworkHandler.openConfigScreen(player);
                return;
            }
            if (args.length == 2 && "debug".equalsIgnoreCase(args[0])
                    && ("true".equalsIgnoreCase(args[1]) || "false".equalsIgnoreCase(args[1]))) {
                Config.debugLogging = Boolean.parseBoolean(args[1]);
                Config.save();
                TextComponentTranslation message = new TextComponentTranslation(Config.debugLogging
                        ? "message.veinminerplus.debug_enabled" : "message.veinminerplus.debug_disabled");
                message.getStyle().setColor(TextFormatting.GREEN);
                sender.sendMessage(message);
                return;
            }
            throw new WrongUsageException(getUsage(sender));
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                BlockPos targetPos) {
            if (args.length == 1) {
                return getListOfStringsMatchingLastWord(args, "gui", "debug");
            }
            if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
                return getListOfStringsMatchingLastWord(args, "true", "false");
            }
            return Collections.emptyList();
        }
    }
}

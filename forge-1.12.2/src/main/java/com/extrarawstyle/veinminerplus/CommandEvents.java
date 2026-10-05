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
            return "/veinminerplus gui";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1 || !"gui".equalsIgnoreCase(args[0])) {
                throw new WrongUsageException(getUsage(sender));
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            NetworkHandler.openConfigScreen(player);
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                BlockPos targetPos) {
            if (args.length == 1) {
                return getListOfStringsMatchingLastWord(args, "gui");
            }
            return Collections.emptyList();
        }
    }
}

package com.extrarawstyle.veinminerplus;

import java.util.Arrays;
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
        private static final int BLAST_SPEED_MIN = 1;
        private static final int BLAST_SPEED_MAX = Integer.MAX_VALUE;
        private static final int BLAST_DISTANCE_MIN = 3;
        private static final int BLAST_DISTANCE_MAX = Integer.MAX_VALUE;

        @Override
        public String getName() {
            return "veinminerplus";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/veinminerplus <gui|blast-speed|blast-distance|blast-auto-radius|hunger-consumption>";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) {
                send(sender, "commands.veinminerplus.blast_speed", Config.maxBlastBlocksPerTick, BLAST_SPEED_MIN,
                        BLAST_SPEED_MAX);
                return;
            }
            String sub = args[0].toLowerCase();
            if ("gui".equals(sub)) {
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                NetworkHandler.openConfigScreen(player);
                return;
            }
            if ("blast-speed".equals(sub)) {
                if (args.length > 1) {
                    Config.maxBlastBlocksPerTick = parseInt(args[1], BLAST_SPEED_MIN, BLAST_SPEED_MAX);
                    Config.save();
                }
                send(sender, "commands.veinminerplus.blast_speed", Config.maxBlastBlocksPerTick, BLAST_SPEED_MIN,
                        BLAST_SPEED_MAX);
                return;
            }
            if ("blast-distance".equals(sub)) {
                if (args.length > 1) {
                    Config.blastSearchDistance = parseInt(args[1], BLAST_DISTANCE_MIN, BLAST_DISTANCE_MAX);
                    Config.save();
                }
                send(sender, "commands.veinminerplus.blast_distance", Config.blastSearchDistance,
                        BLAST_DISTANCE_MIN, BLAST_DISTANCE_MAX);
                return;
            }
            if ("blast-auto-radius".equals(sub)) {
                if (args.length > 1) {
                    Config.blastAutoReduceRadius = parseBoolean(args[1]);
                    Config.save();
                }
                sender.sendMessage(new TextComponentTranslation("commands.veinminerplus.blast_auto_radius",
                        Config.blastAutoReduceRadius));
                return;
            }
            if ("hunger-consumption".equals(sub)) {
                if (args.length > 1) {
                    Config.consumeHunger = parseBoolean(args[1]);
                    Config.save();
                }
                sender.sendMessage(new TextComponentTranslation("commands.veinminerplus.hunger_consumption",
                        Config.consumeHunger));
                return;
            }
            throw new WrongUsageException(getUsage(sender));
        }

        private static void send(ICommandSender sender, String key, Object... args) {
            sender.sendMessage(new TextComponentTranslation(key, args));
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                BlockPos targetPos) {
            if (args.length == 1) {
                return getListOfStringsMatchingLastWord(args, Arrays.asList("gui", "blast-speed",
                        "blast-distance", "blast-auto-radius", "hunger-consumption"));
            }
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if ("blast-auto-radius".equals(sub) || "hunger-consumption".equals(sub)) {
                    return getListOfStringsMatchingLastWord(args, "true", "false");
                }
                if ("blast-speed".equals(sub)) {
                    return getListOfStringsMatchingLastWord(args, Integer.toString(BLAST_SPEED_MIN),
                            Integer.toString(BLAST_SPEED_MAX));
                }
                if ("blast-distance".equals(sub)) {
                    return getListOfStringsMatchingLastWord(args, Integer.toString(BLAST_DISTANCE_MIN),
                            Integer.toString(BLAST_DISTANCE_MAX));
                }
            }
            return Collections.emptyList();
        }
    }
}

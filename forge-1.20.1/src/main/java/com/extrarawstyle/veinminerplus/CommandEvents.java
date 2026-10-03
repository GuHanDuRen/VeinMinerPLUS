package com.extrarawstyle.veinminerplus;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CommandEvents {
    private static final int MIN_BLAST_BLOCKS_PER_TICK = 1;
    private static final int MAX_BLAST_BLOCKS_PER_TICK = Integer.MAX_VALUE;
    private static final int MIN_BLAST_SEARCH_DISTANCE = 3;
    private static final int MAX_BLAST_SEARCH_DISTANCE = Integer.MAX_VALUE;

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("veinminerplus")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("blast-speed")
                .executes(context -> showBlastSpeed(context.getSource()))
                .then(Commands.argument("blocksPerTick",
                        IntegerArgumentType.integer(MIN_BLAST_BLOCKS_PER_TICK, MAX_BLAST_BLOCKS_PER_TICK))
                        .executes(context -> setBlastSpeed(context.getSource(),
                                IntegerArgumentType.getInteger(context, "blocksPerTick")))));
        root.then(Commands.literal("blast-distance")
                .executes(context -> showBlastDistance(context.getSource()))
                .then(Commands.argument("distance",
                        IntegerArgumentType.integer(MIN_BLAST_SEARCH_DISTANCE, MAX_BLAST_SEARCH_DISTANCE))
                        .executes(context -> setBlastDistance(context.getSource(),
                                IntegerArgumentType.getInteger(context, "distance")))));
        root.then(Commands.literal("blast-auto-radius")
                .executes(context -> showBlastAutoRadius(context.getSource()))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> setBlastAutoRadius(context.getSource(),
                                BoolArgumentType.getBool(context, "enabled")))));
        root.then(Commands.literal("hunger-consumption")
                .executes(context -> showHungerConsumption(context.getSource()))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> setHungerConsumption(context.getSource(),
                                BoolArgumentType.getBool(context, "enabled")))));
        root.then(Commands.literal("gui")
                .executes(context -> openGui(context.getSource())));
        event.getDispatcher().register(root);
    }

    private static int showBlastSpeed(CommandSourceStack source) {
        int blocksPerTick = Config.MAX_BLAST_BLOCKS_PER_TICK.get();
        source.sendSuccess(() -> Component.translatable("commands.veinminerplus.blast_speed",
                blocksPerTick, MIN_BLAST_BLOCKS_PER_TICK, MAX_BLAST_BLOCKS_PER_TICK), false);
        return blocksPerTick;
    }

    private static int setBlastSpeed(CommandSourceStack source, int blocksPerTick) {
        Config.MAX_BLAST_BLOCKS_PER_TICK.set(blocksPerTick);
        Config.SPEC.save();
        source.sendSuccess(() -> Component.translatable("commands.veinminerplus.blast_speed",
                blocksPerTick, MIN_BLAST_BLOCKS_PER_TICK, MAX_BLAST_BLOCKS_PER_TICK), true);
        return blocksPerTick;
    }

    private static int showBlastDistance(CommandSourceStack source) {
        int distance = Config.BLAST_SEARCH_DISTANCE.get();
        source.sendSuccess(() -> Component.translatable("commands.veinminerplus.blast_distance",
                distance, MIN_BLAST_SEARCH_DISTANCE, MAX_BLAST_SEARCH_DISTANCE), false);
        return distance;
    }

    private static int setBlastDistance(CommandSourceStack source, int distance) {
        Config.BLAST_SEARCH_DISTANCE.set(distance);
        Config.SPEC.save();
        source.sendSuccess(() -> Component.translatable("commands.veinminerplus.blast_distance",
                distance, MIN_BLAST_SEARCH_DISTANCE, MAX_BLAST_SEARCH_DISTANCE), true);
        return distance;
    }

    private static int showBlastAutoRadius(CommandSourceStack source) {
        boolean enabled = Config.BLAST_AUTO_REDUCE_RADIUS.get();
        source.sendSuccess(() -> Component.translatable("commands.veinminerplus.blast_auto_radius", enabled), false);
        return enabled ? 1 : 0;
    }

    private static int setBlastAutoRadius(CommandSourceStack source, boolean enabled) {
        Config.BLAST_AUTO_REDUCE_RADIUS.set(enabled);
        Config.SPEC.save();
        source.sendSuccess(() -> Component.translatable("commands.veinminerplus.blast_auto_radius", enabled), true);
        return enabled ? 1 : 0;
    }

    private static int openGui(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        NetworkHandler.openConfigScreen(source.getPlayerOrException());
        return 1;
    }

    private static int showHungerConsumption(CommandSourceStack source) {
        boolean enabled = Config.CONSUME_HUNGER.get();
        source.sendSuccess(() -> Component.translatable("commands.veinminerplus.hunger_consumption", enabled), false);
        return enabled ? 1 : 0;
    }

    private static int setHungerConsumption(CommandSourceStack source, boolean enabled) {
        Config.CONSUME_HUNGER.set(enabled);
        Config.SPEC.save();
        source.sendSuccess(() -> Component.translatable("commands.veinminerplus.hunger_consumption", enabled), true);
        return enabled ? 1 : 0;
    }
}

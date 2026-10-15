package com.extrarawstyle.veinminerplus;

import com.mojang.brigadier.arguments.BoolArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CommandEvents {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("veinminerplus")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("gui")
                .executes(context -> openGui(context.getSource())));
        root.then(Commands.literal("debug")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> setDebug(context.getSource(),
                                BoolArgumentType.getBool(context, "enabled")))));
        event.getDispatcher().register(root);
    }

    private static int setDebug(CommandSourceStack source, boolean enabled) {
        Config.DEBUG_LOGGING.set(enabled);
        Config.SPEC.save();
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.veinminerplus.debug_enabled" : "message.veinminerplus.debug_disabled"), true);
        return 1;
    }

    private static int openGui(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        NetworkHandler.openConfigScreen(source.getPlayerOrException());
        return 1;
    }
}

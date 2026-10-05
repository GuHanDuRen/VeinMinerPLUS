package com.extrarawstyle.veinminerplus;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class CommandEvents {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("veinminerplus")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("gui")
                .executes(context -> openGui(context.getSource())));
        event.getDispatcher().register(root);
    }

    private static int openGui(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        NetworkHandler.openConfigScreen(source.getPlayerOrException());
        return 1;
    }
}

package com.extrarawstyle.veinminerplus;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@Mod(VeinMinerPlus.MODID)
public class VeinMinerPlus {
    public static final String MODID = "veinminerplus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VeinMinerPlus(IEventBus modEventBus, ModContainer modContainer) {
        ModEffects.register(modEventBus);
        modEventBus.addListener(NetworkHandler::register);
        modEventBus.addListener(VeinMinerPlus::onConfigReloading);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NeoForge.EVENT_BUS.register(new ChainEvents());
        NeoForge.EVENT_BUS.register(new CommandEvents());
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != Config.SPEC) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                ChainEvents.invalidateChunkScanCache();
                NetworkHandler.broadcastBlacklist();
            });
        }
    }
}

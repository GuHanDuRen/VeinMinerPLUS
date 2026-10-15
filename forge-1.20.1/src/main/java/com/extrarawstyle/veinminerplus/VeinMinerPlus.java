package com.extrarawstyle.veinminerplus;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod(VeinMinerPlus.MODID)
public class VeinMinerPlus {
    public static final String MODID = "veinminerplus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VeinMinerPlus() {
        ModEffects.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);
        NetworkHandler.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new ChainEvents());
        MinecraftForge.EVENT_BUS.register(new CommandEvents());
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != Config.SPEC) {
            return;
        }
        ChainEvents.invalidateChunkScanCache();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                for (var player : server.getPlayerList().getPlayers()) {
                    NetworkHandler.sendBlacklistSync(player);
                }
            });
        }
    }
}

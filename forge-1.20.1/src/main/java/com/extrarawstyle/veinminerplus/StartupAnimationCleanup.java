package com.extrarawstyle.veinminerplus;

import java.lang.reflect.Method;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;

@Mod.EventBusSubscriber(modid = VeinMinerPlus.MODID, value = Dist.CLIENT)
public final class StartupAnimationCleanup {
    private static boolean checked;
    private static Method cleanup;

    private StartupAnimationCleanup() {
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        try {
            if (!checked) {
                checked = true;
                var module = FMLLoader.getGameLayer().findModule("veinminerplus_startup");
                if (module.isPresent()) {
                    Class<?> bootstrap = Class.forName(module.get(),
                            "com.extrarawstyle.veinminerplus.startup.StartupBootstrap");
                    cleanup = bootstrap.getMethod("finishIfWindowClosed");
                }
            }
            if (cleanup != null && Boolean.TRUE.equals(cleanup.invoke(null))) {
                cleanup = null;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            cleanup = null;
            VeinMinerPlus.LOGGER.warn("Could not release the startup animation", exception);
        }
    }
}

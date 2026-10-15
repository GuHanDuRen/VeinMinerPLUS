package com.extrarawstyle.veinminerplus.startup;

import java.io.File;
import java.util.Map;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("VeinMinerPlusStartup")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions("com.extrarawstyle.veinminerplus.startup.")
public final class StartupLoadingPlugin implements IFMLLoadingPlugin {
    private Boolean enabled;

    @Override
    public String[] getASMTransformerClass() {
        if (FMLLaunchHandler.side() == null || !FMLLaunchHandler.side().isClient()) {
            return new String[0];
        }
        if (enabled == null) {
            try {
                File gameDirectory = (File) FMLInjectionData.data()[6];
                enabled = StartupConfig.readEnabled(new File(gameDirectory, "config/veinminerplus-client.cfg"));
            } catch (RuntimeException | LinkageError exception) {
                FMLLog.log.warn("Could not load the startup animation setting; keeping the Forge logo", exception);
                enabled = false;
            }
        }
        return enabled ? new String[] { "com.extrarawstyle.veinminerplus.startup.StartupTransformer" }
                : new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}

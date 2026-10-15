package com.extrarawstyle.veinminerplus.startup;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

final class StartupConfig {
    private StartupConfig() {
    }

    static boolean readEnabled(File file) {
        Configuration config = new Configuration(file);
        boolean enabled = config.getBoolean("customStartupAnimation", Configuration.CATEGORY_CLIENT, true,
                "Replace the Forge startup logo with the bundled animation. Requires a game restart.");
        if (config.hasChanged()) {
            config.save();
        }
        return enabled;
    }
}

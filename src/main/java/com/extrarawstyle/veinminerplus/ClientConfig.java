package com.extrarawstyle.veinminerplus;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CUSTOM_STARTUP_ANIMATION = BUILDER
            .translation("config.veinminerplus.custom_startup_animation")
            .comment("Replace the NeoForge startup fox with the bundled animation. Requires a game restart.")
            .define("customStartupAnimation", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}

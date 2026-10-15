package com.extrarawstyle.veinminerplus;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue CUSTOM_STARTUP_ANIMATION = BUILDER
            .translation("config.veinminerplus.custom_startup_animation")
            .comment("Replace the Forge startup anvil with the bundled animation. Requires a game restart.")
            .define("customStartupAnimation", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}

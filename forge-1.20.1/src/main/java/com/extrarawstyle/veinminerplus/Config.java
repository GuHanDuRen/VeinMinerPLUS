package com.extrarawstyle.veinminerplus;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue MAX_NORMAL_BLOCKS = BUILDER
            .comment("Maximum blocks in normal connected mode. Range: 32-32767.")
            .defineInRange("maxNormalBlocks", 1024, 32, 32767);

    public static final ForgeConfigSpec.IntValue MAX_NORMAL_BLOCKS_PER_TICK = BUILDER
            .comment("Maximum normal chain blocks broken per server tick. Range: 1-384.")
            .defineInRange("maxNormalBlocksPerTick", 8, 1, 384);

    public static final ForgeConfigSpec.IntValue MAX_BLAST_BLOCKS = BUILDER
            .comment("Maximum blocks in blast modes. Range: 32-32767.")
            .defineInRange("maxBlastBlocks", 32767, 32, 32767);

    public static final ForgeConfigSpec.IntValue MAX_BLAST_BLOCKS_PER_TICK = BUILDER
            .comment("Maximum blast blocks broken per server tick. Range: 1-512.")
            .defineInRange("maxBlastBlocksPerTick", 64, 1, 512);

    public static final ForgeConfigSpec.IntValue BLAST_SEARCH_DISTANCE = BUILDER
            .comment("Maximum blast search distance from each found block. Range: 3-128.")
            .defineInRange("blastSearchDistance", 20, 3, 128);

    public static final ForgeConfigSpec.BooleanValue BLAST_MANHATTAN = BUILDER
            .comment("Use Manhattan distance instead of spherical distance for blast searches.")
            .define("blastManhattan", true);

    public static final ForgeConfigSpec.IntValue DEFAULT_MODE = BUILDER
            .comment("Default chain mode ordinal used for players without a session override. Range: 0-6.")
            .defineInRange("defaultMode", 0, 0, 6);

    public static final ForgeConfigSpec.IntValue BLAST_LOW_TPS_THRESHOLD = BUILDER
            .comment("Warn when server TPS falls below this value during blast mining. Range: 5-20.")
            .defineInRange("blastLowTpsThreshold", 15, 5, 20);

    public static final ForgeConfigSpec.BooleanValue BLAST_AUTO_REDUCE_RADIUS = BUILDER
            .comment("Automatically reduce the active blast radius when server TPS is too low.")
            .define("blastAutoReduceRadius", true);

    public static final ForgeConfigSpec.BooleanValue CONSUME_HUNGER = BUILDER
            .comment("Whether chain mining consumes hunger. Disabled by default.")
            .define("consumeHunger", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}

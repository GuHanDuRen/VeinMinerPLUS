package com.extrarawstyle.veinminerplus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final int DEFAULT_MAX_NORMAL_BLOCKS = 1024;
    public static final int DEFAULT_MAX_NORMAL_BLOCKS_PER_TICK = 8;
    public static final int DEFAULT_MAX_BLAST_BLOCKS = 32767;
    public static final int DEFAULT_MAX_BLAST_BLOCKS_PER_TICK = 64;
    public static final int DEFAULT_BLAST_SEARCH_DISTANCE = 20;
    public static final int DEFAULT_BLAST_LOW_TPS_THRESHOLD = 15;
    public static final boolean DEFAULT_BLAST_MANHATTAN = true;
    public static final boolean DEFAULT_BLAST_AUTO_REDUCE_RADIUS = true;
    public static final boolean DEFAULT_CONSUME_HUNGER = false;
    public static final int DEFAULT_MODE_ORDINAL = 0;
    public static final List<String> DEFAULT_BLOCK_WHITELIST = List.of("*ore");
    static final int MAX_WHITELIST_ENTRIES = 256;
    static final int MAX_WHITELIST_ENTRY_LENGTH = 128;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_NORMAL_BLOCKS = BUILDER
            .comment("Maximum blocks in normal connected mode. Range: 32-32767.")
            .defineInRange("maxNormalBlocks", DEFAULT_MAX_NORMAL_BLOCKS, 32, 32767);

    public static final ModConfigSpec.IntValue MAX_NORMAL_BLOCKS_PER_TICK = BUILDER
            .comment("Maximum normal chain blocks broken per server tick. Range: 1-384.")
            .defineInRange("maxNormalBlocksPerTick", DEFAULT_MAX_NORMAL_BLOCKS_PER_TICK, 1, 384);

    public static final ModConfigSpec.IntValue MAX_BLAST_BLOCKS = BUILDER
            .comment("Maximum blocks in blast modes. Range: 32-32767.")
            .defineInRange("maxBlastBlocks", DEFAULT_MAX_BLAST_BLOCKS, 32, 32767);

    public static final ModConfigSpec.IntValue MAX_BLAST_BLOCKS_PER_TICK = BUILDER
            .comment("Maximum blast blocks broken per server tick. Range: 1-512.")
            .defineInRange("maxBlastBlocksPerTick", DEFAULT_MAX_BLAST_BLOCKS_PER_TICK, 1, 512);

    public static final ModConfigSpec.IntValue BLAST_SEARCH_DISTANCE = BUILDER
            .comment("Maximum blast search distance from each found block. Range: 3-128.")
            .defineInRange("blastSearchDistance", DEFAULT_BLAST_SEARCH_DISTANCE, 3, 128);

    public static final ModConfigSpec.BooleanValue BLAST_MANHATTAN = BUILDER
            .comment("Use Manhattan distance instead of spherical distance for blast searches.")
            .define("blastManhattan", DEFAULT_BLAST_MANHATTAN);

    public static final ModConfigSpec.IntValue DEFAULT_MODE = BUILDER
            .comment("Default chain mode ordinal used for players without a session override. Range: 0-6.")
            .defineInRange("defaultMode", DEFAULT_MODE_ORDINAL, 0, 6);

    public static final ModConfigSpec.IntValue BLAST_LOW_TPS_THRESHOLD = BUILDER
            .comment("Warn when server TPS falls below this value during blast mining. Range: 5-20.")
            .defineInRange("blastLowTpsThreshold", DEFAULT_BLAST_LOW_TPS_THRESHOLD, 5, 20);

    public static final ModConfigSpec.BooleanValue BLAST_AUTO_REDUCE_RADIUS = BUILDER
            .comment("Automatically reduce the active blast radius when server TPS is too low.")
            .define("blastAutoReduceRadius", DEFAULT_BLAST_AUTO_REDUCE_RADIUS);

    public static final ModConfigSpec.BooleanValue CONSUME_HUNGER = BUILDER
            .comment("Whether chain mining consumes hunger. Disabled by default.")
            .define("consumeHunger", DEFAULT_CONSUME_HUNGER);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCK_WHITELIST = BUILDER
            .comment("Block whitelist rules. Supports block IDs, * wildcards, and #block tags. Default: *ore.")
            .defineListAllowEmpty("blockWhitelist", DEFAULT_BLOCK_WHITELIST, value -> value instanceof String string
                    && isValidBlockId(string));

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    private static boolean isValidBlockId(String value) {
        return normalizeBlockId(value) != null;
    }

    static List<String> parseWhitelistText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return normalizeWhitelist(List.of(text.split("[,;\\r\\n]+")));
    }

    static List<String> normalizeWhitelist(Iterable<?> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String string)) {
                continue;
            }
            if (string.trim().length() > MAX_WHITELIST_ENTRY_LENGTH) {
                continue;
            }
            String id = normalizeBlockId(string);
            if (id != null) {
                normalized.add(id);
            }
            if (normalized.size() >= MAX_WHITELIST_ENTRIES) {
                break;
            }
        }
        return List.copyOf(normalized);
    }

    static String whitelistText() {
        return String.join(", ", effectiveWhitelist(BLOCK_WHITELIST.get()));
    }

    static List<String> effectiveWhitelist(Iterable<?> values) {
        List<String> normalized = normalizeWhitelist(values);
        return normalized.isEmpty() ? DEFAULT_BLOCK_WHITELIST : normalized;
    }

    static String normalizeBlockId(String value) {
        if (value == null) {
            return null;
        }
        String id = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (id.isEmpty()) {
            return null;
        }
        if (id.length() > MAX_WHITELIST_ENTRY_LENGTH) {
            return null;
        }
        boolean tag = id.startsWith("#");
        if (tag) {
            id = id.substring(1).trim();
            if (id.isEmpty()) {
                return null;
            }
        }
        boolean wildcard = id.indexOf('*') >= 0;
        if (!id.contains(":") && !wildcard) {
            id = "minecraft:" + id;
        }
        if (wildcard) {
            if (tag) {
                return null;
            }
            if (!id.contains(":")) {
                return id.matches("[a-z0-9_.*-]+") ? id : null;
            }
            String[] parts = id.split(":", 2);
            if (parts.length != 2 || !parts[0].matches("[a-z0-9_.-]+")
                    || !parts[1].matches("[a-z0-9_.*-]+")) {
                return null;
            }
            return parts[0] + ":" + parts[1];
        }
        net.minecraft.resources.ResourceLocation location = net.minecraft.resources.ResourceLocation.tryParse(id);
        return location == null ? null : (tag ? "#" + location : location.toString());
    }
}

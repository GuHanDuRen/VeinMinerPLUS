package com.extrarawstyle.veinminerplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;

public final class Config {
    public static final int DEFAULT_MAX_NORMAL_BLOCKS = 1024;
    public static final int DEFAULT_MAX_NORMAL_BLOCKS_PER_TICK = 32;
    public static final int DEFAULT_MAX_BLAST_BLOCKS = 32767;
    public static final int DEFAULT_MAX_BLAST_BLOCKS_PER_TICK = 64;
    public static final int DEFAULT_BLAST_SEARCH_DISTANCE = 48;
    public static final int DEFAULT_BLAST_CHUNK_SCANS_PER_TICK = 8;
    public static final int MAX_BLAST_CHUNK_SCANS_PER_TICK = 1024;
    public static final int DEFAULT_BLAST_LOW_TPS_THRESHOLD = 15;
    public static final boolean DEFAULT_BLAST_MANHATTAN = true;
    public static final boolean DEFAULT_BLAST_AUTO_REDUCE_RADIUS = true;
    public static final boolean DEFAULT_CONSUME_HUNGER = false;
    public static final boolean DEFAULT_DEBUG_LOGGING = false;
    public static final int DEFAULT_MODE_ORDINAL = 0;
    public static final List<String> DEFAULT_BLOCK_WHITELIST = Collections.singletonList("*ore");
    public static final int MAX_WHITELIST_ENTRIES = 256;
    public static final int MAX_WHITELIST_ENTRY_LENGTH = 128;

    private static Configuration config;

    public static int maxNormalBlocks = DEFAULT_MAX_NORMAL_BLOCKS;
    public static int maxNormalBlocksPerTick = DEFAULT_MAX_NORMAL_BLOCKS_PER_TICK;
    public static int maxBlastBlocks = DEFAULT_MAX_BLAST_BLOCKS;
    public static int maxBlastBlocksPerTick = DEFAULT_MAX_BLAST_BLOCKS_PER_TICK;
    public static int blastSearchDistance = DEFAULT_BLAST_SEARCH_DISTANCE;
    public static int blastChunkScansPerTick = DEFAULT_BLAST_CHUNK_SCANS_PER_TICK;
    public static boolean blastManhattan = DEFAULT_BLAST_MANHATTAN;
    public static int defaultMode = DEFAULT_MODE_ORDINAL;
    public static int blastLowTpsThreshold = DEFAULT_BLAST_LOW_TPS_THRESHOLD;
    public static boolean blastAutoReduceRadius = DEFAULT_BLAST_AUTO_REDUCE_RADIUS;
    public static boolean consumeHunger = DEFAULT_CONSUME_HUNGER;
    public static boolean debugLogging = DEFAULT_DEBUG_LOGGING;
    public static List<String> blockWhitelist = new ArrayList<String>(DEFAULT_BLOCK_WHITELIST);

    private Config() {
    }

    public static void load(java.io.File file) {
        config = new Configuration(file);
        syncConfig();
    }

    public static void syncConfig() {
        if (config == null) {
            return;
        }
        String category = "chain_mining";
        maxNormalBlocks = config.getInt("maxNormalBlocks", category, DEFAULT_MAX_NORMAL_BLOCKS, 32,
                Integer.MAX_VALUE, "Maximum blocks in normal connected mode. Range: 32-2147483647.");
        maxNormalBlocksPerTick = config.getInt("maxNormalBlocksPerTick", category,
                DEFAULT_MAX_NORMAL_BLOCKS_PER_TICK, 1, Integer.MAX_VALUE,
                "Maximum normal and area chain blocks broken per server tick. Range: 1-2147483647.");
        maxBlastBlocks = config.getInt("maxBlastBlocks", category, DEFAULT_MAX_BLAST_BLOCKS, 32,
                Integer.MAX_VALUE, "Maximum blocks in blast modes. Range: 32-2147483647.");
        maxBlastBlocksPerTick = config.getInt("maxBlastBlocksPerTick", category,
                DEFAULT_MAX_BLAST_BLOCKS_PER_TICK, 1, Integer.MAX_VALUE,
                "Maximum blast blocks broken per server tick. Range: 1-2147483647.");
        blastSearchDistance = config.getInt("blastSearchDistance", category, DEFAULT_BLAST_SEARCH_DISTANCE, 3,
                Integer.MAX_VALUE, "Maximum blast search distance. Range: 3-2147483647.");
        blastChunkScansPerTick = config.getInt("blastChunkScansPerTick", category,
                DEFAULT_BLAST_CHUNK_SCANS_PER_TICK, 1, MAX_BLAST_CHUNK_SCANS_PER_TICK,
                "Maximum sparse blast chunks scanned per server tick. Range: 1-1024.");
        blastManhattan = config.getBoolean("blastManhattan", category, DEFAULT_BLAST_MANHATTAN,
                "Use Manhattan distance for blast searches.");
        defaultMode = config.getInt("defaultMode", category, DEFAULT_MODE_ORDINAL, 0,
                ChainMode.values().length - 1, "Default chain mode.");
        blastLowTpsThreshold = config.getInt("blastLowTpsThreshold", category, DEFAULT_BLAST_LOW_TPS_THRESHOLD, 5,
                20, "Low TPS warning threshold.");
        blastAutoReduceRadius = config.getBoolean("blastAutoReduceRadius", category,
                DEFAULT_BLAST_AUTO_REDUCE_RADIUS, "Reduce blast radius when TPS is low.");
        consumeHunger = config.getBoolean("consumeHunger", category, DEFAULT_CONSUME_HUNGER,
                "Whether chain mining consumes hunger.");
        debugLogging = config.getBoolean("debugLogging", category, DEFAULT_DEBUG_LOGGING,
                "Enable temporary server-side diagnostics for chain mining.");
        String[] values = config.getStringList("blockWhitelist", category,
                DEFAULT_BLOCK_WHITELIST.toArray(new String[DEFAULT_BLOCK_WHITELIST.size()]),
                "Additional targets for the all-ores blast mode (BLAST_ORES): block IDs, * wildcards, or ore:<OreDictionary name>. Default: *ore.");
        blockWhitelist = new ArrayList<String>(normalizeWhitelist(Arrays.asList(values)));
        if (config.hasChanged()) {
            config.save();
        }
    }

    public static void save() {
        if (config == null) {
            return;
        }
        String category = "chain_mining";
        config.get(category, "maxNormalBlocks", DEFAULT_MAX_NORMAL_BLOCKS).set(maxNormalBlocks);
        config.get(category, "maxNormalBlocksPerTick", DEFAULT_MAX_NORMAL_BLOCKS_PER_TICK).set(maxNormalBlocksPerTick);
        config.get(category, "maxBlastBlocks", DEFAULT_MAX_BLAST_BLOCKS).set(maxBlastBlocks);
        config.get(category, "maxBlastBlocksPerTick", DEFAULT_MAX_BLAST_BLOCKS_PER_TICK).set(maxBlastBlocksPerTick);
        config.get(category, "blastSearchDistance", DEFAULT_BLAST_SEARCH_DISTANCE).set(blastSearchDistance);
        config.get(category, "blastChunkScansPerTick", DEFAULT_BLAST_CHUNK_SCANS_PER_TICK).set(blastChunkScansPerTick);
        config.get(category, "blastManhattan", DEFAULT_BLAST_MANHATTAN).set(blastManhattan);
        config.get(category, "defaultMode", DEFAULT_MODE_ORDINAL).set(defaultMode);
        config.get(category, "blastLowTpsThreshold", DEFAULT_BLAST_LOW_TPS_THRESHOLD).set(blastLowTpsThreshold);
        config.get(category, "blastAutoReduceRadius", DEFAULT_BLAST_AUTO_REDUCE_RADIUS).set(blastAutoReduceRadius);
        config.get(category, "consumeHunger", DEFAULT_CONSUME_HUNGER).set(consumeHunger);
        config.get(category, "debugLogging", DEFAULT_DEBUG_LOGGING).set(debugLogging);
        config.get(category, "blockWhitelist",
                DEFAULT_BLOCK_WHITELIST.toArray(new String[DEFAULT_BLOCK_WHITELIST.size()]))
                .set(blockWhitelist.toArray(new String[blockWhitelist.size()]));
        config.save();
    }

    static List<String> parseWhitelistText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return normalizeWhitelist(Arrays.asList(text.split("[,;\\r\\n]+")));
    }

    static List<String> normalizeWhitelist(Iterable<?> values) {
        Set<String> normalized = new LinkedHashSet<String>();
        if (values == null) {
            return new ArrayList<String>();
        }
        for (Object value : values) {
            if (!(value instanceof String)) {
                continue;
            }
            String rule = normalizeBlockId((String) value);
            if (rule != null) {
                normalized.add(rule);
            }
            if (normalized.size() >= MAX_WHITELIST_ENTRIES) {
                break;
            }
        }
        return new ArrayList<String>(normalized);
    }

    static String whitelistText() {
        return joinRules(effectiveWhitelist(blockWhitelist));
    }

    static List<String> effectiveWhitelist(Iterable<?> values) {
        return normalizeWhitelist(values);
    }

    static String normalizeBlockId(String value) {
        if (value == null) {
            return null;
        }
        String rule = value.trim();
        if (rule.length() == 0 || rule.length() > MAX_WHITELIST_ENTRY_LENGTH || rule.startsWith("#")) {
            return null;
        }
        if (rule.regionMatches(true, 0, "ore:", 0, 4)) {
            String oreName = rule.substring(4).trim();
            if (!oreName.matches("[a-zA-Z0-9_.-]+")) {
                return null;
            }
            return "ore:" + oreName.toLowerCase(Locale.ROOT);
        }

        String id = rule.toLowerCase(Locale.ROOT);
        boolean wildcard = id.indexOf('*') >= 0;
        if (!id.contains(":") && !wildcard) {
            id = "minecraft:" + id;
        }
        if (wildcard) {
            if (id.indexOf(':') < 0) {
                return id.matches("[a-z0-9_.*-]+") ? id : null;
            }
            String[] parts = id.split(":", 2);
            if (parts.length != 2 || !parts[0].matches("[a-z0-9_.-]+")
                    || !parts[1].matches("[a-z0-9_.*-]+")) {
                return null;
            }
            return parts[0] + ":" + parts[1];
        }
        try {
        String[] parts = id.split(":", -1);
        if (parts.length != 2 || !parts[0].matches("[a-z0-9_.-]+")
                || !parts[1].matches("[a-z0-9_./-]+")) {
            return null;
        }
        try {
            ResourceLocation location = new ResourceLocation(id);
            return location.toString();
        } catch (RuntimeException ignored) {
            return null;
        }
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static String normalizeRule(String value) {
        return normalizeBlockId(value);
    }

    private static String joinRules(Iterable<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(value);
        }
        return result.toString();
    }
}
